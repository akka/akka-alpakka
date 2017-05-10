/*
 * Copyright (C) 2016-2017 Lightbend Inc. <http://www.lightbend.com>
 */
package akka.stream.alpakka.s3.impl

import akka.actor.ActorSystem
import akka.http.scaladsl.model.Uri.Query
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.model.{HttpEntity, MediaTypes}
import akka.stream.alpakka.s3.S3Settings
import akka.stream.alpakka.s3.acl.CannedAcl
import akka.stream.scaladsl.Source
import com.typesafe.config.ConfigFactory
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{FlatSpec, Matchers}

class HttpRequestsSpec extends FlatSpec with Matchers with ScalaFutures {

  // test fixtures
  val pathStyleAcessConfig =
    """
     |buffer = "memory"
     |disk-buffer-path = ""
     |debug-logging = false
     |proxy {
     |  host = ""
     |  port = 8080
     |}
     |aws {
     |  access-key-id = ""
     |  secret-access-key = ""
     |  default-region = "us-east-1"
     |}
     |path-style-access = true
   """.stripMargin

  val proxyConfig =
    """
      |buffer = "memory"
      |disk-buffer-path = ""
      |debug-logging = false
      |proxy {
      |  host = "localhost"
      |  port = 8080
      |  secure = false
      |}
      |aws {
      |  access-key-id = ""
      |  secret-access-key = ""
      |  default-region = "us-east-1"
      |}
      |path-style-access = false
    """.stripMargin

  val location = S3Location("bucket", "image-1024@2x")
  val contentType = MediaTypes.`image/jpeg`
  val acl = CannedAcl.PublicRead
  val metaHeaders: Map[String, String] = Map("location" -> "San Francisco", "orientation" -> "portrait")
  val multipartUpload = MultipartUpload(S3Location("testBucket", "testKey"), "uploadId")

  it should "initiate multipart upload when the region is us-east-1" in {
    implicit val settings = S3Settings(ActorSystem())

    val req =
      HttpRequests.initiateMultipartUploadRequest(location,
                                                  contentType,
                                                  "us-east-1",
                                                  S3Headers(acl, MetaHeaders(metaHeaders)))

    req.entity shouldEqual HttpEntity.empty(contentType)
    req.headers should contain(RawHeader("x-amz-acl", acl.value))
    req.uri.authority.host.toString shouldEqual "bucket.s3.amazonaws.com"
    req.uri.path.toString shouldEqual "/image-1024@2x"

    metaHeaders.map { m =>
      req.headers should contain(RawHeader(s"x-amz-meta-${m._1}", m._2))
    }
  }

  it should "initiate multipart upload with other regions" in {
    implicit val settings = S3Settings(ActorSystem())

    val req =
      HttpRequests.initiateMultipartUploadRequest(location,
                                                  contentType,
                                                  "us-east-2",
                                                  S3Headers(acl, MetaHeaders(metaHeaders)))

    req.entity shouldEqual HttpEntity.empty(contentType)
    req.headers should contain(RawHeader("x-amz-acl", acl.value))
    req.uri.authority.host.toString shouldEqual "bucket.s3-us-east-2.amazonaws.com"
    req.uri.path.toString shouldEqual "/image-1024@2x"

    metaHeaders.map { m =>
      req.headers should contain(RawHeader(s"x-amz-meta-${m._1}", m._2))
    }
  }

  it should "initiate multipart upload with path-style access in region us-east-1" in {
    implicit val settings = S3Settings(ConfigFactory.parseString(pathStyleAcessConfig))

    val req =
      HttpRequests.initiateMultipartUploadRequest(location,
                                                  contentType,
                                                  "us-east-1",
                                                  S3Headers(acl, MetaHeaders(metaHeaders)))

    req.uri.authority.host.toString shouldEqual "s3.amazonaws.com"
    req.uri.path.toString shouldEqual "/bucket/image-1024@2x"
  }

  it should "support download requests with path-style access in region us-east-1" in {
    implicit val settings = S3Settings(ConfigFactory.parseString(pathStyleAcessConfig))

    val req = HttpRequests.getDownloadRequest(location, "us-east-1")

    req.uri.authority.host.toString shouldEqual "s3.amazonaws.com"
    req.uri.path.toString shouldEqual "/bucket/image-1024@2x"
  }

  it should "initiate multipart upload with path-style access in other regions" in {
    implicit val settings = S3Settings(ConfigFactory.parseString(pathStyleAcessConfig))

    val req =
      HttpRequests.initiateMultipartUploadRequest(location,
                                                  contentType,
                                                  "us-west-2",
                                                  S3Headers(acl, MetaHeaders(metaHeaders)))

    req.uri.authority.host.toString shouldEqual "s3-us-west-2.amazonaws.com"
    req.uri.path.toString shouldEqual "/bucket/image-1024@2x"

  }

