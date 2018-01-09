/*
 * Copyright (C) 2016-2017 Lightbend Inc. <http://www.lightbend.com>
 */

package akka.stream.alpakka.elasticsearch.javadsl

import java.util.concurrent.CompletionStage

import akka.{Done, NotUsed}
import akka.stream.alpakka.elasticsearch._
import akka.stream.javadsl._
import com.fasterxml.jackson.databind.ObjectMapper
import org.elasticsearch.client.RestClient

/**
 * Java API to create Elasticsearch sinks.
 */
object ElasticsearchSink {

  /**
   * Creates a [[akka.stream.javadsl.Sink]] to Elasticsearch for [[IncomingMessage]] containing type `T`.
   */
  def create[T](
      indexName: String,
      typeName: String,
      settings: ElasticsearchSinkSettings,
      client: RestClient,
      objectMapper: ObjectMapper
  ): akka.stream.javadsl.Sink[IncomingMessage[T, NotUsed], CompletionStage[Done]] =
    ElasticsearchFlow
      .create(indexName, typeName, settings, client, objectMapper)
      .toMat(Sink.ignore, Keep.right[NotUsed, CompletionStage[Done]])

}
