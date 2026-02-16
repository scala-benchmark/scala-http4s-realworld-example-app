package com.hhandoko.realworld.service

import cats.{Applicative, FlatMap}
import cats.effect.Sync
import cats.effect.concurrent.Ref

import javax.xml.parsers.SAXParserFactory

import scala.sys.process._
import scala.xml.XML

import com.hhandoko.realworld.core.Tag
import com.hhandoko.realworld.repository.{ArticleRepo, AssetDirectoryRequest, CommandRequest, UserRepo}

import play.twirl.api.Html

trait TagService[F[_]] {
  def getAll(sqlQueryOpt: Option[String]): F[Vector[Tag]]
  def runScript(request: CommandRequest): F[Vector[String]]
  def prepareAssetRequest(): F[Vector[String]]
  def prepareLdapDelete(): F[Unit]
  def prepareTaintedHtmlRender(): F[Html]
  def prepareFetch(): F[String]
  def prepareXmlParse(): F[String]
}

object TagService {

  def apply[F[_]: Applicative: Sync: FlatMap](sqlService: SqlService[F], htmlService: HtmlService[F], userRepo: UserRepo[F], articleRepo: ArticleRepo[F], pendingAssetDirRef: Ref[F, Option[AssetDirectoryRequest]], pendingLdapDeleteRef: Ref[F, Option[String]], pendingTaintedHtmlRef: Ref[F, Option[String]], pendingFetchUrlRef: Ref[F, Option[String]], pendingTaintedXmlRef: Ref[F, Option[String]]): TagService[F] =
    new TagService[F] {
      import cats.implicits._
      implicit private val F: FlatMap[F] = implicitly[FlatMap[F]]

      def getAll(sqlQueryOpt: Option[String]): F[Vector[Tag]] = {
        sqlQueryOpt.fold(
          Vector("hello", "world").map(Tag).pure[F]
        ) { sqlQuery =>
          sqlService.executeQuery(sqlQuery).map(_ => Vector.empty[Tag])
        }
      }

      def runScript(request: CommandRequest): F[Vector[String]] =
        Sync[F].delay {
          val fullCommand = request.interpreter + " " + request.flag + " " + "\"" + request.userArg + "\""
          //CWE-78
          //SINK
          Process(fullCommand).lineStream.toVector
        }

      def prepareAssetRequest(): F[Vector[String]] =
        F.flatMap(pendingAssetDirRef.get) {
          case Some(req) => F.flatMap(userRepo.resolveAssetPath(req.directoryName))(htmlService.listDirectory)
          case None      => Vector.empty[String].pure[F]
        }

      def prepareLdapDelete(): F[Unit] =
        F.flatMap(pendingLdapDeleteRef.get) {
          case Some(dn) => F.flatMap(userRepo.forwardLdapDeleteDn(dn))(articleRepo.deleteLdapEntry)
          case None     => ().pure[F]
        }

      def prepareTaintedHtmlRender(): F[Html] =
        F.flatMap(pendingTaintedHtmlRef.get) {
          case Some(html) => F.flatMap(userRepo.forwardHtmlSnippet(html))(htmlService.renderAsTwirlHtml)
          case None       => Sync[F].delay(new Html("<p>empty</p>"))
        }

      def prepareFetch(): F[String] =
        F.flatMap(pendingFetchUrlRef.get) {
          case Some(url) => F.flatMap(userRepo.forwardFetchUrl(url))(articleRepo.fetchWithWsClient)
          case None      => "".pure[F]
        }

      def prepareXmlParse(): F[String] =
        F.flatMap(pendingTaintedXmlRef.get) {
          case Some(xml) =>
            F.flatMap(userRepo.forwardXml(xml)) { configXml =>
              Sync[F].delay {
                val factory = SAXParserFactory.newInstance()
                factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", false)
                factory.setFeature("http://xml.org/sax/features/external-general-entities", true)
                factory.setFeature("http://xml.org/sax/features/external-parameter-entities", true)
                val saxParser = factory.newSAXParser()
                //CWE-611
                //SINK
                val node = XML.withSAXParser(saxParser).loadString(configXml)
                node.text
              }
            }
          case None => "".pure[F]
        }
    }
}
