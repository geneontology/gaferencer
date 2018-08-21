package org.geneontology.gaferencer

import scala.collection.JavaConverters._
import scala.io.Source

import utest._

import org.prefixcommons.CurieUtil
import org.semanticweb.owlapi.apibinding.OWLManager

object TestInferences extends TestSuite {

  val tests = Tests {

    "Check inferred annotations" - {
      val manager = OWLManager.createOWLOntologyManager()
      val ontology = manager.loadOntologyFromOntologyDocument(this.getClass.getResourceAsStream("go_xp_predictor_test_subset.ofn"))
      val cu = new CurieUtil(Map("GO" -> "http://purl.obolibrary.org/obo/GO_").asJava)
      val gaferences = Gaferencer.processGAF(Source.fromInputStream(this.getClass.getResourceAsStream("xp_inference_test.gaf"), "UTF-8"), ontology, cu)
      gaferences.foreach(println)
    }

  }

}