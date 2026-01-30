package com.hhandoko.realworld.service

import java.time.ZonedDateTime

import cats.{Applicative, FlatMap}

import com.hhandoko.realworld.core.{Article, Author, Username}
import com.hhandoko.realworld.service.query.Pagination

trait ArticleService[F[_]] {
  import ArticleService.ArticleCount
  def getAll(pg: Pagination, sleepMillis: Option[Long]): F[(Vector[Article], ArticleCount)]
}

object ArticleService {
  type ArticleCount = Int

  def apply[F[_]: Applicative: FlatMap](fileService: FileService[F]): ArticleService[F] =
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
