/*
 * Copyright (C) 2016-2018 Lightbend Inc. <http://www.lightbend.com>
 */

package akka.stream.alpakka.ironmq.scaladsl

import akka.dispatch.ExecutionContexts
import akka.stream.alpakka.ironmq.{IronMqSettings, IronMqSpec, PushMessage}
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.{Done, NotUsed}
import akka.stream.testkit.scaladsl.StreamTestKit.assertAllStagesStopped

import scala.concurrent.{ExecutionContext, Future}

class IronMqProducerSpec extends IronMqSpec {

  import IronMqProducer._

  val messages: Source[PushMessage, NotUsed] =
    Source.fromIterator(() => Iterator.from(0)).map(i => PushMessage(s"test-$i"))
  implicit val ec: ExecutionContext = ExecutionContexts.global()

  "producerSink" should {
    "publish messages on IronMq" in assertAllStagesStopped {

      val queue = givenQueue()
      val settings = IronMqSettings()

      val expectedMessagesBodies = List("test-1", "test-2")

      val done = Source(expectedMessagesBodies).map(PushMessage(_)).runWith(producerSink(queue.name, settings))

      whenReady(done) { _ =>
        ironMqClient
          .pullMessages(queue.name, 20)
          .futureValue
          .map(_.body)
          .toSeq should contain theSameElementsInOrderAs expectedMessagesBodies

      }

    }
  }

  "producerFlow" should {
    "return published messages' ids" in assertAllStagesStopped {

      val queue = givenQueue()
      val settings = IronMqSettings()

      val messageIds = messages.take(10).via(producerFlow(queue.name, settings)).runWith(Sink.seq).futureValue

      ironMqClient
        .pullMessages(queue.name, 10)
        .futureValue
        .map(_.messageId)
        .toSeq should contain theSameElementsInOrderAs messageIds

    }
  }

  "atLeastOnceProducerFlow" should {
    "commit the committables" in assertAllStagesStopped {

      val queue = givenQueue()
      val settings = IronMqSettings()

      val committables = List(
        new MockCommittable,
        new MockCommittable,
        new MockCommittable
      )

      whenReady(
        messages
          .zip(Source(committables))
          .via(atLeastOnceProducerFlow(queue.name, settings, Flow[Committable].mapAsync(1)(_.commit())))
          .runWith(Sink.ignore)
      ) { _ =>
        committables.forall(_.committed) shouldBe true
      }
    }
  }

}

class MockCommittable extends Committable {

  var committed: Boolean = false

  override def commit(): Future[Done] = {
    committed = true
    Future.successful(Done)
  }
}
