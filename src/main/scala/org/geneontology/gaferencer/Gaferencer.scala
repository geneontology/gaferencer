package org.geneontology.gaferencer

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.{Base64, UUID}

import com.typesafe.scalalogging.LazyLogging
import io.circe._
import io.circe.syntax._
import org.geneontology.gaferencer.Vocab._
import org.phenoscape.scowl._
import org.semanticweb.elk.owlapi.ElkReasonerFactory
import org.semanticweb.owlapi.model._
import org.semanticweb.owlapi.model.parameters.Imports
import org.semanticweb.owlapi.reasoner.OWLReasoner
import scalaz.Scalaz._

import scala.collection.JavaConverters._
import scala.io.Source
import scala.util.matching.Regex

object Gaferencer extends LazyLogging {

  val LinkRegex: Regex = raw"(.+)\((.+)\)".r

  def processGAF(file: Source, ontology: OWLOntology, curieUtil: MultiCurieUtil): (Set[Gaferences], Set[TaxonCheck]) = {
    val propertyByName: Map[String, OWLObjectProperty] = indexPropertiesByName(ontology)
    val (termsWithTaxa, extendedAnnotations) = file.getLines
      .filterNot(_.startsWith("!"))
      .flatMap(processLine(_, propertyByName, curieUtil)).toIterable.unzip
    val (taxaTermsToTerms, taxaAxioms) = termsWithTaxa.toSet[TermWithTaxon].map { t =>
      val term = newUUIDClass()
      val axiom = term EquivalentTo t.toExpression
      (t -> term, axiom)
    }.unzip
    val (annotationsToTerms, annotationsAxioms) =extendedAnnotations.toSet.filter(_.extension.nonEmpty).map { ea =>
      val term = newUUIDClass()
      val axiom = term EquivalentTo ea.toExpression
      (ea -> term, axiom)
    }.unzip
    val manager = ontology.getOWLOntologyManager
    val reasoner = new ElkReasonerFactory().createReasoner(ontology)
    val (possibleAnnotationClassesToLinks, possibleAnnotationAxioms) = AnnotationRelationsByAspect.keySet.map(materializeAnnotationRelations(_, reasoner))
      .unzip.mapElements(_.flatten.toMap, _.flatten)
    manager.addAxioms(ontology, possibleAnnotationAxioms.asJava)
    manager.addAxioms(ontology, taxaAxioms.asJava)
    manager.addAxioms(ontology, annotationsAxioms.asJava)
    reasoner.flush()
    val taxonChecks = for {
      (termWithTaxon, taxonomicClass) <- taxaTermsToTerms
      isSatisfiable = reasoner.isSatisfiable(taxonomicClass)
      if !isSatisfiable
    } yield TaxonCheck(termWithTaxon.term, termWithTaxon.taxon, isSatisfiable)
    val gaferences = for {
      (annotation, term) <- annotationsToTerms
      isSatisfiable = reasoner.isSatisfiable(term)
      inferredAnnotations = if (isSatisfiable) reasoner.getSuperClasses(term, true).getFlattened.asScala.flatMap(possibleAnnotationClassesToLinks.get).toSet - annotation.annotation else Set.empty[Link]
      if inferredAnnotations.nonEmpty
    } yield Gaferences(annotation, inferredAnnotations)
    reasoner.dispose()
    (gaferences, taxonChecks)
  }

  def materializeAnnotationRelations(branch: OWLClass, reasoner: OWLReasoner): (Map[OWLClass, Link], Set[OWLAxiom]) = {
    val (classToLink, axioms) = (for {
      term <- reasoner.getSubClasses(branch, false).getFlattened.asScala.filter(_.getIRI.toString.startsWith(GOPrefix)) + branch
      relation <- AnnotationRelationsByAspect(branch)
      annotationTerm = newUUIDClass()
      link = Link(relation, term)
    } yield (annotationTerm -> link, annotationTerm EquivalentTo link.toRestriction)).unzip
    (classToLink.toMap, axioms.toSet)
  }

  def processLine(line: String, propertyByName: Map[String, OWLObjectProperty], curieUtil: MultiCurieUtil): Option[(TermWithTaxon, ExtendedAnnotation)] = {
    val items = line.split("\t", -1)
    val maybeTaxon = items(12).split(raw"\|", -1).headOption.map(_.trim.replaceAllLiterally("taxon:", TaxonPrefix)).map(Class(_))
    if (maybeTaxon.isEmpty) logger.warn(s"Skipping row with badly formatted taxon: ${items(12)}")
    val aspect = items(8).trim
    val relation = AspectToGAFRelation(aspect)
    val term = Class(items(4).trim.replaceAllLiterally("GO:", GOPrefix))
    val links = items(15).split(",", -1).toSet[String].map(_.trim).flatMap(parseLink(_, propertyByName, curieUtil).toSet)
    for {
      taxon <- maybeTaxon
    } yield (TermWithTaxon(term, taxon), ExtendedAnnotation(Link(relation, term), links))
  }

