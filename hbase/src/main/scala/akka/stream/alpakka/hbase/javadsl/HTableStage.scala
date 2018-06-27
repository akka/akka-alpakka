/*
 * Copyright (C) 2016-2018 Lightbend Inc. <http://www.lightbend.com>
 */

package akka.stream.alpakka.hbase.javadsl

import akka.stream.alpakka.hbase.HTableSettings
import akka.stream.alpakka.hbase.internal.HBaseFlowStage
import akka.stream.scaladsl.{Flow, Keep, Sink}
import akka.{Done, NotUsed}
import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.hbase.TableName
import org.apache.hadoop.hbase.client.Mutation

import scala.concurrent.Future

object HTableStage {

  def table[T](conf: Configuration,
               tableName: TableName,
               columnFamilies: java.util.List[String],
               converter: java.util.function.Function[T, java.util.List[Mutation]]): HTableSettings[T] = {
    HTableSettings.create(conf, tableName, columnFamilies, converter)
  }

  def sink[A](config: HTableSettings[A]): akka.stream.javadsl.Sink[A, Future[Done]] =
    Flow[A].via(flow(config)).toMat(Sink.ignore)(Keep.right).asJava

  def flow[A](settings: HTableSettings[A]): akka.stream.javadsl.Flow[A, A, NotUsed] =
    Flow.fromGraph(new HBaseFlowStage[A](settings)).asJava

}
