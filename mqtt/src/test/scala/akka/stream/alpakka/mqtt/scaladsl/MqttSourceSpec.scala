/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package akka.stream.alpakka.mqtt.scaladsl

import akka.actor.ActorSystem
import akka.stream._
import akka.stream.alpakka.mqtt.{ MqttConnectionSettings, MqttMessage, MqttQoS, MqttSourceSettings }
import akka.stream.scaladsl._
import akka.stream.testkit.scaladsl.TestSink
import akka.util.ByteString
import io.moquette.proto.messages.AbstractMessage.QOSType
import io.moquette.proto.messages.PublishMessage
import io.moquette.server.Server
import io.moquette.server.config.FilesystemConfig
import io.moquette.spi.security.IAuthenticator
import org.eclipse.paho.client.mqttv3.MqttException
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import org.scalatest._
import org.scalatest.concurrent.ScalaFutures

import scala.concurrent._
import scala.concurrent.duration._

class MqttSourceSpec extends WordSpec with Matchers with ScalaFutures {

  import MqttSourceSpec._

  implicit val defaultPatience =
    PatienceConfig(timeout = 5.seconds, interval = 100.millis)

  "mqtt source" should {
    "receive a message from a topic" in withBroker(Map("topic1" -> MqttQoS.AtMostOnce)) { p =>
      val f = fixture(p)
      import f._

      val (subscriptionFuture, probe) = MqttSource(p.settings, 8).toMat(TestSink.probe)(Keep.both).run()
      whenReady(subscriptionFuture) { _ =>
        publish("topic1", "ohi")
        probe.requestNext shouldBe MqttMessage("topic1", ByteString("ohi"))
      }
    }

    "receive messages from multiple topics" in withBroker(Map("topic1" -> MqttQoS.AtMostOnce, "topic2" -> MqttQoS.AtMostOnce)) { p =>
      val f = fixture(p)
      import f._

      val (subscriptionFuture, probe) = MqttSource(p.settings, 8).toMat(TestSink.probe)(Keep.both).run()
      whenReady(subscriptionFuture) { _ =>
        publish("topic1", "ohi")
        publish("topic2", "hello again")
        probe.requestNext shouldBe MqttMessage("topic1", ByteString("ohi"))
        probe.requestNext shouldBe MqttMessage("topic2", ByteString("hello again"))
      }
    }

    "receive messages from multiple topics (for docs)" in withBroker(Map.empty) { p =>
      val f = fixture(p)
      import f._

      //#create-settings
      val settings = MqttSourceSettings(
        MqttConnectionSettings(
          "tcp://localhost:1883",
          "test-client",
          new MemoryPersistence
        ),
        Map("topic1" -> MqttQoS.AtMostOnce, "topic2" -> MqttQoS.AtMostOnce)
      )
      //#create-settings

      val messageCount = 7

      //#create-source
      val mqttSource = MqttSource(settings, bufferSize = 8)
      //#create-source

      //#run-source
      val (subscriptionFuture, result) = mqttSource
        .map(m => s"${m.topic}_${m.payload.utf8String}")
        .take(messageCount * 2)
        .toMat(Sink.seq)(Keep.both)
        .run()
      //#run-source

      whenReady(subscriptionFuture) { _ =>
        val expected = (0 until messageCount).flatMap { i =>
          publish("topic1", i.toString)
          publish("topic2", i.toString)
          Seq(s"topic1_$i", s"topic2_$i")
        }

        result.futureValue shouldBe expected
      }
    }

    "fail stream when disconnected" in withBroker(Map("topic1" -> MqttQoS.AtMostOnce)) { p =>
      val f = fixture(p)
      import f._

      val (subscriptionFuture, probe) = MqttSource(p.settings, 8).toMat(TestSink.probe)(Keep.both).run()
      whenReady(subscriptionFuture) { _ =>
        publish("topic1", "ohi")
        probe.requestNext shouldBe MqttMessage("topic1", ByteString("ohi"))

        server.stopServer()
        probe.expectError.getMessage should be("Connection lost")
      }
    }

    "fail connection when not providing the requested credentials" in withBroker(Map(), Some(("user", "passwd"))) { p =>
      val f = fixture(p)
      import f._

      val (subscriptionFuture, probe) = MqttSource(p.settings, 8).toMat(TestSink.probe)(Keep.both).run()
      whenReady(subscriptionFuture.failed) {
        case e: MqttException => e.getMessage should be("Connection lost")
        case e                => throw e
      }
    }

    "receive a message from a topic with right credentials" in withBroker(Map("topic1" -> MqttQoS.AtMostOnce), Some(("user", "passwd"))) { p =>
      val f = fixture(p)
      import f._

      val settings = withClientAuth(p.settings, ("user", "passwd"))
      val (subscriptionFuture, probe) = MqttSource(settings, 8).toMat(TestSink.probe)(Keep.both).run()
      whenReady(subscriptionFuture) { _ =>
        publish("topic1", "ohi")
        probe.requestNext shouldBe MqttMessage("topic1", ByteString("ohi"))
      }
    }

    "signal backpressure" in withBroker(Map("topic1" -> MqttQoS.AtMostOnce)) { p =>
      val f = fixture(p)
      import f._

      val bufferSize = 8
      val overflow = 4

      val (subscriptionFuture, probe) = MqttSource(p.settings, bufferSize).toMat(TestSink.probe)(Keep.both).run()
      whenReady(subscriptionFuture) { _ =>

        (1 to bufferSize + overflow) foreach { i =>
          publish("topic1", s"ohi_$i")
        }

        (1 to bufferSize + overflow) foreach { i =>
          probe.requestNext shouldBe MqttMessage("topic1", ByteString(s"ohi_$i"))
        }
      }
    }

    "work with fast downstream" in withBroker(Map("topic1" -> MqttQoS.AtMostOnce)) { p =>
      val f = fixture(p)
      import f._

      val bufferSize = 8
      val overflow = 4

      val (subscriptionFuture, probe) = MqttSource(p.settings, bufferSize).toMat(TestSink.probe)(Keep.both).run()
      whenReady(subscriptionFuture) { _ =>

        probe.request((bufferSize + overflow).toLong)

        (1 to bufferSize + overflow) foreach { i =>
          publish("topic1", s"ohi_$i")
        }

        (1 to bufferSize + overflow) foreach { i =>
          probe.expectNext() shouldBe MqttMessage("topic1", ByteString(s"ohi_$i"))
        }
      }
    }

    "support multiple materialization" in withBroker(Map("topic1" -> MqttQoS.AtMostOnce)) { p =>
      val f = fixture(p)
      import f._

      val source = MqttSource(p.settings, 8)

      val (sub, elem) = source.toMat(Sink.head)(Keep.both).run()
      whenReady(sub) { _ =>
        publish("topic1", s"ohi")
        elem.futureValue shouldBe MqttMessage("topic1", ByteString("ohi"))
      }

      val (sub2, elem2) = source.toMat(Sink.head)(Keep.both).run()
      whenReady(sub2) { _ =>
        publish("topic1", s"ohi")
        elem2.futureValue shouldBe MqttMessage("topic1", ByteString("ohi"))
      }
    }
  }

