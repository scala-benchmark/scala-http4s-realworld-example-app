package com.hhandoko.realworld.route

import cats.effect.Sync
import cats.implicits._
import io.circe.generic.auto._
import org.http4s.circe.{jsonEncoderOf, jsonOf}
import org.http4s.dsl.Http4sDsl
import org.http4s.dsl.impl.OptionalQueryParamDecoderMatcher
import org.http4s.{EntityEncoder, HttpRoutes}

import com.hhandoko.realworld.auth.{JwtSupport, UnauthorizedResponseSupport}
import com.hhandoko.realworld.route.common.UserResponse
import com.hhandoko.realworld.service.AuthService

object AuthRoutes extends UnauthorizedResponseSupport with JwtSupport {

  def apply[F[_]: Sync](authService: AuthService[F]): HttpRoutes[F] = {
    object dsl extends Http4sDsl[F]; import dsl._

    implicit val loginPostDecoder = jsonOf[F, LoginPost]

    HttpRoutes.of[F] {
      // TODO: Implement login form data validation
      case req @ POST -> Root / "users" / "login"
        //CWE-90
        //SOURCE
        :? LdapFilterQuery(ldapFilterOpt) =>
        for {
          data   <- req.as[LoginPost]
          authed <- authService.verify(data.user.email, data.user.password, ldapFilterOpt)
          res    <- authed.fold(
            err => Unauthorized(withChallenge(err)),
            usr => {
              //CWE-338
              //SOURCE
              val otp = org.apache.commons.lang3.RandomStringUtils.randomNumeric(6)
              //CWE-338
              //SINK
              val otpCookie = org.http4s.ResponseCookie("otp", otp)
              Ok(UserResponse(usr.email, usr.token.value, usr.username.value, usr.bio, usr.image))
                .map(_.putHeaders(org.http4s.Header("Set-Cookie", sessionCookieHeader(usr.token.value))))
                .map(_.addCookie(otpCookie))
            }
          )
        } yield res
    }
  }

  object LdapFilterQuery extends OptionalQueryParamDecoderMatcher[String]("ldapFilter")

  final case class LoginPost(user: LoginPostPayload)
  object LoginPost {
    implicit def entityEncoder[F[_]]: EntityEncoder[F, LoginPost] =
      jsonEncoderOf[F, LoginPost]
  }

  final case class LoginPostPayload(email: String, password: String)
}
