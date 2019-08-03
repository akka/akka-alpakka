/*
 * Copyright (C) 2016-2019 Lightbend Inc. <http://www.lightbend.com>
 */

package akka.stream.alpakka.dynamodb

import akka.util.ByteString
import com.amazonaws.services.dynamodbv2.model._

import scala.collection.JavaConverters._

trait TestOps {

  val tableName: String
  val lowThroughputTableName: String

  val keyCol = "kkey"
  val sortCol = "sort"

  def S(s: String) = new AttributeValue().withS(s)
  def N(n: Int) = new AttributeValue().withN(n.toString)
  def keyMap(hash: String, sort: Int): Map[String, AttributeValue] = Map(
    keyCol -> S(hash),
    sortCol -> N(sort)
  )

  def keyEQ(hash: String): Map[String, Condition] = Map(
    keyCol -> new Condition()
      .withComparisonOperator(ComparisonOperator.EQ)
      .withAttributeValueList(S(hash))
  )

  object common {
    val listTablesRequest = new ListTablesRequest()

    val createTableRequest = new CreateTableRequest()
      .withTableName(tableName)
      .withKeySchema(
        new KeySchemaElement().withAttributeName(keyCol).withKeyType(KeyType.HASH),
        new KeySchemaElement().withAttributeName(sortCol).withKeyType(KeyType.RANGE)
      )
      .withAttributeDefinitions(
        new AttributeDefinition().withAttributeName(keyCol).withAttributeType("S"),
        new AttributeDefinition().withAttributeName(sortCol).withAttributeType("N")
      )
      .withProvisionedThroughput(
        new ProvisionedThroughput().withReadCapacityUnits(10L).withWriteCapacityUnits(10L)
      )

    val createLowThroughputTableRequest = new CreateTableRequest()
      .withTableName(lowThroughputTableName)
      .withKeySchema(
        new KeySchemaElement().withAttributeName(keyCol).withKeyType(KeyType.HASH),
        new KeySchemaElement().withAttributeName(sortCol).withKeyType(KeyType.RANGE)
      )
      .withAttributeDefinitions(
        new AttributeDefinition().withAttributeName(keyCol).withAttributeType("S"),
        new AttributeDefinition().withAttributeName(sortCol).withAttributeType("N")
      )
      .withProvisionedThroughput(
        new ProvisionedThroughput().withReadCapacityUnits(1L).withWriteCapacityUnits(1L)
      )

    val describeTableRequest = new DescribeTableRequest().withTableName(tableName)

    val deleteTableRequest = new DeleteTableRequest().withTableName(tableName)
    val deleteLowThroughputTableRequest = new DeleteTableRequest().withTableName(lowThroughputTableName)
  }

}

object ItemSpecOps extends TestOps {

  override val tableName = "ItemSpecOps"
  override val lowThroughputTableName = "ItemSpecOpsLowThroughput"

  val listTablesRequest = common.listTablesRequest

  val createTableRequest = common.createTableRequest
  val createLowThroughputTableRequest = common.createLowThroughputTableRequest

  val test4Data = "test4data"

  val test4PutItemRequest =
    new PutItemRequest().withTableName(tableName).withItem((keyMap("A", 0) + ("data" -> S(test4Data))).asJava)

  val getItemRequest =
    new GetItemRequest().withTableName(tableName).withKey(keyMap("A", 0).asJava).withAttributesToGet("data")

  val test5Data = "test5Data"

  val test5PutItemRequest =
    new PutItemRequest().withTableName(tableName).withItem((keyMap("A", 1) + ("data" -> S(test5Data))).asJava)

  val batchWriteItemRequest = new BatchWriteItemRequest().withRequestItems(
    Map(
      tableName ->
      List(
        new WriteRequest(new PutRequest().withItem((keyMap("B", 0) + ("data" -> S(test5Data))).asJava)),
        new WriteRequest(new PutRequest().withItem((keyMap("B", 1) + ("data" -> S(test5Data))).asJava))
      ).asJava
    ).asJava
  )

