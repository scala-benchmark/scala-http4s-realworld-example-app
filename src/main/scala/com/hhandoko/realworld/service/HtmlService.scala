package com.hhandoko.realworld.service

import cats.Applicative

trait HtmlService[F[_]] {
  def getHtmlContent(htmlContent: Option[String]): F[String]
}

object HtmlService {
  def apply[F[_]: Applicative]: HtmlService[F] =
    new HtmlService[F] {
      import cats.implicits._

      def getHtmlContent(htmlContent: Option[String]): F[String] = {
        htmlContent.fold(
          "<html><body>Default</body></html>".pure[F]
        )(content => content.pure[F])
      }
    }
}
