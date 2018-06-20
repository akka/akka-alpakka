/*
 * Copyright (C) 2016-2018 Lightbend Inc. <http://www.lightbend.com>
 */

package akka.stream.alpakka.udp.scaladsl

import java.net.InetSocketAddress

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.alpakka.udp.UdpMessage
import akka.stream.alpakka.udp.impl.{UdpBindFlow, UdpSendFlow}
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Flow

import scala.concurrent.Future

object Udp {

  /**
   * Creates a flow that will send all incoming [UdpMessage] messages to the remote address
   * contained in the message. All incoming messages are also emitted from the flow for
   * subsequent processing.
   */
  def sendFlow()(implicit sys: ActorSystem): Flow[UdpMessage, UdpMessage, NotUsed] =
    Flow.fromGraph(new UdpSendFlow())

  /**
   * Creates a sink that will send all incoming [UdpMessage] messages to the remote address
   * contained in the message.
   */
  def sendSink()(implicit sys: ActorSystem): Sink[UdpMessage, NotUsed] = sendFlow().to(Sink.ignore)

  /**
   * Creates a flow that upon materialization binds to the given `localAddress`. All incoming
   * messages to the `localAddress` are emitted from the flow. All incoming messages to the flow
   * are sent to the remote address contained in the message.
   */
  def bindFlow(
      localAddress: InetSocketAddress
  )(implicit sys: ActorSystem): Flow[UdpMessage, UdpMessage, Future[InetSocketAddress]] =
    Flow.fromGraph(new UdpBindFlow(localAddress))
}