  def publish(topic: String, payload: String)(implicit server: Server) = {
    val msg = new PublishMessage()
    msg.setPayload(ByteString(payload).toByteBuffer)
    msg.setTopicName(topic)
    msg.setQos(QOSType.valueOf(MqttQoS.AtMostOnce.byteValue))
    server.internalPublish(msg)
  }

  def withBroker(subscriptions: Map[String, MqttQoS], serverAuth: Option[(String, String)] = None)(test: FixtureParam => Any) = {
    implicit val sys = ActorSystem("MqttSourceSpec")
    val mat = ActorMaterializer()

    val settings = MqttSourceSettings(
      MqttConnectionSettings(
        "tcp://localhost:1883",
        "test-client",
        new MemoryPersistence
      ),
      subscriptions
    )

    val server = new Server()
    val authenticator = new IAuthenticator {
      override def checkValid(username: String, password: Array[Byte]): Boolean =
        serverAuth.fold(true) { case (u, p) => username == u && new String(password) == p }
    }
    server.startServer(new FilesystemConfig, null, null, authenticator, null)
    try {
      test(FixtureParam(settings, server, sys, mat))
    } finally {
      server.stopServer()
    }

    Await.ready(sys.terminate(), 5.seconds)
  }

  def withClientAuth(settings: MqttSourceSettings, auth: (String, String)): MqttSourceSettings =
    settings.copy(connectionSettings = settings.connectionSettings.copy(auth = Some(auth)))

}

object MqttSourceSpec {

  def fixture(p: FixtureParam) = new {
    implicit val server = p.server
    implicit val system = p.sys
    implicit val materializer = p.mat
  }

  case class FixtureParam(settings: MqttSourceSettings, server: Server, sys: ActorSystem, mat: Materializer)

}
