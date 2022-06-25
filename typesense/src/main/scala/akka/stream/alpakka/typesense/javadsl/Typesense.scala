/*
 * Copyright (C) since 2016 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.stream.alpakka.typesense.javadsl

import akka.stream.alpakka.typesense.{CollectionResponse, CollectionSchema, TypesenseSettings}
import akka.stream.javadsl.{Flow, Sink}
import akka.{Done, NotUsed}

import java.util.concurrent.CompletionStage
import scala.compat.java8.FutureConverters.FutureOps

object Typesense {
  private val ScalaTypesense = akka.stream.alpakka.typesense.scaladsl.Typesense

  /**
   * Creates a flow for creating collections.
   */
  def createCollectionFlow(
      settings: TypesenseSettings
  ): Flow[CollectionSchema, CollectionResponse, CompletionStage[NotUsed]] =
    ScalaTypesense
      .createCollectionFlow(settings)
      .mapMaterializedValue(_.toJava)
      .asJava

  /**
   * Creates a sink for creating collections.
   */
  def createCollection(settings: TypesenseSettings): Sink[CollectionSchema, CompletionStage[Done]] =
    ScalaTypesense
      .createCollection(settings)
      .mapMaterializedValue(_.toJava)
      .asJava
}
