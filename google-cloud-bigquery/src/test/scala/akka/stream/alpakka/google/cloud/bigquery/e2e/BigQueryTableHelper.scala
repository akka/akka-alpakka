/*
 * Copyright (C) 2016-2018 Lightbend Inc. <http://www.lightbend.com>
 */

package akka.stream.alpakka.google.cloud.bigquery.e2e
import java.util.UUID

import org.scalatest.{BeforeAndAfterAll, WordSpecLike}

import scala.concurrent.Await
/*
Table is: (A1: string, A2: ?int, A3: ?boolean)
Sample data is:
  ("v1", 1, true)
  ("v2", 2, false)
  ("v3", 3, true)
  ("v4", -4, false)
  ("v5", NULL, false)
  ("v6", 6, NULL)
  ("v7", NULL, NULL)
 */
trait BigQueryTableHelper extends BigQueryTestHelper with WordSpecLike with BeforeAndAfterAll {
  val uuid = UUID.randomUUID().toString.replace("-", "")
  val tableName = s"bqtest_$uuid"

  override def beforeAll(): Unit =
    if (enableE2E) {
      initDb()
      sleep()
    }
  override def afterAll(): Unit =
    if (enableE2E) {
      cleanUpDb()
      sleep()
    }

  def initDb(): Unit = {
    val createTableSql =
      s"""
         |{
         |  "friendlyName": "$tableName",
         |  "tableReference": {
         |    "datasetId": "$dataset",
         |    "projectId": "$projectId",
         |    "tableId": "$tableName"
         |  },
         |  "schema": {
         |    "fields": [
         |      {
         |        "name": "A1",
         |        "type": "STRING",
         |        "mode": "REQUIRED"
         |      },
         |      {
         |        "name": "A2",
         |        "type": "INTEGER"
         |      },
         |      {
         |        "name": "A3",
         |        "type": "BOOL"
         |      }
         |    ]
         |  }
         |}
       """.stripMargin
    val insertDataSql =
      """
        |{
        |  rows: [
        |    {
        |      "json": {
        |        "A1": "v1",
        |        "A2": 1,
        |        "A3": true
        |      }
        |    },
        |    {
        |      "json": {
        |        "A1": "v2",
        |        "A2": 2,
        |        "A3": false
        |      }
        |    },
        |    {
        |      "json": {
        |        "A1": "v3",
        |        "A2": 3,
        |        "A3": true
        |      }
        |    },
        |    {
        |      "json": {
        |        "A1": "v4",
        |        "A2": -4,
        |        "A3": false
        |      }
        |    },
        |    {
        |      "json": {
        |        "A1": "v5",
        |        "A2": null,
        |        "A3": false
        |      }
        |    },
        |    {
        |      "json": {
        |        "A1": "v6",
        |        "A2": 6,
        |        "A3": null
        |      }
        |    },
        |    {
        |      "json": {
        |        "A1": "v7",
        |        "A2": null,
        |        "A3": null
        |      }
        |    }
        |  ]
        |}
      """.stripMargin
    Await.result(for {
      _ <- runRequest(createTable(createTableSql))
      _ <- runRequest(insertInto(insertDataSql, tableName))
    } yield (), defaultTimeout)
    sleep()
  }

  def cleanUpDb(): Unit =
    Await.result(for {
      _ <- runRequest(dropTable(tableName))
    } yield (), defaultTimeout)
}
