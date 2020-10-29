package org.geneontology.gaferencer

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.{Base64, UUID}

import io.circe._
import io.circe.syntax._
import org.geneontology.gaferencer.Vocab._
import org.phenoscape.scowl._
import org.semanticweb.elk.owlapi.ElkReasonerFactory
import org.semanticweb.owlapi.model._
import org.semanticweb.owlapi.model.parameters.Imports
import org.semanticweb.owlapi.reasoner.OWLReasoner
import scalaz.Scalaz._

import scala.jdk.CollectionConverters._
import scala.io.Source
import scala.util.matching.Regex

object Gaferencer {

  val LinkRegex: Regex = raw"(.+)\((.+)\)".r

  def processGAF(file: Source, ontology: OWLOntology, curieUtil: MultiCurieUtil): Set[Gaferences] = {
    val (termsWithTaxa, extendedAnnotations) = parseGAF(file, ontology, curieUtil)
    val (taxaTermsToTerms, taxaAxioms) = nameExpressions(termsWithTaxa)
    val (annotationsToTerms, annotationsAxioms) = nameExpressions(extendedAnnotations)
    val manager = ontology.getOWLOntologyManager
    val reasoner = new ElkReasonerFactory().createReasoner(ontology)
    val (possibleAnnotationClassesToLinks, possibleAnnotationAxioms) = AnnotationRelationsByAspect.keySet
      .map(materializeAnnotationRelations(_, reasoner))
      .unzip
      .mapElements(_.flatten.toMap, _.flatten)
    manager.addAxioms(ontology, possibleAnnotationAxioms.asJava)
    manager.addAxioms(ontology, taxaAxioms.asJava)
    manager.addAxioms(ontology, annotationsAxioms.asJava)
    reasoner.flush()
    val unsatisfiable = reasoner.getUnsatisfiableClasses.getEntities.asScala
    val unsatisfiableTermsWithTaxa = (for {
      (termWithTaxon, taxonomicClass) <- taxaTermsToTerms
      if unsatisfiable(taxonomicClass)
    } yield termWithTaxon).to(Set)
    val gaferences = (for {
      (annotation, term) <- annotationsToTerms
      isSatisfiable = !unsatisfiable(term)
      unsatisfiableTaxon = unsatisfiableTermsWithTaxa(TermWithTaxon(annotation.annotation.target, annotation.taxon))
      inferredAnnotations =
        if (isSatisfiable)
          reasoner
            .getSuperClasses(term, true)
            .getFlattened
            .asScala
            .flatMap(possibleAnnotationClassesToLinks.get)
            .to(Set) - annotation.annotation
        else Set.empty[Link]
      if inferredAnnotations.nonEmpty || !isSatisfiable
    } yield Gaferences(annotation, inferredAnnotations, isSatisfiable, unsatisfiableTaxon)).to(Set)
    reasoner.dispose()
    gaferences
  }

  def processTaxonList(file: Source, ontology: OWLOntology, curieUtil: MultiCurieUtil): Set[TaxonCheck] = {
    val taxa = file.getLines().flatMap(l => curieUtil.getIRI(l.trim).map(Class(_))).to(Set)
    scribe.info(s"Checking ${taxa.size} taxon terms")
    val reasoner = new ElkReasonerFactory().createReasoner(ontology)
    val goTerms = Set(MF, BP, CC).flatMap(reasoner.getSubClasses(_, false).getFlattened.asScala)
    scribe.info(s"Checking ${goTerms.size} GO terms")
    val termsWithTaxa = for {
      goTerm <- goTerms
      taxon <- taxa
    } yield TermWithTaxon(goTerm, taxon)
    scribe.info(s"Checking ${termsWithTaxa.size} combinations")
    val (taxaTermsToTerms, taxaAxioms) = nameExpressions(termsWithTaxa)
    val manager = ontology.getOWLOntologyManager
    manager.addAxioms(ontology, taxaAxioms.asJava)
    reasoner.flush()
    val unsatisfiable = reasoner.getUnsatisfiableClasses.getEntities.asScala
    scribe.info(s"Unsatisfiable classes: ${unsatisfiable.size}")
    val taxonChecks = (for {
      (termWithTaxon, taxonomicClass) <- taxaTermsToTerms
      isUnsatisfiable = unsatisfiable(taxonomicClass)
    } yield TaxonCheck(termWithTaxon.term, termWithTaxon.taxon, !isUnsatisfiable)).to(Set)
    reasoner.dispose()
    taxonChecks
  }

