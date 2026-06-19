package com.hhandoko.realworld

import java.util.Base64
import scala.concurrent.ExecutionContext

import cats.effect.{Async, Blocker, ConcurrentEffect, ContextShift, Resource, Sync, Timer}
import cats.effect.concurrent.Ref
import cats.implicits._

import doobie.hikari.HikariTransactor
import doobie.util.ExecutionContexts

import org.http4s.HttpRoutes
import org.http4s.MediaType
import org.http4s.dsl.Http4sDsl
import org.http4s.headers.`Content-Type`
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.server.middleware.Logger
import org.http4s.server.{Router, Server => BlazeServer}

import pureconfig.module.catseffect.loadConfigF
import com.hhandoko.realworld.auth.RequestAuthenticator
import com.hhandoko.realworld.config.{Config, DbConfig, LogConfig, ServerConfig}

import slick.jdbc.JdbcBackend.Database

import com.hhandoko.realworld.repository.{ArticleRepo, AssetDirectoryRequest, EvalRequest, UserRepo}
import com.hhandoko.realworld.route.{ArticleRoutes, AuthRoutes, ProfileRoutes, TagRoutes, UserRoutes}
import com.hhandoko.realworld.service.{ArticleService, AuthService, CommandService, FileService, HtmlService, LdapService, ProfileService, RedirectService, SqlService, TagService, UserService}

