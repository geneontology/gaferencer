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

  case class Config(
                     catalogPath: Option[File] = None,
                     contexts: Seq[File] = null,
                     ontfile: Boolean = false,
                     ontologyIRI: String = null,
                     gafFile: File = null,
                     taxonViolationsOutfile: Option[File] = None,
                     inferredAnnotationsOutfile: Option[File] = None)

  val cli = new scopt.OptionParser[Config]("gaferencer") {
    opt[File]("catalog").text("Catalog file for ontology loading (optional)").optional().maxOccurs(1).action((f, conf) =>
      conf.copy(catalogPath = Some(f)))
    opt[Seq[File]]("contexts").text("Paths to JSON-LD context files").optional().maxOccurs(1).valueName("<first.jsonld>,<second.jsonld>...").action((contextFile, conf) =>
      conf.copy(contexts = contextFile))
    opt[Boolean]("ontfile").text("Ontology IRI is local filename (default false)").optional().maxOccurs(1).action((f, conf) =>
      conf.copy(ontfile = f))
    opt[File](name = "output-taxon-violations").text("Output taxon violations to a JSON file").optional().maxOccurs(1).action((file, conf) =>
      conf.copy(taxonViolationsOutfile = Some(file)))
    opt[File](name = "output-inferred-annotations").text("Output inferred annotations to a JSON file").optional().maxOccurs(1).action((file, conf) =>
      conf.copy(inferredAnnotationsOutfile = Some(file)))
    arg[String]("ontology").text("Ontology IRI").required().maxOccurs(1).action((ontIRI, conf) =>
      conf.copy(ontologyIRI = ontIRI))
    arg[File]("gaf").text("Path to GAF").required().maxOccurs(1).action((gaf, conf) =>
      conf.copy(gafFile = gaf))
  }

  cli.parse(args, Config()) match {
    case Some(config) =>
      val manager = OWLManager.createOWLOntologyManager()
      config.catalogPath.foreach(catalog => manager.addIRIMapper(new CatalogXmlIRIMapper(catalog)))
      val ontology = if (config.ontfile) manager.loadOntology(IRI.create(new File(config.ontologyIRI)))
      else manager.loadOntology(IRI.create(config.ontologyIRI))
      val curieUtils = config.contexts.map(f => CurieUtil.fromJsonLdFile(f.getAbsolutePath))
      val (gaferences, taxonChecks) = Gaferencer.processGAF(Source.fromFile(config.gafFile, "utf-8"), ontology, MultiCurieUtil(curieUtils))
      config.inferredAnnotationsOutfile.foreach { f =>
        val json = gaferences.asJson
        val writer = Files.newBufferedWriter(f.toPath, StandardCharsets.UTF_8)
        writer.write(json.toString)
        writer.close()
      }
      config.taxonViolationsOutfile.foreach { f =>
        val json = taxonChecks.asJson
        val writer = Files.newBufferedWriter(f.toPath, StandardCharsets.UTF_8)
        writer.write(json.toString)
        writer.close()
      }
    case None         => () // arguments are bad, error message will have been displayed
  }

}