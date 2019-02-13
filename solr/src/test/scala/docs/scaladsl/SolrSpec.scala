/*
 * Copyright (C) 2016-2018 Lightbend Inc. <http://www.lightbend.com>
 */

package docs.scaladsl

import java.io.File
import java.util.{Arrays, Optional}

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, Materializer}
import akka.stream.alpakka.solr._
import akka.stream.alpakka.solr.scaladsl.{SolrFlow, SolrSink, SolrSource}
import akka.stream.scaladsl.{Sink, Source}
import akka.testkit.TestKit
import org.apache.solr.client.solrj.embedded.JettyConfig
import org.apache.solr.client.solrj.impl.{CloudSolrClient, ZkClientClusterStateProvider}
import org.apache.solr.client.solrj.io.stream.expr.{StreamExpressionParser, StreamFactory}
import org.apache.solr.client.solrj.io.stream.{CloudSolrStream, StreamContext, TupleStream}
import org.apache.solr.client.solrj.io.{SolrClientCache, Tuple}
import org.apache.solr.client.solrj.request.{CollectionAdminRequest, UpdateRequest}
import org.apache.solr.cloud.{MiniSolrCloudCluster, ZkTestServer}
import org.apache.solr.common.SolrInputDocument
import org.junit.Assert.assertTrue
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

class SolrSpec extends WordSpecLike with Matchers with BeforeAndAfterAll with ScalaFutures {

  override implicit val patienceConfig: PatienceConfig = PatienceConfig(5.seconds)

  private var cluster: MiniSolrCloudCluster = _

  private var zkTestServer: ZkTestServer = _
  implicit val system: ActorSystem = ActorSystem()
  implicit val commitExecutionContext: ExecutionContext = ExecutionContext.global

  implicit val materializer: Materializer = ActorMaterializer()
  //#init-client
  final val zookeeperPort = 9984
  final val zkHost = s"127.0.0.1:$zookeeperPort/solr"
  implicit val solrClient: CloudSolrClient = new CloudSolrClient.Builder(Arrays.asList(zkHost), Optional.empty()).build

  //#init-client
  //#define-class
  case class Book(title: String, comment: String = "", routerOpt: Option[String] = None)

  val bookToDoc: Book => SolrInputDocument = { b =>
    val doc = new SolrInputDocument
    doc.setField("title", b.title)
    doc.setField("comment", b.comment)
    b.routerOpt.foreach { router =>
      doc.setField("router", router)
    }
    doc
  }

  val tupleToBook: Tuple => Book = { t =>
    val title = t.getString("title")
    Book(title, t.getString("comment"))
  }
  //#define-class

  "Un-typed Solr connector" should {
    "consume and publish SolrInputDocument" in {
      // Copy collection1 to collectionName through document stream
      val collectionName = "collectionName"
      createCollection(collectionName)
      val stream = getTupleStream("collection1")

      //#run-document
      val copyCollection = SolrSource
        .fromTupleStream(stream)
        .map { tuple: Tuple =>
          val book: Book = tupleToBook(tuple)
          val doc: SolrInputDocument = bookToDoc(book)
          WriteMessage.createUpsertMessage(doc)
        }
        .groupedWithin(5, 10.millis)
        .runWith(
          SolrSink.documents(collectionName, SolrUpdateSettings())
        )
        // explicit commit when stream ended
        .map { _ =>
          solrClient.commit(collectionName)
        }(commitExecutionContext)
      //#run-document

      copyCollection.futureValue

      val stream2 = getTupleStream(collectionName)

      val res2 = SolrSource
        .fromTupleStream(stream2)
        .map(tupleToBook)
        .map(_.title)
        .runWith(Sink.seq)

      res2.futureValue shouldEqual Seq(
        "Akka Concurrency",
        "Akka in Action",
        "Effective Akka",
        "Learning Scala",
        "Programming in Scala",
        "Scala Puzzlers",
        "Scala for Spark in Production"
      )
    }

  }

