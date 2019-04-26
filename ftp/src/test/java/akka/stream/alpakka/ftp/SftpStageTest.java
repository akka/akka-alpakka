/*
 * Copyright (C) 2016-2019 Lightbend Inc. <http://www.lightbend.com>
 */

package akka.stream.alpakka.ftp;

import akka.NotUsed;
import akka.stream.IOResult;
import akka.stream.alpakka.ftp.javadsl.Sftp;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import akka.util.ByteString;
import org.junit.Test;

import java.net.InetAddress;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

public class SftpStageTest extends BaseSftpSupport implements CommonStageTest {

  @Test
  public void listFiles() throws Exception {
    CommonStageTest.super.listFiles();
  }

  @Test
  public void fromPath() throws Exception {
    CommonStageTest.super.fromPath();
  }

  @Test
  public void toPath() throws Exception {
    CommonStageTest.super.toPath();
  }

  @Test
  public void remove() throws Exception {
    CommonStageTest.super.remove();
  }

  @Test
  public void move() throws Exception {
    CommonStageTest.super.move();
  }

  public Source<FtpFile, NotUsed> getBrowserSource(String basePath) throws Exception {
    return Sftp.ls(ROOT_PATH + basePath, settings());
  }

  public Source<ByteString, CompletionStage<IOResult>> getIOSource(String path) throws Exception {
    return Sftp.fromPath(ROOT_PATH + path, settings());
  }

  public Sink<ByteString, CompletionStage<IOResult>> getIOSink(String path) throws Exception {
    return Sftp.toPath(ROOT_PATH + path, settings());
  }

  public Sink<FtpFile, CompletionStage<IOResult>> getRemoveSink() throws Exception {
    return Sftp.remove(settings());
  }

  public Sink<FtpFile, CompletionStage<IOResult>> getMoveSink(
      Function<FtpFile, String> destinationPath) throws Exception {
    return Sftp.move(f -> ROOT_PATH + destinationPath.apply(f), settings());
  }

  private SftpSettings settings() throws Exception {
    return SftpSettings.create(InetAddress.getByName(HOSTNAME))
        .withPort(PORT)
        .withCredentials(CREDENTIALS)
        .withStrictHostKeyChecking(false);
  }
}
