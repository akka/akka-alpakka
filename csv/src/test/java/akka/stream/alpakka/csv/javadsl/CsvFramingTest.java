package akka.stream.alpakka.csv.javadsl;

import akka.NotUsed;
import akka.actor.ActorSystem;
import akka.stream.ActorMaterializer;
import akka.stream.Materializer;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import akka.testkit.JavaTestKit;
import akka.util.ByteString;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Collection;
import java.util.concurrent.CompletionStage;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;

public class CsvFramingTest {
    private static ActorSystem system;
    private static Materializer materializer;

    public void documentation() {
        // #flow-type
        Flow<ByteString, Collection<ByteString>, NotUsed> flow
                = CsvFraming.lineScanner();
        // #flow-type
    }

    @Test
    public void lineParserShouldParseOneLine() {
        CompletionStage<Collection<ByteString>> completionStage =
        // #line-scanner
            Source.single(ByteString.fromString("eins,zwei,drei\n"))
                .via(CsvFraming.lineScanner())
                .runWith(Sink.head(), materializer);
        // #line-scanner
        completionStage.thenAccept((list) -> {
            String[] res = list.stream().map(ByteString::utf8String).toArray(String[]::new);
            assertThat(res[0], equalTo("eins"));
            assertThat(res[1], equalTo("zwei"));
            assertThat(res[2], equalTo("drei"));
        });

    }

    @BeforeClass
    public static void setup() throws Exception {
        system = ActorSystem.create();
        materializer = ActorMaterializer.create(system);
    }

    @AfterClass
    public static void teardown() throws Exception {
        JavaTestKit.shutdownActorSystem(system);
    }
}
