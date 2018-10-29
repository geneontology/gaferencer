package org.geneontology.gaferencer

import java.util.Optional

import org.prefixcommons.CurieUtil

case class MultiCurieUtil(curieUtils: Seq[CurieUtil]) {

  import MultiCurieUtil._

  def getIRI(curie: String): Option[String] = curieUtils.toStream.flatMap(_.getIri(curie).asScala).headOption

  def getCURIE(iri: String): Option[String] = curieUtils.toStream.flatMap(_.getCurie(iri).asScala).headOption

}

object MultiCurieUtil {

  implicit class OptionalConverter[T](val self: Optional[T]) extends AnyVal {

    def asScala: Option[T] = if (self.isPresent) Some(self.get) else None

  }

}