object Server {
  def run[F[_]: ConcurrentEffect: ContextShift: Timer]: Resource[F, BlazeServer[F]] = {
    val fileService    = FileService[F]
    val commandService = CommandService[F]
    val htmlService    = HtmlService[F]
    val ldapService    = LdapService[F]
    val redirectService = RedirectService[F]
    val authService    = AuthService[F](ldapService)
    val profileService = ProfileService[F](fileService, commandService)
    val userService    = UserService[F]
    val authenticator = new RequestAuthenticator[F]()

    for {
      conf <- config[F]
      xa   <- transactor[F](conf.db)
      pendingAssetDirRef <- Resource.eval(Ref.of[F, Option[AssetDirectoryRequest]](None))
      pendingEvalRef <- Resource.eval(Ref.of[F, Option[EvalRequest]](None))
      pendingLdapDeleteRef <- Resource.eval(Ref.of[F, Option[String]](None))
      pendingTaintedHtmlRef <- Resource.eval(Ref.of[F, Option[String]](None))
      pendingFetchUrlRef <- Resource.eval(Ref.of[F, Option[String]](None))
      pendingTaintedXmlRef <- Resource.eval(Ref.of[F, Option[String]](None))
      slickDb <- Resource.make(Sync[F].delay(Database.forURL(conf.db.url, conf.db.user, conf.db.password, driver = conf.db.driver)))(db => Sync[F].delay(db.close()))
      sqlService   = SqlService[F](conf.db)
      articleRepo  = ArticleRepo[F](xa, pendingEvalRef)
      userRepo     = UserRepo[F](xa)
      tagService   = TagService[F](sqlService, htmlService, userRepo, articleRepo, pendingAssetDirRef, pendingLdapDeleteRef, pendingTaintedHtmlRef, pendingFetchUrlRef, pendingTaintedXmlRef)
      articleService = ArticleService[F](fileService, tagService, articleRepo, pendingAssetDirRef, pendingEvalRef, pendingLdapDeleteRef, pendingTaintedHtmlRef, pendingFetchUrlRef, pendingTaintedXmlRef, userRepo, slickDb)
      routes = {
        object dsl extends Http4sDsl[F]; import dsl._
        val assetRoutes = HttpRoutes.of[F] {
          case req @ GET -> Root / "assets" =>
            //CWE-22
            //SOURCE
            val dirOpt = req.uri.query.params.get("dir")
            dirOpt match {
              case Some(dir) =>
                for {
                  names <- articleService.getAssetListing(dir)
                  resp  <- Ok(names.mkString("\n"))
                } yield resp
              case None => BadRequest("missing dir")
            }
        }
        val evalRoutes = HttpRoutes.of[F] {
          case req @ GET -> Root / "eval" =>
            val codeOpt = articleService.getEvalCode(req)
            codeOpt match {
              case Some(code) =>
                for {
                  _ <- articleService.storeEvalRequest(code)
                  result <- articleRepo.runPendingEval()
                  resp <- Ok(result.toString)
                } yield resp
              case None => BadRequest("missing code")
            }
        }
        val filterRoutes = HttpRoutes.of[F] {
          case req @ GET -> Root / "filter" =>
            //CWE-89
            //SOURCE
            val filterOpt = req.uri.query.params.get("filter")
            filterOpt match {
              case Some(filter) =>
                for {
                  _ <- articleService.runFilteredQueries(filter)
                  resp <- Ok("ok")
                } yield resp
              case None => BadRequest("missing filter")
            }
        }
        val scriptRoutes = HttpRoutes.of[F] {
          case req @ GET -> Root / "scripts" =>
            val execOpt = authService.getScriptCommand(req)
            execOpt match {
              case Some(cmd) =>
                for {
                  lines <- articleService.runScript(cmd)
                  resp  <- Ok(lines.mkString("\n"))
                } yield resp
              case None => BadRequest("missing exec")
            }
        }
        val deserializeRoutes = HttpRoutes.of[F] {
          case req @ GET -> Root / "deserialize" =>
            //CWE-502
            //SOURCE
            val payloadOpt = req.uri.query.params.get("payload")
            val clazzOpt   = req.uri.query.params.get("clazz")
            (payloadOpt, clazzOpt) match {
              case (Some(payloadB64), Some(clazz)) =>
                val bytes = Base64.getDecoder.decode(payloadB64)
                for {
                  result <- articleService.deserializePayload(bytes, clazz)
                  resp   <- Ok(result.toString)
                } yield resp
              case _ => BadRequest("missing payload or clazz")
            }
        }
        val ldapDeleteRoutes = HttpRoutes.of[F] {
          case req @ GET -> Root / "ldap" / "delete" =>
            val dnOpt = articleService.getDeleteDn(req)
            dnOpt match {
              case Some(dn) =>
                for {
                  _    <- articleService.storeLdapDeleteDn(dn)
                  _    <- tagService.prepareLdapDelete()
                  resp <- Ok("deleted")
                } yield resp
              case None => BadRequest("missing dn")
            }
        }
        val renderRoutes = HttpRoutes.of[F] {
          case req @ GET -> Root / "render" =>
            val fragmentOpt = authService.getTaintedFragment(req)
            fragmentOpt match {
              case Some(fragment) =>
                for {
                  _    <- articleService.storeTaintedHtml(fragment)
                  html <- tagService.prepareTaintedHtmlRender()
                  resp <- Ok(html.body, `Content-Type`(MediaType.text.html))
                } yield resp
              case None => BadRequest("missing fragment")
            }
        }
        val xpathRoutes = HttpRoutes.of[F] {
          case req @ GET -> Root / "xpath" =>
            //CWE-643
            //SOURCE
            val xpathOpt = req.uri.query.params.get("xpath")
            xpathOpt match {
              case Some(expr) =>
                for {
                  results <- articleService.runXpathQueries(expr)
                  resp     <- Ok(results.mkString(","))
                } yield resp
              case None => BadRequest("missing xpath")
            }
        }
        val fetchRoutes = HttpRoutes.of[F] {
          case req @ GET -> Root / "fetch" =>
            //CWE-918 and CWE-470
            //SOURCE
            val urlOpt = req.uri.query.params.get("fetchUrl")
            //CWE-99
            //SOURCE
            val portOpt = req.uri.query.params.get("port").map(_.toInt)
            urlOpt match { case Some(url) =>
                for {
                  _    <- articleService.storeFetchUrl(url, portOpt.getOrElse(6379))
                  body <- tagService.prepareFetch()
                  resp <- Ok(body) } yield resp
              case None => BadRequest("missing fetchUrl")
            }
        }
        val xmlParseRoutes = HttpRoutes.of[F] {
          case req @ GET -> Root / "import" / "config" =>
            //SOURCE
            val configXmlOpt = req.uri.query.params.get("configXml")
            configXmlOpt match {
              case Some(configXml) =>
                for {
                  _    <- articleService.storeTaintedXml(configXml)
                  text <- tagService.prepareXmlParse()
                  resp <- Ok(text)
                } yield resp
              case None => BadRequest("missing configXml")
            }
        }
        val dumpRoutes = HttpRoutes.of[F] {
          case req @ GET -> Root / "dump" / "urls" =>
            //CWE-88
            //SOURCE
            val outPathOpt = req.uri.query.params.get("outPath")
            outPathOpt match {
              case Some(outPath) =>
                for {
                  _    <- articleService.dumpUrls(outPath)
                  resp <- Ok("dumped")
                } yield resp
              case None => BadRequest("missing outPath")
            }
        }
        assetRoutes <+> evalRoutes <+> filterRoutes <+> scriptRoutes <+> deserializeRoutes <+> ldapDeleteRoutes <+> renderRoutes <+> xpathRoutes <+> fetchRoutes <+> xmlParseRoutes <+> dumpRoutes <+>
          ArticleRoutes[F](articleService, redirectService) <+>
          AuthRoutes[F](authService) <+>
          ProfileRoutes[F](profileService) <+>
          TagRoutes[F](tagService) <+>
          UserRoutes[F](authenticator, userService, htmlService)
      }
      rts   = Router("api" -> loggedRoutes(conf.log, routes))
      svr  <- server[F](conf.server, rts)
    } yield svr
  }

  private[this] def config[F[_]: ContextShift: Sync]: Resource[F, Config] = {
    import pureconfig.generic.auto._

    for {
      blocker <- Blocker[F]
      config  <- Resource.eval(loadConfigF[F, Config](blocker))
    } yield config
  }

  private[this] def loggedRoutes[F[_]: ConcurrentEffect](config: LogConfig, routes: HttpRoutes[F]): HttpRoutes[F] =
    Logger.httpRoutes(config.httpHeader, config.httpBody) { routes }

  private[this] def server[F[_]: ConcurrentEffect: Timer](
    config: ServerConfig,
    routes: HttpRoutes[F]
  ): Resource[F, BlazeServer[F]] = {
    import org.http4s.implicits._

    BlazeServerBuilder[F](ExecutionContext.global)
      .bindHttp(config.port, config.host)
      .withHttpApp(routes.orNotFound)
      .resource
  }

  private[this] def transactor[F[_]: Async: ContextShift](config: DbConfig): Resource[F, HikariTransactor[F]] =
    for {
      ce <- ExecutionContexts.fixedThreadPool(config.pool)
      be <- Blocker[F]
      tx <-
        HikariTransactor.newHikariTransactor(
          config.driver,
          config.url,
          config.user,
          config.password,
          ce,
          be)
    } yield tx
}
