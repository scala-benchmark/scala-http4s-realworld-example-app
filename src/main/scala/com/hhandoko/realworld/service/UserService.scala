package com.hhandoko.realworld.service

import cats.effect.Sync

import com.hhandoko.realworld.auth.JwtSupport
import com.hhandoko.realworld.core.{User, Username}
import io.circe.generic.auto._

import scala.reflect.runtime.universe
import scala.tools.reflect.ToolBox

trait UserService[F[_]] {
  def get(username: Username): F[Option[User]]
  def deserializeUser(json: String): F[Either[io.circe.Error, User]]
  def executeCode(code: String): F[Any]
}

object UserService extends JwtSupport {

  def apply[F[_]: Sync]: UserService[F] =
    new UserService[F] {
      import cats.implicits._

      private lazy val toolBox = universe.runtimeMirror(getClass.getClassLoader).mkToolBox()

      def get(username: Username): F[Option[User]] =
        Option(
          User(
            email = s"${username.value}@test.com",
            token = encodeToken(username),
            username = username,
            bio = None,
            image = None
          )
        ).pure[F]

      def deserializeUser(json: String): F[Either[io.circe.Error, User]] = {
        //CWE-502
        //SINK
        io.circe.parser.decode[User](json).pure[F]
      }

      def executeCode(code: String): F[Any] =
        Sync[F].delay {
          //CWE-94
          //SINK
          toolBox.eval(toolBox.parse(code))
        }
    }
}
