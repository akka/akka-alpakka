/*
 * Copyright (C) 2016-2018 Lightbend Inc. <http://www.lightbend.com>
 */

package akka.stream.alpakka.s3.scaladsl

import akka.actor.ActorSystem
import akka.http.scaladsl.model.ContentTypes
import akka.stream.ActorMaterializer
import akka.stream.alpakka.s3.{S3Client, S3Settings}
import akka.stream.alpakka.s3.impl.{ListBucketVersion1, MetaHeaders, S3Headers}
import akka.stream.scaladsl.{Keep, Sink, Source}
import akka.util.ByteString
import com.amazonaws.auth.{AWSStaticCredentialsProvider, BasicAWSCredentials}
import com.typesafe.config.ConfigFactory
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest._

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import com.amazonaws.regions.AwsRegionProvider

trait S3IntegrationSpec extends FlatSpecLike with BeforeAndAfterAll with Matchers with ScalaFutures with OptionValues {

  implicit val actorSystem = ActorSystem()
  implicit val materializer = ActorMaterializer()
  implicit val ec = materializer.executionContext

  implicit val defaultPatience =
    PatienceConfig(timeout = Span(5, Seconds), interval = Span(30, Millis))

  val defaultRegion = "us-east-1"
  val defaultRegionProvider = new AwsRegionProvider {
    val getRegion: String = defaultRegion
  }
  val defaultRegionBucket = "my-test-us-east-1"

  val otherRegion = "eu-central-1"
  val otherRegionProvider = new AwsRegionProvider {
    val getRegion: String = otherRegion
  }
  val otherRegionBucket = "my.test.frankfurt" // with dots forcing path style access

  val objectKey = "test"

  val objectValue = "Some String"
  val metaHeaders: Map[String, String] = Map("location" -> "Africa", "datatype" -> "image")

  def settings =
    S3Settings(ConfigFactory.load().getConfig("aws"))
      .copy(s3RegionProvider = defaultRegionProvider)
  def otherRegionSettings =
    settings.copy(pathStyleAccess = true, s3RegionProvider = otherRegionProvider)
  def listBucketVersion1Settings =
    settings.copy(listBucketApiVersion = ListBucketVersion1)

  def defaultRegionContentCount = 4
  def otherRegionContentCount = 5

  lazy val defaultRegionClient = S3Client(settings)
  lazy val otherRegionClient = S3Client(otherRegionSettings)
  lazy val version1DefaultRegionClient = S3Client(listBucketVersion1Settings)

  it should "list with real credentials" in {
    val result = S3.listBucket(defaultRegionBucket, None)(defaultRegionClient).runWith(Sink.seq)

    val listingResult = result.futureValue
    listingResult.size shouldBe defaultRegionContentCount
  }

  it should "list with real credentials using the Version 1 API" in {
    val result = S3.listBucket(defaultRegionBucket, None)(version1DefaultRegionClient).runWith(Sink.seq)

    val listingResult = result.futureValue
    listingResult.size shouldBe defaultRegionContentCount
  }

  it should "list with real credentials in non us-east-1 zone" in {
    val result = S3.listBucket(otherRegionBucket, None)(otherRegionClient).runWith(Sink.seq)

    val listingResult = result.futureValue
    listingResult.size shouldBe otherRegionContentCount
  }

  it should "upload with real credentials" in {
    val objectKey = "putTest"
    val bytes = ByteString(objectValue)
    val data = Source.single(ByteString(objectValue))

    val result =
      S3.putObject(defaultRegionBucket, objectKey, data, bytes.length, s3Headers = S3Headers(MetaHeaders(metaHeaders)))(
        defaultRegionClient
      )

    val uploadResult = Await.ready(result, 90.seconds).futureValue
    uploadResult.eTag should not be empty
  }

  it should "upload and delete" in {
    implicit val client = defaultRegionClient
    val objectKey = "putTest"
    val bytes = ByteString(objectValue)
    val data = Source.single(ByteString(objectValue))

    val result = for {
      put <- S3.putObject(defaultRegionBucket,
                          objectKey,
                          data,
                          bytes.length,
                          s3Headers = S3Headers(MetaHeaders(metaHeaders)))
      metaBefore <- S3.getObjectMetadata(defaultRegionBucket, objectKey)
      delete <- S3.deleteObject(defaultRegionBucket, objectKey)
      metaAfter <- S3.getObjectMetadata(defaultRegionBucket, objectKey)
    } yield {
      (put, delete, metaBefore, metaAfter)
    }

    val (putResult, deleteResult, metaBefore, metaAfter) = Await.ready(result, 90.seconds).futureValue
    putResult.eTag should not be empty
    metaBefore should not be empty
    metaBefore.get.contentType shouldBe Some(ContentTypes.`application/octet-stream`.value)
    metaAfter shouldBe empty
  }