  def parseGAF(file: Source,
               ontology: OWLOntology,
               curieUtil: MultiCurieUtil): (Set[TermWithTaxon], Set[ExtendedAnnotation]) = {
    val propertyByName: Map[String, OWLObjectProperty] = indexPropertiesByName(ontology)
    file
      .getLines()
      .filterNot(_.startsWith("!"))
      .flatMap(processLine(_, propertyByName, curieUtil))
      .to(Iterable)
      .unzip
      .mapElements(_.to(Set), _.to(Set))
  }

  def nameExpressions[A <: Expressable](es: Iterable[A]): (Map[A, OWLClass], Set[OWLAxiom]) =
    es.map { t =>
      val term = newUUIDClass()
      val axiom = term EquivalentTo t.toExpression
      (t -> term, axiom)
    }.unzip
      .mapElements(_.toMap, _.to(Set))

  def materializeAnnotationRelations(branch: OWLClass, reasoner: OWLReasoner): (Map[OWLClass, Link], Set[OWLAxiom]) = {
    val (classToLink, axioms) = (for {
      term <-
        reasoner
          .getSubClasses(branch, false)
          .getFlattened
          .asScala
          .to(Set)
          .filter(_.getIRI.toString.startsWith(GOPrefix)) + branch
      relation <- AnnotationRelationsByAspect(branch)
      annotationTerm = newUUIDClass()
      link = Link(relation, term)
    } yield (annotationTerm -> link, annotationTerm EquivalentTo link.toRestriction)).unzip
    (classToLink.toMap, axioms.to(Set))
  }

  def processLine(line: String,
                  propertyByName: Map[String, OWLObjectProperty],
                  curieUtil: MultiCurieUtil): Set[(TermWithTaxon, ExtendedAnnotation)] = {
    val items = line.split("\t", -1)
    if (items.size < 13) Set.empty
    else {
      val maybeQualifier = (items(3).split(raw"\|", -1).map(_.trim).filter(_.nonEmpty).to(Set) - "NOT").headOption
      val maybeRelation = maybeQualifier
        .map { qualifier =>
          val maybeQualifierProp = Qualifiers.get(qualifier)
          if (maybeQualifierProp.isEmpty) scribe.warn(s"Row with bad qualifier: $qualifier")
          maybeQualifierProp
        }
        .getOrElse {
          val aspect = items(8).trim
          AspectToGAFRelation.get(aspect)
        }
      val maybeTaxon =
        items(12).split(raw"\|", -1).headOption.map(_.trim.replace("taxon:", TaxonPrefix)).map(Class(_))
      if (maybeTaxon.isEmpty) scribe.warn(s"Skipping row with badly formatted taxon: ${items(12)}")
      val term = Class(items(4).trim.replace("GO:", GOPrefix))
      (for {
        taxon <- maybeTaxon.toIterable
        relation <- maybeRelation.toIterable
        conjunction <- if (items.size > 15) items(15).split("\\|", -1) else Array("")
        links = conjunction.split(",", -1).to(Set).map(_.trim).flatMap(parseLink(_, propertyByName, curieUtil).to(Set))
      } yield (TermWithTaxon(term, taxon), ExtendedAnnotation(Link(relation, term), taxon, links))).to(Set)
    }
  }

  def parseLink(text: String, propertyByName: Map[String, OWLObjectProperty], curieUtil: MultiCurieUtil): Option[Link] =
    text match {
      case LinkRegex(relation, term) =>
        val maybeProperty = propertyByName.get(relation.trim)
        val maybeFiller = curieUtil.getIRI(term).map(Class(_))
        if (maybeFiller.isEmpty) scribe.warn(s"Could not find IRI for annotation extension filler: $term")
        for {
          property <- maybeProperty
          filler <- maybeFiller
        } yield Link(property, filler)
      case "" => None
      case _ =>
        scribe.warn(s"Skipping badly formatted extension: $text")
        None
    }

