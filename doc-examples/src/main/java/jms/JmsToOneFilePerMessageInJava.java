/*
 * Copyright (C) 2016-2018 Lightbend Inc. <http://www.lightbend.com>
 */

package jms;

// #sample
import akka.Done;
import akka.actor.ActorSystem;
import akka.japi.Pair;
import akka.stream.ActorMaterializer;
import akka.stream.KillSwitch;
import akka.stream.Materializer;
import akka.stream.alpakka.jms.JmsConsumerSettings;
import akka.stream.alpakka.jms.JmsProducerSettings;
import akka.stream.alpakka.jms.javadsl.JmsConsumer;
import akka.stream.alpakka.jms.javadsl.JmsProducer;
import akka.stream.javadsl.FileIO;
import akka.stream.javadsl.Keep;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import akka.util.ByteString;

import java.nio.file.Paths;
import java.util.Arrays;
import java.util.concurrent.CompletionStage;

// #sample
import playground.ActiveMqBroker;
import scala.concurrent.ExecutionContext;

import javax.jms.ConnectionFactory;


public class JmsToOneFilePerMessageInJava {

  public static void main(String[] args) throws Exception {
    JmsToOneFilePerMessageInJava me = new JmsToOneFilePerMessageInJava();
    me.run();
  }

  private final ActorSystem system = ActorSystem.create();
  private final Materializer materializer = ActorMaterializer.create(system);
  private final ExecutionContext ec = system.dispatcher();

  private void enqueue(ConnectionFactory connectionFactory, String... msgs) {
    Sink<String, ?> jmsSink =
        JmsProducer.textSink(
            JmsProducerSettings.create(connectionFactory).withQueue("test")
        );
    Source.from(Arrays.asList(msgs)).runWith(jmsSink, materializer);
  }


  private void run() throws Exception {
    ActiveMqBroker activeMqBroker = new ActiveMqBroker();
    activeMqBroker.start();

    ConnectionFactory connectionFactory = activeMqBroker.createConnectionFactory();
    enqueue(connectionFactory, "a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k");
    // #sample

    Source<String, KillSwitch> jmsSource =        // (1)
        JmsConsumer.textSource(
            JmsConsumerSettings.create(connectionFactory)
                .withBufferSize(10)
                .withQueue("test")
        );

    Pair<KillSwitch, CompletionStage<Done>> pair =
        jmsSource                              //: String
            .map(ByteString::fromString)       //: ByteString    (2)
            .zip(Source.fromIterator(() -> Arrays.asList(0, 1, 2, 3).iterator())) //: Pair<ByteString, Integer> (3)
            .mapAsyncUnordered(5, (in) -> {
                  ByteString byteString = in.first();
                  Integer number = in.second();
                  return
                      Source                            //   (4)
                          .single(byteString)
                          .runWith(FileIO.toPath(Paths.get("target/out-" + number + ".txt")), materializer);
                }
            )                                           //: IoResult
            .toMat(Sink.ignore(), Keep.both())
            .run(materializer);

    // #sample

    KillSwitch runningSource = pair.first();
    CompletionStage<Done> streamCompletion = pair.second();

    runningSource.shutdown();
    streamCompletion.thenAccept(res -> system.terminate());
    system.getWhenTerminated().thenAccept(t ->
        activeMqBroker.stop(ec)
    );

  }
}
