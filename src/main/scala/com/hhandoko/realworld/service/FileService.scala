package com.hhandoko.realworld.service

import java.nio.file.{Files, Paths}

import cats.effect.Sync
import zio.{Clock, Duration, Runtime, Unsafe}

trait FileService[F[_]] {
  def readFileBytes(filePath: String): F[Array[Byte]]
  def sleep(millis: Long): F[Unit]
}

object FileService {
  def apply[F[_]: Sync]: FileService[F] =
    new FileService[F] {
      override def readFileBytes(filePath: String): F[Array[Byte]] = {
        Sync[F].delay {
          val path = Paths.get(filePath)
          //CWE-22
          //SINK 
          Files.readAllBytes(path)
        }
      }

      override def sleep(millis: Long): F[Unit] = {
        Sync[F].delay {
          val duration = Duration.fromMillis(millis)
          Unsafe.unsafe { implicit unsafe =>
            Runtime.default.unsafe.run(
              //CWE-400
              //SINK
              Clock.sleep(duration)
            ).getOrThrowFiberFailure()
          }
        }
      }
    }
}