  def batchWriteLargeItemRequest(range: Range) = new BatchWriteItemRequest().withRequestItems(
    Map(
      tableName ->
      range.map { i =>
        // 400k is the of one write request
        new WriteRequest(
          new PutRequest().withItem((keyMap(i.toString, i) + ("data1" -> S("0123456789" * 39000))).asJava)
        )
      }.asJava
    ).asJava
  )

  def batchGetLargeItemRequest(range: Range) = new BatchGetItemRequest().withRequestItems(
    Map(
      tableName ->
      new KeysAndAttributes()
        .withKeys {
          range.map { i =>
            Map(keyCol -> S(i.toString), sortCol -> N(i)).asJava
          }.asJava
        }
        .withAttributesToGet("data1")
    ).asJava
  )

  def batchGetItemRequest(items: java.util.Map[String, KeysAndAttributes]) =
    new BatchGetItemRequest().withRequestItems(items)

  val queryItemsRequest = new QueryRequest()
    .withTableName(tableName)
    .withKeyConditions(keyEQ("B").asJava)
    .withLimit(1)

  val deleteItemRequest = new DeleteItemRequest().withTableName(tableName).withKey(keyMap("A", 0).asJava)

  def test7PutItemRequest(n: Int) =
    new PutItemRequest().withTableName(tableName).withItem((keyMap("A", n)).asJava)

  val querySize =
    new QueryRequest()
      .withTableName(tableName)
      .withKeyConditionExpression(s"$keyCol = :k")
      .withExpressionAttributeValues(Map(":k" -> S("A")).asJava)

  val test8Data = "test8Data"

  val transactPutItemsRequest = new TransactWriteItemsRequest().withTransactItems(
    List(
      new TransactWriteItem()
        .withPut(new Put().withTableName(tableName).withItem((keyMap("C", 0) + ("data" -> S(test8Data))).asJava)),
      new TransactWriteItem()
        .withPut(new Put().withTableName(tableName).withItem((keyMap("C", 1) + ("data" -> S(test8Data))).asJava))
    ).asJava
  )

  val transactGetItemsRequest = new TransactGetItemsRequest().withTransactItems(
    List(
      new TransactGetItem().withGet(new Get().withTableName(tableName).withKey(keyMap("C", 0).asJava)),
      new TransactGetItem().withGet(new Get().withTableName(tableName).withKey(keyMap("C", 1).asJava))
    ).asJava
  )

  val transactDeleteItemsRequest = new TransactWriteItemsRequest().withTransactItems(
    List(
      new TransactWriteItem().withDelete(new Delete().withTableName(tableName).withKey(keyMap("C", 0).asJava)),
      new TransactWriteItem().withDelete(new Delete().withTableName(tableName).withKey(keyMap("C", 1).asJava))
    ).asJava
  )

  val deleteTableRequest = common.deleteTableRequest
  val deleteLowThroughputTableRequest = common.deleteLowThroughputTableRequest
}

object TableSpecOps extends TestOps {

  override val tableName = "TableSpecOps"
  override val lowThroughputTableName = "TableSpecOpsLowThroughput"

  val createTableRequest = common.createTableRequest

  val listTablesRequest = common.listTablesRequest

  val describeTableRequest = common.describeTableRequest

  val newMaxLimit = 5L
  val describeLimitsRequest = new DescribeLimitsRequest()
  val updateTableRequest = new UpdateTableRequest()
    .withTableName(tableName)
    .withProvisionedThroughput(
      new ProvisionedThroughput().withWriteCapacityUnits(newMaxLimit).withReadCapacityUnits(newMaxLimit)
    )

  val describeTimeToLiveRequest = new DescribeTimeToLiveRequest()
  val updateTimeToLiveRequest = new UpdateTimeToLiveRequest()
    .withTableName(tableName)
    .withTimeToLiveSpecification(
      new TimeToLiveSpecification().withAttributeName("expires").withEnabled(true)
    )

  val deleteTableRequest = common.deleteTableRequest

}