  "Typed Solr connector" should {
    "consume and publish documents as specific type using a bean" in {
      // Copy collection1 to collection3 through bean stream
      val collectionName = "collection3"
      createCollection(collectionName)
      val stream = getTupleStream("collection1")

      //#define-bean
      import org.apache.solr.client.solrj.beans.Field

      import scala.annotation.meta.field
      case class BookBean(@(Field @field) title: String)
      //#define-bean

      //#run-bean
      val copyCollection = SolrSource
        .fromTupleStream(stream)
        .map { tuple: Tuple =>
          val title = tuple.getString("title")
          WriteMessage.createUpsertMessage(BookBean(title))
        }
        .groupedWithin(5, 10.millis)
        .runWith(
          SolrSink.beans[BookBean](collectionName, SolrUpdateSettings())
        )
        // explicit commit when stream ended
        .map { _ =>
          solrClient.commit(collectionName)
        }(commitExecutionContext)
      //#run-bean

      copyCollection.futureValue

      val stream2 = getTupleStream(collectionName)

      val res2 = SolrSource
        .fromTupleStream(stream2)
        .map(tupleToBook)
        .map(_.title)
        .runWith(Sink.seq)

      res2.futureValue shouldEqual Seq(
        "Akka Concurrency",
        "Akka in Action",
        "Effective Akka",
        "Learning Scala",
        "Programming in Scala",
        "Scala Puzzlers",
        "Scala for Spark in Production"
      )
    }

    "consume and publish documents as specific type with a binder" in {
      // Copy collection1 to collection4 through typed stream
      val collectionName = "collection4"
      createCollection(collectionName)
      val stream = getTupleStream("collection1")

      //#run-typed
      val copyCollection = SolrSource
        .fromTupleStream(stream)
        .map { tuple: Tuple =>
          val book: Book = tupleToBook(tuple)
          WriteMessage.createUpsertMessage(book)
        }
        .groupedWithin(5, 10.millis)
        .runWith(
          SolrSink
            .typeds[Book](
              collectionName,
              SolrUpdateSettings(),
              binder = bookToDoc
            )
        )
        // explicit commit when stream ended
        .map { _ =>
          solrClient.commit(collectionName)
        }(commitExecutionContext)
      //#run-typed

      copyCollection.futureValue

      val stream2 = getTupleStream(collectionName)

      val res2 = SolrSource
        .fromTupleStream(stream2)
        .map(tupleToBook)
        .map(_.title)
        .runWith(Sink.seq)

      res2.futureValue shouldEqual Seq(
        "Akka Concurrency",
        "Akka in Action",
        "Effective Akka",
        "Learning Scala",
        "Programming in Scala",
        "Scala Puzzlers",
        "Scala for Spark in Production"
      )
    }
  }

  "SolrFlow" should {
    "store documents and pass status to downstream" in {
      // Copy collection1 to collection5 through typed stream
      val collectionName = "collection5"
      createCollection(collectionName)
      val stream = getTupleStream("collection1")

      //#run-flow
      val copyCollection = SolrSource
        .fromTupleStream(stream)
        .map { tuple: Tuple =>
          val book: Book = tupleToBook(tuple)
          WriteMessage.createUpsertMessage(book)
        }
        .groupedWithin(5, 10.millis)
        .via(
          SolrFlow
            .typeds[Book](
              collectionName,
              SolrUpdateSettings(),
              binder = bookToDoc
            )
        )
        .runWith(Sink.seq)
        // explicit commit when stream ended
        .map { seq =>
          solrClient.commit(collectionName)
          seq
        }(commitExecutionContext)
      //#run-flow

      val result1 = copyCollection.futureValue

      // Assert no errors
      assert(result1.forall(_.exists(_.status == 0)))

      val stream2 = getTupleStream(collectionName)

      val res2 = SolrSource
        .fromTupleStream(stream2)
        .map(tupleToBook)
        .map(_.title)
        .runWith(Sink.seq)

      res2.futureValue shouldEqual Seq(
        "Akka Concurrency",
        "Akka in Action",
        "Effective Akka",
        "Learning Scala",
        "Programming in Scala",
        "Scala Puzzlers",
        "Scala for Spark in Production"
      )
    }

    "kafka-example - store documents and pass responses with passThrough" in {
      val collectionName = "collection6"
      createCollection(collectionName)

      //#kafka-example
      // We're going to pretend we got messages from kafka.
      // After we've written them to Solr, we want
      // to commit the offset to Kafka

      case class KafkaOffset(offset: Int)
      case class KafkaMessage(book: Book, offset: KafkaOffset)

      val messagesFromKafka = List(
        KafkaMessage(Book("Book 1"), KafkaOffset(0)),
        KafkaMessage(Book("Book 2"), KafkaOffset(1)),
        KafkaMessage(Book("Book 3"), KafkaOffset(2))
      )

      var committedOffsets = List[KafkaOffset]()

      def commitToKafka(offset: KafkaOffset): Unit =
        committedOffsets = committedOffsets :+ offset

      val copyCollection = Source(messagesFromKafka)
        .map { kafkaMessage: KafkaMessage =>
          val book = kafkaMessage.book
          // Transform message so that we can write to solr
          WriteMessage.createUpsertMessage(book).withPassThrough(kafkaMessage.offset)
        }
        .groupedWithin(5, 10.millis)
        .via( // write to Solr
          SolrFlow.typedsWithPassThrough[Book, KafkaOffset](
            collectionName,
            // use implicit commits to Solr
            SolrUpdateSettings().withCommitWithin(5),
            binder = bookToDoc
          )
        )
        .map { messageResults =>
          messageResults.foreach { result =>
            if (result.status != 0)
              throw new Exception("Failed to write message to Solr")
            // Commit to kafka
            commitToKafka(result.passThrough)
          }
        }
        .runWith(Sink.ignore)
      //#kafka-example

      copyCollection.futureValue

      // Make sure all messages was committed to kafka
      assert(List(0, 1, 2) == committedOffsets.map(_.offset))

      val stream = getTupleStream(collectionName)

      val res2 = SolrSource
        .fromTupleStream(stream)
        .map(tupleToBook)
        .map(_.title)
        .runWith(Sink.seq)

      res2.futureValue.sorted shouldEqual messagesFromKafka.map(_.book.title).sorted
    }
  }

