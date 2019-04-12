/*
 * Copyright (C) 2016-2019 Lightbend Inc. <http://www.lightbend.com>
 */

package elastic

import akka.actor.ActorSystem
import akka.kafka._
import akka.kafka.scaladsl.{Committer, Consumer, Producer}
import akka.stream.{ActorMaterializer, Materializer}
import akka.stream.alpakka.elasticsearch.WriteMessage
import akka.stream.alpakka.elasticsearch.scaladsl.{ElasticsearchFlow, ElasticsearchSource}
import akka.stream.scaladsl.{Keep, Sink, Source}
import akka.{Done, NotUsed}
import org.apache.http.HttpHost
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.{
  IntegerDeserializer,
  IntegerSerializer,
  StringDeserializer,
  StringSerializer
}
import org.elasticsearch.client.RestClient
import org.slf4j.LoggerFactory
import org.testcontainers.containers.KafkaContainer
import org.testcontainers.elasticsearch.ElasticsearchContainer
import spray.json.DefaultJsonProtocol._
import spray.json._

import scala.collection.immutable
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

object KafkaToElastic extends App {

  final val log = LoggerFactory.getLogger(getClass)

  // Testcontainers: start Elasticsearch in Docker
  val elasticsearchContainer = new ElasticsearchContainer("docker.elastic.co/elasticsearch/elasticsearch-oss:6.4.3")
  elasticsearchContainer.start()
  val elasticsearchAddress = elasticsearchContainer.getHttpHostAddress

  // Testcontainers: start Kafka in Docker
  val kafka = new KafkaContainer()
  kafka.start()
  val kafkaBootstrapServers = kafka.getBootstrapServers

  implicit val actorSystem: ActorSystem = ActorSystem()
  implicit val actorMaterializer: Materializer = ActorMaterializer()
  implicit val executionContext: ExecutionContext = actorSystem.dispatcher

  val topic = "movies-to-elasticsearch"
  private val groupId = "docs-group"

  case class Movie(id: Int, title: String)

  implicit val movieFormat: JsonFormat[Movie] = jsonFormat2(Movie)

  implicit val elasticsearchClient: RestClient =
    RestClient
      .builder(HttpHost.create(elasticsearchAddress))
      .build()

  val indexName = "movies"

  private def writeToKafka(movies: immutable.Iterable[Movie]) = {
    val kafkaProducerSettings = ProducerSettings(actorSystem, new IntegerSerializer, new StringSerializer)
      .withBootstrapServers(kafkaBootstrapServers)

    val producing: Future[Done] = Source(movies)
      .map { movie =>
        log.debug("producing {}", movie)
        new ProducerRecord(topic, Int.box(movie.id), movie.toJson.compactPrint)
      }
      .runWith(Producer.plainSink(kafkaProducerSettings))
    producing.foreach(_ => log.info("Producing finished"))
    producing
  }

  private def readFromKafkaWriteToElasticsearch() = {
    val kafkaConsumerSettings = ConsumerSettings(actorSystem, new IntegerDeserializer, new StringDeserializer)
      .withBootstrapServers(kafkaBootstrapServers)
      .withGroupId(groupId)
      .withProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
      .withStopTimeout(5.seconds)

    val control: Consumer.DrainingControl[Done] = Consumer
      .committableSource(kafkaConsumerSettings, Subscriptions.topics(topic))
      .startContextPropagation(_.committableOffset)
      .map(_.record)
      .map { consumerRecord =>
        val movie = consumerRecord.value().parseJson.convertTo[Movie]
        WriteMessage.createUpsertMessage(movie.id.toString, movie)
      }
      .via(ElasticsearchFlow.createWithContext(indexName, "_doc"))
      .map { writeResult =>
        writeResult.error.foreach { errorJson =>
          throw new RuntimeException(s"Elasticsearch update failed ${writeResult.errorReason.getOrElse(errorJson)}")
        }
        NotUsed
      }
      .endContextPropagation
      .map {
        case (_, committableOffset) =>
          committableOffset
      }
      .toMat(Committer.sink(CommitterSettings(actorSystem)))(Keep.both)
      .mapMaterializedValue(Consumer.DrainingControl.apply)
      .run()

    control
  }

  private def readFromElasticsearch(): Future[immutable.Seq[Movie]] = {
    val reading = ElasticsearchSource
      .typed[Movie](indexName, "_doc", """{"match_all": {}}""")
      .map(_.source)
      .runWith(Sink.seq)
    reading.foreach(_ => log.info("Reading finished"))
    reading
  }

  val movies = List(Movie(23, "Psycho"), Movie(423, "Citizen Kane"))
  val writing: Future[Done] = writeToKafka(movies)
  Await.result(writing, 10.seconds)

  val control = readFromKafkaWriteToElasticsearch()
  // Let the read/write stream run a bit
  Thread.sleep(5.seconds.toMillis)
  val copyingFinished = control.drainAndShutdown()
  Await.result(copyingFinished, 10.seconds)
  val reading = readFromElasticsearch()

  for {
    read <- reading
  } {
    read.foreach(m => println(s"read $m"))
    kafka.stop()
    elasticsearchClient.close()
    elasticsearchContainer.stop()
    actorSystem.terminate()
  }
}
