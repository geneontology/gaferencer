package org.geneontology.gaffer

import java.io.File

import org.obolibrary.robot.CatalogXmlIRIMapper
import org.prefixcommons.CurieUtil
import org.semanticweb.owlapi.apibinding.OWLManager
import org.semanticweb.owlapi.model.IRI

import io.circe._
import io.circe.generic.auto._
import io.circe.syntax._
import java.io.FileWriter
import java.nio.file.Files
import java.nio.charset.StandardCharsets
import java.nio.file.Paths

object Main extends App {

  case class Config(catalogPath: Option[File] = None, context: File = null, ontologyIRI: IRI = null, gafFile: File = null, outfile: File = null)

  val cli = new scopt.OptionParser[Config]("gaffer") {
    opt[File]("catalog").text("Catalog file for ontology loading (optional)").optional().maxOccurs(1).action((f, conf) =>
      conf.copy(catalogPath = Some(f)))
    arg[File]("context").text("Path to JSON-LD context file").required().maxOccurs(1).action((contextFile, conf) =>
      conf.copy(context = contextFile))
    arg[String]("ontology").text("Ontology IRI").required().maxOccurs(1).action((ontIRI, conf) =>
      conf.copy(ontologyIRI = IRI.create(ontIRI)))
    arg[File]("gaf").text("Path to GAF").required().maxOccurs(1).action((gaf, conf) =>
      conf.copy(gafFile = gaf))
    arg[File]("outfile").text("Path to output JSON").required().maxOccurs(1).action((f, conf) =>
      conf.copy(outfile = f))
  }

  cli.parse(args, Config()) match {
    case Some(config) =>
      val manager = OWLManager.createOWLOntologyManager()
      config.catalogPath.foreach(catalog => manager.addIRIMapper(new CatalogXmlIRIMapper(catalog)))
      val ontology = manager.loadOntology(config.ontologyIRI)
      val curieUtil = CurieUtil.fromJsonLdFile(config.context.getAbsolutePath)
      val gaferences = Gaffer.processGAF(config.gafFile, ontology, curieUtil)
      val json = gaferences.asJson
      val writer = Files.newBufferedWriter(config.outfile.toPath, StandardCharsets.UTF_8)
      writer.write(json.toString)
      writer.close()
    case None => () // arguments are bad, error message will have been displayed
  }

}