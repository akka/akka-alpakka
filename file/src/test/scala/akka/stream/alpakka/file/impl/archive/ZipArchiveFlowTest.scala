/*
 * Copyright (C) 2016-2019 Lightbend Inc. <http://www.lightbend.com>
 */

package akka.stream.alpakka.file.impl.archive

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Keep
import akka.stream.testkit.scaladsl.{TestSink, TestSource}
import akka.testkit.TestKit
import akka.util.ByteString
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{Matchers, WordSpecLike}

class ZipArchiveFlowTest extends TestKit(ActorSystem("ziparchive")) with WordSpecLike with Matchers with ScalaFutures {

  implicit val mat = ActorMaterializer()

  "ZipArchiveFlowStage" when {
    "steam ends" should {
      "emit element only when downstream requests" in {
        val (upstream, downstream) =
          TestSource
            .probe[ByteString]
            .via(new ZipArchiveFlow())
            .toMat(TestSink.probe)(Keep.both)
            .run()

        upstream.sendNext(FileByteStringSeparators.createStartingByteString("test"))
        upstream.sendNext(ByteString(1))
        upstream.sendNext(FileByteStringSeparators.createEndingByteString())
        upstream.sendComplete()

        downstream.request(2)
        downstream.expectNextN(2)
        downstream.request(1)
        downstream.expectNextN(1)
        downstream.expectComplete()
      }
    }
  }
}
