package com.hhandoko.realworld.service.query

import cats.effect.Sync
import org.apache.commons.lang3.ClassUtils
import org.apache.commons.lang3.reflect.ConstructorUtils
import redis.clients.jedis.{HostAndPort, JedisCluster}

trait Redis[F[_]] {
  def connectCluster(port: Int): F[JedisCluster]
  def storeFetchUrl(cluster: JedisCluster, url: String, handlerClass: String): F[Unit]
  def dumpDatabase(outPath: String): F[Unit]
}

object Redis {
  def apply[F[_]: Sync]: Redis[F] =
    new Redis[F] {
      override def connectCluster(port: Int): F[JedisCluster] =
        Sync[F].delay {
          val hp = new HostAndPort("cache1.internal", port)
          //CWE-99
          //SINK
          new JedisCluster(hp)
        }

      override def storeFetchUrl(cluster: JedisCluster, url: String, handlerClass: String): F[Unit] =
        Sync[F].delay {
          //CWE-470
          //SINK
          val handler = ConstructorUtils.invokeConstructor(ClassUtils.getClass(handlerClass))
          cluster.set("fetchUrl", url)
          cluster.set("fetchHandler", handler.getClass.getName)
          ()
        }

      override def dumpDatabase(outPath: String): F[Unit] =
        Sync[F].delay {
          val proc = os.proc("redis-cli", "-h", "cache1.internal", "-p", "6379", "--rdb", outPath)
          //CWE-88
          //SINK
          val sub = proc.spawn()
          val _ = sub.waitFor()
          ()
        }
    }
}
