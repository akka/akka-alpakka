/*
 * Copyright (C) 2016-2018 Lightbend Inc. <http://www.lightbend.com>
 */

package docs.scaladsl

import akka.stream.alpakka.couchbase.scaladsl.CouchbaseSource
import akka.stream.alpakka.couchbase.testing.CouchbaseSupport
import akka.stream.scaladsl.Sink
import akka.stream.testkit.scaladsl.StreamTestKit._
import com.couchbase.client.java.document.json.JsonObject
import org.specs2.matcher.Matchers
import org.specs2.mutable.Specification
import org.specs2.specification.BeforeAfterAll

import scala.collection.immutable.Seq
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

class CouchbaseSourceSpec extends Specification with BeforeAfterAll with CouchbaseSupport with Matchers {

  sequential

  "CouchbaseSource" should {

    "run simple Statement Query" in assertAllStagesStopped {
      // #statement
      import com.couchbase.client.java.query.Select.select
      import com.couchbase.client.java.query.dsl.Expression._

      val resultAsFuture: Future[Seq[JsonObject]] =
        CouchbaseSource
          .fromStatement(sessionSettings, select("*").from(i(queryBucketName)).limit(10), bucketName)
          .runWith(Sink.seq)
      // #statement
      val result = Await.result(resultAsFuture, 5.seconds)
      result.length shouldEqual 4
    }

    "run simple N1QL query" in assertAllStagesStopped {

      //#n1ql
      import com.couchbase.client.java.query.{N1qlParams, N1qlQuery}

      val params = N1qlParams.build.adhoc(false)
      val query = N1qlQuery.simple(s"select count(*) from $queryBucketName", params)

      val resultAsFuture: Future[Seq[JsonObject]] =
        CouchbaseSource
          .fromN1qlQuery(sessionSettings, query, bucketName)
          .runWith(Sink.seq)
      //#n1ql

      val result = Await.result(resultAsFuture, 5.seconds)
      result.head.get("$1") shouldEqual 4
    }

  }

  override def beforeAll(): Unit = {
    super.beforeAll()
    upsertSampleData();
  }

  override def afterAll(): Unit = {
    cleanAllInBucket(sampleSequence.map(_.id), queryBucketName)
    super.afterAll()
  }

}