  "Un-typed Solr connector" should {
    "consume and delete documents" in {
      // Copy collection1 to collection2 through document stream
      val collectionName = "collection7"
      createCollection(collectionName)
      val stream = getTupleStream("collection1")

      val copyCollection = SolrSource
        .fromTupleStream(stream)
        .map { tuple: Tuple =>
          val book: Book = tupleToBook(tuple)
          val doc: SolrInputDocument = bookToDoc(book)
          WriteMessage.createUpsertMessage(doc)
        }
        .groupedWithin(5, 10.millis)
        .runWith(
          SolrSink.documents(collectionName, SolrUpdateSettings())
        )
        // explicit commit when stream ended
        .map { _ =>
          solrClient.commit(collectionName)
        }(commitExecutionContext)

      copyCollection.futureValue

      val stream2 = getTupleStream(collectionName)

      //#delete-documents
      val deleteDocuments = SolrSource
        .fromTupleStream(stream2)
        .map { tuple: Tuple =>
          val id = tuple.fields.get("title").toString
          WriteMessage.createDeleteMessage[SolrInputDocument](id)
        }
        .groupedWithin(5, 10.millis)
        .runWith(
          SolrSink.documents(collectionName, SolrUpdateSettings())
        )
        // explicit commit when stream ended
        .map { _ =>
          solrClient.commit(collectionName)
        }(commitExecutionContext)
      //#delete-documents

      deleteDocuments.futureValue

      val stream3 = getTupleStream(collectionName)

      val res2 = SolrSource
        .fromTupleStream(stream3)
        .map(tupleToBook)
        .map(_.title)
        .runWith(Sink.seq)

      res2.futureValue shouldEqual Seq.empty[String]
    }

    "consume and update atomically documents" in {
      // Copy collection1 to collection2 through document stream
      val collectionName = "collection8"
      createCollection(collectionName)
      val stream = getTupleStream("collection1")

      val upsertCollection = SolrSource
        .fromTupleStream(stream)
        .map { tuple: Tuple =>
          val book: Book = tupleToBook(tuple)
            .copy(comment = "Written by good authors.")
          val doc: SolrInputDocument = bookToDoc(book)
          WriteMessage.createUpsertMessage(doc)
        }
        .groupedWithin(5, 10.millis)
        .runWith(
          SolrSink.documents(collectionName, SolrUpdateSettings())
        )
        // explicit commit when stream ended
        .map { _ =>
          solrClient.commit(collectionName)
        }(commitExecutionContext)

      upsertCollection.futureValue

      val stream2 = getTupleStream(collectionName)

      //#update-atomically-documents
      val updateCollection = SolrSource
        .fromTupleStream(stream2)
        .map { tuple: Tuple =>
          val id = tuple.fields.get("title").toString
          val comment = tuple.fields.get("comment").toString
          WriteMessage.createUpdateMessage[SolrInputDocument](
            idField = "title",
            idValue = id,
            updates = Map(
              "comment" ->
              Map("set" -> (comment + " It is a good book!!!"))
            )
          )
        }
        .groupedWithin(5, 10.millis)
        .runWith(
          SolrSink.documents(collectionName, SolrUpdateSettings())
        )
        // explicit commit when stream ended
        .map { _ =>
          solrClient.commit(collectionName)
        }(commitExecutionContext)
      //#update-atomically-documents

      updateCollection.futureValue

      val stream3 = getTupleStream(collectionName)

      val res2 = SolrSource
        .fromTupleStream(stream3)
        .map(tupleToBook)
        .map { b =>
          b.title + ". " + b.comment
        }
        .runWith(Sink.seq)

      res2.futureValue shouldEqual Seq(
        "Akka Concurrency. Written by good authors. It is a good book!!!",
        "Akka in Action. Written by good authors. It is a good book!!!",
        "Effective Akka. Written by good authors. It is a good book!!!",
        "Learning Scala. Written by good authors. It is a good book!!!",
        "Programming in Scala. Written by good authors. It is a good book!!!",
        "Scala Puzzlers. Written by good authors. It is a good book!!!",
        "Scala for Spark in Production. Written by good authors. It is a good book!!!"
      )
    }

  }

