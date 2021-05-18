/*
 * Copyright (C) 2016-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.stream.alpakka.googlecloud.bigquery.storage.mock

import akka.NotUsed
import akka.actor.ActorSystem
import akka.grpc.GrpcServiceException
import akka.grpc.scaladsl.Metadata
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.http.scaladsl.{Http, HttpConnectionContext}
import akka.stream.scaladsl.Source
import com.google.cloud.bigquery.storage.v1.arrow.ArrowSchema
import com.google.cloud.bigquery.storage.v1.avro.AvroSchema
import com.google.cloud.bigquery.storage.v1.storage._
import com.google.cloud.bigquery.storage.v1.stream._
import com.google.protobuf.ByteString
import io.grpc.Status
import org.apache.avro

import scala.collection.mutable
import scala.concurrent.Future

class BigQueryMockServer(port: Int) extends BigQueryMockData {

  private val RequestParamsHeader = "x-goog-request-params"

  def run()(implicit sys: ActorSystem): Future[Http.ServerBinding] = {
    val service: HttpRequest => Future[HttpResponse] =
      BigQueryReadPowerApiHandler(new BigQueryReadPowerApi {
        val sessionSchemas: mutable.Map[String, Any] = mutable.Map.empty

        /**
         * Validate the project and table names, if they are as expected,
         * return maxStreamCount streams of Avro data, or DefaultNumStreams if not provided.
         * Only supports Avro DataFormat currently
         */
        override def createReadSession(in: CreateReadSessionRequest, metadata: Metadata): Future[ReadSession] = {
          val readSession = in.readSession.get
          val sessionName = readSessionName()
          if (metadata.getText(RequestParamsHeader).isEmpty) {
            val msg = "Request is missing 'x-goog-request-params' header."
            Future.failed(new GrpcServiceException(Status.INVALID_ARGUMENT.augmentDescription(msg)))
          } else if (in.parent != ProjectFullName) {
            val msg = s"Wrong project, should be $ProjectFullName"
            Future.failed(new GrpcServiceException(Status.INVALID_ARGUMENT.augmentDescription(msg)))
          } else if (readSession.table != TableFullName) {
            val msg = s"Wrong table, should be $TableFullName"
            Future.failed(new GrpcServiceException(Status.INVALID_ARGUMENT.augmentDescription(msg)))
          } else {
            val numStreams = if (in.maxStreamCount != 0) in.maxStreamCount else DefaultNumStreams
            val streams =
              if (readSession.readOptions.map(_.rowRestriction).getOrElse("").isEmpty)
                (1 to numStreams).map(id => ReadStream(s"$sessionName/streams/$id"))
              else Seq.empty
            val schema =
              if (readSession.dataFormat == DataFormat.AVRO) {
                val avroSchema = readSession.readOptions.map(_.selectedFields.toList).getOrElse(Nil) match {
                  case Col1 :: Nil => Col1Schema
                  case Col2 :: Nil => Col2Schema
                  case _ => FullAvroSchema
                }
                sessionSchemas += (sessionName -> avroSchema)
                ReadSession.Schema.AvroSchema(AvroSchema(avroSchema.toString))
              } else {
                val arrowSchema = readSession.readOptions.map(_.selectedFields.toList).getOrElse(Nil) match {
                  case _ => FullArrowSchema
                }
                sessionSchemas += (sessionName -> arrowSchema)
                ReadSession.Schema.ArrowSchema(
                  ArrowSchema(serializedSchema = ByteString.copyFromUtf8(arrowSchema.toJson))
                )
              }

            Future.successful(
              readSession.copy(
                schema = schema,
                name = sessionName,
                streams = streams
              )
            )
          }
        }

        /**
         * Regardless of the request, return a stream of ResponsesPerStream ReadRowsResponses,
         * each with RecordsPerReadRowsResponse Avro records
         */
        override def readRows(in: ReadRowsRequest, metadata: Metadata): Source[ReadRowsResponse, NotUsed] =
          if (metadata.getText(RequestParamsHeader).isEmpty) {
            val msg = "Request is missing 'x-goog-request-params' header."
            Source.failed(new GrpcServiceException(Status.INVALID_ARGUMENT.augmentDescription(msg)))
          } else {
            val sessionName = in.readStream.split("/").dropRight(2).mkString("/")
            val record = sessionSchemas(sessionName) match {
              case FullAvroSchema => FullAvroRecord
              case Col1Schema => Col1AvroRecord
              case Col2Schema => Col2AvroRecord
              case _ => println("hi"); Col2AvroRecord
            }
            Source(1 to ResponsesPerStream).map(
              _ =>
                ReadRowsResponse(rowCount = RecordsPerReadRowsResponse,
                                 rows = ReadRowsResponse.Rows.AvroRows(recordsAsRows(record)))
            )
          }

        override def splitReadStream(in: SplitReadStreamRequest, metadata: Metadata): Future[SplitReadStreamResponse] =
          ???
      })

    Http().bindAndHandleAsync(service, interface = "0.0.0.0", port = port, connectionContext = HttpConnectionContext())
  }
}
