/*
 * Copyright (C) 2016-2018 Lightbend Inc. <http://www.lightbend.com>
 */

package akka.stream.alpakka.solr.scaladsl

import akka.stream.alpakka.solr.{SolrUpdateSettings, WriteMessage}
import akka.stream.scaladsl.{Keep, Sink}
import akka.{Done, NotUsed}
import org.apache.solr.client.solrj.SolrClient
import org.apache.solr.common.SolrInputDocument

import scala.concurrent.Future

object SolrSink {

  /**
   * Scala API: creates a [[SolrFlow]] to Solr for [[WriteMessage]] containing [[SolrInputDocument]].
   *
   * @deprecated ("use the method documents to batch operation")
   */
  def document[T](collection: String, settings: SolrUpdateSettings)(
      implicit client: SolrClient
  ): Sink[WriteMessage[SolrInputDocument, NotUsed], Future[Done]] =
    SolrFlow
      .document(collection, settings)
      .toMat(Sink.ignore)(Keep.right)

  /**
   * Scala API: creates a [[SolrFlow]] to Solr for [[WriteMessage]] containing type `T`
   * with [[org.apache.solr.client.solrj.beans.DocumentObjectBinder]].
   *
   * @deprecated ("use the method beans to batch operation","0.20")
   */
  def bean[T](collection: String, settings: SolrUpdateSettings)(
      implicit client: SolrClient
  ): Sink[WriteMessage[T, NotUsed], Future[Done]] =
    SolrFlow
      .bean[T](collection, settings)
      .toMat(Sink.ignore)(Keep.right)

  /**
   * Scala API: creates a [[SolrFlow]] to Solr for sequence of [[WriteMessage]] containing type `T` with `binder` of type 'T'.
   *
   * @deprecated ("use the method typeds to batch operation","0.20")
   */
  def typed[T](
      collection: String,
      settings: SolrUpdateSettings,
      binder: T => SolrInputDocument
  )(implicit client: SolrClient): Sink[WriteMessage[T, NotUsed], Future[Done]] =
    SolrFlow
      .typed[T](collection, settings, binder)
      .toMat(Sink.ignore)(Keep.right)

  /**
   * Scala API: creates a [[SolrFlow]] to Solr for sequence of [[WriteMessage]] containing [[SolrInputDocument]].
   */
  def documents[T](collection: String, settings: SolrUpdateSettings)(
      implicit client: SolrClient
  ): Sink[Seq[WriteMessage[SolrInputDocument, NotUsed]], Future[Done]] =
    SolrFlow
      .documents(collection, settings)
      .toMat(Sink.ignore)(Keep.right)

  /**
   * Scala API: creates a [[SolrFlow]] to Solr for sequence of [[WriteMessage]] containing type `T`
   * with [[org.apache.solr.client.solrj.beans.DocumentObjectBinder]].
   */
  def beans[T](collection: String, settings: SolrUpdateSettings)(
      implicit client: SolrClient
  ): Sink[Seq[WriteMessage[T, NotUsed]], Future[Done]] =
    SolrFlow
      .beans[T](collection, settings)
      .toMat(Sink.ignore)(Keep.right)

  /**
   * Scala API: creates a [[SolrFlow]] to Solr for sequence of [[WriteMessage]] containing type `T` with `binder` of type 'T'.
   */
  def typeds[T](
      collection: String,
      settings: SolrUpdateSettings,
      binder: T => SolrInputDocument
  )(implicit client: SolrClient): Sink[Seq[WriteMessage[T, NotUsed]], Future[Done]] =
    SolrFlow
      .typeds[T](collection, settings, binder)
      .toMat(Sink.ignore)(Keep.right)

}
