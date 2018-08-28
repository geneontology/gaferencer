package org.geneontology.gaferencer

import java.io.File
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64
import java.util.Optional
import java.util.UUID

import scala.collection.JavaConverters._
import scala.io.Source

import org.phenoscape.scowl._
import org.prefixcommons.CurieUtil
import org.semanticweb.elk.owlapi.ElkReasonerFactory
import org.semanticweb.owlapi.model.IRI
import org.semanticweb.owlapi.model.OWLAxiom
import org.semanticweb.owlapi.model.OWLClass
import org.semanticweb.owlapi.model.OWLClassExpression
import org.semanticweb.owlapi.model.OWLObjectProperty
import org.semanticweb.owlapi.model.OWLObjectSomeValuesFrom
import org.semanticweb.owlapi.model.OWLOntology
import org.semanticweb.owlapi.model.parameters.Imports
import org.semanticweb.owlapi.reasoner.OWLReasoner

import com.typesafe.scalalogging.LazyLogging

import Vocab._
import io.circe._
import io.circe.syntax._
import scalaz._
import scalaz.Scalaz._

object Gaferencer extends LazyLogging {

  val LinkRegex = raw"(.+)\((.+)\)".r

  def processGAF(file: Source, ontology: OWLOntology, curieUtil: MultiCurieUtil): Set[Gaferences] = {
    val propertyByName: Map[String, OWLObjectProperty] = indexPropertiesByName(ontology)
    val tuples = file.getLines
      .filterNot(_.startsWith("!"))
      .map(processLine(_, propertyByName, curieUtil))
      .flatten.toSet
    val (tuplesTerms, tupleAxioms) = (tuples.map { t =>
      val term = newUUIDClass()
      val axiom = term EquivalentTo t.toExpression
      (t -> term, axiom)
    }).unzip
    val manager = ontology.getOWLOntologyManager
    val reasoner = new ElkReasonerFactory().createReasoner(ontology)
    val (annotationClassesToLinks, annotationAxioms) = AnnotationRelationsByAspect.keySet.map(materializeAnnotationRelations(_, reasoner))
      .unzip.mapElements(_.flatten.toMap, _.flatten)
    manager.addAxioms(ontology, annotationAxioms.asJava)
    manager.addAxioms(ontology, tupleAxioms.asJava)
    manager.saveOntology(ontology, IRI.create(new File("enhanced_ontology.ofn")))
    reasoner.flush()
    val gaferences = for {
      (tuple, term) <- tuplesTerms
    } yield if (reasoner.isSatisfiable(term))
      Gaferences(
        tuple,
        reasoner.getSuperClasses(term, true).getFlattened.asScala
          .flatMap(annotationClassesToLinks.get).toSet - tuple.annotation,
        true)
    else Gaferences(tuple, Set.empty, false)
    reasoner.dispose()
    gaferences
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

  def processLine(line: String, propertyByName: Map[String, OWLObjectProperty], curieUtil: MultiCurieUtil): Option[GAFTuple] = {
    val items = line.split("\t", -1)
    val maybeTaxon = items(12).split(raw"\|", -1).headOption.map(_.trim.replaceAllLiterally("taxon:", TaxonPrefix)).map(Class(_))
    if (maybeTaxon.isEmpty) logger.warn(s"Skipping row with badly formatted taxon: ${items(12)}")
    val aspect = items(8).trim
    val relation = AspectToGAFRelation(aspect)
    val term = Class(items(4).trim.replaceAllLiterally("GO:", GOPrefix))
    val links = items(15).split(",", -1).toSet[String].map(_.trim).flatMap(parseLink(_, propertyByName, curieUtil).toSet)
    for {
      taxon <- maybeTaxon
    } yield GAFTuple(taxon, Link(relation, term), links)
  }

  def parseLink(text: String, propertyByName: Map[String, OWLObjectProperty], curieUtil: MultiCurieUtil): Option[Link] = text match {
    case LinkRegex(relation, term) =>
      val maybeProperty = propertyByName.get(relation.trim)
      val termComponents = term.split(":", 2)
      val maybeFiller = curieUtil.getIRI(term).map(Class(_))
      if (maybeFiller.isEmpty) logger.warn(s"Could not find IRI for annotation extension filler: $term")
      for {
        property <- maybeProperty
        filler <- maybeFiller
      } yield Link(property, filler)
    case "" => None
    case _ =>
      logger.warn(s"Skipping badly formatted extension: $text")
      None
  }

  def indexPropertiesByName(ontology: OWLOntology): Map[String, OWLObjectProperty] = (for {
    ont <- ontology.getImportsClosure.asScala
    property <- ontology.getObjectPropertiesInSignature(Imports.INCLUDED).asScala
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

final case class GAFTuple(taxon: OWLClass, annotation: Link, extension: Set[Link]) {

  def hashIRI: String = {
    val digest = MessageDigest.getInstance("SHA-256")
    val hash = digest.digest(this.asJson.toString.getBytes(StandardCharsets.UTF_8))
    s"http://example.org/${Base64.getEncoder().encodeToString(hash)}"
  }

  // We intersect with a dummy class to ensure this annotation is not subsumed by any other annotation (and thus blocked from deepenings)
  def toExpression: OWLClassExpression = Class(hashIRI) and (annotation.relation some (annotation.target and ObjectIntersectionOf(extension.map(_.toRestriction)) and (InTaxon some taxon)))

}

object GAFTuple {

  implicit val encoder: Encoder[GAFTuple] = Encoder.forProduct3("taxon", "annotation", "extension")(t => (t.taxon.getIRI.toString, t.annotation, t.extension))

}

final case class Gaferences(tuple: GAFTuple, inferences: Set[Link], satisfiable: Boolean)