  it should "support download requests with path-style access in other regions" in {
    implicit val settings = S3Settings(ConfigFactory.parseString(pathStyleAcessConfig))

    val req = HttpRequests.getDownloadRequest(location, "eu-west-1")

    req.uri.authority.host.toString shouldEqual "s3-eu-west-1.amazonaws.com"
    req.uri.path.toString shouldEqual "/bucket/image-1024@2x"
  }

  it should "support download requests via HTTP when such scheme configured for `proxy`" in {
    implicit val settings = S3Settings(ConfigFactory.parseString(proxyConfig))

    val req = HttpRequests.getDownloadRequest(location, "region")

    req.uri.scheme shouldEqual "http"
  }

  it should "support multipart init upload requests via HTTP when such scheme configured for `proxy`" in {
    implicit val settings = S3Settings(ConfigFactory.parseString(proxyConfig))

    val req =
      HttpRequests.initiateMultipartUploadRequest(location,
                                                  contentType,
                                                  "region",
                                                  S3Headers(acl, MetaHeaders(metaHeaders)))

    req.uri.scheme shouldEqual "http"
  }

  it should "support multipart upload part requests via HTTP when such scheme configured for `proxy`" in {
    implicit val settings = S3Settings(ConfigFactory.parseString(proxyConfig))

    val req =
      HttpRequests.uploadPartRequest(multipartUpload, 1, Source.empty, 1, "region")

    req.uri.scheme shouldEqual "http"
  }

  it should "support multipart upload complete requests via HTTP when such scheme configured for `proxy`" in {
    implicit val settings = S3Settings(ConfigFactory.parseString(proxyConfig))
    implicit val executionContext = scala.concurrent.ExecutionContext.global

    val reqFuture =
      HttpRequests.completeMultipartUploadRequest(multipartUpload, (1, "part") :: Nil, "region")

    reqFuture.futureValue.uri.scheme shouldEqual "http"
  }

  it should "initiate multipart upload with AES-256 server side encryption" in {
    implicit val settings = S3Settings(ActorSystem())
    val s3Headers = S3Headers(ServerSideEncryption.AES256)
    val req = HttpRequests.initiateMultipartUploadRequest(location, contentType, "us-east-2", s3Headers)

    req.headers should contain(RawHeader("x-amz-server-side-encryption", "AES256"))
  }

  it should "initiate multipart upload with aws:kms server side encryption" in {
    implicit val settings = S3Settings(ActorSystem())
    val testArn = "arn:aws:kms:my-region:my-account-id:key/my-key-id"
    val s3Headers = S3Headers(ServerSideEncryption.KMS(testArn))
    val req = HttpRequests.initiateMultipartUploadRequest(location, contentType, "us-east-2", s3Headers)

    req.headers should contain(RawHeader("x-amz-server-side-encryption", "aws:kms"))
    req.headers should contain(RawHeader("x-amz-server-side-encryption-aws-kms-key-id", testArn))
  }

  it should "initiate multipart upload with custom s3 storage class" in {
    implicit val settings = S3Settings(ActorSystem())
    val s3Headers = S3Headers(StorageClass.ReducedRedundancy)
    val req = HttpRequests.initiateMultipartUploadRequest(location, contentType, "us-east-2", s3Headers)

    req.headers should contain(RawHeader("x-amz-storage-class", "REDUCED_REDUNDANCY"))
  }

  it should "initiate multipart upload with custom s3 headers" in {
    implicit val settings = S3Settings(ActorSystem())
    val s3Headers = S3Headers(Map("Cache-Control" -> "no-cache"))
    val req = HttpRequests
      .initiateMultipartUploadRequest(location, contentType, "us-east-2", s3Headers)

    req.headers should contain(RawHeader("Cache-Control", "no-cache"))
  }

  it should "properly construct the list bucket request with no prefix or continuation token passed" in {
    implicit val settings = S3Settings(ConfigFactory.parseString(pathStyleAcessConfig))

    val req =
      HttpRequests.listBucket(location.bucket, "region")

    req.uri.query() shouldEqual Query("list-type" -> "2")
  }

  it should "properly construct the list bucket request with a prefix and token passed" in {
    implicit val settings = S3Settings(ConfigFactory.parseString(pathStyleAcessConfig))

    val req =
      HttpRequests.listBucket(location.bucket, "region", Some("random/prefix"), Some("randomToken"))

    req.uri.query() shouldEqual Query("list-type" -> "2",
                                      "prefix" -> "random/prefix",
                                      "continuation-token" -> "randomToken")
  }
}
