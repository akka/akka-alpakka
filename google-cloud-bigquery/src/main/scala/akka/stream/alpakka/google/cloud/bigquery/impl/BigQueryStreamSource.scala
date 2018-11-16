/*
 * Copyright (C) 2016-2018 Lightbend Inc. <http://www.lightbend.com>
 */

package akka.stream.alpakka.google.cloud.bigquery.impl

import akka.NotUsed
import akka.http.scaladsl.HttpExt
import akka.http.scaladsl.model.HttpRequest
import akka.stream._
import akka.stream.alpakka.google.cloud.bigquery.impl.pagetoken.{AddPageToken, EndOfStreamDetector}
import akka.stream.alpakka.google.cloud.bigquery.impl.parser.Parser
import akka.stream.alpakka.google.cloud.bigquery.impl.parser.Parser.PagingInfo
import akka.stream.alpakka.google.cloud.bigquery.impl.sendrequest.SendRequestWithOauthHandling
import akka.stream.alpakka.google.cloud.bigquery.impl.util.{Delay, FlowInitializer, OnFinishCallback}
import akka.stream.scaladsl.{GraphDSL, Source, Zip}
import spray.json.JsObject

import scala.concurrent.ExecutionContext

object BigQueryStreamSource {

  private[bigquery] def callbackConverter(onFinishCallback: PagingInfo => NotUsed): ((Boolean, PagingInfo)) => Unit =
    (t: (Boolean, PagingInfo)) => { onFinishCallback(t._2); {} }

  private[bigquery] def apply[T](httpRequest: HttpRequest,
                                 parserFn: JsObject => Option[T],
                                 onFinishCallback: PagingInfo => NotUsed,
                                 googleSession: GoogleSession,
                                 http: HttpExt,
  )(
      implicit mat: Materializer
  ): Source[T, NotUsed] =
    Source.fromGraph(GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._

      implicit val ec: ExecutionContext = mat.executionContext

      val in = builder.add(Source.repeat(httpRequest))
      val requestSender = builder.add(SendRequestWithOauthHandling(googleSession, http))
      val parser = builder.add(Parser(parserFn))
      val uptreamFinishHandler =
        builder.add(OnFinishCallback[(Boolean, PagingInfo)](callbackConverter(onFinishCallback)))
      val endOfStreamDetector = builder.add(EndOfStreamDetector())
      val flowInitializer = builder.add(FlowInitializer((false, PagingInfo(None, None))))
      val delay = builder.add(Delay[(Boolean, PagingInfo)](_._1, 60))
      val zip = builder.add(Zip[HttpRequest, (Boolean, PagingInfo)]())
      val addPageTokenNode = builder.add(AddPageToken())

      in ~> zip.in0
      requestSender ~> parser.in
      parser.out1 ~> uptreamFinishHandler
      uptreamFinishHandler ~> endOfStreamDetector
      endOfStreamDetector ~> delay
      delay ~> flowInitializer
      flowInitializer ~> zip.in1
      zip.out ~> addPageTokenNode
      addPageTokenNode ~> requestSender

      SourceShape(parser.out0)

    /*
          +--------+           +------------+          +-------+         +------+
          |Request |           |AddPageToken|          |Request|         |Parser|
          |Repeater+---------->+            +--------->+Sender +-------->+      +-----+(response)+----->
          |        |           |            |          |       |         |      |
          +--------+           +-----+------+          +-------+         +---+--+
                                     ^                                       |
                                     |                                       |
                                     |     +-----------+                +----+------+
                                     |     |   Flow    |                | UpStream  |
                                     +<----+Initializer|                |  Finish   |
                                     |     | (single)  |                |  Handler  |
                                     |     +-----------+                +----+------+
                                     |                                       |
                                     |       +-----+       +-----------+     |
                                     |       |Delay|       |EndOfStream|     |
                                     +-------+     +<------+  Detector +<----+
                                             |     |       |           |
                                             +-----+       +-----------+
     */
    })
}