  "Solr connector" should {
    "consume and delete beans" in {
      val collectionName = "collection9"
      createCollection(collectionName)
      val stream = getTupleStream("collection1")

      val copyCollection = SolrSource
        .fromTupleStream(stream)
        .map { tuple: Tuple =>
          val book: Book = tupleToBook(tuple)
          WriteMessage.createUpsertMessage(book)
        }
        .groupedWithin(5, 10.millis)
        .runWith(
          SolrSink.typeds[Book](
            collectionName,
            SolrUpdateSettings(),
            binder = bookToDoc
          )
        )
        // explicit commit when stream ended
        .map { _ =>
          solrClient.commit(collectionName)
        }(commitExecutionContext)

      copyCollection.futureValue

      val stream2 = getTupleStream(collectionName)

      val deleteElements = SolrSource
        .fromTupleStream(stream2)
        .map { tuple: Tuple =>
          val title = tuple.fields.get("title").toString
          WriteMessage.createDeleteMessage[Book](title)
        }
        .groupedWithin(5, 10.millis)
        .runWith(
          SolrSink.typeds[Book](
            collectionName,
            SolrUpdateSettings().withCommitWithin(5),
            binder = bookToDoc
          )
        )

      deleteElements.futureValue

      val stream3 = getTupleStream(collectionName)

      val res2 = SolrSource
        .fromTupleStream(stream3)
        .map(tupleToBook)
        .map(_.title)
        .runWith(Sink.seq)

      res2.futureValue shouldEqual Seq.empty[String]
    }

  }

  "Solr connector" should {
    "consume and update atomically beans" in {
      // Copy collection1 to collection2 through document stream
      val collectionName = "collection10"
      createCollection(collectionName, Some("router"))
      val stream = getTupleStream("collection1")

      val copyCollection = SolrSource
        .fromTupleStream(stream)
        .map { tuple: Tuple =>
          val book: Book =
            tupleToBook(tuple).copy(comment = "Written by good authors.", routerOpt = Some("router-value"))
          WriteMessage.createUpsertMessage(book)
        }
        .groupedWithin(5, 10.millis)
        .runWith(
          SolrSink.typeds[Book](
            collectionName,
            SolrUpdateSettings(),
            binder = bookToDoc
          )
        )
        // explicit commit when stream ended
        .map { _ =>
          solrClient.commit(collectionName)
        }(commitExecutionContext)

      copyCollection.futureValue

      val stream2 = getTupleStream(collectionName)

      val updateCollection = SolrSource
        .fromTupleStream(stream2)
        .map { tuple: Tuple =>
          WriteMessage.createUpdateMessage[Book](
            idField = "title",
            tuple.fields.get("title").toString,
            routingFieldValue = Some("router-value"),
            updates = Map("comment" -> Map("set" -> (tuple.fields.get("comment") + " It is a good book!!!")))
          )
        }
        .groupedWithin(5, 10.millis)
        .runWith(
          SolrSink.typeds[Book](
            collectionName,
            SolrUpdateSettings(),
            binder = bookToDoc
          )
        )
        // explicit commit when stream ended
        .map { _ =>
          solrClient.commit(collectionName)
        }(commitExecutionContext)

      updateCollection.futureValue

      val stream3 = getTupleStream(collectionName)

      val res2 = SolrSource
        .fromTupleStream(stream3)
        .map(tupleToBook)
        .map { b =>
          b.title + ". " + b.comment
        }
        .runWith(Sink.seq)

      res2.futureValue shouldEqual Seq(
        "Akka Concurrency. Written by good authors. It is a good book!!!",
        "Akka in Action. Written by good authors. It is a good book!!!",
        "Effective Akka. Written by good authors. It is a good book!!!",
        "Learning Scala. Written by good authors. It is a good book!!!",
        "Programming in Scala. Written by good authors. It is a good book!!!",
        "Scala Puzzlers. Written by good authors. It is a good book!!!",
        "Scala for Spark in Production. Written by good authors. It is a good book!!!"
      )
    }

  }

