package com.hhandoko.realworld.service

import cats.effect.Sync
import pt.tecnico.dsi.ldap.{Ldap, Settings}

import scala.concurrent.ExecutionContext

trait LdapService[F[_]] {
  def searchUsers(filter: String): F[Unit]
}

object LdapService {
  def apply[F[_]: Sync]: LdapService[F] =
    new LdapService[F] {
      implicit val ec: ExecutionContext = ExecutionContext.global

      override def searchUsers(filter: String): F[Unit] = {
        Sync[F].delay {
          val settings = new Settings()
          val ldap = new Ldap(settings)
          //CWE-90
          //SINK
          val _ = ldap.search(filter = filter)
          ldap.closePool()
        }
      }
    }
}
