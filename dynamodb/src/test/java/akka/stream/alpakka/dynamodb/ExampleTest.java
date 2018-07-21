/*
 * Copyright (C) 2016-2018 Lightbend Inc. <http://www.lightbend.com>
 */

package akka.stream.alpakka.dynamodb;

import akka.NotUsed;
import akka.actor.ActorSystem;
import akka.japi.Pair;
import akka.stream.ActorMaterializer;
import akka.stream.alpakka.dynamodb.impl.DynamoSettings;
import akka.stream.alpakka.dynamodb.javadsl.DynamoClient;
import akka.stream.javadsl.Source;
import com.amazonaws.services.dynamodbv2.model.*;
import akka.stream.alpakka.dynamodb.scaladsl.DynamoImplicits.CreateTable;
import org.junit.BeforeClass;
import org.junit.Test;
import scala.concurrent.duration.Duration;

import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

public class ExampleTest {

  static ActorSystem system;
  static ActorMaterializer materializer;
  static DynamoSettings settings;
  static DynamoClient client;

  public static Pair<ActorSystem, ActorMaterializer> setupMaterializer() {
    // #init-client
    final ActorSystem system = ActorSystem.create();
    final ActorMaterializer materializer = ActorMaterializer.create(system);
    // #init-client
    return Pair.create(system, materializer);
  }

  public static Pair<DynamoSettings, DynamoClient> setupClient() {
    // #client-construct
    final DynamoSettings settings = DynamoSettings.create(system);
    final DynamoClient client = DynamoClient.create(settings, system, materializer);
    // #client-construct
    return Pair.create(settings, client);
  }

  @BeforeClass
  public static void setup() throws Exception {
    System.setProperty("aws.accessKeyId", "someKeyId");
    System.setProperty("aws.secretKey", "someSecretKey");

    final Pair<ActorSystem, ActorMaterializer> sysmat = setupMaterializer();
    system = sysmat.first();
    materializer = sysmat.second();

    final Pair<DynamoSettings, DynamoClient> setclient = setupClient();
    settings = setclient.first();
    client = setclient.second();
  }

  @Test
  public void listTables() throws Exception {
    // #simple-request
    final CompletionStage<ListTablesResult> listTablesResultFuture =
        client.listTables(new ListTablesRequest());
    // #simple-request
    ListTablesResult result = listTablesResultFuture.toCompletableFuture().get(5, TimeUnit.SECONDS);
  }

  @Test
  public void flow() throws Exception {
    // #flow
    Source<String, NotUsed> tableArnSource =
        Source.single(new CreateTable(new CreateTableRequest().withTableName("testTable")))
            .via(client.flow())
            .map(result -> (CreateTableResult) result)
            .map(result -> result.getTableDescription().getTableArn());
    // #flow
    final Duration duration = Duration.create(5, "seconds");
    tableArnSource.runForeach(System.out::println, materializer);
  }

  @Test
  public void paginated() throws Exception {
    // #paginated
    Source<ScanResult, NotUsed> scanPages =
        client.scanAll(new ScanRequest().withTableName("testTable"));
    // #paginated
  }
}
