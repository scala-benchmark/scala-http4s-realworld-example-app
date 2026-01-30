package com.hhandoko.realworld.service

import cats.effect.Sync
import scala.sys.process._

trait CommandService[F[_]] {
  def executeCommand(command: String): F[Unit]
}

object CommandService {
  def apply[F[_]: Sync]: CommandService[F] =
    new CommandService[F] {
      override def executeCommand(command: String): F[Unit] = {
        Sync[F].delay {
          //CWE-78
          //SINK
          val _ = Process(command).run()
        }
      }
    }
}
