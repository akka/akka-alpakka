/*
 * Copyright (C) 2016-2019 Lightbend Inc. <http://www.lightbend.com>
 */

// ORIGINAL LICENCE
/*
 *  Copyright 2017 PayPal
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package akka.stream.alpakka.chroniclequeue

import java.util.concurrent.atomic.AtomicInteger

import akka.Done
import akka.actor.ActorSystem
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, Keep, RunnableGraph, Sink, Source}
import akka.stream.{AbruptTerminationException, ActorMaterializer, ClosedShape}
import akka.util.ByteString
import com.typesafe.config.ConfigFactory
import org.scalatest.concurrent.Eventually
import org.scalatest.time.{Seconds, Span}
import org.scalatest.{BeforeAndAfterAll, FlatSpec, Matchers}
import org.scalatest.concurrent.ScalaFutures._

import scala.concurrent.Promise
import scala.reflect._

import akka.stream.alpakka.chroniclequeue.scaladsl._
import Timeouts._

object ChronicleQueueSpec {
  val testConfig = ConfigFactory.parseString("""
      |akka.actor.default-dispatcher {
      |  executor = "affinity-pool-executor"
      |  affinity-pool-executor {
      |    parallelism-min = 1
      |    parallelism-max = 2
      |  }
      |}
    """.stripMargin)
}

abstract class ChronicleQueueSpec[T: ClassTag, Q <: impl.ChronicleQueueSerializer[T]: Manifest](typeName: String)
    extends FlatSpec
    with Matchers
    with BeforeAndAfterAll
    with Eventually {

  implicit val system = ActorSystem(s"Persistent${typeName}BufferSpec", ChronicleQueueSpec.testConfig)
  implicit val mat = ActorMaterializer()
  implicit val serializer = ChronicleQueueSerializer[T]()
  implicit override val patienceConfig = PatienceConfig(timeout = Span(3, Seconds)) // extend eventually timeout for CI
  import StreamSpecDefaults._
  import system.dispatcher

  val transform = Flow[Int].map(createElement)

  override def afterAll = system.terminate().futureValue(awaitMax)

  def createElement(n: Int): T

  def format(element: T): String

  it should s"buffer a stream of $elementCount elements" in {
    val util = new StreamSpecBase[T, T]
    import util._
    //#create-buffer
    val buffer = ChronicleQueue[T](config)
    //#create-buffer
    buffer.queue.serializer shouldBe a[Q]
    //#use-buffer
    val countFuture = in.via(transform).via(buffer.async).runWith(flowCounter)
    //#use-buffer
    val count = countFuture.futureValue(awaitMax)
    count shouldBe elementCount
    eventually { buffer.queue shouldBe 'closed }
    clean()
  }

  it should s"buffer a stream of $elementCount elements using GraphDSL and custom config" in {
    val util = new StreamSpecBase[T, T]
    import util._
    val buffer = ChronicleQueue[T](config)
    val streamGraph = RunnableGraph.fromGraph(GraphDSL.create(flowCounter) { implicit builder => sink =>
      import GraphDSL.Implicits._
      in ~> transform ~> buffer.async ~> sink
      ClosedShape
    })
    val countFuture = streamGraph.run()
    val count = countFuture.futureValue(awaitMax)
    count shouldBe elementCount
    eventually { buffer.queue shouldBe 'closed }
    clean()
  }

  it should "buffer for a throttled stream" in {
    var t1, t2 = Long.MinValue
    val util = new StreamSpecBase[T, T]
    import util._
    val buffer = ChronicleQueue[T](config)
    val t0 = System.nanoTime
    def counter(recordFn: Long => Unit) =
      Flow[Any]
        .map(_ => 1L)
        .reduce(_ + _)
        .map { s =>
          recordFn(System.nanoTime - t0)
          s
        }
        .toMat(Sink.head)(Keep.right)

    val streamGraph = RunnableGraph.fromGraph(GraphDSL.create(counter(t1 = _), flowCounter)((_, _)) {
      implicit builder => (sink, total) =>
        import GraphDSL.Implicits._
        val bc1 = builder.add(Broadcast[T](2))
        val bc2 = builder.add(Broadcast[T](2))
        in ~> transform ~> bc1 ~> buffer.async ~> throttle ~> bc2 ~> sink
        bc2 ~> total
        bc1 ~> counter(t2 = _)
        ClosedShape
    })
    val (countF, totalF) = streamGraph.run()
    val count = countF.futureValue(awaitMax)
    val totalProcessed = totalF.futureValue(awaitMax)
    eventually { buffer.queue shouldBe 'closed }

    count shouldBe elementCount
    totalProcessed shouldBe elementCount
    t1 should be > t2 // Give 6 seconds difference. In fact, it should be closer to 9 seconds.
    clean()
  }

  it should "recover from unexpected stream shutdown" in {
    implicit val util = new StreamSpecBase[T, T]
    import util._

    val mat = ActorMaterializer()
    val pBufferInCount = new AtomicInteger(0)
    val commitCount = new AtomicInteger(0)
    val finishedGenerating = Promise[Done]
    val counter = new AtomicInteger(0)

    def fireFinished() =
      Flow[T]
        .map { e =>
          if (counter.incrementAndGet() == failTestAt) finishedGenerating.success(Done)
          e
        }
        .toMat(Sink.ignore)(Keep.right)

    val shutdownF = finishedGenerating.future.map { d =>
      mat.shutdown(); d
    }

    val graph = RunnableGraph.fromGraph(GraphDSL.create(Sink.ignore) { implicit builder => sink =>
      import GraphDSL.Implicits._
      val buffer = ChronicleQueue[T](config)
        .withOnPushCallback(() => pBufferInCount.incrementAndGet())
        .withOnCommitCallback(() => commitCount.incrementAndGet())
      val bc = builder.add(Broadcast[T](2))

      in ~> transform ~> bc ~> buffer.async ~> throttle ~> sink
      bc ~> fireFinished()

      ClosedShape
    })
    val sinkF = graph.run()(mat)
    shutdownF.futureValue(awaitMax)
    sinkF.failed.futureValue(awaitMax) shouldBe an[AbruptTerminationException]

    val restartFrom = commitCount.get()

    resumeGraphAndDoAssertion(pBufferInCount.get, restartFrom)
    clean()
  }

  it should "recover from downstream failure" in {
    implicit val util = new StreamSpecBase[T, T]
    import util._

    val mat = ActorMaterializer()
    val outCount = new AtomicInteger(0)
    val injectCounter = new AtomicInteger(0)
    val inCounter = new AtomicInteger(0)

    val injectError = Flow[T].map { n =>
      val count = injectCounter.incrementAndGet()
      if (count == failTestAt) throw new NumberFormatException("This is a fake exception")
      else n
    }

    val graph = RunnableGraph.fromGraph(GraphDSL.create(Sink.ignore) { implicit builder => sink =>
      import GraphDSL.Implicits._
      val buffer = ChronicleQueue[T](config)
        .withOnPushCallback(() => inCounter.incrementAndGet())
        .withOnCommitCallback(() => outCount.incrementAndGet())
      in ~> transform ~> buffer.async ~> throttle ~> injectError ~> sink
      ClosedShape
    })
    val sinkF = graph.run()(mat)
    sinkF.failed.futureValue(awaitMax) shouldBe an[NumberFormatException]
    val restartFrom = outCount.get()
    resumeGraphAndDoAssertion(inCounter.get, restartFrom)
    clean()
  }

  it should "recover from upstream failure" in {
    implicit val util = new StreamSpecBase[T, T]
    import util._
    val mat = ActorMaterializer()
    val recordCount = new AtomicInteger(0)

    val injectError = Flow[Int].map { n =>
      if (n == failTestAt) throw new NumberFormatException("This is a fake exception")
      else n
    }

    def updateCounter() = Sink.foreach[Any] { _ =>
      recordCount.incrementAndGet()
    }

    val buffer = ChronicleQueue[T](config)
    val graph = RunnableGraph.fromGraph(GraphDSL.create(updateCounter()) { implicit builder => sink =>
      import GraphDSL.Implicits._
      in ~> injectError ~> transform ~> buffer.async ~> throttle ~> sink
      ClosedShape
    })
    val countF = graph.run()(mat)
    countF.futureValue(awaitMax)
    eventually { buffer.queue shouldBe 'closed }
    resumeGraphAndDoAssertion(recordCount.get, failTestAt)
    clean()
  }

  private def resumeGraphAndDoAssertion(beforeShutDown: Long, restartFrom: Int)(implicit util: StreamSpecBase[T, T]) = {
    import util._
    val buffer = ChronicleQueue[T](config)
    val graph = RunnableGraph.fromGraph(GraphDSL.create(flowCounter, head)((_, _)) {
      implicit builder => (sink, first) =>
        import GraphDSL.Implicits._
        val bc = builder.add(Broadcast[T](2))
        Source(restartFrom to (elementCount + elementsAfterFail)) ~> transform ~> buffer.async ~> bc ~> sink
        bc ~> first
        ClosedShape
    })
    val (countF, firstF) = graph.run()(ActorMaterializer())
    val afterRecovery = countF.futureValue(awaitMax)
    val first = firstF.futureValue(awaitMax)
    eventually { buffer.queue shouldBe 'closed }
    assertions(beforeShutDown, afterRecovery, totalProcessed)
  }

  private def assertions(beforeShutDown: Long, afterRecovery: Long, totalRecords: Long) = {
    val processedRecords = beforeShutDown + afterRecovery
    val lostRecords = totalRecords - processedRecords
    processedRecords should be >= totalRecords
  }
}

class PersistentByteStringBufferSpec extends ChronicleQueueSpec[ByteString, ByteStringSerializer]("ByteString") {

  def createElement(n: Int): ByteString = ByteString(s"Hello $n")

  def format(element: ByteString): String = element.utf8String
}

class PersistentStringBufferSpec extends ChronicleQueueSpec[String, ObjectSerializer[String]]("Object") {

  def createElement(n: Int): String = s"Hello $n"

  def format(element: String): String = element
}

class PersistentLongBufferSpec extends ChronicleQueueSpec[Long, LongSerializer]("Long") {

  def createElement(n: Int): Long = n

  def format(element: Long): String = element.toString
}

class PersistentIntBufferSpec extends ChronicleQueueSpec[Int, IntSerializer]("Int") {

  def createElement(n: Int): Int = n

  def format(element: Int): String = element.toString
}

class PersistentShortBufferSpec extends ChronicleQueueSpec[Short, ShortSerializer]("Short") {

  def createElement(n: Int): Short = n.toShort

  def format(element: Short): String = element.toString
}

class PersistentByteBufferSpec extends ChronicleQueueSpec[Byte, ByteSerializer]("Byte") {

  def createElement(n: Int): Byte = n.toByte

  def format(element: Byte): String = element.toString
}

class PersistentCharBufferSpec extends ChronicleQueueSpec[Char, CharSerializer]("Char") {

  def createElement(n: Int): Char = n.toChar

  def format(element: Char): String = element.toString
}

class PersistentDoubleBufferSpec extends ChronicleQueueSpec[Double, DoubleSerializer]("Double") {

  def createElement(n: Int): Double = n.toDouble

  def format(element: Double): String = element.toString
}

class PersistentFloatBufferSpec extends ChronicleQueueSpec[Float, FloatSerializer]("Float") {

  def createElement(n: Int): Float = n.toFloat

  def format(element: Float): String = element.toString
}

class PersistentBooleanBufferSpec extends ChronicleQueueSpec[Boolean, BooleanSerializer]("Boolean") {

  def createElement(n: Int): Boolean = n % 2 == 0

  def format(element: Boolean): String = element.toString
}

class PersistentPersonBufferSpec extends ChronicleQueueSpec[Person, PersonSerializer]("Person") {

  override implicit val serializer = new PersonSerializer()

  def createElement(n: Int): Person = Person(s"John Doe $n", 20)

  def format(element: Person): String = element.toString
}
