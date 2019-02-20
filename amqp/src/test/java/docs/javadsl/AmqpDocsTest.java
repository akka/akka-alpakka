/*
 * Copyright (C) 2016-2018 Lightbend Inc. <http://www.lightbend.com>
 */

package docs.javadsl;

import akka.Done;
import akka.NotUsed;
import akka.actor.ActorSystem;
import akka.japi.Pair;
import akka.stream.ActorMaterializer;
import akka.stream.KillSwitches;
import akka.stream.Materializer;
import akka.stream.UniqueKillSwitch;
import akka.stream.alpakka.amqp.*;
import akka.stream.alpakka.amqp.javadsl.AmqpRpcFlow;
import akka.stream.alpakka.amqp.javadsl.AmqpSink;
import akka.stream.alpakka.amqp.javadsl.AmqpSource;
import akka.stream.alpakka.amqp.javadsl.CommittableIncomingMessage;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.Keep;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import akka.stream.testkit.TestSubscriber;
import akka.stream.testkit.javadsl.StreamTestKit;
import akka.stream.testkit.javadsl.TestSink;
import akka.testkit.javadsl.TestKit;
import akka.util.ByteString;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;

/** Needs a local running AMQP server on the default port with no password. */
public class AmqpDocsTest {

  private static ActorSystem system;
  private static Materializer materializer;

  @BeforeClass
  public static void setup() {
    system = ActorSystem.create();
    materializer = ActorMaterializer.create(system);
  }

  @AfterClass
  public static void teardown() {
    TestKit.shutdownActorSystem(system);
  }

  @After
  public void checkForStageLeaks() {
    StreamTestKit.assertAllStagesStopped(materializer);
  }

  private AmqpConnectionProvider connectionProvider = AmqpLocalConnectionProvider.getInstance();

  @Test
  public void publishAndConsume() throws Exception {
    // #queue-declaration
    final String queueName = "amqp-conn-it-test-simple-queue-" + System.currentTimeMillis();
    final QueueDeclaration queueDeclaration = QueueDeclaration.create(queueName);
    // #queue-declaration

    @SuppressWarnings("unchecked")
    AmqpDetailsConnectionProvider connectionProvider =
        AmqpDetailsConnectionProvider.create("invalid", 5673)
            .withHostsAndPorts(
                Arrays.asList(Pair.create("localhost", 5672), Pair.create("localhost", 5674)));

    // #create-sink
    final Sink<ByteString, CompletionStage<Done>> amqpSink =
        AmqpSink.createSimple(
            AmqpSinkSettings.create(connectionProvider)
                .withRoutingKey(queueName)
                .withDeclaration(queueDeclaration));
    // #create-sink

    // #create-source
    final Integer bufferSize = 10;
    final Source<IncomingMessage, NotUsed> amqpSource =
        AmqpSource.atMostOnceSource(
            NamedQueueSourceSettings.create(connectionProvider, queueName)
                .withDeclaration(queueDeclaration),
            bufferSize);
    // #create-source

    // #run-sink
    final List<String> input = Arrays.asList("one", "two", "three", "four", "five");
    Source.from(input).map(ByteString::fromString).runWith(amqpSink, materializer);
    // #run-sink

    // #run-source
    final CompletionStage<List<IncomingMessage>> result =
        amqpSource.take(input.size()).runWith(Sink.seq(), materializer);
    // #run-source

    assertEquals(
        input,
        result
            .toCompletableFuture()
            .get(3, TimeUnit.SECONDS)
            .stream()
            .map(m -> m.bytes().utf8String())
            .collect(Collectors.toList()));
  }

