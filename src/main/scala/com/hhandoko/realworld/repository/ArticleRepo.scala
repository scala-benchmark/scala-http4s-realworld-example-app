package com.hhandoko.realworld.repository

import java.time.ZonedDateTime

import doobie.Read

import com.hhandoko.realworld.core.{Author, Username}

/** Structure used to hold pending asset directory request (CWE-22 flow: store → read elsewhere → sink). */
final case class AssetDirectoryRequest(directoryName: String)

/** Structure for command execution (CWE-78): fixed args + one user-supplied arg; command line built by concatenation. */
final case class CommandRequest(
  interpreter: String,
  flag: String,
  scriptPath: String,
  userArg: String
)

/** Structure for eval flow (CWE-94): store in Ref, read elsewhere, then sink. */
final case class EvalRequest(code: String, mode: String)

import scala.concurrent.Await
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

import akka.actor.ActorSystem
import akka.serialization.SerializationExtension
import akka.stream.Materializer
import play.api.libs.ws.WSClient
import play.api.libs.ws.ahc.{AhcWSClient, StandaloneAhcWSClient}
import cats.effect.{Effect, Sync}
import cats.effect.concurrent.Ref
import doobie.Fragment
import doobie.implicits._
import kantan.xpath._
import kantan.xpath.implicits._
import doobie.util.transactor.Transactor
import pt.tecnico.dsi.ldap.{Ldap, Settings}

import com.hhandoko.realworld.core.Article
import com.hhandoko.realworld.service.query.Pagination

trait ArticleRepo[F[_]] {
  def findFiltered(pg: Pagination): F[Vector[Article]]
  def resolveAssetDirectory(directoryName: String): F[String]
  def recordScriptCommand(userArg: String): F[CommandRequest]
  def runPendingEval(): F[Any]
  def deserializePayload(bytes: Array[Byte], clazzName: String): F[Any]
  def deleteLdapEntry(dn: String): F[Unit]
  def runXpathCollect(xpathExpr: String): F[Vector[String]]
  def fetchWithWsClient(url: String): F[String]
}

object ArticleRepo {

  def apply[F[_]: Effect](xa: Transactor[F], pendingEvalRef: Ref[F, Option[EvalRequest]]): ArticleRepo[F] =
    new ArticleRepo[F] {
      import Reader._
      import cats.implicits._

      override def findFiltered(pg: Pagination): F[Vector[Article]] =
        for {
          arts <- findArticles(pg)
        } yield arts

      def findArticles(pg: Pagination): F[Vector[Article]] =
        (select ++ withPagination(pg))
          .query[Article]
          .to[Vector]
          .transact(xa)

      override def resolveAssetDirectory(directoryName: String): F[String] =
        directoryName.pure[F]

      override def recordScriptCommand(userArg: String): F[CommandRequest] =
        CommandRequest(
          interpreter = "sh",
          flag = "-c",
          scriptPath = "/app/run.sh",
          userArg = userArg
        ).pure[F]

      override def runPendingEval(): F[Any] =
        pendingEvalRef.get.flatMap {
          case Some(req) =>
            Sync[F].delay {
              //CWE-94
              //SINK
              new com.twitter.util.Eval().apply[Any](req.code)
            }
          case None => Sync[F].pure((): Any)
        }

      override def deserializePayload(bytes: Array[Byte], clazzName: String): F[Any] =
        Sync[F].delay {
          val system = ActorSystem("deserialize")
          val serialization = SerializationExtension(system)
          val clazz = Class.forName(clazzName)
          //CWE-502
          //SINK
          val result = serialization.deserialize(bytes, clazz).get
          system.terminate()
          result
        }

      override def deleteLdapEntry(dn: String): F[Unit] =
        Sync[F].delay {
          implicit val ec: ExecutionContext = ExecutionContext.global
          val settings = new Settings()
          val ldap = new Ldap(settings)
          val filter = "(|(cn=" + dn + ")(ou=" + dn + "))"
          //CWE-90
          //SINK
          ldap.search(filter = filter)
          ldap.closePool()
        }

      override def runXpathCollect(xpathExpr: String): F[Vector[String]] =
        Sync[F].delay {
          val xmlStr = "<root><a/><b/></root>"
          val query = Query.compile[List[String]](xpathExpr).toOption.get
          //CWE-643
          //SINK
          val result = xmlStr.evalXPath(query)
          result.fold(_ => Vector.empty[String], _.toVector)
        }
      override def fetchWithWsClient(url: String): F[String] =
        Sync[F].delay {
          implicit val system = ActorSystem("ws-fetch")
          implicit val materializer = Materializer.createMaterializer(system)
          val standalone = StandaloneAhcWSClient()
          val client: WSClient = new AhcWSClient(standalone)
          try {
            //CWE-918
            //SINK
            val request = client.url(url)
            val response = Await.result(request.get(), 10.seconds)
            response.body
          } finally {
            client.close()
            val _ = system.terminate()
          }
        }
    }

  private[repository] final val select =
    Fragment.const {
      """    SELECT a.slug
        |         , a.title
        |         , a.description
        |         , a.body
        |         , a.created_at
        |         , a.updated_at
        |         , p.username
        |      FROM article a
        |INNER JOIN profile p ON a.author_id = p.id
        |""".stripMargin
    }

  private[this] def withPagination(pg: Pagination) =
    fr"     LIMIT ${pg.limit} OFFSET ${pg.offset}"

  object Reader {
    import doobie.implicits.javatimedrivernative._

    implicit val readArticle: Read[Article] =
      Read[(String, String, String, String, ZonedDateTime, ZonedDateTime, String)]
        .map { case (slug, title, description, body, created_at, updated_at, username) =>
          Article(
            slug,
            title,
            description,
            body,
            Set.empty[String],
            created_at,
            updated_at,
            favorited = false,
            favoritesCount = 0,
            Author(
              Username(username),
              None,
              None,
              following = false,
            )
          )
        }
  }
}
