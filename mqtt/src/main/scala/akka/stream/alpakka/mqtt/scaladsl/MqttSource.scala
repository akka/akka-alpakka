/*
 * Copyright (C) 2016-2017 Lightbend Inc. <http://www.lightbend.com>
 */

package akka.stream.alpakka.mqtt.scaladsl

import akka.Done
import akka.stream.alpakka.mqtt.{MqttMessage, MqttQoS, MqttSourceSettings}
import akka.stream.scaladsl.{Keep, Source}

import scala.concurrent.Future

object MqttSource {

  /**
   * Scala API: create an [[MqttSource]] with a provided bufferSize.
   */
  @deprecated("use atMostOnce instead", "0.14")
  def apply(settings: MqttSourceSettings, bufferSize: Int): Source[MqttMessage, Future[Done]] =
    Source.maybe.viaMat(
      MqttFlow(settings, bufferSize, qos = MqttQoS.AtLeastOnce)
    )(Keep.right)

  def atMostOnce(settings: MqttSourceSettings, bufferSize: Int): Source[MqttMessage, Future[Done]] =
    Source.maybe.viaMat(
      MqttFlow.atLeastOnce(settings, bufferSize, qos = MqttQoS.AtLeastOnce)
    )(Keep.right).map(cm => cm.message)

  def atLeastOnce(settings: MqttSourceSettings, bufferSize: Int): Source[MqttCommittableMessage, Future[Done]] =
    Source.maybe.viaMat(
      MqttFlow.atLeastOnce(settings, bufferSize, qos = MqttQoS.AtLeastOnce)
    )(Keep.right)

}
