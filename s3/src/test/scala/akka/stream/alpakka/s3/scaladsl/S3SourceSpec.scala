/*
 * Copyright (C) 2016-2018 Lightbend Inc. <http://www.lightbend.com>
 */

package akka.stream.alpakka.s3.scaladsl

import akka.NotUsed
import akka.http.scaladsl.model.headers.ByteRange
import akka.stream.alpakka.s3.impl.{ListBucketVersion2, ServerSideEncryption}
import akka.stream.alpakka.s3._
import akka.stream.scaladsl.{Sink, Source}
import akka.util.ByteString

import scala.concurrent.Future
import com.amazonaws.auth.{AWSStaticCredentialsProvider, BasicAWSCredentials}
import com.amazonaws.regions.AwsRegionProvider

class S3SourceSpec extends S3WireMockBase with S3ClientIntegrationSpec {

  "S3Source" should "download a stream of bytes from S3" in {

    mockDownload()

    //#download
    val downloadResult = S3.download(bucket, bucketKey)
    //#download

    val Some((s3Source: Source[ByteString, _], _)) = downloadResult.runWith(Sink.head).futureValue
    val result: Future[String] = s3Source.map(_.utf8String).runWith(Sink.head)

    result.futureValue shouldBe body
  }

  "S3Source" should "download a metadata from S3" in {

    val contentLength = 8
    mockHead(contentLength)

    //#objectMetadata
    val metadata = S3.getObjectMetadata(bucket, bucketKey)
    //#objectMetadata

    val Some(result) = metadata.runWith(Sink.head).futureValue

    result.eTag shouldBe Some(etag)
    result.contentLength shouldBe contentLength
    result.versionId shouldBe empty
  }

  "S3Source" should "download a metadata from S3 for a big file" in {

    val contentLength = Long.MaxValue
    mockHead(contentLength)

    //#objectMetadata
    val metadata = s3Client.getObjectMetadata(bucket, bucketKey)
    //#objectMetadata

    val Some(result) = metadata.futureValue

    result.eTag shouldBe Some(etag)
    result.contentLength shouldBe contentLength
    result.versionId shouldBe empty
  }

  "S3Source" should "download a metadata from S3 for specific version" in {

    val versionId = "3/L4kqtJlcpXroDTDmJ+rmSpXd3dIbrHY+MTRCxf3vjVBH40Nr8X8gdRQBpUMLUo"
    mockHeadWithVersion(versionId)

    //#objectMetadata
    val metadata = S3.getObjectMetadata(bucket, bucketKey, Some(versionId))
    //#objectMetadata

    val Some(result) = metadata.runWith(Sink.head).futureValue

    result.eTag shouldBe Some(etag)
    result.contentLength shouldBe 8
    result.versionId.fold(fail("unable to get versionId from S3")) { vId =>
      vId shouldEqual versionId
    }
  }

  it should "download a metadata from S3 using server side encryption" in {

    mockHeadSSEC()

    //#objectMetadata
    val metadata = S3.getObjectMetadata(bucket, bucketKey, sse = Some(sseCustomerKeys))
    //#objectMetadata

    val Some(result) = metadata.runWith(Sink.head).futureValue

    result.eTag shouldBe Some(etagSSE)
    result.contentLength shouldBe 8
  }

  it should "download a range of file's bytes from S3 if bytes range given" in {

    mockRangedDownload()

    //#rangedDownload
    val downloadResult = S3.download(bucket, bucketKey, Some(ByteRange(bytesRangeStart, bytesRangeEnd)))
    //#rangedDownload

    val Some((s3Source: Source[ByteString, _], _)) = downloadResult.runWith(Sink.head).futureValue
    val result: Future[Array[Byte]] = s3Source.map(_.toArray).runWith(Sink.head)

    result.futureValue shouldBe rangeOfBody
  }

  it should "download a stream of bytes using customer server side encryption" in {

    mockDownloadSSEC()

    //#download
    val downloadResult = S3.download(bucket, bucketKey, sse = Some(sseCustomerKeys))
    //#download

    val Some((s3Source: Source[ByteString, _], _)) = downloadResult.runWith(Sink.head).futureValue
    val result = s3Source.map(_.utf8String).runWith(Sink.head)

    result.futureValue shouldBe bodySSE
  }

  it should "download a stream of bytes using customer server side encryption with version" in {
    val versionId = "3/L4kqtJlcpXroDTDmJ+rmSpXd3dIbrHY+MTRCxf3vjVBH40Nr8X8gdRQBpUMLUo"
    mockDownloadSSECWithVersion(versionId)

    //#download
    val downloadResult =
      S3.download(bucket, bucketKey, versionId = Some(versionId), sse = Some(sseCustomerKeys))
    //#download

    val Some((s3Source: Source[ByteString, _], metadata)) = downloadResult.runWith(Sink.head).futureValue
    val result = s3Source.map(_.utf8String).runWith(Sink.head)

    result.futureValue shouldBe bodySSE
    metadata.versionId.fold(fail("unable to get versionId from S3")) { vId =>
      vId shouldEqual versionId
    }
  }

  it should "fail if request returns 404" in {

    mock404s()

    val download = S3
      .download("nonexisting_bucket", "nonexisting_file.xml")
      .runWith(Sink.head)
      .futureValue

    download shouldBe None
  }

  it should "fail if download using server side encryption returns 'Invalid Request'" in {

    mockSSEInvalidRequest()

    import system.dispatcher

    val sse = ServerSideEncryption.CustomerKeys("encoded-key", Some("md5-encoded-key"))
    val result = S3
      .download(bucket, bucketKey, sse = Some(sse))
      .runWith(Sink.head)
      .flatMap {
        case Some((downloadSource, _)) =>
          downloadSource
            .map(_.decodeString("utf8"))
            .runWith(Sink.head)
            .map(Some.apply)
        case None => Future.successful(None)
      }

    whenReady(result.failed) { e =>
      e shouldBe a[S3Exception]
      e.asInstanceOf[S3Exception].code should equal("InvalidRequest")
    }
  }

  it should "list keys for a given bucket with a prefix" in {
    mockListBucket()

    //#list-bucket
    val keySource: Source[ListBucketResultContents, NotUsed] = S3.listBucket(bucket, Some(listPrefix))
    //#list-bucket

    val result = keySource.runWith(Sink.head)

    result.futureValue.key shouldBe listKey
  }

  it should "list keys for a given bucket with a prefix using the version 1 api" in {
    mockListBucketVersion1()

    val keySource: Source[ListBucketResultContents, NotUsed] =
      S3.listBucket(bucket, Some(listPrefix))

    val result = keySource.runWith(Sink.head)

    result.futureValue.key shouldBe listKey
  }

  override protected def afterAll(): Unit = {
    super.afterAll()
    stopWireMockServer()
  }
}