  it should "upload multipart with real credentials" in {
    implicit val client = defaultRegionClient

    val source: Source[ByteString, Any] = Source(ByteString(objectValue) :: Nil)
    //val source: Source[ByteString, Any] = FileIO.fromPath(Paths.get("/tmp/IMG_0470.JPG"))

    val result =
      source.runWith(
        S3.multipartUpload(defaultRegionBucket, objectKey, metaHeaders = MetaHeaders(metaHeaders))
      )

    val multipartUploadResult = Await.ready(result, 90.seconds).futureValue
    multipartUploadResult.bucket shouldBe defaultRegionBucket
    multipartUploadResult.key shouldBe objectKey
  }

  it should "download with real credentials" in {
    implicit val client = defaultRegionClient

    val Some((source, meta)) =
      Await.ready(S3.download(defaultRegionBucket, objectKey), 5.seconds).futureValue

    val bodyFuture = source
      .map(_.decodeString("utf8"))
      .toMat(Sink.head)(Keep.right)
      .run()

    val body = Await.ready(bodyFuture, 5.seconds).futureValue
    body shouldBe objectValue
    meta.eTag should not be empty
    meta.contentType shouldBe Some(ContentTypes.`application/octet-stream`.value)
  }

  it should "delete with real credentials" in {
    implicit val client = defaultRegionClient

    val delete = S3.deleteObject(defaultRegionBucket, objectKey)
    delete.futureValue shouldEqual akka.Done
  }

  it should "upload, download and delete with spaces in the key" in {
    implicit val client = defaultRegionClient

    val objectKey = "test folder/test file.txt"
    val source: Source[ByteString, Any] = Source(ByteString(objectValue) :: Nil)

    val results = for {
      upload <- source.runWith(
        S3.multipartUpload(defaultRegionBucket, objectKey, metaHeaders = MetaHeaders(metaHeaders))
      )
      download <- S3.download(defaultRegionBucket, objectKey).flatMap {
        case Some((downloadSource, _)) =>
          downloadSource
            .map(_.decodeString("utf8"))
            .runWith(Sink.head)
            .map(Some.apply)
        case None => Future.successful(None)
      }
    } yield (upload, download)

    val (multipartUploadResult, downloaded) = Await.result(results, 10.seconds)

    multipartUploadResult.bucket shouldBe defaultRegionBucket
    multipartUploadResult.key shouldBe objectKey
    downloaded shouldBe objectValue

    S3.deleteObject(defaultRegionBucket, objectKey).futureValue shouldEqual akka.Done
  }

  it should "upload, download and delete with brackets in the key" in {
    implicit val client = defaultRegionClient

    val objectKey = "abc/DEF/2017/06/15/1234 (1).TXT"
    val source: Source[ByteString, Any] = Source(ByteString(objectValue) :: Nil)

    val results = for {
      upload <- source.runWith(
        S3.multipartUpload(defaultRegionBucket, objectKey, metaHeaders = MetaHeaders(metaHeaders))
      )
      download <- S3.download(defaultRegionBucket, objectKey).flatMap {
        case Some((downloadSource, _)) =>
          downloadSource
            .map(_.decodeString("utf8"))
            .runWith(Sink.head)
            .map(Some.apply)
        case None => Future.successful(None)
      }
    } yield (upload, download)

    val (multipartUploadResult, downloaded) = Await.result(results, 10.seconds)

    multipartUploadResult.bucket shouldBe defaultRegionBucket
    multipartUploadResult.key shouldBe objectKey
    downloaded shouldBe objectValue

    S3.deleteObject(defaultRegionBucket, objectKey).futureValue shouldEqual akka.Done
  }

  it should "upload, download and delete with spaces in the key in non us-east-1 zone" in {
    val objectKey = "test folder/test file.txt"
    val source: Source[ByteString, Any] = Source(ByteString(objectValue) :: Nil)

    val results = for {
      upload <- source.runWith(
        S3.multipartUpload(otherRegionBucket, objectKey, metaHeaders = MetaHeaders(metaHeaders))(
          otherRegionClient
        )
      )
      download <- S3.download(defaultRegionBucket, objectKey)(defaultRegionClient).flatMap {
        case Some((downloadSource, _)) =>
          downloadSource
            .map(_.decodeString("utf8"))
            .runWith(Sink.head)
            .map(Some.apply)
        case None => Future.successful(None)
      }
    } yield (upload, download)

    val (multipartUploadResult, downloaded) = Await.result(results, 10.seconds)

    multipartUploadResult.bucket shouldBe otherRegionBucket
    multipartUploadResult.key shouldBe objectKey
    downloaded shouldBe objectValue

    S3.deleteObject(otherRegionBucket, objectKey)(defaultRegionClient).futureValue shouldEqual akka.Done
  }

