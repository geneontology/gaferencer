package org.geneontology.gaferencer

import org.geneontology.gaferencer.Vocab.AspectToGAFRelation
import org.phenoscape.scowl._
import org.prefixcommons.CurieUtil
import org.semanticweb.owlapi.model.OWLObjectProperty
import utest._

import scala.jdk.CollectionConverters._

object TestParsing extends TestSuite {

  val Mouse = Class("http://purl.obolibrary.org/obo/NCBITaxon_10090")
  val GOTerm = Class("http://purl.obolibrary.org/obo/GO_0006412")
  val Regulates = ObjectProperty("http://example.org/regulates")
  val NegativelyRegulates = ObjectProperty("http://example.org/negatively_regulates")
  val OccursIn = ObjectProperty("http://example.org/occurs_in")

  val tests = Tests {

    test("Check parsing of annotation extensions") - {
      val line1 = "FOO\tFOO:1\tfoo1\t\tGO:0006412\tTEST:1\tIDA\t\tP\t\t\tgene\ttaxon:10090\t20100209\tFOO\t\t"
      val line2 =
        "FOO\tFOO:2\tfoo2\t\tGO:0006412\tTEST:1\tIDA\t\tP\t\t\tgene\ttaxon:10090\t20100209\tFOO\tregulates(GO:0051594)\t"
      val line3 =
        "FOO\tFOO:2\tfoo2\t\tGO:0006412\tTEST:1\tIDA\t\tP\t\t\tgene\ttaxon:10090\t20100209\tFOO\tregulates(GO:0051594),occurs_in(GO:0005739)\t"
      val line4 =
        "FOO\tFOO:2\tfoo2\t\tGO:0006412\tTEST:1\tIDA\t\tP\t\t\tgene\ttaxon:10090\t20100209\tFOO\tregulates(GO:0051594),occurs_in(GO:0005739)|occurs_in(GO:0005739),negatively_regulates(GO:0051594)\t"
      val line5 = "FOO\tFOO:1\tfoo1\t\tGO:0006412\tTEST:1\tIDA\t\tP\t\t\tgene\ttaxon:10090\t20100209\tFOO"
      val line6 = "FOO\tFOO:1\tfoo1\t\tGO:0006412"
      val properties =
        Map("regulates" -> Regulates, "negatively_regulates" -> NegativelyRegulates, "occurs_in" -> OccursIn)
      val cu = MultiCurieUtil(Seq(new CurieUtil(Map("GO" -> "http://purl.obolibrary.org/obo/GO_").asJava)))
      val relation = AspectToGAFRelation("P")
      val res1 = Gaferencer.processLine(line1, properties, cu)
      assert(res1.size == 1)
      assert(res1(TermWithTaxon(GOTerm, Mouse) -> ExtendedAnnotation(Link(relation, GOTerm), Mouse, Set.empty)))
      val res2 = Gaferencer.processLine(line2, properties, cu)
      assert(res2.size == 1)
      assert(
        res2(
          TermWithTaxon(GOTerm, Mouse) -> ExtendedAnnotation(
            Link(relation, GOTerm),
            Mouse,
            Set(Link(Regulates, Class("http://purl.obolibrary.org/obo/GO_0051594")))))
      )
      val res3 = Gaferencer.processLine(line3, properties, cu)
      assert(res3.size == 1)
      assert(
        res3(
          TermWithTaxon(GOTerm, Mouse) -> ExtendedAnnotation(
            Link(relation, GOTerm),
            Mouse,
            Set(Link(Regulates, Class("http://purl.obolibrary.org/obo/GO_0051594")),
                Link(OccursIn, Class("http://purl.obolibrary.org/obo/GO_0005739")))
          )
        )
      )
      val res4 = Gaferencer.processLine(line4, properties, cu)
      assert(res4.size == 2)
      assert(
        res4(
          TermWithTaxon(GOTerm, Mouse) -> ExtendedAnnotation(
            Link(relation, GOTerm),
            Mouse,
            Set(Link(Regulates, Class("http://purl.obolibrary.org/obo/GO_0051594")),
                Link(OccursIn, Class("http://purl.obolibrary.org/obo/GO_0005739")))
          )
        )
      )
      assert(
        res4(
          TermWithTaxon(GOTerm, Mouse) -> ExtendedAnnotation(
            Link(relation, GOTerm),
            Mouse,
            Set(Link(NegativelyRegulates, Class("http://purl.obolibrary.org/obo/GO_0051594")),
                Link(OccursIn, Class("http://purl.obolibrary.org/obo/GO_0005739")))
          )
        )
      )
      val res5 = Gaferencer.processLine(line5, properties, cu)
      assert(res5.size == 1)
      val res6 = Gaferencer.processLine(line6, properties, cu)
      assert(res6.isEmpty)
    }

    test("Check parsing of qualifier for GAF 2.1 vs 2.2") - {
      val cu = MultiCurieUtil(Seq(new CurieUtil(Map("GO" -> "http://purl.obolibrary.org/obo/GO_").asJava)))
      val properties = Map.empty[String, OWLObjectProperty]
      val line1 = "FOO\tFOO:1\tfoo1\t\tGO:0006412\tTEST:1\tIDA\t\tP\t\t\tgene\ttaxon:10090\t20100209\tFOO\t\t"
      val line2 = "FOO\tFOO:1\tfoo1\tNOT\tGO:0006412\tTEST:1\tIDA\t\tP\t\t\tgene\ttaxon:10090\t20100209\tFOO\t\t"
      val line3 =
        "FOO\tFOO:1\tfoo1\tinvolved_in\tGO:0006412\tTEST:1\tIDA\t\tP\t\t\tgene\ttaxon:10090\t20100209\tFOO\t\t"
      val line4 =
        "FOO\tFOO:1\tfoo1\tacts_upstream_of\tGO:0006412\tTEST:1\tIDA\t\tP\t\t\tgene\ttaxon:10090\t20100209\tFOO\t\t"
      val line5 =
        "FOO\tFOO:1\tfoo1\tNOT|acts_upstream_of\tGO:0006412\tTEST:1\tIDA\t\tP\t\t\tgene\ttaxon:10090\t20100209\tFOO\t\t"
      val res1 = Gaferencer.processLine(line1, properties, cu)
      val res2 = Gaferencer.processLine(line2, properties, cu)
      val res3 = Gaferencer.processLine(line3, properties, cu)
      val res4 = Gaferencer.processLine(line4, properties, cu)
      val res5 = Gaferencer.processLine(line5, properties, cu)
      assert(res1.size == 1)
      assert(res1.head._2.annotation.relation == Vocab.InvolvedIn)
      assert(res2.size == 1)
      assert(res2.head._2.annotation.relation == Vocab.InvolvedIn)
      assert(res3.size == 1)
      assert(res3.head._2.annotation.relation == Vocab.InvolvedIn)
      assert(res4.size == 1)
      assert(res4.head._2.annotation.relation == Vocab.ActsUpstreamOf)
      assert(res5.size == 1)
      assert(res5.head._2.annotation.relation == Vocab.ActsUpstreamOf)
    }

  }

}
