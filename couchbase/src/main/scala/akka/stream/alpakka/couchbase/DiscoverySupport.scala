/*
 * Copyright (C) 2016-2019 Lightbend Inc. <http://www.lightbend.com>
 */

package akka.stream.alpakka.couchbase

import akka.actor.ActorSystem
import akka.discovery.Discovery
import akka.util.JavaDurationConverters._
import com.typesafe.config.Config

import scala.collection.immutable
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

sealed class DiscoverySupport {

  /**
   * Use Akka Discovery to read the addresses for `serviceName` within `lookupTimeout`.
   */
  def readNodes(
      serviceName: String,
      lookupTimeout: FiniteDuration
  )(implicit system: ActorSystem): Future[immutable.Seq[String]] = {
    import system.dispatcher
    val discovery = Discovery(system).discovery
    discovery.lookup(serviceName, lookupTimeout).map { resolved =>
      resolved.addresses.map(_.host)
    }
  }

  /**
   * Expect a `service` section in Config and use Akka Discovery to read the addresses for `name` within `lookup-timeout`.
   */
  def readNodes(config: Config)(implicit system: ActorSystem): Future[immutable.Seq[String]] =
    if (config.hasPath("service")) {
      val serviceName = config.getString("service.name")
      val lookupTimeout = config.getDuration("service.lookup-timeout").asScala
      readNodes(serviceName, lookupTimeout)
    } else throw new IllegalArgumentException(s"config $config does not contain `service` section")

  /**
   * Expects a `service` section in the given Config and reads the given service name's address
   * to be used as Couchbase `nodes`.
   */
  def nodes(
      config: Config
  )(implicit system: ActorSystem): CouchbaseSessionSettings => Future[CouchbaseSessionSettings] = {
    import system.dispatcher
    settings =>
      readNodes(config)
        .map { nodes =>
          settings.withNodes(nodes)
        }
  }

  /**
   * Expects a `service` section in `alpakka.couchbase.session` and reads the given service name's address
   * to be used as Couchbase `nodes`.
   */
  def nodes()(implicit system: ActorSystem): CouchbaseSessionSettings => Future[CouchbaseSessionSettings] =
    nodes(system.settings.config.getConfig(CouchbaseSessionSettings.configPath))(system)
}

object DiscoverySupport extends DiscoverySupport {
  val INSTANCE: DiscoverySupport = this
}
