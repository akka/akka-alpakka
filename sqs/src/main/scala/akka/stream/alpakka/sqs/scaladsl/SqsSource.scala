/*
 * Copyright (C) since 2016 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.stream.alpakka.sqs.scaladsl

import akka._
import akka.stream._
import akka.stream.alpakka.sqs.SqsSourceSettings
import akka.stream.alpakka.sqs.impl.BalancingMapAsync
import akka.stream.scaladsl.{Flow, Source}
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import software.amazon.awssdk.services.sqs.model._

import scala.jdk.CollectionConverters._
import scala.jdk.FutureConverters._

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
    SqsAckFlow.checkClient(sqsClient)
    Source
      .repeat {
        val requestBuilder =
          ReceiveMessageRequest
            .builder()
            .queueUrl(queueUrl)
            .messageSystemAttributeNamesWithStrings(settings.attributeNames.map(_.name).asJava)
            .messageAttributeNames(settings.messageAttributeNames.map(_.name).asJava)
            .maxNumberOfMessages(settings.maxBatchSize)
            .waitTimeSeconds(settings.waitTimeSeconds)

        settings.visibilityTimeout match {
          case None => requestBuilder.build()
          case Some(t) => requestBuilder.visibilityTimeout(t.toSeconds.toInt).build()
        }
      }
      .via(resolveHandler(settings.parallelRequests))
      .map(_.messages().asScala.toList)
      .takeWhile(messages => !settings.closeOnEmptyReceive || messages.nonEmpty)
      .mapConcat(identity)
      .buffer(settings.maxBufferSize, OverflowStrategy.backpressure)
  }

  private def resolveHandler(parallelism: Int)(implicit sqsClient: SqsAsyncClient) =
    if (parallelism == 1) {
      Flow[ReceiveMessageRequest].mapAsyncUnordered(parallelism)(sqsClient.receiveMessage(_).asScala)
    } else {
      BalancingMapAsync[ReceiveMessageRequest, ReceiveMessageResponse](
        parallelism,
        sqsClient.receiveMessage(_).asScala,
        (response, _) => if (response.messages().isEmpty) 1 else parallelism
      )
    }
}