  def indexPropertiesByName(ontology: OWLOntology): Map[String, OWLObjectProperty] =
    (for {
      ont <- ontology.getImportsClosure.asScala
      property <- ont.getObjectPropertiesInSignature(Imports.INCLUDED).asScala
      AnnotationAssertion(_, RDFSLabel, _, label ^^ _) <- ontology.getAnnotationAssertionAxioms(property.getIRI).asScala
      underscoreLabel = label.replace(" ", "_")
    } yield underscoreLabel -> property).toMap

  def newUUIDClass(): OWLClass = Class(s"urn:uuid:${UUID.randomUUID().toString}")

  def taxonChecksToTable(checks: Iterable[TaxonCheck], curieUtil: MultiCurieUtil): String = {
    val grouped = checks.groupBy(_.term)
    val sortedTaxa = checks.map(_.taxon).to(Set).toSeq.sortBy(_.getIRI.toString)
    val sortedTerms = grouped.keys.toSeq.sortBy(_.getIRI.toString)
    val header = ("GOterm" +: sortedTaxa.map { t =>
      val iri = t.getIRI.toString
      curieUtil.getCURIE(iri).getOrElse(iri)
    }).mkString("\t")
    val table = sortedTerms
      .map { goTerm =>
        val groupedByTaxon = grouped(goTerm).groupBy(_.taxon)
        val values = sortedTaxa
          .map { taxon =>
            val check = groupedByTaxon(taxon).head
            if (check.satisfiable) "1" else "0"
          }
          .mkString("\t")
        val goIRI = goTerm.getIRI.toString
        val goID = curieUtil.getCURIE(goIRI).getOrElse(goIRI)
        s"$goID\t$values"
      }
      .mkString("\n")
    s"$header\n$table"
  }

}

final case class Link(relation: OWLObjectProperty, target: OWLClass) {

  def toRestriction: OWLObjectSomeValuesFrom = relation some target

}

object Link {

  implicit val encoder: Encoder[Link] =
    Encoder.forProduct2("relation", "term")(l => (l.relation.getIRI.toString, l.target.getIRI.toString))

}

sealed trait Expressable {

  def toExpression: OWLClassExpression

}

final case class ExtendedAnnotation(annotation: Link, taxon: OWLClass, extension: Set[Link]) extends Expressable {

  def hashIRI: String = {
    val digest = MessageDigest.getInstance("SHA-256")
    val hash = digest.digest(this.asJson.toString.getBytes(StandardCharsets.UTF_8))
    s"http://example.org/${Base64.getEncoder.encodeToString(hash)}"
  }

  // We intersect with a dummy class to ensure this annotation is not subsumed by any other annotation (and thus blocked from deepenings)
  def toExpression: OWLClassExpression =
    Class(hashIRI) and (annotation.relation some ObjectIntersectionOf(
      Set(annotation.target, InTaxon some taxon) ++ extension.map(_.toRestriction)))

}

object ExtendedAnnotation {

  implicit val encoder: Encoder[ExtendedAnnotation] = Encoder.forProduct3("annotation", "taxon", "extension")(ea =>
    (ea.annotation, ea.taxon.getIRI.toString, ea.extension))

}

final case class TermWithTaxon(term: OWLClass, taxon: OWLClass) extends Expressable {

  def toExpression: OWLClassExpression = term and (InTaxon some taxon)

}

final case class Gaferences(annotation: ExtendedAnnotation,
                            inferences: Set[Link],
                            satisfiable: Boolean,
                            taxonProblem: Boolean)

final case class TaxonCheck(term: OWLClass, taxon: OWLClass, satisfiable: Boolean)

object TaxonCheck {

  implicit val encoder: Encoder[TaxonCheck] = Encoder.forProduct3("term", "taxon", "satisfiable")(t =>
    (t.term.getIRI.toString, t.taxon.getIRI.toString, t.satisfiable))

}
