package com.hhandoko.realworld.service

import cats.effect.Sync
import cats.implicits._
import org.http4s.{Response, Uri}
import org.http4s.dsl.Http4sDsl
import org.http4s.headers.Location

trait RedirectService[F[_]] {
  def performRedirect(redirectUrl: Option[String]): F[Response[F]]
}

object RedirectService {
  def apply[F[_]: Sync]: RedirectService[F] =
    new RedirectService[F] {
      object dsl extends Http4sDsl[F]; import dsl._

      def performRedirect(redirectUrl: Option[String]): F[Response[F]] = {
        redirectUrl.fold(
          NotFound().widen[Response[F]]
        ) { url =>
          Uri.fromString(url).fold(
            _ => NotFound().widen[Response[F]],
            uri => {
              //CWE-601
              //SINK
              TemporaryRedirect(Location(uri))
            }
          )
        }
      }
    }
}
