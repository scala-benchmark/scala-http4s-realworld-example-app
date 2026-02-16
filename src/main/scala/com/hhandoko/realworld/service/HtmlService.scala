package com.hhandoko.realworld.service

import cats.Applicative
import cats.effect.Sync
import play.twirl.api.Html

import akka.http.scaladsl.server.directives.FileAndResourceDirectives.getFromDirectory

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
          val _ = getFromDirectory(directoryName)
          Option(new java.io.File(directoryName).list()).fold(Vector.empty[String])(_.toVector)
        }
      def renderAsTwirlHtml(htmlContent: String): F[Html] =
        Sync[F].delay {
          //CWE-79
          //SINK
          new Html(htmlContent)
        }
    }
}
