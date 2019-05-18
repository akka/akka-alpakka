/*
 * Copyright (C) 2016-2019 Lightbend Inc. <http://www.lightbend.com>
 */

package docs.scaladsl

import java.util.concurrent.TimeUnit

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.alpakka.influxdb.{InfluxDbSettings, InfluxDbWriteMessage, InfluxDbWriteResult}
import akka.stream.alpakka.influxdb.scaladsl.InfluxDbFlow
import akka.stream.scaladsl.{Sink, Source}
import akka.testkit.TestKit
import org.influxdb.InfluxDB
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, MustMatchers, WordSpec}
import org.scalatest.concurrent.ScalaFutures
import docs.javadsl.TestUtils._
import akka.stream.testkit.scaladsl.StreamTestKit.assertAllStagesStopped
import org.influxdb.dto.Point

private final case class InvalidModel(description: String) {}

class FlowSpec extends WordSpec with MustMatchers with BeforeAndAfterEach with BeforeAndAfterAll with ScalaFutures {

  implicit val system = ActorSystem()
  implicit val mat = ActorMaterializer()

  final val DatabaseName = this.getClass.getSimpleName

  implicit var influxDB: InfluxDB = _

  override protected def beforeAll(): Unit =
    influxDB = setupConnection(DatabaseName)

  override protected def afterAll(): Unit = {
    dropDatabase(influxDB, DatabaseName)
    TestKit.shutdownActorSystem(system)
  }

  "invalid model" in assertAllStagesStopped {
    val result = Source(
      List(InfluxDbWriteMessage(InvalidModel("Invalid measurement one")),
           InfluxDbWriteMessage(InvalidModel("Invalid measurement two")))
    ).via(InfluxDbFlow.create[InvalidModel](InfluxDbSettings()))
      .recover {
        case _: RuntimeException => InfluxDbWriteResult(null, Some("error occurred"))
      }
      .runWith(Sink.seq)
      .futureValue

    result mustBe Seq(InfluxDbWriteResult(null, Some("error occurred")))
  }

  "mixed model" in assertAllStagesStopped {

    val point = Point
      .measurement("disk")
      .time(System.currentTimeMillis(), TimeUnit.MILLISECONDS)
      .addField("used", 80L)
      .addField("free", 1L)
      .build()

    val validMessage = InfluxDbWriteMessage(point)
      .withDatabaseName(DatabaseName)

    val result = Source(
      List(
        validMessage
      )
    ).via(InfluxDbFlow.create[Point](InfluxDbSettings()))
      .runWith(Sink.seq)
      .futureValue

    result(0).error mustBe None
  }
}
