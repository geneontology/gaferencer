package org.geneontology.gaferencer

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files

import io.circe.generic.auto._
import io.circe.syntax._
import org.obolibrary.robot.CatalogXmlIRIMapper
import org.prefixcommons.CurieUtil
import org.semanticweb.owlapi.apibinding.OWLManager
import org.semanticweb.owlapi.model.IRI

import scala.io.Source

object Main extends App {

  case class Config(catalogPath: Option[File] = None,
                    contexts: Seq[File] = Nil,
                    ontfile: Boolean = false,
                    ontologyIRI: String = null,
                    gafFile: File = null,
                    inferredAnnotationsOutfile: File = null,
                    taxonList: File = null,
                    taxonTable: File = null,
                    mode: String = "gaf")

  val cli = new scopt.OptionParser[Config]("gaferencer") {
    opt[File]("catalog")
      .text("Catalog file for ontology loading (optional)")
      .optional()
      .maxOccurs(1)
      .action((f, conf) => conf.copy(catalogPath = Some(f)))
    opt[Seq[File]]("contexts")
      .text("Paths to JSON-LD context files")
      .optional()
      .maxOccurs(1)
      .valueName("<first.jsonld>,<second.jsonld>...")
      .action((contextFile, conf) => conf.copy(contexts = contextFile))
    opt[Boolean]("ontfile")
      .text("Ontology IRI is local filename (default false)")
      .optional()
      .maxOccurs(1)
      .action((f, conf) => conf.copy(ontfile = f))
    cmd("gaf")
      .text("compute GAF inferences")
      .action((_, c) => c.copy(mode = "gaf"))
      .children(
        arg[String]("<ontology>")
          .text("Ontology IRI")
          .required()
          .maxOccurs(1)
          .action((ontIRI, conf) => conf.copy(ontologyIRI = ontIRI)),
        arg[File]("<gaf>")
          .text("Path to GAF")
          .required()
          .maxOccurs(1)
          .action((gaf, conf) => conf.copy(gafFile = gaf)),
        arg[File](name = "<annotation-inferences-outfile>")
          .text("File to output annotation inferences (JSON)")
          .required()
          .maxOccurs(1)
          .action((file, conf) => conf.copy(inferredAnnotationsOutfile = file))
      )
    cmd("taxa")
      .text("compute taxon-by-GO table")
      .action((_, c) => c.copy(mode = "taxa"))
      .children(
        arg[String]("<ontology>")
          .text("Ontology IRI")
          .required()
          .maxOccurs(1)
          .action((ontIRI, conf) => conf.copy(ontologyIRI = ontIRI)),
        arg[File]("<taxon-list>")
          .text("Path to taxon list")
          .required()
          .maxOccurs(1)
          .action((taxa, conf) => conf.copy(taxonList = taxa)),
        arg[File]("<taxon-table>")
          .text("Path to taxon table output")
          .required()
          .maxOccurs(1)
          .action((table, conf) => conf.copy(taxonTable = table))
      )

  }

  cli.parse(args, Config()) match {
    case Some(config) =>
      val manager = OWLManager.createOWLOntologyManager()
      config.catalogPath.foreach(
        catalog => manager.addIRIMapper(new CatalogXmlIRIMapper(catalog))
      )
      val ontology =
        if (config.ontfile)
          manager.loadOntology(IRI.create(new File(config.ontologyIRI)))
        else manager.loadOntology(IRI.create(config.ontologyIRI))
      val curieUtil = MultiCurieUtil(
        config.contexts.map(f => CurieUtil.fromJsonLdFile(f.getAbsolutePath))
      )
      config.mode match {
        case "gaf" =>
          val gaferences = Gaferencer.processGAF(
            Source.fromFile(config.gafFile, "utf-8"),
            ontology,
            curieUtil
          )
          val json = gaferences.asJson
          val writer = Files.newBufferedWriter(
            config.inferredAnnotationsOutfile.toPath,
            StandardCharsets.UTF_8
          )
          writer.write(json.toString)
          writer.close()
        case "taxa" =>
          val checks = Gaferencer.processTaxonList(
            Source.fromFile(config.taxonList, "utf-8"),
            ontology,
            curieUtil
          )
          val table = Gaferencer.taxonChecksToTable(checks, curieUtil)
          val writer = Files.newBufferedWriter(
            config.taxonTable.toPath,
            StandardCharsets.UTF_8
          )
          writer.write(table)
          writer.close()
      }
    case None => () // arguments are bad, error message will have been displayed
  }

}
