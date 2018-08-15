/*
 * Copyright (C) 2016-2018 Lightbend Inc. <http://www.lightbend.com>
 */

package akka.stream.alpakka.sqs.javadsl;

import akka.Done;
import akka.actor.ActorSystem;
import akka.stream.ActorMaterializer;
import akka.stream.alpakka.sqs.SqsAckSinkSettings;
import akka.stream.alpakka.sqs.SqsBatchAckFlowSettings;
import akka.stream.alpakka.sqs.SqsBatchFlowSettings;
import akka.stream.alpakka.sqs.SqsSinkSettings;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import akka.testkit.javadsl.TestKit;
import com.amazonaws.services.sqs.model.Message;
import com.amazonaws.services.sqs.model.ReceiveMessageRequest;
import com.amazonaws.services.sqs.model.SendMessageRequest;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;

public class SqsSinkTest extends BaseSqsTest {

  @Test
  public void constructBatchSettings() {
    // #SqsBatchFlowSettings
    SqsBatchFlowSettings batchSettings =
        SqsBatchFlowSettings.Defaults()
            .withMaxBatchSize(10)
            .withMaxBatchWait(500, TimeUnit.MILLISECONDS)
            .withConcurrentRequests(1);
    // #SqsBatchFlowSettings
    assertEquals(10, batchSettings.maxBatchSize());
  }

  @Test
  public void constructSinkSettings() {
    // #SqsSinkSettings
    SqsSinkSettings sinkSettings = SqsSinkSettings.Defaults().withMaxInFlight(10);
    // #SqsSinkSettings
    assertEquals(10, sinkSettings.maxInFlight());
  }

  @Test
  public void constructAckSinkSettings() {
    // #SqsAckSinkSettings
    SqsAckSinkSettings sinkSettings = SqsAckSinkSettings.Defaults().withMaxInFlight(10);
    // #SqsAckSinkSettings
    assertEquals(10, sinkSettings.maxInFlight());
  }

  @Test
  public void constructBatchAckSinkSettings() {
    // #SqsBatchAckFlowSettings
    SqsBatchAckFlowSettings flowSettings =
        SqsBatchAckFlowSettings.Defaults()
            .withMaxBatchSize(10)
            .withMaxBatchWait(Duration.ofMillis(500))
            .withConcurrentRequests(1);
    // #SqsBatchAckFlowSettings
    assertEquals(10, flowSettings.maxBatchSize());
  }

  @Test
  public void sendToQueue() throws Exception {

    final String queueUrl = randomQueueUrl();

    // #run-string
    CompletionStage<Done> done =
        Source.single("alpakka").runWith(SqsSink.create(queueUrl, sqsClient), materializer);
    // #run-string
    done.toCompletableFuture().get(1, TimeUnit.SECONDS);
    List<Message> messages = sqsClient.receiveMessage(queueUrl).getMessages();

    assertEquals(1, messages.size());
    assertEquals("alpakka", messages.get(0).getBody());
  }

  @Test
  public void sendMessageRequestToQueue() throws Exception {

    final String queueUrl = randomQueueUrl();

    // #run-send-request
    CompletionStage<Done> done =
        Source.single(new SendMessageRequest().withMessageBody("alpakka"))
            .runWith(SqsSink.messageSink(queueUrl, sqsClient), materializer);
    // #run-send-request
    done.toCompletableFuture().get(1, TimeUnit.SECONDS);
    List<Message> messages = sqsClient.receiveMessage(queueUrl).getMessages();

    assertEquals(1, messages.size());
    assertEquals("alpakka", messages.get(0).getBody());
  }

  @Test
  public void sendViaFlow() throws Exception {
    final String queueUrl = randomQueueUrl();

    // #flow
    CompletionStage<Done> done =
        Source.single(new SendMessageRequest(queueUrl, "alpakka-flow"))
            .via(SqsFlow.create(queueUrl, sqsClient))
            .runWith(Sink.ignore(), materializer);
    // #flow
    done.toCompletableFuture().get(1, TimeUnit.SECONDS);
    List<Message> messages = sqsClient.receiveMessage(queueUrl).getMessages();

    assertEquals(1, messages.size());
    assertEquals("alpakka-flow", messages.get(0).getBody());
  }