  "Un-typed Solr connector" should {
    "consume and delete documents by query" in {
      val collectionName = "collection11"
      createCollection(collectionName)
      val stream = getTupleStream("collection1")

      val copyCollection = SolrSource
        .fromTupleStream(stream)
        .map { tuple: Tuple =>
          val book: Book = tupleToBook(tuple)
          val doc: SolrInputDocument = bookToDoc(book)
          WriteMessage.createUpsertMessage(doc)
        }
        .groupedWithin(5, 10.millis)
        .runWith(
          SolrSink.documents(collectionName, SolrUpdateSettings())
        )
        // explicit commit when stream ended
        .map { _ =>
          solrClient.commit(collectionName)
        }(commitExecutionContext)

      copyCollection.futureValue

      val stream2 = getTupleStream(collectionName)

      //#delete-documents-query
      val deleteByQuery = SolrSource
        .fromTupleStream(stream2)
        .map { tuple: Tuple =>
          val title = tuple.fields.get("title").toString
          WriteMessage.createDeleteByQueryMessage[SolrInputDocument](
            s"""title:"$title" """
          )
        }
        .groupedWithin(5, 10.millis)
        .runWith(
          SolrSink.documents(collectionName, SolrUpdateSettings())
        )
        // explicit commit when stream ended
        .map { _ =>
          solrClient.commit(collectionName)
        }(commitExecutionContext)
      //#delete-documents-query

      deleteByQuery.futureValue

      val stream3 = getTupleStream(collectionName)

      val res2 = SolrSource
        .fromTupleStream(stream3)
        .map(tupleToBook)
        .map(_.title)
        .runWith(Sink.seq)

      res2.futureValue shouldEqual Seq.empty[String]
    }

  }

  override def beforeAll(): Unit = {
    setupCluster()
    new UpdateRequest()
      .add("title", "Akka in Action")
      .add("title", "Programming in Scala")
      .add("title", "Learning Scala")
      .add("title", "Scala for Spark in Production")
      .add("title", "Scala Puzzlers")
      .add("title", "Effective Akka")
      .add("title", "Akka Concurrency")
      .commit(solrClient, "collection1")
  }

  override def afterAll(): Unit = {
    solrClient.close()
    cluster.shutdown()
    zkTestServer.shutdown()
    TestKit.shutdownActorSystem(system)
  }

  private def setupCluster(): Unit = {
    val targetDir = new File("solr/target")
    val testWorkingDir =
      new File(targetDir, "scala-solr-" + System.currentTimeMillis)
    if (!testWorkingDir.isDirectory)
      testWorkingDir.mkdirs

    val confDir = new File("solr/src/test/resources/conf")

    val zkDir = testWorkingDir.toPath.resolve("zookeeper/server/data").toString
    zkTestServer = new ZkTestServer(zkDir, zookeeperPort)
    zkTestServer.run()

    cluster = new MiniSolrCloudCluster(
      1,
      testWorkingDir.toPath,
      MiniSolrCloudCluster.DEFAULT_CLOUD_SOLR_XML,
      JettyConfig.builder.setContext("/solr").build,
      zkTestServer
    )
    solrClient.getClusterStateProvider
      .asInstanceOf[ZkClientClusterStateProvider]
      .uploadConfig(confDir.toPath, "conf")
    solrClient.setIdField("router")

    createCollection("collection1")

    assertTrue(!solrClient.getZkStateReader.getClusterState.getLiveNodes.isEmpty)
  }

  private def createCollection(name: String, routerFieldOpt: Option[String] = None) =
    CollectionAdminRequest
      .createCollection(name, "conf", 1, 1)
      .setRouterField(routerFieldOpt.orNull)
      .process(solrClient)

  private def getTupleStream(collection: String): TupleStream = {
    //#tuple-stream
    val factory = new StreamFactory().withCollectionZkHost(collection, zkHost)
    val solrClientCache = new SolrClientCache()
    val streamContext = new StreamContext()
    streamContext.setSolrClientCache(solrClientCache)

    val expression =
      StreamExpressionParser.parse(s"""search($collection, q=*:*, fl="title,comment", sort="title asc")""")
    val stream: TupleStream = new CloudSolrStream(expression, factory)
    stream.setStreamContext(streamContext)

    val source = SolrSource
      .fromTupleStream(stream)
    //#tuple-stream
    stream
  }

  private def documentation: Unit = {
    //#solr-update-settings
    import akka.stream.alpakka.solr.SolrUpdateSettings

    val settings = SolrUpdateSettings()
    //#solr-update-settings
  }
}
