/*
 * Copyright (C) since 2016 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.stream.alpakka
package azure

import java.time.{ZoneOffset, ZonedDateTime}
import java.time.format.DateTimeFormatter

package object storage {

  private[storage] val NewLine: String = "\n"
  private[storage] val AuthorizationHeaderKey = "Authorization"
  private[storage] val XmsDateHeaderKey = "x-ms-date"
  private[storage] val XmsVersionHeaderKey = "x-ms-version"
  private[storage] val BlobTypeHeaderKey = "x-ms-blob-type"
  private[storage] val LeaseIdHeaderKey = "x-ms-lease-id"
  private[storage] val FileWriteTypeHeaderKey = "x-ms-write"
  private[storage] val XMsContentLengthHeaderKey = "x-ms-content-length"
  private[storage] val FileTypeHeaderKey = "x-ms-type"
  private[storage] val BlobType = "blob"
  private[storage] val FileType = "file"

  private[storage] def getFormattedDate: String =
    DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.now(ZoneOffset.UTC))

  /** Removes ETag quotes in the same way the official AWS tooling does. See
   */
  private[storage] def removeQuotes(string: String): String = {
    val trimmed = string.trim()
    val tail = if (trimmed.startsWith("\"")) trimmed.drop(1) else trimmed
    if (tail.endsWith("\"")) tail.dropRight(1) else tail
  }

  /** This method returns `None` if given an empty `String`. This is typically used when parsing XML since its common to
   * have XML elements with an empty text value inside.
   */
  private[storage] def emptyStringToOption(value: String): Option[String] = if (value == "") None else Some(value)
}
