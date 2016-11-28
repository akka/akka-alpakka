/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package akka.stream.alpakka.mqtt

import java.util.concurrent.Semaphore

import akka.Done
import akka.stream._
import akka.stream.stage._
import org.eclipse.paho.client.mqttv3.{ IMqttAsyncClient, IMqttToken }

import scala.collection.mutable
import scala.concurrent._
import scala.util.Try

final class MqttSourceStage(settings: MqttSourceSettings, bufferSize: Int)
    extends GraphStageWithMaterializedValue[SourceShape[MqttMessage], Future[Done]] {

  import MqttConnectorLogic._

  val out = Outlet[MqttMessage]("MqttSource.out")
  override val shape: SourceShape[MqttMessage] = SourceShape.of(out)
  override protected def initialAttributes: Attributes = Attributes.name("MqttSource")

  override def createLogicAndMaterializedValue(inheritedAttributes: Attributes) = {

    val subscriptionPromise = Promise[Done]

    (new GraphStageLogic(shape) with MqttConnectorLogic {

      private val queue = mutable.Queue[MqttMessage]()
      private val mqttSubscriptionCallback: Try[IMqttToken] => Unit = conn =>
        subscriptionPromise.complete(conn.map { _ =>
          Done
        })
      private val backpressure = new Semaphore(bufferSize)

      override val connectionSettings = settings.connectionSettings

      setHandler(out,
        new OutHandler {
        override def onPull(): Unit =
          if (queue.nonEmpty) {
            pushMessage(queue.dequeue())
          }
      })

      override def handleConnection(client: IMqttAsyncClient) = {
        val (topics, qos) = settings.subscriptions.unzip
        client.subscribe(topics.toArray, qos.map(_.byteValue.toInt).toArray, (), mqttSubscriptionCallback)
      }

      override def beforeHandleMessage(): Unit =
        backpressure.acquire()

      override def handleMessage(message: MqttMessage): Unit = {
        require(queue.size <= bufferSize)
        if (isAvailable(out)) {
          pushMessage(message)
        } else {
          queue.enqueue(message)
        }
      }

      def pushMessage(message: MqttMessage): Unit = {
        push(out, message)
        backpressure.release()
      }

      override def handleConnectionLost(ex: Throwable) =
        failStage(ex)

    }, subscriptionPromise.future)
  }

}
