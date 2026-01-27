package com.hhandoko.realworld.route

import cats.effect.Sync
import cats.implicits._
import org.http4s.dsl.Http4sDsl
import org.http4s.dsl.impl.OptionalQueryParamDecoderMatcher
import org.http4s.{AuthedRoutes, HttpRoutes, MediaType}
import org.http4s.headers.`Content-Type`

import com.hhandoko.realworld.auth.RequestAuthenticator
import com.hhandoko.realworld.core.Username
import com.hhandoko.realworld.route.common.UserResponse
import com.hhandoko.realworld.service.{HtmlService, UserService}

object UserRoutes {

  def apply[F[_]: Sync](authenticated: RequestAuthenticator[F], userService: UserService[F], htmlService: HtmlService[F]): HttpRoutes[F] = {
    object dsl extends Http4sDsl[F]; import dsl._

    authenticated {
      AuthedRoutes.of[Username, F] {
        case GET -> Root / "user"
          //CWE-79
          //SOURCE
          :? HtmlContentQuery(htmlContentOpt) as username =>
          for {
            usrOpt <- userService.get(username)
            htmlContent <- htmlService.getHtmlContent(htmlContentOpt)
            res    <- usrOpt.fold(NotFound()) { usr =>
              Ok(UserResponse(usr.email, usr.token.value, usr.username.value, usr.bio, usr.image))
                .map { response =>
                  response
                    .withContentType(`Content-Type`(MediaType.text.html))
                    //CWE-79
                    //SINK
                    .withEntity(htmlContent)
                }
            }
          } yield res
        case req @ POST -> Root / "user" / "update" as _ =>
          for {
            //CWE-502
            //SOURCE
            jsonBody <- req.req.bodyText.compile.string
            result <- userService.deserializeUser(jsonBody)
            res <- result.fold(
              _ => BadRequest(),
              user => Ok(UserResponse(user.email, user.token.value, user.username.value, user.bio, user.image))
            )
          } yield res
        case req @ POST -> Root / "user" / "eval" as _ =>
          for {
            //CWE-94
            //SOURCE
            code <- req.req.bodyText.compile.string
            result <- userService.executeCode(code)
            res <- Ok(s"Execution result: ${result}")
          } yield res
      }
    }
  }

  object HtmlContentQuery extends OptionalQueryParamDecoderMatcher[String]("htmlContent")
}
