package org.geneontology.gaffer

import java.io.File
import java.util.Optional
import java.util.UUID

import scala.collection.JavaConverters._
import scala.io.Source

import org.phenoscape.scowl._
import org.prefixcommons.CurieUtil
import org.semanticweb.elk.owlapi.ElkReasonerFactory
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

object Gaffer extends LazyLogging {

  val LinkRegex = raw"(.+)\((.+)\)".r

  def processGAF(file: File, ontology: OWLOntology, curieUtil: CurieUtil): Set[Gaferences] = {
    val propertyByName: Map[String, OWLObjectProperty] = indexPropertiesByName(ontology)
    val tuples = Source.fromFile(file, "utf-8").getLines
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
    val gaferences = for {
      (tuple, term) <- tuplesTerms
    } yield if (reasoner.isSatisfiable(term))
      Gaferences(
        tuple,
        reasoner.getSuperClasses(term, true).getFlattened.asScala
          .flatMap(annotationClassesToLinks.get).toSet.filterNot(_ == tuple.annotation),
        true)
    else Gaferences(tuple, Set.empty, false)
    reasoner.dispose()
    gaferences
  }

  def materializeAnnotationRelations(branch: OWLClass, reasoner: OWLReasoner): (Map[OWLClass, Link], Set[OWLAxiom]) = {
    val (classToLink, axioms) = (for {
      term <- reasoner.getSubClasses(branch, false).getFlattened.asScala + branch
      relation <- AnnotationRelationsByAspect(branch)
      annotationTerm = newUUIDClass()
      link = Link(relation, term)
    } yield (annotationTerm -> link, annotationTerm EquivalentTo link.toRestriction)).unzip
    (classToLink.toMap, axioms.toSet)
  }

  def processLine(line: String, propertyByName: Map[String, OWLObjectProperty], curieUtil: CurieUtil): Option[GAFTuple] = {
    val items = line.split("\t", -1)
    val maybeTaxon = items(12).split("|", -1).headOption.map(_.trim.replaceAllLiterally("taxon:", TaxonPrefix)).map(Class(_))
    if (maybeTaxon.isEmpty) logger.warn(s"Skipping row with badly formatted taxon: ${items(12)}")
    val aspect = items(8).trim
    val relation = AspectToGAFRelation(aspect)
    val term = Class(items(4).trim.replaceAllLiterally("GO:", GOPrefix))
    val links = items(16).split(",", -1).map(_.trim).flatMap(parseLink(_, propertyByName, curieUtil)).toSet
    for {
      taxon <- maybeTaxon
    } yield GAFTuple(taxon, Link(relation, term), links)
  }

  def parseLink(text: String, propertyByName: Map[String, OWLObjectProperty], curieUtil: CurieUtil): Option[Link] = text match {
    case LinkRegex(relation, term) =>
      val maybeProperty = propertyByName.get(relation.trim)
      val termComponents = term.split(":", 2)
      val maybeFiller = if (termComponents.size == 2) curieUtil.getIri(termComponents(0)).asScala.map(prefix => Class(s"$prefix${termComponents(1)}"))
      else None
      for {
        property <- maybeProperty
        filler <- maybeFiller
      } yield Link(property, filler)
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

  implicit class OptionalConverter[T](val self: Optional[T]) extends AnyVal {

    def asScala: Option[T] = if (self.isPresent) Some(self.get) else None

  }

}

final case class Link(relation: OWLObjectProperty, target: OWLClass) {

  def toRestriction: OWLObjectSomeValuesFrom = relation some target

}

object Link {

  implicit val encoder: Encoder[Link] = Encoder.forProduct2("relation", "term")(l => (l.relation.getIRI.toString, l.target.getIRI.toString))

}

final case class GAFTuple(taxon: OWLClass, annotation: Link, extension: Set[Link]) {

  def toExpression: OWLClassExpression = annotation.relation some (annotation.target and ObjectIntersectionOf(extension.map(_.toRestriction)) and (InTaxon some taxon))

}

object GAFTuple {

  implicit val encoder: Encoder[GAFTuple] = Encoder.forProduct3("taxon", "annotation", "extension")(t => (t.taxon.getIRI.toString, t.annotation.asJson, t.extension.asJson))

}

final case class Gaferences(tuple: GAFTuple, inferences: Set[Link], satisfiable: Boolean)
