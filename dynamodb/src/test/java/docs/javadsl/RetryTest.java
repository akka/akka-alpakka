/*
 * Copyright (C) 2016-2019 Lightbend Inc. <http://www.lightbend.com>
 */

package docs.javadsl;

import akka.NotUsed;
import akka.actor.ActorSystem;
import akka.japi.Pair;
import akka.stream.ActorMaterializer;
import akka.stream.Materializer;
import akka.stream.alpakka.dynamodb.DynamoDbOp;
import akka.stream.alpakka.dynamodb.ItemSpecOps;
import akka.stream.alpakka.dynamodb.impl.javadsl.RetryFlow;
import akka.stream.alpakka.dynamodb.javadsl.DynamoDb;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import akka.stream.testkit.javadsl.StreamTestKit;
import org.junit.*;
import scala.util.Try;

import java.net.URI;
import java.time.Duration;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

import static akka.japi.Option.*;
import static org.junit.Assert.*;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.model.BatchGetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.BatchGetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;

public class RetryTest extends ItemSpecOps {

  static ActorSystem system;
  static Materializer materializer;
  static DynamoDbAsyncClient client;

  @BeforeClass
  public static void setup() throws Exception {
    System.setProperty("aws.accessKeyId", "someKeyId");
    System.setProperty("aws.secretKey", "someSecretKey");

    final ActorSystem system = ActorSystem.create();
    final Materializer materializer = ActorMaterializer.create(system);
    final DynamoDbAsyncClient client =
        DynamoDbAsyncClient.builder()
            .credentialsProvider(
                StaticCredentialsProvider.create(AwsBasicCredentials.create("x", "x")))
            .region(Region.AWS_GLOBAL)
            .endpointOverride(new URI("http://localhost:8001/"))
            .build();

    RetryTest.system = system;
    RetryTest.materializer = materializer;
    RetryTest.client = client;
  }

  @AfterClass
  public static void tearDown() {
    StreamTestKit.assertAllStagesStopped(materializer);

    client.close();
    system.terminate();
  }

  @Before
  public void createTable() throws Exception {
    DynamoDb.single(client, DynamoDbOp.createTable(), createTableRequest(), materializer)
        .toCompletableFuture()
        .get(5, TimeUnit.SECONDS);
  }

  @After
  public void deleteTable() throws Exception {
    DynamoDb.single(client, DynamoDbOp.deleteTable(), deleteTableRequest(), materializer)
        .toCompletableFuture()
        .get(5, TimeUnit.SECONDS);
  }

  @Override
  public String tableName() {
    return "RetryTest";
  }

  @Test
  public void retrySuccessfulRequests() throws Exception {
    DynamoDb.single(
            client, DynamoDbOp.batchWriteItem(), batchWriteLargeItemRequest(1, 25), materializer)
        .toCompletableFuture()
        .get(5, TimeUnit.SECONDS);
    DynamoDb.single(
            client, DynamoDbOp.batchWriteItem(), batchWriteLargeItemRequest(26, 50), materializer)
        .toCompletableFuture()
        .get(5, TimeUnit.SECONDS);

    final Flow<
            Pair<BatchGetItemRequest, NotUsed>, Pair<Try<BatchGetItemResponse>, NotUsed>, NotUsed>
        retryFlow =
            RetryFlow.withBackoff(
                8,
                Duration.ofMillis(10),
                Duration.ofSeconds(5),
                0d,
                DynamoDb.<BatchGetItemRequest, BatchGetItemResponse, NotUsed>flowWithContext(
                        client, DynamoDbOp.batchGetItem(), 1)
                    .asFlow(),
                resp -> {
                  final Try<BatchGetItemResponse> response = resp.first();
                  if (response.isSuccess()) {
                    final BatchGetItemResponse result = response.get();
                    if (result.unprocessedKeys().size() > 0) {
                      return some(
                          Collections.singleton(
                              Pair.create(
                                  batchGetItemRequest(result.unprocessedKeys()),
                                  NotUsed.getInstance())));
                    } else {
                      return none();
                    }
                  } else {
                    return none();
                  }
                });

    final long responses =
        Source.single(Pair.create(batchGetLargeItemRequest(1, 50), NotUsed.getInstance()))
            .via(retryFlow)
            .runFold(0, (cnt, i) -> cnt + 1, materializer)
            .toCompletableFuture()
            .get(30, TimeUnit.SECONDS);

    assertEquals(2, responses);
  }

  @Test
  public void retryFailedRequests() throws Exception {
    // #create-retry-flow
    final Flow<Pair<GetItemRequest, Integer>, Pair<Try<GetItemResponse>, Integer>, NotUsed>
        retryFlow =
            RetryFlow.withBackoff(
                8,
                Duration.ofMillis(10),
                Duration.ofSeconds(5),
                0d,
                DynamoDb.<GetItemRequest, GetItemResponse, Integer>flowWithContext(
                        client, DynamoDbOp.getItem(), 1)
                    .asFlow(),
                resp -> {
                  final Try<GetItemResponse> response = resp.first();
                  final Integer retries = resp.second();
                  if (response.isFailure()) {
                    return some(Collections.singleton(Pair.create(getItemRequest(), retries + 1)));
                  } else {
                    return none();
                  }
                });
    // #create-retry-flow

    // #use-retry-flow
    final Pair<Try<GetItemResponse>, Integer> responsePair =
        Source.single(Pair.create(getItemMalformedRequest(), 0))
            .via(retryFlow)
            .runWith(Sink.head(), materializer)
            .toCompletableFuture()
            .get(30, TimeUnit.SECONDS);
    // #use-retry-flow

    final Try<GetItemResponse> response = responsePair.first();
    final long retries = responsePair.second();

    assertTrue(response.isSuccess());
    assertEquals(1, retries);
  }
}
