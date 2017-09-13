/*
 * Copyright (C) 2016-2017 Lightbend Inc. <http://www.lightbend.com>
 */
package akka.stream.alpakka.ibm.eventstore.javadsl

import java.util.concurrent.CompletionStage

import akka.Done
import akka.stream.alpakka.ibm.eventstore.EventStoreConfiguration
import akka.stream.alpakka.ibm.eventstore.scaladsl.{EventStoreSink => ScalaEventStoreSink}
import akka.stream.javadsl
import org.apache.spark.sql.Row

import scala.compat.java8.FutureConverters._
import scala.concurrent.ExecutionContext

object EventStoreSink {

  def create(
      configuration: EventStoreConfiguration,
      ec: ExecutionContext
  ): javadsl.Sink[Row, CompletionStage[Done]] = {

    ScalaEventStoreSink.apply(configuration)(ec).
      mapMaterializedValue(_.toJava).asJava
  }
}
