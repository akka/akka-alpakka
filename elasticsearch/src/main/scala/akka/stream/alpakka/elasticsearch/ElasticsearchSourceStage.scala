/*
 * Copyright (C) 2016-2017 Lightbend Inc. <http://www.lightbend.com>
 */
package akka.stream.alpakka.elasticsearch

import java.io.ByteArrayOutputStream

import akka.stream.{Attributes, Outlet, SourceShape}
import akka.stream.stage.{GraphStage, GraphStageLogic, OutHandler}
import org.apache.http.entity.StringEntity
import org.elasticsearch.client.{Response, ResponseListener, RestClient}
import spray.json._
import DefaultJsonProtocol._

import scala.collection.JavaConverters._

final case class ElasticsearchSourceSettings(bufferSize: Int = 10)

final case class OutgoingMessage[T](id: String, source: T)

final class ElasticsearchSourceStage[T](indexName: String,
                                        typeName: String,
                                        query: String,
                                        client: RestClient,
                                        settings: ElasticsearchSourceSettings)(implicit reader: JsonReader[T])
    extends GraphStage[SourceShape[OutgoingMessage[T]]] {

  val out: Outlet[OutgoingMessage[T]] = Outlet("ElasticsearchSource.out")
  override val shape: SourceShape[OutgoingMessage[T]] = SourceShape(out)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new ElasticsearchSourceLogic[T](indexName, typeName, query, client, settings, out, shape)

}

sealed class ElasticsearchSourceLogic[T](indexName: String,
                                         typeName: String,
                                         query: String,
                                         client: RestClient,
                                         settings: ElasticsearchSourceSettings,
                                         out: Outlet[OutgoingMessage[T]],
                                         shape: SourceShape[OutgoingMessage[T]])(implicit reader: JsonReader[T])
    extends GraphStageLogic(shape)
    with ResponseListener {

  private var started = false
  private var scrollId: String = null
  private val responseHandler = getAsyncCallback[Response](handleResponse)
  private val failureHandler = getAsyncCallback[Throwable](handleFailure)

  def sendScrollScanRequest(): Unit =
    try {
      if (scrollId == null) {
        client.performRequestAsync(
          "POST",
          s"$indexName/$typeName/_search",
          Map("scroll" -> "5m", "sort" -> "_doc").asJava,
          new StringEntity(s"""{"size": ${settings.bufferSize}, "query": ${query}}"""),
          this
        )
      } else {
        client.performRequestAsync(
          "POST",
          s"/_search/scroll",
          Map[String, String]().asJava,
          new StringEntity(Map("scroll" -> "5m", "scroll_id" -> scrollId).toJson.toString),
          this
        )
      }
    } catch {
      case ex: Exception => handleFailure(ex)
    }

  override def onFailure(exception: Exception) = failureHandler.invoke(exception)
  override def onSuccess(response: Response) = responseHandler.invoke(response)

  def handleFailure(ex: Throwable): Unit =
    failStage(ex)

  def handleResponse(res: Response): Unit = {
    val json = {
      val out = new ByteArrayOutputStream()
      try {
        res.getEntity.writeTo(out)
        new String(out.toByteArray, "UTF-8")
      } finally {
        out.close()
      }
    }

    val jsObj = json.parseJson.asJsObject

    jsObj.fields.get("error") match {
      case None => {
        val hits = jsObj.fields("hits").asJsObject.fields("hits").asInstanceOf[JsArray]
        if (hits.elements.isEmpty && scrollId != null) {
          completeStage()
        } else {
          scrollId = jsObj.fields("_scroll_id").asInstanceOf[JsString].value
          val messages = hits.elements.reverse.map { element =>
            val doc = element.asJsObject
            val id = doc.fields("_id").asInstanceOf[JsString].value
            val source = doc.fields("_source").asJsObject
            OutgoingMessage(id, source.convertTo[T])
          }
          emitMultiple(out, messages)
          sendScrollScanRequest()
        }
      }
      case Some(error) => {
        failStage(new IllegalStateException(error.toString))
      }
    }
  }

  setHandler(out, new OutHandler {
    override def onPull(): Unit =
      if (!started) {
        started = true
        sendScrollScanRequest()
      }
  })

}
