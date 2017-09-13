/*
 * Copyright (C) 2016-2017 Lightbend Inc. <http://www.lightbend.com>
 */
package akka.stream.alpakka.ibm.eventstore.scaladsl

import akka.Done
import akka.actor.ActorSystem
import akka.stream._
import akka.stream.scaladsl.Flow
import akka.stream.scaladsl.Keep
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Source
import akka.stream.alpakka.ibm.eventstore.EventStoreConfiguration

import scala.concurrent.{ExecutionContext, Future}
import com.ibm.event.common.ConfigurationReader
import com.ibm.event.oltp.EventContext
import org.apache.spark.sql.Row

object EventStoreSink {
  def apply(
      configuration: EventStoreConfiguration
  )(implicit ec: ExecutionContext): Sink[Row, Future[Done]] = {

    ConfigurationReader.setConnectionEndpoints(s"${configuration.host}:${configuration.port}")
    val context = EventContext.getEventContext(configuration.databaseName)
    val schema = context.getTable(configuration.tableName)

    Flow[Row]
      .mapAsyncUnordered(configuration.parallelism) { row ⇒
        context.insertAsync(schema, row)
      }
      .toMat(Sink.ignore)(Keep.right)
  }
}