  def parseLink(text: String, propertyByName: Map[String, OWLObjectProperty], curieUtil: MultiCurieUtil): Option[Link] = text match {
    case LinkRegex(relation, term) =>
      val maybeProperty = propertyByName.get(relation.trim)
      val maybeFiller = curieUtil.getIRI(term).map(Class(_))
      if (maybeFiller.isEmpty) logger.warn(s"Could not find IRI for annotation extension filler: $term")
      for {
        property <- maybeProperty
        filler <- maybeFiller
      } yield Link(property, filler)
    case ""                        => None
    case _                         =>
      logger.warn(s"Skipping badly formatted extension: $text")
      None
  }

  def indexPropertiesByName(ontology: OWLOntology): Map[String, OWLObjectProperty] = (for {
    ont <- ontology.getImportsClosure.asScala
    property <- ont.getObjectPropertiesInSignature(Imports.INCLUDED).asScala
    AnnotationAssertion(_, RDFSLabel, _, label ^^ _) <- ontology.getAnnotationAssertionAxioms(property.getIRI).asScala
    underscoreLabel = label.replaceAllLiterally(" ", "_")
  } yield underscoreLabel -> property).toMap

  def newUUIDClass(): OWLClass = Class(s"urn:uuid:${UUID.randomUUID().toString}")

}

final case class Link(relation: OWLObjectProperty, target: OWLClass) {

  def toRestriction: OWLObjectSomeValuesFrom = relation some target

}

object Link {

  implicit val encoder: Encoder[Link] = Encoder.forProduct2("relation", "term")(l => (l.relation.getIRI.toString, l.target.getIRI.toString))

}

final case class ExtendedAnnotation(annotation: Link, extension: Set[Link]) {

  def hashIRI: String = {
    val digest = MessageDigest.getInstance("SHA-256")
    val hash = digest.digest(this.asJson.toString.getBytes(StandardCharsets.UTF_8))
    s"http://example.org/${Base64.getEncoder.encodeToString(hash)}"
  }

  // We intersect with a dummy class to ensure this annotation is not subsumed by any other annotation (and thus blocked from deepenings)
  def toExpression: OWLClassExpression = Class(hashIRI) and (annotation.relation some (annotation.target and ObjectIntersectionOf(extension.map(_.toRestriction))))

}

object ExtendedAnnotation {

  implicit val encoder: Encoder[ExtendedAnnotation] = Encoder.forProduct2("annotation", "extension")(ea => (ea.annotation, ea.extension))

}

final case class TermWithTaxon(term: OWLClass, taxon: OWLClass) {

  def toExpression: OWLClassExpression = term and (InTaxon some taxon)

}

final case class GAFTuple(taxon: OWLClass, annotation: Link, extension: Set[Link]) {

  def hashIRI: String = {
    val digest = MessageDigest.getInstance("SHA-256")
    val hash = digest.digest(this.asJson.toString.getBytes(StandardCharsets.UTF_8))
    s"http://example.org/${Base64.getEncoder.encodeToString(hash)}"
  }

  // We intersect with a dummy class to ensure this annotation is not subsumed by any other annotation (and thus blocked from deepenings)
  def toExpression: OWLClassExpression = Class(hashIRI) and (annotation.relation some (annotation.target and ObjectIntersectionOf(extension.map(_.toRestriction)) and (InTaxon some taxon)))



}

object GAFTuple {

  implicit val encoder: Encoder[GAFTuple] = Encoder.forProduct3("taxon", "annotation", "extension")(t => (t.taxon.getIRI.toString, t.annotation, t.extension))

}

final case class Gaferences(annotation: ExtendedAnnotation, inferences: Set[Link])

final case class TaxonCheck(term: OWLClass, taxon: OWLClass, satisfiable: Boolean)

object TaxonCheck {

  implicit val encoder: Encoder[TaxonCheck] = Encoder.forProduct3("term", "taxon", "satisfiable")(t => (t.term.getIRI.toString, t.taxon.getIRI.toString, t.satisfiable))

}
