/*
 * Copyright (C) 2016-2017 Lightbend Inc. <http://www.lightbend.com>
 */
package akka.stream.alpakka.csv.javadsl;

import akka.NotUsed;
import akka.stream.alpakka.csv.scaladsl.CsvQuotingStyle$;
import akka.stream.javadsl.Flow;
import akka.util.ByteString;
import scala.Option;
import scala.Some;
import scala.collection.JavaConversions;
import scala.collection.immutable.List;

import java.util.Collection;
import java.util.Optional;

/**
 * Provides CSV formatting flows that convert a sequence of String into their CSV representation
 * in {@see akka.util.ByteString}.
 */
public class CsvFormatting {

    public static final char BACKSLASH = '\\';
    public static final char COMMA = ',';
    public static final char SEMI_COLON = ';';
    public static final char COLON = ':';
    public static final char TAB = '\t';
    public static final char DOUBLE_QUOTE = '"';
    public static final String CR_LF = "\r\n";

    /**
     * Generates standard CSV format (with commas).
     *
     * @param <T> Any collection implementation
     * @return The formatting flow
     */
    public static <T extends Collection<String>> Flow<T, ByteString, NotUsed> format() {
        return format(COMMA, DOUBLE_QUOTE, BACKSLASH, CR_LF, CsvQuotingStyle.REQUIRED, ByteString.UTF_8(), Optional.empty());
    }

    /**
     * Generates CSV with the specified special characters and character set.
     *
     * @param delimiter    Delimiter between columns
     * @param quoteChar    Quoting character
     * @param escapeChar   Escape character
     * @param endOfLine    End of line character sequence
     * @param quotingStyle Quote all values or as required
     * @param charsetName  Character set to be used
     * @param <T>          Any collection implementation
     * @return The formatting flow
     */
    public static <T extends Collection<String>> Flow<T, ByteString, NotUsed> format(char delimiter, char quoteChar, char escapeChar, String endOfLine, CsvQuotingStyle quotingStyle, String charsetName, Optional<ByteString> byteOrderMark) {
        akka.stream.alpakka.csv.scaladsl.CsvQuotingStyle qs = CsvQuotingStyle$.MODULE$.asScala(quotingStyle);
        Option byteOrderMarkScala = byteOrderMark.<Option>map(Some::apply).orElse(Option.<ByteString>empty());
        akka.stream.scaladsl.Flow<List<String>, ByteString, NotUsed> formattingFlow
            = akka.stream.alpakka.csv.scaladsl.CsvFormatting
                .format(delimiter, quoteChar, escapeChar, endOfLine, qs, charsetName, byteOrderMarkScala);
        return Flow.<T>create()
                .map(c -> JavaConversions.collectionAsScalaIterable(c).toList())
                .via(formattingFlow);
    }
}
