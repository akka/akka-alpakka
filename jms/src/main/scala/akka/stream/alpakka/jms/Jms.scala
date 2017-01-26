/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package akka.stream.alpakka.jms

import scala.collection.JavaConversions._
import java.util
import javax.jms.ConnectionFactory

sealed trait JmsSettings {
  def connectionFactory: ConnectionFactory
  def destination: Option[Destination]
  def credentials: Option[Credentials]
}

sealed trait Destination
final case class Topic(name: String) extends Destination
final case class Queue(name: String) extends Destination

final case class JmsTextMessage(body: String, properties: Map[String, Any] = Map.empty)

object JmsTextMessage {

  /**
   * Java API: create  [[JmsTextMessage]]
   */
  def create(body: String) = JmsTextMessage(body, Map.empty)
  def create(body: String, properties: util.HashMap[String, Any]) = JmsTextMessage(body, properties.toMap)
}

object JmsSourceSettings {

  def create(connectionFactory: ConnectionFactory) = JmsSourceSettings(connectionFactory)

}

final case class JmsSourceSettings(connectionFactory: ConnectionFactory,
                                   destination: Option[Destination] = None,
                                   credentials: Option[Credentials] = None,
                                   bufferSize: Int = 100)
    extends JmsSettings {
  def withCredential(credentials: Credentials) = copy(credentials = Some(credentials))
  def withBufferSize(size: Int) = copy(bufferSize = size)
  def withQueue(name: String) = copy(destination = Some(Queue(name)))
  def withTopic(name: String) = copy(destination = Some(Topic(name)))
}

object JmsSinkSettings {

  def create(connectionFactory: ConnectionFactory) = JmsSinkSettings(connectionFactory)

}

final case class JmsSinkSettings(connectionFactory: ConnectionFactory,
                                 destination: Option[Destination] = None,
                                 credentials: Option[Credentials] = None)
    extends JmsSettings {
  def withCredential(credentials: Credentials) = copy(credentials = Some(credentials))
  def withQueue(name: String) = copy(destination = Some(Queue(name)))
  def withTopic(name: String) = copy(destination = Some(Topic(name)))
}

final case class Credentials(username: String, password: String)
