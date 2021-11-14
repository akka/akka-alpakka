/*
 * Copyright (C) 2016-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.javadsl;

import java.util.List;
import java.util.concurrent.CompletionStage;

import akka.NotUsed;
import akka.actor.ActorSystem;
import akka.stream.ActorMaterializer;
// #read-all
import akka.stream.alpakka.googlecloud.bigquery.storage.BigQueryRecord;
import akka.stream.alpakka.googlecloud.bigquery.storage.BigQueryStorageSettings;
import akka.stream.alpakka.googlecloud.bigquery.storage.javadsl.BigQueryArrowStorage;
import akka.stream.alpakka.googlecloud.bigquery.storage.javadsl.BigQueryAvroStorage;
import akka.stream.alpakka.googlecloud.bigquery.storage.javadsl.BigQueryStorage;
import akka.stream.alpakka.googlecloud.bigquery.storage.javadsl.BigQueryStorageAttributes;
import akka.stream.alpakka.googlecloud.bigquery.storage.javadsl.GrpcBigQueryStorageReader;
import akka.stream.javadsl.Source;
import akka.util.ByteString;
import scala.Tuple2;
import com.google.cloud.bigquery.storage.v1.DataFormat;
import com.google.cloud.bigquery.storage.v1.ReadSession;
import com.google.cloud.bigquery.storage.v1.storage.ReadRowsResponse;
import akka.http.javadsl.unmarshalling.Unmarshaller;

// #read-all

public class ExampleReader {

  static final ActorSystem sys = ActorSystem.create("ExampleReader");
  static final ActorMaterializer mat = ActorMaterializer.create(sys);

  // #read-all
  Source<
          Tuple2<
              com.google.cloud.bigquery.storage.v1.stream.ReadSession.Schema,
              List<Source<ReadRowsResponse.Rows, NotUsed>>>,
          CompletionStage<NotUsed>>
      sourceOfSources =
          BigQueryStorage.create("projectId", "datasetId", "tableId", DataFormat.AVRO);
  // #read-all

  // #read-options
  ReadSession.TableReadOptions readOptions =
      ReadSession.TableReadOptions.newBuilder()
          .setSelectedFields(0, "stringField")
          .setSelectedFields(1, "intField")
          .setRowRestriction("intField >= 5")
          .build();

  Source<
          Tuple2<
              com.google.cloud.bigquery.storage.v1.stream.ReadSession.Schema,
              List<Source<ReadRowsResponse.Rows, NotUsed>>>,
          CompletionStage<NotUsed>>
      sourceOfSourcesFiltered =
          BigQueryStorage.create(
              "projectId", "datasetId", "tableId", DataFormat.AVRO, readOptions, 1);
  // #read-options

  // #read-sequential
  Unmarshaller<ByteString, List<BigQueryRecord>> unmarshaller = null;
  Source<List<BigQueryRecord>, CompletionStage<NotUsed>> sequentialSource =
      BigQueryStorage.<List<BigQueryRecord>>createMergedStreams(
          "projectId", "datasetId", "tableId", DataFormat.AVRO, unmarshaller);

  // #read-sequential

  // #read-arrow-sequential
  Source<List<BigQueryRecord>, CompletionStage<NotUsed>> arrowSequentialSource =
      BigQueryArrowStorage.readRecordsMerged("projectId", "datasetId", "tableId");
  // #read-arrow-sequential

  // #read-arrow-parallel
  Source<List<Source<BigQueryRecord, NotUsed>>, CompletionStage<NotUsed>> arrowParallelSource =
      BigQueryArrowStorage.readRecords("projectId", "datasetId", "tableId");
  // #read-arrow-parallel

  // #read-avro-sequential
  Source<List<BigQueryRecord>, CompletionStage<NotUsed>> avroSequentialSource =
      BigQueryAvroStorage.readRecordsMerged("projectId", "datasetId", "tableId");
  // #read-avro-sequential

  // #read-avro-parallel
  Source<List<Source<BigQueryRecord, NotUsed>>, CompletionStage<NotUsed>> avroParallelSource =
      BigQueryAvroStorage.readRecords("projectId", "datasetId", "tableId");
  // #read-avro-parallel

  // #attributes
  GrpcBigQueryStorageReader reader =
      GrpcBigQueryStorageReader.create(BigQueryStorageSettings.apply("localhost", 8000), sys);

  Source<
          Tuple2<
              com.google.cloud.bigquery.storage.v1.stream.ReadSession.Schema,
              List<Source<ReadRowsResponse.Rows, NotUsed>>>,
          CompletionStage<NotUsed>>
      sourceForReader =
          BigQueryStorage.create(
                  "projectId", "datasetId", "tableId", DataFormat.AVRO, readOptions, 1)
              .withAttributes(BigQueryStorageAttributes.reader(reader));
  // #attributes
}
