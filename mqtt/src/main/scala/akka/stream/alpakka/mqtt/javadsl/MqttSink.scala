/*
 * Copyright (C) 2016-2017 Lightbend Inc. <http://www.lightbend.com>
 */

package akka.stream.alpakka.mqtt.javadsl

import java.util.concurrent.CompletionStage

import akka.Done
import akka.stream.alpakka.mqtt.{MqttConnectionSettings, MqttMessage, MqttQoS, MqttSourceSettings}
import akka.stream.javadsl.Sink

object MqttSink {

  /**
   * Java API: create an [[MqttSink]] for a provided QoS.
   */
  def create(connectionSettings: MqttConnectionSettings,
             qos: MqttQoS): akka.stream.javadsl.Sink[MqttMessage, CompletionStage[Done]] =
    MqttFlow
      .create(MqttSourceSettings(connectionSettings), 0, qos)
      .to(Sink.ignore.asScala)
}
