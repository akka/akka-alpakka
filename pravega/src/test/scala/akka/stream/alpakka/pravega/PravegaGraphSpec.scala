/*
 * Copyright (C) 2016-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.stream.alpakka.pravega

import akka.stream.KillSwitches
import akka.stream.alpakka.pravega.scaladsl.Pravega
import akka.stream.alpakka.testkit.scaladsl.Repeated
import akka.stream.scaladsl.{Keep, Sink, Source}
import com.typesafe.config.ConfigFactory
import io.pravega.client.stream.impl.UTF8StringSerializer

import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, Promise}

class PravegaGraphSpec extends PravegaBaseSpec with Repeated {

  val serializer = new UTF8StringSerializer
  val nEvent = 1000
  val timeout = 10.seconds

  "Pravega connector" should {

    "runs sources" in {

      implicit val readerSettings = ReaderSettingsBuilder(
        system.settings.config
          .getConfig(ReaderSettingsBuilder.configPath)
          .withFallback(ConfigFactory.parseString(s"group-name = ${newGroupName()}"))
      ).withSerializer(serializer)

      implicit val writerSettings = WriterSettingsBuilder(system)
        .withSerializer(serializer)

      val writerSettingsWithRoutingKey = WriterSettingsBuilder(system)
        .withKeyExtractor((str: String) => str.substring(0, 2))
        .withSerializer(serializer)

      logger.info(s"Write $nEvent events")

      // #writing
      val done = time(s"Write $nEvent events",
                      Source(1 to nEvent)
                        .map(i => f"$i%02d_event")
                        .runWith(Pravega.sink(scope, streamName)))

      time("Wait write", Await.ready(done, timeout))

      val doneWithRoutingKey = Source(1 to nEvent)
        .map(i => f"$i%02d_event")
        .runWith(Pravega.sink(scope, streamName)(writerSettingsWithRoutingKey))

      Await.ready(doneWithRoutingKey, timeout)

      // #writing

      val finishReading = Promise[Unit]()

      val eventToread = nEvent * 2

      // #reading

      val (kill, fut) = Pravega
        .source(scope, streamName)
        .viaMat(KillSwitches.single)(Keep.right)
        .toMat(Sink.fold(eventToread) { (acc, _) =>
          if (acc == 1)
            finishReading.success(())
          acc - 1
        })(Keep.both)
        .run()

      // #reading

      time(s"Read $eventToread events", Await.ready(finishReading.future, timeout))

      logger.debug("Die, die by my hand.")
      kill.shutdown()

      whenReady(fut) { r =>
        r mustEqual 0
        logger.info(s"Read $nEvent events.")
      }
    }

  }

}
