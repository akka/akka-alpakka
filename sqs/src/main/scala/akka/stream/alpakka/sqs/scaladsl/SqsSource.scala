/*
 * Copyright (C) 2016-2019 Lightbend Inc. <http://www.lightbend.com>
 */

package akka.stream.alpakka.sqs.scaladsl

import java.time.Clock

import akka._
import akka.stream._
import akka.stream.alpakka.sqs.{ControlledThrottling, SqsSourceSettings}
import akka.stream.scaladsl.Source
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import software.amazon.awssdk.services.sqs.model.{Message, QueueAttributeName, ReceiveMessageRequest}

import scala.collection.JavaConverters._
import scala.compat.java8.FutureConverters._
import scala.concurrent.duration._

/**
 * Scala API to create SQS sources.
 */
object SqsSource {

  /**
   * creates a [[akka.stream.scaladsl.Source Source]] for a SQS queue using [[software.amazon.awssdk.services.sqs.SqsAsyncClient SqsAsyncClient]]
   */
  def apply(
      queueUrl: String,
      settings: SqsSourceSettings = SqsSourceSettings.Defaults
  )(implicit sqsClient: SqsAsyncClient): Source[Message, NotUsed] = {
    val controlledThrottling = new ControlledThrottling[ReceiveMessageRequest](Clock.systemDefaultZone(), settings.waitTimeSeconds.seconds)

    Source
      .repeat {
        val requestBuilder =
          ReceiveMessageRequest
            .builder()
            .queueUrl(queueUrl)
            .attributeNames(settings.attributeNames.map(_.name).map(QueueAttributeName.fromValue).asJava)
            .messageAttributeNames(settings.messageAttributeNames.map(_.name).asJava)
            .maxNumberOfMessages(settings.maxBatchSize)
            .waitTimeSeconds(settings.waitTimeSeconds)

        settings.visibilityTimeout match {
          case None => requestBuilder.build()
          case Some(t) => requestBuilder.visibilityTimeout(t.toSeconds.toInt).build()
        }
      }
      .via(controlledThrottling)
      .mapAsync(settings.parallelRequests)(sqsClient.receiveMessage(_).toScala)
      .map(_.messages().asScala.toList)
      .takeWhile(messages => !settings.closeOnEmptyReceive || messages.nonEmpty)
      .wireTap { messages =>
        controlledThrottling.setThrottling(messages.isEmpty)
      }
      .mapConcat(identity)
      .buffer(settings.maxBufferSize, OverflowStrategy.backpressure)
  }
}
