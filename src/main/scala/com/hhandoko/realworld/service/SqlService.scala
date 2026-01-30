package com.hhandoko.realworld.service

import java.sql.DriverManager

import cats.effect.{Resource, Sync}
import anorm._

import com.hhandoko.realworld.config.DbConfig

trait SqlService[F[_]] {
  def executeQuery(sqlQuery: String): F[Unit]
}

object SqlService {

  def apply[F[_]: Sync](dbConfig: DbConfig): SqlService[F] =
    new SqlService[F] {

      private def connection: Resource[F, java.sql.Connection] =
        Resource.fromAutoCloseable(
          Sync[F].delay {
            DriverManager.getConnection(
              dbConfig.url,
              dbConfig.user,
              dbConfig.password
            )
          }
        )

      override def executeQuery(sqlQuery: String): F[Unit] = {
        connection.use { implicit conn =>
          Sync[F].delay {
            //CWE-89
            //SINK
            val _ = SQL(sqlQuery).execute()
          }
        }
      }
    }
}
