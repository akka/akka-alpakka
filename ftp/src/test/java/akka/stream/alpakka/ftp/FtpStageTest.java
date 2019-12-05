/*
 * Copyright (C) 2016-2019 Lightbend Inc. <http://www.lightbend.com>
 */

package akka.stream.alpakka.ftp;

import akka.NotUsed;
import akka.stream.IOResult;
import akka.stream.alpakka.ftp.javadsl.Ftp;
import akka.stream.alpakka.testkit.javadsl.LogCapturingJunit4;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import akka.util.ByteString;
import org.junit.Rule;
import org.junit.Test;
import java.net.InetAddress;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

public class FtpStageTest extends BaseFtpSupport implements CommonFtpStageTest {

  @Rule
  public final LogCapturingJunit4 logCapturing = new LogCapturingJunit4();

  @Test
  public void listFiles() throws Exception {
    CommonFtpStageTest.super.listFiles();
  }

  @Test
  public void fromPath() throws Exception {
    CommonFtpStageTest.super.fromPath();
  }

  @Test
  public void toPath() throws Exception {
    CommonFtpStageTest.super.toPath();
  }

  @Test
  public void remove() throws Exception {
    CommonFtpStageTest.super.remove();
  }

  @Test
  public void move() throws Exception {
    CommonFtpStageTest.super.move();
  }

  public Source<FtpFile, NotUsed> getBrowserSource(String basePath) throws Exception {
    return Ftp.ls(basePath, settings());
  }

  public Source<ByteString, CompletionStage<IOResult>> getIOSource(String path) throws Exception {
    return Ftp.fromPath(path, settings());
  }

  public Sink<ByteString, CompletionStage<IOResult>> getIOSink(String path) throws Exception {
    return Ftp.toPath(path, settings());
  }

  public Sink<FtpFile, CompletionStage<IOResult>> getRemoveSink() throws Exception {
    return Ftp.remove(settings());
  }

  public Sink<FtpFile, CompletionStage<IOResult>> getMoveSink(
      Function<FtpFile, String> destinationPath) throws Exception {
    return Ftp.move(destinationPath, settings());
  }

  private FtpSettings settings() throws Exception {
    return FtpSettings.create(InetAddress.getByName(HOSTNAME))
        .withPort(PORT)
        .withCredentials(CREDENTIALS)
        .withBinary(false)
        .withPassiveMode(true);
  }
}
