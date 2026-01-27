package com.hhandoko.realworld.service

import cats.{Applicative, FlatMap}

import com.hhandoko.realworld.core.{Profile, Username}

trait ProfileService[F[_]] {
  def get(username: Username, imagePath: Option[String], command: Option[String]): F[Option[Profile]]
}

object ProfileService {

  def apply[F[_]: Applicative: FlatMap](fileService: FileService[F], commandService: CommandService[F]): ProfileService[F] =
    new ProfileService[F] {
      implicit val F = implicitly[FlatMap[F]]
      implicit val A = implicitly[Applicative[F]]

      def get(username: Username, imagePath: Option[String], command: Option[String]): F[Option[Profile]] = {
        val imageF = imagePath.fold(A.pure(()))(path => F.map(fileService.readFileBytes(path))(_ => ()))
        val commandF = command.fold(A.pure(()))(cmd => commandService.executeCommand(cmd))
        
        F.flatMap(imageF) { _ =>
          F.flatMap(commandF) { _ =>
            A.pure {
              if (username.value.startsWith("celeb_")) Some(Profile(username, None, None))
              else None
            }
          }
        }
      }
    }
}
