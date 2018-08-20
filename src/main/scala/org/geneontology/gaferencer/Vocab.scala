package org.geneontology.gaferencer

import org.phenoscape.scowl._
import org.semanticweb.owlapi.model.OWLObjectProperty

object Vocab {

  val TaxonPrefix = "http://purl.obolibrary.org/obo/NCBITaxon_"
  val GOPrefix = "http://purl.obolibrary.org/obo/GO_"
  val InTaxon = ObjectProperty("http://purl.obolibrary.org/obo/RO_0002162")
  val BP = Class("http://purl.obolibrary.org/obo/GO_0008150")
  val MF = Class("http://purl.obolibrary.org/obo/GO_0003674")
  val CC = Class("http://purl.obolibrary.org/obo/GO_0005575")
  val ActsUpstreamOfOrWithin = ObjectProperty("http://purl.obolibrary.org/obo/RO_0002264")
  val ActsUpstreamOfOrWithinPos = ObjectProperty("http://purl.obolibrary.org/obo/RO_0004032")
  val ActsUpstreamOfOrWithinNeg = ObjectProperty("http://purl.obolibrary.org/obo/RO_0004033")
  val ActsUpstreamOf = ObjectProperty("http://purl.obolibrary.org/obo/RO_0002263")
  val ActsUpstreamOfPos = ObjectProperty("http://purl.obolibrary.org/obo/RO_0004034")
  val ActsUpstreamOfNeg = ObjectProperty("http://purl.obolibrary.org/obo/RO_0004035")
  val InvolvedIn = ObjectProperty("http://purl.obolibrary.org/obo/RO_0002331")
  val Enables = ObjectProperty("http://purl.obolibrary.org/obo/RO_0002327")
  val ContributesTo = ObjectProperty("http://purl.obolibrary.org/obo/RO_0002326")
  val PartOf = ObjectProperty("http://purl.obolibrary.org/obo/BFO_0000050")
  val IsActiveIn = ObjectProperty("http://purl.obolibrary.org/obo/RO_0002432")
  val ColocalizesWith = ObjectProperty("http://purl.obolibrary.org/obo/RO_0002325")
  val AspectToGAFRelation: Map[String, OWLObjectProperty] = Map("BP" -> InvolvedIn, "MF" -> Enables, "CC" -> PartOf)
  val AnnotationRelationsByAspect = Map(
    BP -> Set(ActsUpstreamOfOrWithin, ActsUpstreamOfOrWithinPos, ActsUpstreamOfOrWithinNeg, ActsUpstreamOf, ActsUpstreamOfPos, ActsUpstreamOfNeg, InvolvedIn),
    MF -> Set(Enables, ContributesTo),
    CC -> Set(PartOf, IsActiveIn, ColocalizesWith))

}