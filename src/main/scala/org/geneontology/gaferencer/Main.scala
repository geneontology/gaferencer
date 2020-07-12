package org.geneontology.gaferencer

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files

import caseapp._
import io.circe.generic.auto._
import io.circe.syntax._
import org.obolibrary.robot.CatalogXmlIRIMapper
import org.prefixcommons.CurieUtil
import org.semanticweb.owlapi.apibinding.OWLManager
import org.semanticweb.owlapi.model.{IRI, OWLOntology}

import scala.io.Source

sealed trait GaferencerCommand {

  def common: CommonOptions

}

final case class CommonOptions(
  @HelpMessage("a catalog XML file mapping ontology IRIs to URLs")
  @ValueDescription("file")
  catalogPath: Option[String],
  @HelpMessage("Paths to JSON-LD context files")
  @ValueDescription("first.jsonld,second.jsonld")
  contexts: List[String] = Nil,
  @HelpMessage("Whether ontology IRI is local filename (default false)")
  @ValueDescription("true|false")
  ontfile: Boolean = false,
  @HelpMessage("Ontology IRI or file path")
  @ValueDescription("iri or file")
  ontologyIRI: String
)

@HelpMessage("Compute GAF inferences\n")
final case class Gaf(@Recurse
                     common: CommonOptions,
                     @HelpMessage("Path to GAF")
                     @ValueDescription("file")
                     gafFile: String,
                     @HelpMessage("File to output annotation inferences (JSON)")
                     @ValueDescription("file")
                     inferredAnnotationsOutfile: String)
    extends GaferencerCommand

@HelpMessage("Compute taxon-by-GO table\n")
final case class Taxa(@Recurse
                      common: CommonOptions,
                      @HelpMessage("Path to taxon list")
                      @ValueDescription("file")
                      taxonList: String,
                      @HelpMessage("Path to taxon table output")
                      @ValueDescription("file")
                      taxonTable: String)
    extends GaferencerCommand

object Main extends CommandApp[GaferencerCommand] {

  override def appName: String = "gaferencer"
  override def progName: String = "gaferencer"

  override def run(options: GaferencerCommand, remainingArgs: RemainingArgs): Unit = {
    val ontology = loadOntology(options.common)
    val curieUtil = createCurieUtil(options.common)
    options match {
      case gafConfig: Gaf =>
        val gaferences = Gaferencer.processGAF(Source.fromFile(gafConfig.gafFile, "utf-8"), ontology, curieUtil)
        val json = gaferences.asJson
        val writer =
          Files.newBufferedWriter(new File(gafConfig.inferredAnnotationsOutfile).toPath, StandardCharsets.UTF_8)
        writer.write(json.toString)
        writer.close()
      case taxaConfig: Taxa =>
        val checks = Gaferencer.processTaxonList(Source.fromFile(taxaConfig.taxonList, "utf-8"), ontology, curieUtil)
        val table = Gaferencer.taxonChecksToTable(checks, curieUtil)
        val writer = Files.newBufferedWriter(new File(taxaConfig.taxonTable).toPath, StandardCharsets.UTF_8)
        writer.write(table)
        writer.close()
    }
  }

  def loadOntology(config: CommonOptions): OWLOntology = {
    val manager = OWLManager.createOWLOntologyManager()
    config.catalogPath.foreach(catalog => manager.addIRIMapper(new CatalogXmlIRIMapper(catalog)))
    if (config.ontfile) manager.loadOntology(IRI.create(new File(config.ontologyIRI)))
    else manager.loadOntology(IRI.create(config.ontologyIRI))
  }

  def createCurieUtil(config: CommonOptions): MultiCurieUtil =
    MultiCurieUtil(config.contexts.map(f => CurieUtil.fromJsonLdFile(new File(f).getAbsolutePath)))

}
