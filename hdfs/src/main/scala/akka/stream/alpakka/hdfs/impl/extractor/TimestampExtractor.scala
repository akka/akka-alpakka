/*
 * Copyright (C) 2016-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.stream.alpakka.hdfs.impl.extractor

import akka.annotation.InternalApi

/**
 * Internal API
 */
@InternalApi
private[hdfs] trait TimestampExtractor {
  def extract(source: Any): Long
}

abstract class DefaultTimestampExtractor extends TimestampExtractor {
  override def extract(source: Any): Long
}

class NoTimestampExtractor extends DefaultTimestampExtractor {
  override def extract(source: Any): Long = ???
}

object TimestampExtractor {
  def none: TimestampExtractor = new NoTimestampExtractor
}
