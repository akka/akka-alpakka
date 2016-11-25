/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package akka.stream.alpakka.ftp.javadsl

import akka.NotUsed
import akka.stream.alpakka.ftp.impl._
import akka.stream.alpakka.ftp.{ FtpFile, RemoteFileSettings }
import akka.stream.alpakka.ftp.impl.{ FtpLike, FtpSourceFactory }
import akka.stream.IOResult
import akka.stream.javadsl.Source
import akka.stream.scaladsl.{ Source => ScalaSource }
import akka.util.ByteString
import com.jcraft.jsch.JSch
import org.apache.commons.net.ftp.FTPClient
import java.nio.file.Path
import java.util.concurrent.CompletionStage

trait FtpApi[FtpClient] { _: FtpSourceFactory[FtpClient] =>

  /**
   * Java API: creates a [[Source]] of [[FtpFile]]s from the remote user `root` directory.
   * By default, `anonymous` credentials will be used.
   *
   * @param host FTP, FTPs or SFTP host
   * @return A [[Source]] of [[FtpFile]]s
   */
  def ls(host: String): Source[FtpFile, NotUsed] =
    ls(host, basePath = "")

  /**
   * Java API: creates a [[Source]] of [[FtpFile]]s from a base path.
   * By default, `anonymous` credentials will be used.
   *
   * @param host FTP, FTPs or SFTP host
   * @param basePath Base path from which traverse the remote file server
   * @return A [[Source]] of [[FtpFile]]s
   */
  def ls(
      host: String,
      basePath: String
  ): Source[FtpFile, NotUsed] =
    ls(basePath, defaultSettings(host))

  /**
   * Java API: creates a [[Source]] of [[FtpFile]]s from the remote user `root` directory.
   *
   * @param host FTP, FTPs or SFTP host
   * @param username username
   * @param password password
   * @return A [[Source]] of [[FtpFile]]s
   */
  def ls(
      host: String,
      username: String,
      password: String
  ): Source[FtpFile, NotUsed] =
    ls("", defaultSettings(host, Some(username), Some(password)))

  /**
   * Java API: creates a [[Source]] of [[FtpFile]]s from a base path.
   *
   * @param host FTP, FTPs or SFTP host
   * @param username username
   * @param password password
   * @param basePath Base path from which traverse the remote file server
   * @return A [[Source]] of [[FtpFile]]s
   */
  def ls(
      host: String,
      username: String,
      password: String,
      basePath: String
  ): Source[FtpFile, NotUsed] =
    ls(basePath, defaultSettings(host, Some(username), Some(password)))

  /**
   * Java API: creates a [[Source]] of [[FtpFile]]s from a base path.
   *
   * @param basePath Base path from which traverse the remote file server
   * @param connectionSettings connection settings
   * @return A [[Source]] of [[FtpFile]]s
   */
  def ls(
      basePath: String,
      connectionSettings: RemoteFileSettings
  ): Source[FtpFile, NotUsed] =
    ScalaSource.fromGraph(createBrowserGraph(ftpBrowserSourceName, basePath, connectionSettings)).asJava

  /**
   * Java API: creates a [[Source]] of [[ByteString]] from some file [[Path]].
   *
   * @param host FTP, FTPs or SFTP host
   * @param path the file path
   * @return A [[Source]] of [[ByteString]] that materializes to a [[CompletionStage]] of [[IOResult]]
   */
  def fromPath(
      host: String,
      path: Path
  ): Source[ByteString, CompletionStage[IOResult]] =
    fromPath(path, defaultSettings(host))

  /**
   * Java API: creates a [[Source]] of [[ByteString]] from some file [[Path]].
   *
   * @param host FTP, FTPs or SFTP host
   * @param username username
   * @param password password
   * @param path the file path
   * @return A [[Source]] of [[ByteString]] that materializes to a [[CompletionStage]] of [[IOResult]]
   */
  def fromPath(
      host: String,
      username: String,
      password: String,
      path: Path
  ): Source[ByteString, CompletionStage[IOResult]] =
    fromPath(path, defaultSettings(host, Some(username), Some(password)))

  /**
   * Java API: creates a [[Source]] of [[ByteString]] from some file [[Path]].
   *
   * @param path the file path
   * @param connectionSettings connection settings
   * @return A [[Source]] of [[ByteString]] that materializes to a [[CompletionStage]] of [[IOResult]]
   */
  def fromPath(
      path: Path,
      connectionSettings: RemoteFileSettings
  ): Source[ByteString, CompletionStage[IOResult]] =
    fromPath(path, connectionSettings, DefaultChunkSize)

  /**
   * Java API: creates a [[Source]] of [[ByteString]] from some file [[Path]].
   *
   * @param path the file path
   * @param connectionSettings connection settings
   * @param chunkSize the size of transmitted [[ByteString]] chunks
   * @return A [[Source]] of [[ByteString]] that materializes to a [[CompletionStage]] of [[IOResult]]
   */
  def fromPath(
      path: Path,
      connectionSettings: RemoteFileSettings,
      chunkSize: Int = DefaultChunkSize
  ): Source[ByteString, CompletionStage[IOResult]] = {
    import scala.compat.java8.FutureConverters._
    ScalaSource
      .fromGraph(createIOGraph(ftpIOSourceName, path, connectionSettings, chunkSize))
      .mapMaterializedValue(_.toJava)
      .asJava
  }

  /** Java API: needed because of the lack of implicit resolution in Java */
  protected[this] implicit def ftpLike: FtpLike[FtpClient]
}

object Ftp extends FtpApi[FTPClient] with FtpSourceFactory[FTPClient] with FtpSource with FtpDefaultSettings {
  protected[this] val ftpLike: FtpLike[FTPClient] = FtpLike.ftpLikeInstance
}
object Ftps extends FtpApi[FTPClient] with FtpSourceFactory[FTPClient] with FtpsSource with FtpsDefaultSettings {
  protected[this] val ftpLike: FtpLike[FTPClient] = FtpLike.ftpLikeInstance
}
object Sftp extends FtpApi[JSch] with FtpSourceFactory[JSch] with SftpSource with SftpDefaultSettings {
  protected[this] val ftpLike: FtpLike[JSch] = FtpLike.sFtpLikeInstance
}