  it should "upload, download and delete with special characters in the key in non us-east-1 zone" in {
    // we want ASCII and other UTF-8 characters!
    val objectKey = "føldęrü/1234()[]><!? .TXT"
    val source: Source[ByteString, Any] = Source(ByteString(objectValue) :: Nil)

    val results = for {
      upload <- source.runWith(
        S3.multipartUpload(otherRegionBucket, objectKey, metaHeaders = MetaHeaders(metaHeaders))(
          otherRegionClient
        )
      )
      download <- S3.download(defaultRegionBucket, objectKey)(defaultRegionClient).flatMap {
        case Some((downloadSource, _)) =>
          downloadSource
            .map(_.decodeString("utf8"))
            .runWith(Sink.head)
            .map(Some.apply)
        case None => Future.successful(None)
      }
    } yield (upload, download)

    val (multipartUploadResult, downloaded) = Await.result(results, 10.seconds)

    multipartUploadResult.bucket shouldBe otherRegionBucket
    multipartUploadResult.key shouldBe objectKey
    downloaded shouldBe objectValue

    S3.deleteObject(otherRegionBucket, objectKey)(defaultRegionClient).futureValue shouldEqual akka.Done
  }

  it should "upload, copy, download the copy, and delete" in {
    implicit val client = defaultRegionClient

    val sourceKey = "original/file.txt"
    val targetKey = "copy/file.txt"
    val source: Source[ByteString, Any] = Source(ByteString(objectValue) :: Nil)

    val results = for {
      upload <- source.runWith(S3.multipartUpload(defaultRegionBucket, sourceKey))
      copy <- S3.multipartCopy(defaultRegionBucket, sourceKey, defaultRegionBucket, targetKey)
      download <- S3.download(defaultRegionBucket, objectKey).flatMap {
        case Some((downloadSource, _)) =>
          downloadSource
            .map(_.decodeString("utf8"))
            .runWith(Sink.head)
            .map(Some.apply)
        case None => Future.successful(None)
      }
    } yield (upload, copy, download)

    whenReady(results) {
      case (upload, copy, downloaded) =>
        upload.bucket shouldEqual defaultRegionBucket
        upload.key shouldEqual sourceKey
        copy.bucket shouldEqual defaultRegionBucket
        copy.key shouldEqual targetKey
        downloaded shouldBe objectValue

        S3.deleteObject(defaultRegionBucket, sourceKey).futureValue shouldEqual akka.Done
        S3.deleteObject(defaultRegionBucket, targetKey).futureValue shouldEqual akka.Done
    }
  }
}

/*
 * This is an integration test and ignored by default
 *
 * For running the tests you need to create 2 buckets:
 *  - one in region us-east-1
 *  - one in an other region (eg eu-central-1)
 * Update the bucket name and regions in the code below
 *
 * Set your keys aws access-key-id and secret-access-key in src/test/resources/application.conf
 *
 * Comment @ignore and run the tests
 * (tests that do listing counts might need some tweaking)
 *
 */
@Ignore
class AWSS3IntegrationSpec extends S3IntegrationSpec

/*
 * This is an integration test and ignored by default
 *
 * For this test, you need a local s3 mirror, for instance minio (https://github.com/minio/minio).
 * With docker and the aws cli installed, you could run something like this:
 *
 * docker run -e MINIO_ACCESS_KEY=TESTKEY -e MINIO_SECRET_KEY=TESTSECRET -p 9000:9000 minio/minio server /data
 * AWS_ACCESS_KEY_ID=TESTKEY AWS_SECRET_ACCESS_KEY=TESTSECRET aws --endpoint-url http://localhost:9000 s3api create-bucket --bucket my-test-us-east-1
 * AWS_ACCESS_KEY_ID=TESTKEY AWS_SECRET_ACCESS_KEY=TESTSECRET aws --endpoint-url http://localhost:9000 s3api create-bucket --bucket my.test.frankfurt
 *
 * aws --endpoint-url http://localhost:9000 s3 create-bucket my-test-us-east-1
 * aws cli --endpoint-url http://localhost:9000 s3 create-bucket my.test.frankfurt
 *
 * Comment out @Ignore and run the tests from inside sbt:
 * s3/testOnly akka.stream.alpakka.s3.scaladsl.MinioS3IntegrationSpec
 */
@Ignore
class MinioS3IntegrationSpec extends S3IntegrationSpec {
  val accessKey = "TESTKEY"
  val secret = "TESTSECRET"
  val endpointUrl = "http://localhost:9000"

  val staticProvider = new AWSStaticCredentialsProvider(
    new BasicAWSCredentials(accessKey, secret)
  )

  override val defaultRegionContentCount = 0
  override val otherRegionContentCount = 0

  override def settings = super.settings.copy(
    credentialsProvider = staticProvider,
    endpointUrl = Some(endpointUrl),
    pathStyleAccess = true
  )

  override def otherRegionSettings = super.settings.copy(
    credentialsProvider = staticProvider,
    endpointUrl = Some(endpointUrl),
    pathStyleAccess = true
  )

  it should "properly set the endpointUrl" in {
    settings.endpointUrl.value shouldEqual endpointUrl
  }
}
