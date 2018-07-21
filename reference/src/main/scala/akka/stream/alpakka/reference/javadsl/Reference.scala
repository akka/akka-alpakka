/*
 * Copyright (C) 2016-2018 Lightbend Inc. <http://www.lightbend.com>
 */

package akka.stream.alpakka.reference.javadsl

import java.util.concurrent.{CompletionStage, Executor}

import akka.{Done, NotUsed}
import akka.stream.alpakka.reference
import akka.stream.alpakka.reference.{ReferenceReadMessage, ReferenceWriteMessage, SourceSettings}
import akka.stream.javadsl.{Flow, Source}

import scala.concurrent.ExecutionContext

object Reference {

  /**
   * No Java API at the start of the method doc needed, since the package is dedicated to the Java API.
   *
   * Call Scala source factory and convert both: the source and materialized values to Java classes.
   */
  def source(settings: SourceSettings): Source[ReferenceReadMessage, CompletionStage[Done]] = {
    import scala.compat.java8.FutureConverters._
    reference.scaladsl.Reference.source(settings).mapMaterializedValue(_.toJava).asJava
  }

  /**
   * Only convert the flow type, as the materialized value type is the same between Java and Scala.
   */
  def flow(): Flow[ReferenceWriteMessage, ReferenceWriteMessage, NotUsed] =
    reference.scaladsl.Reference.flow().asJava

  /**
   * In Java API take Executor as parameter if the operator needs to perform asynchronous tasks.
   */
  def flowAsyncMapped(ex: Executor): Flow[ReferenceWriteMessage, ReferenceWriteMessage, NotUsed] =
    reference.scaladsl.Reference.flowAsyncMapped()(ExecutionContext.fromExecutor(ex)).asJava

}