  @Test
  public void publishAndConsumeRpc() throws Exception {

    final String queueName = "amqp-conn-it-test-rpc-queue-" + System.currentTimeMillis();
    final QueueDeclaration queueDeclaration = QueueDeclaration.create(queueName);

    // #create-rpc-flow
    final Flow<ByteString, ByteString, CompletionStage<String>> ampqRpcFlow =
        AmqpRpcFlow.createSimple(
            AmqpSinkSettings.create(connectionProvider)
                .withRoutingKey(queueName)
                .withDeclaration(queueDeclaration),
            1);
    // #create-rpc-flow

    final Integer bufferSize = 10;
    final Source<IncomingMessage, NotUsed> amqpSource =
        AmqpSource.atMostOnceSource(
            NamedQueueSourceSettings.create(connectionProvider, queueName)
                .withDeclaration(queueDeclaration),
            bufferSize);

    final List<String> input = Arrays.asList("one", "two", "three", "four", "five");
    // #run-rpc-flow
    Pair<CompletionStage<String>, TestSubscriber.Probe<ByteString>> result =
        Source.from(input)
            .map(ByteString::fromString)
            .viaMat(ampqRpcFlow, Keep.right())
            .toMat(TestSink.probe(system), Keep.both())
            .run(materializer);
    // #run-rpc-flow
    result.first().toCompletableFuture().get(3, TimeUnit.SECONDS);

    Sink<OutgoingMessage, CompletionStage<Done>> amqpSink =
        AmqpSink.createReplyTo(AmqpReplyToSinkSettings.create(connectionProvider));

    UniqueKillSwitch killSwitch =
        amqpSource
            .viaMat(KillSwitches.single(), Keep.right())
            .map(
                b ->
                    OutgoingMessage.create(
                            b.bytes().concat(ByteString.fromString("a")), false, false)
                        .withProperties(b.properties()))
            .to(amqpSink)
            .run(materializer);

    result
        .second()
        .request(5)
        .expectNextUnordered(
            ByteString.fromString("onea"),
            ByteString.fromString("twoa"),
            ByteString.fromString("threea"),
            ByteString.fromString("foura"),
            ByteString.fromString("fivea"))
        .expectComplete();

    killSwitch.shutdown();
  }

  @Test
  public void publishFanoutAndConsume() throws Exception {
    // #exchange-declaration
    final String exchangeName = "amqp-conn-it-test-pub-sub-" + System.currentTimeMillis();
    final ExchangeDeclaration exchangeDeclaration =
        ExchangeDeclaration.create(exchangeName, "fanout");
    // #exchange-declaration

    // #create-exchange-sink
    final Sink<ByteString, CompletionStage<Done>> amqpSink =
        AmqpSink.createSimple(
            AmqpSinkSettings.create(connectionProvider)
                .withExchange(exchangeName)
                .withDeclaration(exchangeDeclaration));
    // #create-exchange-sink

    // #create-exchange-source
    final int fanoutSize = 4;
    final int bufferSize = 1;

    Source<Pair<Integer, String>, NotUsed> mergedSources = Source.empty();
    for (int i = 0; i < fanoutSize; i++) {
      final int fanoutBranch = i;
      mergedSources =
          mergedSources.merge(
              AmqpSource.atMostOnceSource(
                      TemporaryQueueSourceSettings.create(connectionProvider, exchangeName)
                          .withDeclaration(exchangeDeclaration),
                      bufferSize)
                  .map(msg -> Pair.create(fanoutBranch, msg.bytes().utf8String())));
    }
    // #create-exchange-source

    final CompletableFuture<Done> completion = new CompletableFuture<>();
    UniqueKillSwitch mergingFlow =
        mergedSources
            .viaMat(KillSwitches.single(), Keep.right())
            .to(
                Sink.fold(
                    new HashSet<Integer>(),
                    (seen, branchElem) -> {
                      if (seen.size() == fanoutSize) {
                        completion.complete(Done.getInstance());
                      }
                      seen.add(branchElem.first());
                      return seen;
                    }))
            .run(materializer);

    system
        .scheduler()
        .scheduleOnce(
            Duration.ofSeconds(5),
            () ->
                completion.completeExceptionally(
                    new Error("Did not get at least one element from every fanout branch")),
            system.dispatcher());

    UniqueKillSwitch repeatingFlow =
        Source.repeat("stuff")
            .viaMat(KillSwitches.single(), Keep.right())
            .map(ByteString::fromString)
            .to(amqpSink)
            .run(materializer);

    assertEquals(Done.getInstance(), completion.get(10, TimeUnit.SECONDS));
    mergingFlow.shutdown();
    repeatingFlow.shutdown();
  }

