package akka.stream.alpakka.csv

import akka.stream.alpakka.csv.scaladsl.CsvFraming
import akka.util.ByteString
import org.scalatest.{Matchers, WordSpec}

class CsvFormatterSpec extends WordSpec with Matchers {

  "CSV Formatter comma as delimiter" should {
    val formatter = new CsvFormatter(',', '\"', '\\', ByteString("\r\n"), CsvQuotingStyle.REQUIRED)

    "format Strings" in {
      expectInOut(formatter, "ett", "två", "tre")("ett,två,tre\r\n")
    }

    "format Strings containing commas" in {
      expectInOut(formatter, "ett", "t,vå", "tre")("ett,\"t,vå\",tre\r\n")
    }

    "format Strings containing quotes" in {
      expectInOut(formatter, "ett", "t\"vå", "tre")("ett,\"t\"\"vå\",tre\r\n")
    }

  }

  "CSV Formatter quoting everything" should {
    val formatter = new CsvFormatter(',', '\"', '\\', ByteString("\r\n"), CsvQuotingStyle.ALWAYS)

    "format Strings" in {
      expectInOut(formatter, "ett", "två", "tre")(""""ett","två","tre"""" + "\r\n")
    }

    "format Strings with commas" in {
      expectInOut(formatter, "ett", "t,vå", "tre")(""""ett","t,vå","tre"""" + "\r\n")
    }

    "format Strings containing quotes" in {
      expectInOut(formatter, "ett", "t\"vå", "tre")(""""ett","t""vå","tre"""" + "\r\n")
    }

    "format Strings containing quotes twice" in {
      expectInOut(formatter, "ett", "t\"v\"å", "tre")(""""ett","t""v""å","tre"""" + "\r\n")
    }

  }

  "CSV Formatter with required quoting" should {
    val formatter = new CsvFormatter(';', '\"', '\\', ByteString("\r\n"), CsvQuotingStyle.REQUIRED)

    "format Strings" in {
      expectInOut(formatter, "ett", "två", "tre")("ett;två;tre\r\n")
    }

    "quote Strings with delimiters" in {
      expectInOut(formatter, "ett", "t;vå", "tre")("ett;\"t;vå\";tre\r\n")
    }

    "quote Strings with quotes" in {
      expectInOut(formatter, "ett", "t\"vå", "tre")("""ett;"t""vå";tre""" + "\r\n")
    }

    "quote Strings with quote at end" in {
      expectInOut(formatter, "ett", "två\"", "tre")("ett;\"två\"\"\";tre\r\n")
    }

    "quote Strings with just a quote" in {
      expectInOut(formatter, "ett", "\"", "tre")("ett;\"\"\"\";tre\r\n")
    }

    "quote Strings containing LF" in {
      expectInOut(formatter, "ett", "\n", "tre")("ett;\"\n\";tre\r\n")
    }

    "quote Strings containing CR, LF" in {
      expectInOut(formatter, "ett", "prefix\r\npostfix", "tre")("ett;\"prefix\r\npostfix\";tre\r\n")
    }

    "duplicate escape char" in {
      expectInOut(formatter, "ett", "prefix\\postfix", "tre")("ett;\"prefix\\\\postfix\";tre\r\n")
    }

    "duplicate escape chars and quotes" in {
      expectInOut(formatter, "ett", "one\\two\"three\\four", "tre")("ett;\"one\\\\two\"\"three\\\\four\";tre\r\n")
    }
  }

  def expectInOut(formatter: CsvFormatter, in: String*)(expect: String): Unit =
    formatter.toCsv(in.toList).utf8String should be(expect)

}
