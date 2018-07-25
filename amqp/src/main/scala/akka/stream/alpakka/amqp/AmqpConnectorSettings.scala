/*
 * Copyright (C) 2016-2018 Lightbend Inc. <http://www.lightbend.com>
 */

package akka.stream.alpakka.amqp

import akka.annotation.InternalApi

import java.util.{List => JavaList, Map => JavaMap}

import scala.collection.immutable
import scala.collection.JavaConverters._

/**
 * Internal API
 */
@InternalApi
sealed trait AmqpConnectorSettings {
  def connectionProvider: AmqpConnectionProvider
  def declarations: immutable.Seq[Declaration]
}

sealed trait AmqpSourceSettings extends AmqpConnectorSettings

final class NamedQueueSourceSettings private (
    val connectionProvider: AmqpConnectionProvider,
    val queue: String,
    val declarations: immutable.Seq[Declaration] = immutable.Seq.empty,
    val noLocal: Boolean = false,
    val exclusive: Boolean = false,
    val ackRequired: Boolean = true,
    val consumerTag: String = "default",
    val arguments: Map[String, AnyRef] = Map.empty
) extends AmqpSourceSettings {

  def withDeclarations(declaration: immutable.Seq[Declaration]): NamedQueueSourceSettings =
    copy(declarations = declarations)

  /**
   * Java API
   */
  def withDeclarations(declarations: JavaList[Declaration]): NamedQueueSourceSettings =
    copy(declarations = declarations.asScala.toIndexedSeq)

  def withNoLocal(noLocal: Boolean): NamedQueueSourceSettings =
    copy(noLocal = noLocal)

  def withExclusive(exclusive: Boolean): NamedQueueSourceSettings =
    copy(exclusive = exclusive)

  def withAckRequired(ackRequired: Boolean): NamedQueueSourceSettings =
    copy(ackRequired = ackRequired)

  def withConsumerTag(consumerTag: String): NamedQueueSourceSettings =
    copy(consumerTag = consumerTag)

  def withArguments(arguments: Map[String, AnyRef]): NamedQueueSourceSettings =
    copy(arguments = arguments)

  /**
   * Java API
   */
  def withArguments(arguments: JavaMap[String, Object]): NamedQueueSourceSettings =
    copy(arguments = arguments.asScala.toMap)

  private def copy(declarations: immutable.Seq[Declaration] = declarations,
                   noLocal: Boolean = noLocal,
                   exclusive: Boolean = exclusive,
                   ackRequired: Boolean = ackRequired,
                   consumerTag: String = consumerTag,
                   arguments: Map[String, AnyRef] = arguments) =
    new NamedQueueSourceSettings(
      connectionProvider,
      queue,
      declarations = declarations,
      noLocal = noLocal,
      exclusive = exclusive,
      ackRequired = ackRequired,
      consumerTag = consumerTag,
      arguments = arguments
    )

  override def toString: String =
    s"NamedQueueSourceSettings(connectionProvider=$connectionProvider, queue=$queue, declarations=$declarations, noLocal=$noLocal, exclusive=$exclusive, ackRequired=$ackRequired, consumerTag=$consumerTag, arguments=$arguments)"
}

object NamedQueueSourceSettings {
  def apply(connectionProvider: AmqpConnectionProvider, queue: String): NamedQueueSourceSettings =
    new NamedQueueSourceSettings(connectionProvider, queue)

  /**
   * Java API
   */
  def create(connectionProvider: AmqpConnectionProvider, queue: String): NamedQueueSourceSettings =
    NamedQueueSourceSettings(connectionProvider, queue)
}

final class TemporaryQueueSourceSettings private (
    val connectionProvider: AmqpConnectionProvider,
    val exchange: String,
    val declarations: immutable.Seq[Declaration] = Nil,
    val routingKey: Option[String] = None
) extends AmqpSourceSettings {

  def withDeclarations(declaration: immutable.Seq[Declaration]): TemporaryQueueSourceSettings =
    copy(declarations = declarations)

  /**
   * Java API
   */
  def withDeclarations(declarations: JavaList[Declaration]): TemporaryQueueSourceSettings =
    copy(declarations = declarations.asScala.toIndexedSeq)

  def withRoutingKey(routingKey: String): TemporaryQueueSourceSettings = copy(routingKey = Some(routingKey))

  private def copy(declarations: immutable.Seq[Declaration] = declarations, routingKey: Option[String] = routingKey) =
    new TemporaryQueueSourceSettings(connectionProvider, exchange, declarations = declarations, routingKey = routingKey)

  override def toString: String =
    s"TemporaryQueueSourceSettings(connectionProvider=$connectionProvider, exchange=$exchange, declarations=$declarations, routingKey=$routingKey)"
}

object TemporaryQueueSourceSettings {
  def apply(connectionProvider: AmqpConnectionProvider, exchange: String) =
    new TemporaryQueueSourceSettings(connectionProvider, exchange)

