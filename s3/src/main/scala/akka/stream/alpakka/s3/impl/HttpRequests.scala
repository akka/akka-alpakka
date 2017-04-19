/*
 * Copyright (C) 2016-2017 Lightbend Inc. <http://www.lightbend.com>
 */
package akka.stream.alpakka.s3.impl

import akka.http.scaladsl.marshallers.xml.ScalaXmlSupport._
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model.Uri.Query
import akka.http.scaladsl.model.headers.Host
import akka.http.scaladsl.model.{ContentTypes, RequestEntity, _}
import akka.stream.alpakka.s3.S3Settings
import akka.stream.scaladsl.Source
import akka.util.ByteString

import scala.collection.immutable.Seq
import scala.concurrent.{ExecutionContext, Future}

private[alpakka] object HttpRequests {

  def getDownloadRequest(s3Location: S3Location, region: String)(implicit conf: S3Settings): HttpRequest =
    s3Request(s3Location, region: String)

  def initiateMultipartUploadRequest(s3Location: S3Location,
                                     contentType: ContentType,
                                     region: String,
                                     s3Headers: S3Headers)(implicit conf: S3Settings): HttpRequest =
    s3Request(s3Location, region, HttpMethods.POST, _.withQuery(Query("uploads")))
      .withDefaultHeaders(s3Headers.headers: _*)
      .withEntity(HttpEntity.empty(contentType))

  def uploadPartRequest(upload: MultipartUpload,
                        partNumber: Int,
                        payload: Source[ByteString, _],
                        payloadSize: Int,
                        region: String)(implicit conf: S3Settings): HttpRequest =
    s3Request(
      upload.s3Location,
      region,
      HttpMethods.PUT,
      _.withQuery(Query("partNumber" -> partNumber.toString, "uploadId" -> upload.uploadId))
    ).withEntity(HttpEntity(ContentTypes.`application/octet-stream`, payloadSize, payload))

  def completeMultipartUploadRequest(upload: MultipartUpload, parts: Seq[(Int, String)], region: String)(
      implicit ec: ExecutionContext,
      conf: S3Settings
  ): Future[HttpRequest] = {

    //Do not let the start PartNumber,ETag and the end PartNumber,ETag be on different lines
    //  They tend to get split when this file is formatted by IntelliJ unless http://stackoverflow.com/a/19492318/1216965
    // @formatter:off
    val payload = <CompleteMultipartUpload>
                    {
                      parts.map { case (partNumber, etag) => <Part><PartNumber>{ partNumber }</PartNumber><ETag>{ etag }</ETag></Part> }
                    }
                  </CompleteMultipartUpload>
    // @formatter:on
    for {
      entity <- Marshal(payload).to[RequestEntity]
    } yield {
      s3Request(
        upload.s3Location,
        region,
        HttpMethods.POST,
        _.withQuery(Query("uploadId" -> upload.uploadId))
      ).withEntity(entity)
    }
  }

  private[this] def s3Request(s3Location: S3Location,
                              region: String,
                              method: HttpMethod = HttpMethods.GET,
                              uriFn: (Uri => Uri) = identity)(implicit conf: S3Settings): HttpRequest = {

    def requestHost(s3Location: S3Location, region: String)(implicit conf: S3Settings): Uri.Host =
      conf.proxy match {
        case None =>
          region match {
            case "us-east-1" =>
              if (conf.pathStyleAccess) {
                Uri.Host("s3.amazonaws.com")
              } else {
                Uri.Host(s"${s3Location.bucket}.s3.amazonaws.com")
              }
            case _ =>
              if (conf.pathStyleAccess) {
                Uri.Host(s"s3-$region.amazonaws.com")
              } else {
                Uri.Host(s"${s3Location.bucket}.s3-$region.amazonaws.com")
              }
          }
        case Some(proxy) => Uri.Host(proxy.host)
      }

    def requestUri(s3Location: S3Location, region: String)(implicit conf: S3Settings): Uri = {
      val uri = if (conf.pathStyleAccess) {
        Uri(s"/${s3Location.bucket}/${s3Location.key}").withHost(requestHost(s3Location, region))
      } else {
        Uri(s"/${s3Location.key}").withHost(requestHost(s3Location, region))
      }
      conf.proxy match {
        case None => uri.withScheme("https")
        case Some(proxy) => uri.withPort(proxy.port).withScheme(proxy.scheme)
      }
    }

    HttpRequest(method)
      .withHeaders(Host(requestHost(s3Location, region)))
      .withUri(uriFn(requestUri(s3Location, region)))
  }
}
