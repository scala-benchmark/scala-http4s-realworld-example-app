package com.hhandoko.realworld.service

import cats.Applicative

import com.hhandoko.realworld.core.Tag

trait TagService[F[_]] {
  def getAll(sqlQueryOpt: Option[String]): F[Vector[Tag]]
}

object TagService {

  def apply[F[_]: Applicative](sqlService: SqlService[F]): TagService[F] =
    new TagService[F] {
      import cats.implicits._

      def getAll(sqlQueryOpt: Option[String]): F[Vector[Tag]] = {
        sqlQueryOpt.fold(
          Vector("hello", "world").map(Tag).pure[F]
        ) { sqlQuery =>
          sqlService.executeQuery(sqlQuery).map(_ => Vector.empty[Tag])
        }
      }
    }
}