  @Test
  public void publishAndConsumeWithoutAutoAck() throws Exception {
    final String queueName = "amqp-conn-it-test-no-auto-ack-" + System.currentTimeMillis();
    final QueueDeclaration queueDeclaration = QueueDeclaration.create(queueName);

    final Sink<ByteString, CompletionStage<Done>> amqpSink =
        AmqpSink.createSimple(
            AmqpSinkSettings.create(connectionProvider)
                .withRoutingKey(queueName)
                .withDeclaration(queueDeclaration));

    // #create-source-withoutautoack
    final Integer bufferSize = 10;
    final Source<CommittableIncomingMessage, NotUsed> amqpSource =
        AmqpSource.committableSource(
            NamedQueueSourceSettings.create(connectionProvider, queueName)
                .withDeclaration(queueDeclaration),
            bufferSize);
    // #create-source-withoutautoack

    final List<String> input = Arrays.asList("one", "two", "three", "four", "five");
    Source.from(input).map(ByteString::fromString).runWith(amqpSink, materializer);

    // #run-source-withoutautoack
    final CompletionStage<List<IncomingMessage>> result =
        amqpSource
            .mapAsync(1, cm -> cm.ack(false).thenApply(unused -> cm.message()))
            .take(input.size())
            .runWith(Sink.seq(), materializer);
    // #run-source-withoutautoack

    assertEquals(
        input,
        result
            .toCompletableFuture()
            .get(3, TimeUnit.SECONDS)
            .stream()
            .map(m -> m.bytes().utf8String())
            .collect(Collectors.toList()));
  }

  @Test
  public void republishMessageWithoutAutoAckIfNacked() throws Exception {
    final String queueName = "amqp-conn-it-test-no-auto-ack-nacked-" + System.currentTimeMillis();
    final QueueDeclaration queueDeclaration = QueueDeclaration.create(queueName);

    final Sink<ByteString, CompletionStage<Done>> amqpSink =
        AmqpSink.createSimple(
            AmqpSinkSettings.create(connectionProvider)
                .withRoutingKey(queueName)
                .withDeclaration(queueDeclaration));

    final List<String> input = Arrays.asList("one", "two", "three", "four", "five");
    Source.from(input)
        .map(ByteString::fromString)
        .runWith(amqpSink, materializer)
        .toCompletableFuture()
        .get(5, TimeUnit.SECONDS);

    final Integer bufferSize = 10;
    final Source<CommittableIncomingMessage, NotUsed> amqpSource =
        AmqpSource.committableSource(
            NamedQueueSourceSettings.create(connectionProvider, queueName)
                .withDeclaration(queueDeclaration),
            bufferSize);

    // #run-source-withoutautoack-and-nack
    final CompletionStage<List<CommittableIncomingMessage>> result1 =
        amqpSource
            .take(input.size())
            .mapAsync(1, cm -> cm.nack(false, true).thenApply(unused -> cm))
            .runWith(Sink.seq(), materializer);
    // #run-source-withoutautoack-and-nack

    result1.toCompletableFuture().get(3, TimeUnit.SECONDS);

    final CompletionStage<List<CommittableIncomingMessage>> result2 =
        amqpSource
            .mapAsync(1, cm -> cm.ack(false).thenApply(unused -> cm))
            .take(input.size())
            .runWith(Sink.seq(), materializer);

    assertEquals(
        input,
        result2
            .toCompletableFuture()
            .get(3, TimeUnit.SECONDS)
            .stream()
            .map(m -> m.message().bytes().utf8String())
            .collect(Collectors.toList()));
  }
}
