/*
 * Copyright (C) 2016-2017 Lightbend Inc. <http://www.lightbend.com>
 */

package akka.stream.alpakka.jms

import java.time.Duration
import javax.jms.ConnectionFactory

sealed trait JmsSettings {
  def connectionFactory: ConnectionFactory

  def destination: Option[Destination]

  def credentials: Option[Credentials]
}

sealed trait Destination

final case class Topic(name: String) extends Destination

final case class Queue(name: String) extends Destination

object JmsSourceSettings {

  def create(connectionFactory: ConnectionFactory) = JmsSourceSettings(connectionFactory)

}

final case class JmsSourceSettings(connectionFactory: ConnectionFactory,
                                   destination: Option[Destination] = None,
                                   credentials: Option[Credentials] = None,
                                   bufferSize: Int = 100,
                                   selector: Option[String] = None)
    extends JmsSettings {
  def withCredential(credentials: Credentials) = copy(credentials = Some(credentials))

  def withBufferSize(size: Int) = copy(bufferSize = size)

  def withQueue(name: String) = copy(destination = Some(Queue(name)))

  def withTopic(name: String) = copy(destination = Some(Topic(name)))

  def withSelector(selector: String) = copy(selector = Some(selector))
}

object JmsSinkSettings {

  def create(connectionFactory: ConnectionFactory) = JmsSinkSettings(connectionFactory)

}

final case class JmsSinkSettings(connectionFactory: ConnectionFactory,
                                 destination: Option[Destination] = None,
                                 credentials: Option[Credentials] = None,
                                 timeToLive: Option[Duration] = None)
    extends JmsSettings {
  def withCredential(credentials: Credentials) = copy(credentials = Some(credentials))

  def withQueue(name: String) = copy(destination = Some(Queue(name)))

  def withTopic(name: String) = copy(destination = Some(Topic(name)))

  def withTimeToLive(ttl: Duration) = copy(timeToLive = Some(ttl))
}

final case class Credentials(username: String, password: String)
