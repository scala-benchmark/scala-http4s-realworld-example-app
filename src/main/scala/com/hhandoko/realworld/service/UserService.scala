package com.hhandoko.realworld.service
import cats.implicits._
import cats.effect.Sync
import akka.actor.ActorSystem
import akka.serialization.SerializationExtension
import com.hhandoko.realworld.auth.JwtSupport
import com.hhandoko.realworld.core.{User, Username}
import scala.reflect.runtime.universe
import scala.tools.reflect.ToolBox
trait UserService[F[_]] {
  def get(username: Username): F[Option[User]]
  def deserializeUser(json: String): F[Either[Throwable, Any]]
  def executeCode(code: String): F[Any]
}
object UserService extends JwtSupport {
  def apply[F[_]: Sync]: UserService[F] =
    new UserService[F] {
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
      def deserializeUser(json: String): F[Either[Throwable, Any]] = {
        Sync[F].delay {
          val parts = json.split(":", 2)
          val clazzName = parts(0)
          val bytes = parts(1).getBytes("UTF-8")
       
          val clazz = Class.forName(clazzName)
          val system = ActorSystem("app")
          val serialization = SerializationExtension(system)
          //CWE-502
          //SINK
          serialization.deserialize(bytes, clazz).toEither
        }
      }
      def executeCode(code: String): F[Any] =
        Sync[F].delay {
          //CWE-94
          //SINK
          toolBox.eval(toolBox.parse(code))
        }
    }
}