  @Test
  public void sendToQueueWithBatches() throws Exception {
    final String queueUrl = randomQueueUrl();

    // #group
    ArrayList<String> messagesToSend = new ArrayList<>();
    for (int i = 0; i < 20; i++) {
      messagesToSend.add("message - " + i);
    }

    CompletionStage<Done> done =
        Source.from(messagesToSend).runWith(SqsSink.grouped(queueUrl, sqsClient), materializer);
    // #group
    done.toCompletableFuture().get(1, TimeUnit.SECONDS);

    List<Message> messagesFirstBatch =
        sqsClient
            .receiveMessage(
                new ReceiveMessageRequest().withQueueUrl(queueUrl).withMaxNumberOfMessages(10))
            .getMessages();
    List<Message> messagesSecondBatch =
        sqsClient
            .receiveMessage(
                new ReceiveMessageRequest().withQueueUrl(queueUrl).withMaxNumberOfMessages(10))
            .getMessages();

    assertEquals(20, messagesFirstBatch.size() + messagesSecondBatch.size());
  }

  @Test
  public void sendBatchesToQueue() throws Exception {
    final String queueUrl = randomQueueUrl();

    // #batch-string
    ArrayList<String> messagesToSend = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      messagesToSend.add("Message - " + i);
    }
    Iterable<String> it = messagesToSend;

    CompletionStage<Done> done =
        Source.single(it).runWith(SqsSink.batch(queueUrl, sqsClient), materializer);
    // #batch-string
    done.toCompletableFuture().get(1, TimeUnit.SECONDS);

    List<Message> messagesFirstBatch =
        sqsClient
            .receiveMessage(
                new ReceiveMessageRequest().withQueueUrl(queueUrl).withMaxNumberOfMessages(10))
            .getMessages();

    assertEquals(10, messagesFirstBatch.size());
  }

  @Test
  public void sendBatchesOfSendMessageRequestsToQueue() throws Exception {
    final String queueUrl = randomQueueUrl();

    // #batch-send-request
    ArrayList<SendMessageRequest> messagesToSend = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      messagesToSend.add(new SendMessageRequest().withMessageBody("Message - " + i));
    }
    Iterable<SendMessageRequest> it = messagesToSend;

    CompletionStage<Done> done =
        Source.single(it).runWith(SqsSink.batchedMessageSink(queueUrl, sqsClient), materializer);
    // #batch-send-request
    done.toCompletableFuture().get(1, TimeUnit.SECONDS);

    List<Message> messagesFirstBatch =
        sqsClient
            .receiveMessage(
                new ReceiveMessageRequest().withQueueUrl(queueUrl).withMaxNumberOfMessages(10))
            .getMessages();

    assertEquals(10, messagesFirstBatch.size());
  }

  @Test
  public void sendMessageWithBatchesAsFlow() throws Exception {
    final String queueUrl = randomQueueUrl();

    ArrayList<SendMessageRequest> messagesToSend = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      messagesToSend.add(new SendMessageRequest(queueUrl, "Message - " + i));
    }

    CompletionStage<Done> done =
        Source.from(messagesToSend)
            .via(SqsFlow.grouped(queueUrl, sqsClient))
            .runWith(Sink.ignore(), materializer);

    done.toCompletableFuture().get(1, TimeUnit.SECONDS);

    List<Message> messagesFirstBatch =
        sqsClient
            .receiveMessage(
                new ReceiveMessageRequest().withQueueUrl(queueUrl).withMaxNumberOfMessages(10))
            .getMessages();

    assertEquals(10, messagesFirstBatch.size());
  }

  @Test
  public void sendBatchesAsFlow() throws Exception {
    final String queueUrl = randomQueueUrl();

    ArrayList<SendMessageRequest> messagesToSend = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      messagesToSend.add(new SendMessageRequest(queueUrl, "Message - " + i));
    }
    Iterable<SendMessageRequest> it = messagesToSend;

    CompletionStage<Done> done =
        Source.single(it)
            .via(SqsFlow.batch(queueUrl, sqsClient))
            .runWith(Sink.ignore(), materializer);

    done.toCompletableFuture().get(1, TimeUnit.SECONDS);

    List<Message> messagesFirstBatch =
        sqsClient
            .receiveMessage(
                new ReceiveMessageRequest().withQueueUrl(queueUrl).withMaxNumberOfMessages(10))
            .getMessages();

    assertEquals(10, messagesFirstBatch.size());
  }

  @Test
  public void ackViaFlow() throws Exception {
    final String queueUrl = randomQueueUrl();

    CompletionStage<Done> done =
        Source.single(new SendMessageRequest(queueUrl, "alpakka-flow"))
            .via(SqsFlow.create(queueUrl, sqsClient))
            .runWith(Sink.ignore(), materializer);
    done.toCompletableFuture().get(1, TimeUnit.SECONDS);
    List<Message> messages = sqsClient.receiveMessage(queueUrl).getMessages();

    assertEquals(1, messages.size());
    assertEquals("alpakka-flow", messages.get(0).getBody());
  }
}
