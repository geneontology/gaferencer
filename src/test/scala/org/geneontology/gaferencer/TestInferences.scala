package org.geneontology.gaferencer

import scala.jdk.CollectionConverters._
import scala.io.Source

import utest._

import org.prefixcommons.CurieUtil
import org.semanticweb.owlapi.apibinding.OWLManager
import org.phenoscape.scowl._

object TestInferences extends TestSuite {

  val InvolvedIn = ObjectProperty("http://purl.obolibrary.org/obo/RO_0002331")
  val OccursIn = ObjectProperty("http://purl.obolibrary.org/obo/BFO_0000066")
  val Regulates = ObjectProperty("http://purl.obolibrary.org/obo/RO_0002211")
  val Mouse = Class("http://purl.obolibrary.org/obo/NCBITaxon_10090")

  val tests = Tests {

    test("Check inferred annotations") - {
      val manager = OWLManager.createOWLOntologyManager()
      val ontology =
        manager.loadOntologyFromOntologyDocument(this.getClass.getResourceAsStream("go_xp_predictor_test_subset.ofn"))
      val cu = MultiCurieUtil(Seq(new CurieUtil(Map("GO" -> "http://purl.obolibrary.org/obo/GO_").asJava)))
      val gaferences = Gaferencer.processGAF(
        Source.fromInputStream(this.getClass.getResourceAsStream("xp_inference_test.gaf"), "UTF-8"),
        ontology,
        cu)
      val grouped = gaferences.groupBy(_.annotation)
      assert(
        grouped(
          ExtendedAnnotation(Link(InvolvedIn, Class("http://purl.obolibrary.org/obo/GO_0006412")),
                             Mouse,
                             Set(Link(OccursIn, Class("http://purl.obolibrary.org/obo/GO_0005739"))))
        ).head.inferences(Link(InvolvedIn, Class("http://purl.obolibrary.org/obo/GO_0032543")))
      )
      assert(
        grouped(
          ExtendedAnnotation(Link(InvolvedIn, Class("http://purl.obolibrary.org/obo/GO_0048585")),
                             Mouse,
                             Set(Link(Regulates, Class("http://purl.obolibrary.org/obo/GO_0051594"))))
        ).head.inferences(Link(InvolvedIn, Class("http://purl.obolibrary.org/obo/GO_2000970")))
      )
    }

    test("Check taxon constraints") - {
      val manager = OWLManager.createOWLOntologyManager()
      val ontology =
        manager.loadOntologyFromOntologyDocument(this.getClass.getResourceAsStream("taxon_constraint_test.ofn"))
      val cu = MultiCurieUtil(Seq(new CurieUtil(Map("GO" -> "http://purl.obolibrary.org/obo/GO_").asJava)))
      val gaferences = Gaferencer.processGAF(
        Source.fromInputStream(this.getClass.getResourceAsStream("taxon_constraint_test.gaf"), "UTF-8"),
        ontology,
        cu)
      val grouped = gaferences.groupBy(c => c.annotation.annotation.target -> c.annotation.taxon)
      assert(
        !grouped
          .getOrElse(Class("http://purl.obolibrary.org/obo/GO_0009272") -> Class(
                       "http://purl.obolibrary.org/obo/NCBITaxon_10090"),
                     Set.empty)
          .head
          .satisfiable
      )
      assert(
        grouped
          .getOrElse(Class("http://purl.obolibrary.org/obo/GO_0009272") -> Class(
                       "http://purl.obolibrary.org/obo/NCBITaxon_40296"),
                     Set.empty)
          .isEmpty)
    }

  }

}
