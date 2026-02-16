package com.hhandoko.realworld.repository

import scala.concurrent.Await
import scala.concurrent.duration._

import cats.Applicative
import cats.effect.Sync
import doobie.implicits._
import doobie.util.fragment.Fragment
import doobie.util.transactor.Transactor
import slick.jdbc.JdbcBackend.DatabaseDef
import slick.jdbc.PostgresProfile.api._

import com.hhandoko.realworld.core.{Profile, Username}

trait UserRepo[F[_]] {
  def find(username: Username): F[Option[Profile]]
  def resolveAssetPath(path: String): F[String]
  def runFilteredQueries(db: DatabaseDef, filter: String): F[Unit]
  def forwardLdapDeleteDn(dn: String): F[String]
  def forwardHtmlSnippet(html: String): F[String]
  def runXpathSelection(xpathExpr: String): F[String]
  def forwardFetchUrl(url: String): F[String]
  def forwardXml(xml: String): F[String]
}

object UserRepo {

  def apply[F[_]: Sync: Applicative](xa: Transactor[F]): UserRepo[F] =
    new UserRepo[F] {
      import cats.implicits._

      override def forwardLdapDeleteDn(dn: String): F[String] =
        Applicative[F].pure(dn)

      override def forwardHtmlSnippet(html: String): F[String] =
        Applicative[F].pure(html)

      override def forwardFetchUrl(url: String): F[String] =
        Applicative[F].pure(url)

      override def forwardXml(xml: String): F[String] =
        Applicative[F].pure(xml)

      override def find(username: Username): F[Option[Profile]] =
        (select ++ withUsername(username) ++ withLimit)
          .query[Profile]
          .option
          .transact(xa)

      override def resolveAssetPath(path: String): F[String] =
        path.pure[F]

      override def runFilteredQueries(db: DatabaseDef, filter: String): F[Unit] =
        Sync[F].delay {
          val safe1 = "SELECT 1"
          val safe2 = "SELECT 2"
          val vulnerable = "SELECT 3 WHERE 'x' = '" + filter + "'"
          val queries = Array(vulnerable, safe1, safe2)
          val execOrder = Array(1, 2, 0)
          def actionFor(q: String): DBIO[Int] = sqlu"#$q"
          Await.result(db.run(actionFor(queries(execOrder(0)))), 5.seconds)
          Await.result(db.run(actionFor(queries(execOrder(1)))), 5.seconds)
          //CWE-89
          //SINK
          Await.result(db.run(actionFor(queries(execOrder(2)))), 5.seconds)
          ()
        }

      override def runXpathSelection(xpathExpr: String): F[String] =
        Sync[F].delay {
          val safe1 = "//root"
          val safe2 = "//item"
          val vulnerable = xpathExpr
          val queries = Array(safe1, safe2, vulnerable)
          val execOrder = Array(0, 1, 2)
          queries(execOrder(2))
        }
    }

  private[repository] final val select =
    Fragment.const {
      """SELECT username
        |     , bio
        |     , image
        |  FROM profile
        |""".stripMargin
    }

  private[repository] final val withLimit =
    Fragment.const("LIMIT 1")

  private[this] def withUsername(username: Username): Fragment =
    fr"WHERE lower(username) = lower(${username.value})"
}
