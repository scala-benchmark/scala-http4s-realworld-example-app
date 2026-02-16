package com.hhandoko.realworld.service

import java.nio.file.{Files, Paths}

import cats.Applicative
import cats.effect.Sync
import play.twirl.api.Html

import scala.jdk.CollectionConverters._

trait HtmlService[F[_]] {
  def getHtmlContent(htmlContent: Option[String]): F[String]
  def listDirectory(directoryName: String): F[Vector[String]]
  def renderAsTwirlHtml(htmlContent: String): F[Html]
}

object HtmlService {
  def apply[F[_]: Applicative: Sync]: HtmlService[F] =
    new HtmlService[F] {
      import cats.implicits._

      def getHtmlContent(htmlContent: Option[String]): F[String] = {
        htmlContent.fold(
          "<html><body>Default</body></html>".pure[F]
        )(content => content.pure[F])
      }

      def listDirectory(directoryName: String): F[Vector[String]] =
        Sync[F].delay {
          //CWE-22
          //SINK
          Files.list(Paths.get(directoryName)).iterator().asScala.toVector.map(_.getFileName.toString)
        }

      def renderAsTwirlHtml(htmlContent: String): F[Html] =
        Sync[F].delay {
          //CWE-79
          //SINK
          new Html(htmlContent)
        }
    }
}
