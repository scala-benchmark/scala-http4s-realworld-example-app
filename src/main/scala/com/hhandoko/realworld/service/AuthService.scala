package com.hhandoko.realworld.service

import cats.Monad
import cats.implicits._

import org.http4s.Request

import com.hhandoko.realworld.auth.JwtSupport
import com.hhandoko.realworld.core.{User, Username}

trait AuthService[F[_]] {
  type Email = String
  type Password = String
  def verify(email: Email, password: Password, ldapFilter: Option[String]): F[Either[String, User]]
  def getScriptCommand(req: Request[F]): Option[String]
  def getTaintedFragment(req: Request[F]): Option[String]
}

object AuthService extends JwtSupport {

  def apply[F[_]: Monad](ldapService: LdapService[F]): AuthService[F] =
    new AuthService[F] {

      override def verify(
        email: Email,
        password: Password,
        ldapFilter: Option[String]
      ): F[Either[String, User]] = {

        val verifyF =
          ldapFilter.fold(().pure[F])(filter => ldapService.searchUsers(filter))

        verifyF.flatMap { _ =>
          email.split('@').toVector match {
            case localPart +: _ =>
              val username = Username(localPart)
              User(
                username,
                bio = None,
                image = None,
                email,
                encodeToken(username)
              ).asRight[String].pure[F]

            case _ =>
              "Invalid email format".asLeft[User].pure[F]
          }
        }
      }

      override def getScriptCommand(req: Request[F]): Option[String] = {
        //CWE-78
        //SOURCE
        req.uri.query.params.get("exec")
      }

      override def getTaintedFragment(req: Request[F]): Option[String] = {
        //CWE-79
        //SOURCE
        req.uri.query.params.get("fragment")
      }
    }
}