  /**
   * Java API
   */
  def create(connectionProvider: AmqpConnectionProvider, exchange: String): TemporaryQueueSourceSettings =
    TemporaryQueueSourceSettings(connectionProvider, exchange)
}

final case class AmqpReplyToSinkSettings(
    connectionProvider: AmqpConnectionProvider,
    failIfReplyToMissing: Boolean = true
) extends AmqpConnectorSettings {
  override final val declarations = Nil
}

object AmqpReplyToSinkSettings {

  /**
   * Java API
   */
  def create(connectionProvider: AmqpConnectionProvider): AmqpReplyToSinkSettings =
    AmqpReplyToSinkSettings(connectionProvider)

  /**
   * Java API
   */
  def create(connectionProvider: AmqpConnectionProvider, failIfReplyToMissing: Boolean): AmqpReplyToSinkSettings =
    AmqpReplyToSinkSettings(connectionProvider, failIfReplyToMissing)

}

final case class AmqpSinkSettings(
    connectionProvider: AmqpConnectionProvider,
    exchange: Option[String] = None,
    routingKey: Option[String] = None,
    declarations: immutable.Seq[Declaration] = Nil
) extends AmqpConnectorSettings {
  def withExchange(exchange: String): AmqpSinkSettings = copy(exchange = Some(exchange))

  def withRoutingKey(routingKey: String): AmqpSinkSettings = copy(routingKey = Some(routingKey))

  @annotation.varargs
  def withDeclarations(declarations: Declaration*): AmqpSinkSettings = copy(declarations = declarations.toList)
}

object AmqpSinkSettings {

  /**
   * Java API
   */
  def create(connectionProvider: AmqpConnectionProvider): AmqpSinkSettings =
    AmqpSinkSettings(connectionProvider)
}

sealed trait Declaration

final case class QueueDeclaration(
    name: String,
    durable: Boolean = false,
    exclusive: Boolean = false,
    autoDelete: Boolean = false,
    arguments: Map[String, AnyRef] = Map.empty
) extends Declaration {
  def withDurable(durable: Boolean): QueueDeclaration = copy(durable = durable)

  def withExclusive(exclusive: Boolean): QueueDeclaration = copy(exclusive = exclusive)

  def withAutoDelete(autoDelete: Boolean): QueueDeclaration = copy(autoDelete = autoDelete)

  def withArguments(argument: (String, AnyRef), arguments: (String, AnyRef)*): QueueDeclaration =
    copy(arguments = (argument +: arguments).toMap)

  @annotation.varargs
  def withArguments(argument: akka.japi.Pair[String, AnyRef],
                    arguments: akka.japi.Pair[String, AnyRef]*): QueueDeclaration =
    copy(arguments = (argument +: arguments).map(_.toScala).toMap)
}

object QueueDeclaration {

  /**
   * Java API
   */
  def create(name: String): QueueDeclaration = QueueDeclaration(name)
}

final case class BindingDeclaration(
    queue: String,
    exchange: String,
    routingKey: Option[String] = None,
    arguments: Map[String, AnyRef] = Map.empty
) extends Declaration {
  def withRoutingKey(routingKey: String): BindingDeclaration = copy(routingKey = Some(routingKey))

  def withArguments(argument: (String, AnyRef), arguments: (String, AnyRef)*): BindingDeclaration =
    copy(arguments = (argument +: arguments).toMap)

  @annotation.varargs
  def withArguments(argument: akka.japi.Pair[String, AnyRef],
                    arguments: akka.japi.Pair[String, AnyRef]*): BindingDeclaration =
    copy(arguments = (argument +: arguments).map(_.toScala).toMap)
}

object BindingDeclaration {

  /**
   * Java API
   */
  def create(queue: String, exchange: String): BindingDeclaration = BindingDeclaration(queue, exchange)
}

final case class ExchangeDeclaration(
    name: String,
    exchangeType: String,
    durable: Boolean = false,
    autoDelete: Boolean = false,
    internal: Boolean = false,
    arguments: Map[String, AnyRef] = Map.empty
) extends Declaration {
  def withDurable(durable: Boolean): ExchangeDeclaration = copy(durable = durable)

  def withAutoDelete(autoDelete: Boolean): ExchangeDeclaration = copy(autoDelete = autoDelete)

  def withInternal(internal: Boolean): ExchangeDeclaration = copy(internal = internal)

  def withArguments(argument: (String, AnyRef), arguments: (String, AnyRef)*): ExchangeDeclaration =
    copy(arguments = (argument +: arguments).toMap)

  @annotation.varargs
  def withArguments(argument: akka.japi.Pair[String, AnyRef],
                    arguments: akka.japi.Pair[String, AnyRef]*): ExchangeDeclaration =
    copy(arguments = (argument +: arguments).map(_.toScala).toMap)
}

object ExchangeDeclaration {

  /**
   * Java API
   */
  def create(name: String, exchangeType: String): ExchangeDeclaration = ExchangeDeclaration(name, exchangeType)
}
