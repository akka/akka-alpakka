/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package akka.stream.alpakka.csv.javadsl;


import akka.stream.alpakka.csv.CsvToMapJavaStage;
import akka.stream.javadsl.Flow;
import akka.util.ByteString;
import scala.Option;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;

class CsvToMap {

    /**
     * A flow translating incoming {@link Collection<ByteString>} to a {@link Map<String, ByteString>} using the streams first
     * element's values as keys. The charset to decode [[ByteString]] to [[String]] defaults to UTF-8.
     */
    public static Flow<Collection<ByteString>, Map<String, ByteString>, ?> toMap() {
        return toMap(StandardCharsets.UTF_8);
    }

    /**
     * A flow translating incoming {@link Collection<ByteString>} to a {@link Map<String, ByteString>} using the streams first
     * element's values as keys.
     * @param charset the charset to decode {@link ByteString} to {@link String}
     */
    public static Flow<Collection<ByteString>, Map<String, ByteString>, ?> toMap(Charset charset) {
        CsvToMapJavaStage stage = new CsvToMapJavaStage(Option.empty(), charset);
        return Flow.fromGraph(stage).named("csvToMap");
    }

    /**
     * A flow translating incoming {@link Collection<ByteString>} to a {@link Map<String, ByteString>} using the given headers
     * as keys.
     * @param headers column names to be used as map keys
     */
    public static Flow<Collection<ByteString>, Map<String, ByteString>, ?> withHeaders(String... headers) {
        CsvToMapJavaStage stage = new CsvToMapJavaStage(Option.apply(Arrays.asList(headers)), StandardCharsets.UTF_8);
        return Flow.fromGraph(stage).named("csvToMap");
    }
}
