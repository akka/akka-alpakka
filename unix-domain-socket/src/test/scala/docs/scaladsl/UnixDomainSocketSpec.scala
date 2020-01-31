/*
 * Copyright (C) 2016-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.scaladsl

import java.io.IOException
import java.nio.file.{Files, Paths}
import java.util.concurrent.TimeUnit

import akka.Done
import akka.actor.ActorSystem
import akka.stream.alpakka.testkit.scaladsl.LogCapturing
import akka.stream.alpakka.unixdomainsocket.UnixSocketAddress
import akka.stream.{ActorMaterializer, OverflowStrategy}
import akka.stream.alpakka.unixdomainsocket.scaladsl.UnixDomainSocket
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.testkit._
import akka.util.ByteString
import org.scalatest._
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.concurrent.duration._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

class UnixDomainSocketSpec
    extends TestKit(ActorSystem("UnixDomainSocketSpec"))
    with AnyWordSpecLike
    with Matchers
    with ScalaFutures
    with BeforeAndAfterAll
    with IntegrationPatience
    with LogCapturing {

  override def afterAll: Unit =
    TestKit.shutdownActorSystem(system)

  implicit val ma: ActorMaterializer = ActorMaterializer()
  implicit val ec: ExecutionContext = system.dispatcher

  private val dir = Files.createTempDirectory("UnixDomainSocketSpec")

  "A Unix Domain Socket" should {
    "receive some bytes" in {
      //#binding
      val path: java.nio.file.Path = // ...
        //#binding
        dir.resolve("sock1")
      val received = Promise[ByteString]

      val serverSideFlow = Flow[ByteString]
        .buffer(1, OverflowStrategy.backpressure)
        .wireTap(bytes => received.success(bytes))

      //#binding
      val binding: Future[UnixDomainSocket.ServerBinding] =
        UnixDomainSocket().bindAndHandle(serverSideFlow, path)
      //#binding

      //#outgoingConnection
      val sendBytes = ByteString("Hello")
      binding.flatMap { _ => // connection
        Source
          .single(sendBytes)
          .via(UnixDomainSocket().outgoingConnection(path))
          .runWith(Sink.ignore)
      }
      //#outgoingConnection
      received.future.futureValue shouldBe sendBytes
      binding.futureValue.unbind().futureValue should be(())
    }

    "send and receive more ten times the size of a buffer" ignore {
      val BufferSizeBytes = 64 * 1024

      val path = dir.resolve("sock2")

      val binding: Future[UnixDomainSocket.ServerBinding] =
        UnixDomainSocket().bindAndHandle(Flow.fromFunction(identity), path, halfClose = true)

      val sendBytes = ByteString(Array.ofDim[Byte](BufferSizeBytes * 10))
      val result: Future[ByteString] =
        binding.flatMap { connection =>
          Source
            .single(sendBytes)
            .via(UnixDomainSocket().outgoingConnection(path))
            .runWith(Sink.fold(ByteString.empty) { case (acc, b) => acc ++ b })

        }
      result.futureValue shouldBe sendBytes
      binding.futureValue.unbind().futureValue should be(())
    }

    "allow the client to close the connection" in {
      val path = dir.resolve("sock3")

      val sendBytes = ByteString("Hello")

      val binding =
        UnixDomainSocket().bindAndHandle(Flow[ByteString].delay(5.seconds), path)

      val result = binding.flatMap { connection =>
        Source
          .single(sendBytes)
          .via(UnixDomainSocket().outgoingConnection(UnixSocketAddress(path), halfClose = false))
          .runWith(Sink.headOption)
      }
      result.futureValue shouldBe Symbol("empty")
      binding.futureValue.unbind().futureValue should be(())
    }

    "close the server once the client is also closed" in {
      val path = dir.resolve("sock4")

      val sendBytes = ByteString("Hello")
      val receiving = Promise[Done]

      val binding =
        UnixDomainSocket().bindAndHandle(
          Flow.fromFunction[ByteString, ByteString](identity).wireTap(_ => receiving.success(Done)).delay(1.second),
          path,
          halfClose = true
        )

      val result = binding.flatMap { connection =>
        Source
          .tick(0.seconds, 1.second, sendBytes)
          .takeWhile(_ => !receiving.isCompleted)
          .via(UnixDomainSocket().outgoingConnection(path))
          .runWith(Sink.headOption)
      }
      result.futureValue shouldNot be(Symbol("empty"))
      binding.futureValue.unbind().futureValue should be(())
    }

    "be able to materialize outgoing connection flow more than once" in {
      def materialize(flow: Flow[ByteString, ByteString, _]): Future[Done] =
        Source.single(ByteString("Hello")).via(flow).runWith(Sink.ignore)

      val path = dir.resolve("sock5")

      val receivedLatch = new java.util.concurrent.CountDownLatch(2)

      val serverSideFlow = Flow[ByteString]
        .buffer(1, OverflowStrategy.backpressure)
        .wireTap(_ => receivedLatch.countDown())

      val _ = UnixDomainSocket().bindAndHandle(serverSideFlow, path)

      val connection = UnixDomainSocket().outgoingConnection(path)

      materialize(connection)
      materialize(connection)

      receivedLatch.await(5, TimeUnit.SECONDS)
    }

    "not be able to bind to a non-existent file" in {
      val binding =
        UnixDomainSocket().bindAndHandle(Flow.fromFunction(identity), Paths.get("/thisshouldnotexist"))

      binding.failed.futureValue shouldBe an[IOException]
    }

    "not be able to connect to a non-existent file" in {
      val connection =
        Source
          .single(ByteString("hi"))
          .via(UnixDomainSocket().outgoingConnection(Paths.get("/thisshouldnotexist")))
          .runWith(Sink.head)

      connection.failed.futureValue shouldBe an[IOException]
    }

  }
}
