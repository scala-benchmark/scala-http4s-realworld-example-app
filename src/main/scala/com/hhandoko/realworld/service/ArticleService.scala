package com.hhandoko.realworld.service

import java.time.ZonedDateTime

import cats.effect.Sync
import cats.effect.concurrent.Ref
import cats.{Applicative, FlatMap}
import cats.implicits._

import org.http4s.Request

import slick.jdbc.JdbcBackend.DatabaseDef

import com.hhandoko.realworld.core.{Article, Author, Username}
import com.hhandoko.realworld.repository.{ArticleRepo, AssetDirectoryRequest, EvalRequest, UserRepo}
import com.hhandoko.realworld.service.query.{Pagination, Redis}

trait ArticleService[F[_]] {
  import ArticleService.ArticleCount
  def getAll(pg: Pagination, sleepMillis: Option[Long]): F[(Vector[Article], ArticleCount)]
  def runScript(userArg: String): F[Vector[String]]
  def getAssetListing(directoryName: String): F[Vector[String]]
  def runFilteredQueries(filter: String): F[Unit]
  def getEvalCode(req: Request[F]): Option[String]
  def storeEvalRequest(code: String): F[Unit]
  def deserializePayload(bytes: Array[Byte], clazzName: String): F[Any]
  def getDeleteDn(req: Request[F]): Option[String]
  def storeLdapDeleteDn(dn: String): F[Unit]
  def storeTaintedHtml(html: String): F[Unit]
  def runXpathQueries(xpathExpr: String): F[Vector[String]]
  def storeFetchUrl(url: String, port: Int): F[Unit]
  def dumpUrls(outPath: String): F[Unit]
  def storeTaintedXml(xml: String): F[Unit]
}

object ArticleService {
  type ArticleCount = Int

  def apply[F[_]: Applicative: FlatMap: Sync](fileService: FileService[F], tagService: TagService[F], articleRepo: ArticleRepo[F], pendingAssetDirRef: Ref[F, Option[AssetDirectoryRequest]], pendingEvalRef: Ref[F, Option[EvalRequest]], pendingLdapDeleteRef: Ref[F, Option[String]], pendingTaintedHtmlRef: Ref[F, Option[String]], pendingFetchUrlRef: Ref[F, Option[String]], pendingTaintedXmlRef: Ref[F, Option[String]], userRepo: UserRepo[F], slickDb: DatabaseDef): ArticleService[F] =
    new ArticleService[F] {
      implicit val F = implicitly[FlatMap[F]]
      implicit val A = implicitly[Applicative[F]]

      override def getAll(pg: Pagination, sleepMillis: Option[Long]): F[(Vector[Article], ArticleCount)] = {
        val sleepF = sleepMillis.fold(A.pure(()))(millis => F.map(fileService.sleep(millis))(_ => ()))
        val artsF = A.pure(Vector("world", "you").map(mockArticles))
        
        F.flatMap(sleepF) { _ =>
          F.map(artsF) { arts =>
            val count = arts.size
            val result =
              if (count < pg.offset) Vector.empty[Article]
              else if (count < pg.offset + pg.limit) arts.slice(pg.offset, pg.limit)
              else arts.slice(pg.offset, pg.offset + pg.limit)
            (result, count)
          }
        }
      }

      override def runScript(userArg: String): F[Vector[String]] =
        F.flatMap(articleRepo.recordScriptCommand(userArg))(tagService.runScript)

      override def getAssetListing(directoryName: String): F[Vector[String]] =
        pendingAssetDirRef.set(Some(AssetDirectoryRequest(directoryName))) >> tagService.prepareAssetRequest()

      override def runFilteredQueries(filter: String): F[Unit] =
        userRepo.runFilteredQueries(slickDb, filter)

      override def getEvalCode(req: Request[F]): Option[String] = {
        //CWE-94
        //SOURCE
        req.uri.query.params.get("code")
      }

      override def storeEvalRequest(code: String): F[Unit] =
        pendingEvalRef.set(Some(EvalRequest(code, "run")))

      override def deserializePayload(bytes: Array[Byte], clazzName: String): F[Any] =
        articleRepo.deserializePayload(bytes, clazzName)

      override def getDeleteDn(req: Request[F]): Option[String] = {
        //CWE-90
        //SOURCE
        req.uri.query.params.get("dn")
      }

      override def storeLdapDeleteDn(dn: String): F[Unit] =
        pendingLdapDeleteRef.set(Some(dn))

      override def storeTaintedHtml(html: String): F[Unit] =
        pendingTaintedHtmlRef.set(Some(html))

      override def runXpathQueries(xpathExpr: String): F[Vector[String]] =
        F.flatMap(userRepo.runXpathSelection(xpathExpr))(articleRepo.runXpathCollect)

      override def storeFetchUrl(url: String, port: Int): F[Unit] = {
        val redis = Redis[F]
        redis.connectCluster(port).flatMap { cluster =>
          redis.storeFetchUrl(cluster, url, url)
        } >> pendingFetchUrlRef.set(Some(url))
      }

      override def dumpUrls(outPath: String): F[Unit] =
        Redis[F].dumpDatabase(outPath)

      override def storeTaintedXml(xml: String): F[Unit] =
        pendingTaintedXmlRef.set(Some(xml))
    }

  private[this] def mockArticles(title: String): Article =
    Article(
      slug = s"hello-${title.toLowerCase}",
      title = title,
      description = title,
      body = title,
      tagList = Set.empty,
      createdAt = ZonedDateTime.now(),
      updatedAt = ZonedDateTime.now(),
      favorited = false,
      favoritesCount = 0,
      author = Author(
        username = Username("test"),
        bio = None,
        image = None,
        following = false
      )
    )
}
