/*
 * Copyright (C) since 2016 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.stream.alpakka.s3

import java.nio.file.{Path, Paths}
import java.time.{Duration => JavaDuration}
import java.util.concurrent.TimeUnit
import java.util.{Objects, Optional}
import akka.actor.{ActorSystem, ClassicActorSystemProvider}
import akka.http.scaladsl.model.Uri
import akka.stream.alpakka.s3.AccessStyle.{PathAccessStyle, VirtualHostAccessStyle}
import com.typesafe.config.Config
import org.slf4j.LoggerFactory
import software.amazon.awssdk.auth.credentials._
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.regions.providers._

import scala.jdk.OptionConverters._
import scala.concurrent.duration._
import scala.util.Try

final class Proxy private (
    val host: String,
    val port: Int,
    val scheme: String
) {

  /** Java API */
  def getHost: String = host

  /** Java API */
  def getPort: Int = port

  /** Java API */
  def getScheme: String = scheme

  def withHost(value: String): Proxy = copy(host = value)
  def withPort(value: Int): Proxy = copy(port = value)
  def withScheme(value: String): Proxy = copy(scheme = value)

  private def copy(host: String = host, port: Int = port, scheme: String = scheme): Proxy =
    new Proxy(host = host, port = port, scheme = scheme)

  override def toString: String =
    "Proxy(" +
    s"host=$host," +
    s"port=$port," +
    s"scheme=$scheme" +
    ")"

  override def equals(other: Any): Boolean = other match {
    case that: Proxy =>
      Objects.equals(this.host, that.host) &&
      Objects.equals(this.port, that.port) &&
      Objects.equals(this.scheme, that.scheme)
    case _ => false
  }

  override def hashCode(): Int =
    Objects.hash(host, Int.box(port), scheme)
}

object Proxy {

  /** Scala API */
  def apply(host: String, port: Int, scheme: String): Proxy =
    new Proxy(host, port, scheme)

  /** Java API */
  def create(host: String, port: Int, scheme: String): Proxy =
    apply(host, port, scheme)
}

final class ForwardProxyCredentials private (val username: String, val password: String) {

  /** Java API */
  def getUsername: String = username

  /** Java API */
  def getPassword: String = password

  def withUsername(username: String): ForwardProxyCredentials = copy(username = username)
  def withPassword(password: String): ForwardProxyCredentials = copy(password = password)

  private def copy(username: String = username, password: String = password) =
    new ForwardProxyCredentials(username, password)

  override def toString: String =
    "ForwardProxyCredentials(" +
    s"username=$username," +
    s"password=******" +
    ")"

  override def equals(other: Any): Boolean = other match {
    case that: ForwardProxyCredentials =>
      Objects.equals(this.username, that.username) &&
      Objects.equals(this.password, that.password)
    case _ => false
  }

  override def hashCode(): Int =
    Objects.hash(username, password)

}

object ForwardProxyCredentials {

  /** Scala API */
  def apply(username: String, password: String): ForwardProxyCredentials =
    new ForwardProxyCredentials(username, password)

  /** Java API */
  def create(username: String, password: String): ForwardProxyCredentials =
    apply(username, password)

}

final class ForwardProxy private (val scheme: String,
                                  val host: String,
                                  val port: Int,
                                  val credentials: Option[ForwardProxyCredentials]) {

  require(scheme == "http" || scheme == "https", "scheme must be either `http` or `https`")

  /** Java API */
  def getScheme: String = scheme

  /** Java API */
  def getHost: String = host

  /** Java API */
  def getPort: Int = port

  /** Java API */
  def getCredentials: java.util.Optional[ForwardProxyCredentials] = credentials.toJava

  def withScheme(value: String): ForwardProxy = copy(scheme = value)
  def withHost(host: String): ForwardProxy = copy(host = host)
  def withPort(port: Int): ForwardProxy = copy(port = port)
  def withCredentials(credentials: ForwardProxyCredentials): ForwardProxy = copy(credentials = Option(credentials))

  private def copy(scheme: String = scheme,
                   host: String = host,
                   port: Int = port,
                   credentials: Option[ForwardProxyCredentials] = credentials) =
    new ForwardProxy(scheme, host, port, credentials)

  override def toString: String =
    "ForwardProxy(" +
    s"scheme=$scheme," +
    s"host=$host," +
    s"port=$port," +
    s"credentials=$credentials)"

  override def equals(other: Any): Boolean = other match {
    case that: ForwardProxy =>
      Objects.equals(this.scheme, that.scheme) &&
      Objects.equals(this.host, that.host) &&
      Objects.equals(this.port, that.port) &&
      Objects.equals(this.credentials, that.credentials)
    case _ => false
  }

  override def hashCode(): Int =
    Objects.hash(scheme, host, Int.box(port), credentials)
}

object ForwardProxy {

  /** Scala API */
  def apply(host: String, port: Int, credentials: Option[ForwardProxyCredentials]) =
    new ForwardProxy("https", host, port, credentials)

  /** Java API */
  def create(host: String, port: Int, credentials: Optional[ForwardProxyCredentials]): ForwardProxy =
    apply(host, port, credentials.toScala)

  /** Use an HTTP proxy. */
  def http(host: String, port: Int): ForwardProxy = new ForwardProxy("http", host, port, credentials = None)

  def apply(c: Config): ForwardProxy = {
    val maybeCredentials =
      if (c.hasPath("credentials"))
        Some(ForwardProxyCredentials(c.getString("credentials.username"), c.getString("credentials.password")))
      else None

    val scheme =
      if (c.hasPath("scheme")) c.getString("scheme")
      else "https"
    new ForwardProxy(scheme, c.getString("host"), c.getInt("port"), maybeCredentials)
  }

}

sealed abstract class ApiVersion
object ApiVersion {
  sealed abstract class ListBucketVersion1 extends ApiVersion
  case object ListBucketVersion1 extends ListBucketVersion1

  /** Java Api */
  def getListBucketVersion1: ListBucketVersion1 = ListBucketVersion1

  sealed abstract class ListBucketVersion2 extends ApiVersion
  case object ListBucketVersion2 extends ListBucketVersion2

  /** Java Api */
  def getListBucketVersion2: ListBucketVersion2 = ListBucketVersion2
}

sealed abstract class AccessStyle
object AccessStyle {
  sealed abstract class PathAccessStyle extends AccessStyle
  case object PathAccessStyle extends PathAccessStyle

  /** Java API */
  def pathAccessStyle: PathAccessStyle = PathAccessStyle

  sealed abstract class VirtualHostAccessStyle extends AccessStyle
  case object VirtualHostAccessStyle extends VirtualHostAccessStyle

  /** Java API */
  def virtualHostAccessStyle: VirtualHostAccessStyle = VirtualHostAccessStyle
}

final class RetrySettings private (val maxRetries: Int,
                                   val minBackoff: FiniteDuration,
                                   val maxBackoff: FiniteDuration,
                                   val randomFactor: Double) {

  /** Java API */
  def getMaxRetries: Int = maxRetries

  /** Java API */
  def getMinBackoff: JavaDuration = JavaDuration.ofNanos(minBackoff.toNanos)

  /** Java API */
  def getMaxBackoff: JavaDuration = JavaDuration.ofNanos(maxBackoff.toNanos)

  /** Java API */
  def getRandomFactor: Double = randomFactor

  def withMaxRetries(value: Int): RetrySettings = copy(maxRetries = value)

  def withMinBackoff(value: FiniteDuration): RetrySettings = copy(minBackoff = value)

  /** Java API */
  def withMinBackoff(value: JavaDuration): RetrySettings =
    copy(minBackoff = FiniteDuration(value.toNanos, TimeUnit.NANOSECONDS))

  def withMaxBackoff(value: FiniteDuration): RetrySettings = copy(maxBackoff = value)

  /** Java API */
  def withMaxBackoff(value: JavaDuration): RetrySettings =
    copy(maxBackoff = FiniteDuration(value.toNanos, TimeUnit.NANOSECONDS))

  def withRandomFactor(value: Double): RetrySettings = copy(randomFactor = value)

  private def copy(maxRetries: Int = maxRetries,
                   minBackoff: FiniteDuration = minBackoff,
                   maxBackoff: FiniteDuration = maxBackoff,
                   randomFactor: Double = randomFactor) =
    new RetrySettings(maxRetries, minBackoff, maxBackoff, randomFactor)

  override def toString: String =
    "RetrySettings(" +
    s"maxRetries=$maxRetries," +
    s"minBackoff=$minBackoff," +
    s"maxBackoff=$maxBackoff," +
    s"randomFactor=$randomFactor)"

  override def equals(other: Any): Boolean = other match {
    case that: RetrySettings =>
      Objects.equals(this.maxRetries, that.maxRetries) &&
      Objects.equals(this.minBackoff, that.minBackoff) &&
      Objects.equals(this.maxBackoff, that.maxBackoff) &&
      Objects.equals(this.randomFactor, that.randomFactor)
    case _ => false
  }

  override def hashCode(): Int =
    Objects.hash(Int.box(maxRetries), minBackoff, maxBackoff, Double.box(randomFactor))
}

object RetrySettings {
  val default: RetrySettings = RetrySettings(3, 200.milliseconds, 10.seconds, 0.0)

  /** Scala API */
  def apply(maxRetries: Int,
            minBackoff: FiniteDuration,
            maxBackoff: FiniteDuration,
            randomFactor: Double): RetrySettings =
    new RetrySettings(maxRetries, minBackoff, maxBackoff, randomFactor)

  /** Java API */
  def create(maxRetries: Int, minBackoff: JavaDuration, maxBackoff: JavaDuration, randomFactor: Double): RetrySettings =
    apply(maxRetries,
          FiniteDuration(minBackoff.toNanos, TimeUnit.NANOSECONDS),
          FiniteDuration(maxBackoff.toNanos, TimeUnit.NANOSECONDS),
          randomFactor)

  def apply(config: Config): RetrySettings = {
    RetrySettings(
      config.getInt("max-retries"),
      FiniteDuration(config.getDuration("min-backoff").toNanos, TimeUnit.NANOSECONDS),
      FiniteDuration(config.getDuration("max-backoff").toNanos, TimeUnit.NANOSECONDS),
      config.getDouble("random-factor")
    )
  }
}

final class MultipartUploadSettings private (val retrySettings: RetrySettings) {

  /** Java API */
  def getRetrySettings: RetrySettings = retrySettings

  def withRetrySettings(value: RetrySettings): MultipartUploadSettings = MultipartUploadSettings(value)

  override def toString =
    s"MultipartUploadSettings(retrySettings=$retrySettings)"

  override def equals(other: Any): Boolean = other match {
    case that: MultipartUploadSettings => Objects.equals(this.retrySettings, that.retrySettings)
    case _ => false
  }

  override def hashCode(): Int = Objects.hash(retrySettings)
}

object MultipartUploadSettings {

  /** Scala API */
  def apply(retrySettings: RetrySettings): MultipartUploadSettings =
    new MultipartUploadSettings(retrySettings)

  /** Java API */
  def create(retrySettings: RetrySettings): MultipartUploadSettings =
    apply(retrySettings)
}

final class S3Settings private (
    val bufferType: BufferType,
    val credentialsProvider: AwsCredentialsProvider,
    val s3RegionProvider: AwsRegionProvider,
    val accessStyle: AccessStyle,
    val endpointUrl: Option[String],
    val listBucketApiVersion: ApiVersion,
    val forwardProxy: Option[ForwardProxy],
    val validateObjectKey: Boolean,
    val retrySettings: RetrySettings,
    val multipartUploadSettings: MultipartUploadSettings,
    val signAnonymousRequests: Boolean
) {

  /** Java API */
  def getBufferType: BufferType = bufferType

  /** Java API */
  def getCredentialsProvider: AwsCredentialsProvider = credentialsProvider

  /** Java API */
  def getS3RegionProvider: AwsRegionProvider = s3RegionProvider

  /** Java API */
  def isPathStyleAccess: Boolean = accessStyle == PathAccessStyle

  def pathStyleAccess: Boolean = accessStyle == PathAccessStyle

  /** Java API */
  def getEndpointUrl: java.util.Optional[String] = endpointUrl.toJava

  /** Java API */
  def getListBucketApiVersion: ApiVersion = listBucketApiVersion

  /** Java API */
  def getForwardProxy: java.util.Optional[ForwardProxy] = forwardProxy.toJava

  /** Java API */
  def getAccessStyle: AccessStyle = accessStyle

  /** Java API */
  def getRetrySettings: RetrySettings = retrySettings

  /** Java API */
  def getMultipartUploadSettings: MultipartUploadSettings = multipartUploadSettings

  /** Java API */
  def isSignAnonymousRequests: Boolean = signAnonymousRequests

  def withBufferType(value: BufferType): S3Settings = copy(bufferType = value)

  def withCredentialsProvider(value: AwsCredentialsProvider): S3Settings =
    copy(credentialsProvider = value)
  def withS3RegionProvider(value: AwsRegionProvider): S3Settings = copy(s3RegionProvider = value)

  def withAccessStyle(value: AccessStyle): S3Settings =
    if (accessStyle == value) this else copy(accessStyle = value);

  def withEndpointUrl(value: String): S3Settings = copy(endpointUrl = Option(value))
  def withListBucketApiVersion(value: ApiVersion): S3Settings =
    copy(listBucketApiVersion = value)
  def withForwardProxy(value: ForwardProxy): S3Settings =
    copy(forwardProxy = Option(value))
  def withValidateObjectKey(value: Boolean): S3Settings =
    if (validateObjectKey == value) this else copy(validateObjectKey = value)

  def withRetrySettings(value: RetrySettings): S3Settings = copy(retrySettings = value)

  def withMultipartUploadSettings(value: MultipartUploadSettings): S3Settings = copy(multipartUploadSettings = value)

  def withSignAnonymousRequests(value: Boolean): S3Settings =
    if (signAnonymousRequests == value) this else copy(signAnonymousRequests = value)

  private def copy(
      bufferType: BufferType = bufferType,
      credentialsProvider: AwsCredentialsProvider = credentialsProvider,
      s3RegionProvider: AwsRegionProvider = s3RegionProvider,
      accessStyle: AccessStyle = accessStyle,
      endpointUrl: Option[String] = endpointUrl,
      listBucketApiVersion: ApiVersion = listBucketApiVersion,
      forwardProxy: Option[ForwardProxy] = forwardProxy,
      validateObjectKey: Boolean = validateObjectKey,
      retrySettings: RetrySettings = retrySettings,
      multipartUploadSettings: MultipartUploadSettings = multipartUploadSettings,
      signAnonymousRequests: Boolean = signAnonymousRequests
  ): S3Settings = new S3Settings(
    bufferType,
    credentialsProvider,
    s3RegionProvider,
    accessStyle,
    endpointUrl,
    listBucketApiVersion,
    forwardProxy,
    validateObjectKey,
    retrySettings,
    multipartUploadSettings,
    signAnonymousRequests
  )

  override def toString: String =
    "S3Settings(" +
    s"bufferType=$bufferType," +
    s"credentialsProvider=$credentialsProvider," +
    s"s3RegionProvider=$s3RegionProvider," +
    s"accessStyle=$accessStyle," +
    s"endpointUrl=$endpointUrl," +
    s"listBucketApiVersion=$listBucketApiVersion," +
    s"forwardProxy=$forwardProxy," +
    s"validateObjectKey=$validateObjectKey" +
    s"retrySettings=$retrySettings" +
    s"multipartUploadSettings=$multipartUploadSettings)" +
    s"signAnonymousRequests=$signAnonymousRequests"

  override def equals(other: Any): Boolean = other match {
    case that: S3Settings =>
      java.util.Objects.equals(this.bufferType, that.bufferType) &&
      Objects.equals(this.credentialsProvider, that.credentialsProvider) &&
      Objects.equals(this.s3RegionProvider, that.s3RegionProvider) &&
      Objects.equals(this.accessStyle, that.accessStyle) &&
      Objects.equals(this.endpointUrl, that.endpointUrl) &&
      Objects.equals(this.listBucketApiVersion, that.listBucketApiVersion) &&
      Objects.equals(this.forwardProxy, that.forwardProxy) &&
      this.validateObjectKey == that.validateObjectKey &&
      Objects.equals(this.retrySettings, that.retrySettings) &&
      Objects.equals(this.multipartUploadSettings, multipartUploadSettings) &&
      this.signAnonymousRequests == that.signAnonymousRequests
    case _ => false
  }

  override def hashCode(): Int =
    Objects.hash(
      bufferType,
      credentialsProvider,
      s3RegionProvider,
      accessStyle,
      endpointUrl,
      listBucketApiVersion,
      forwardProxy,
      Boolean.box(validateObjectKey),
      retrySettings,
      multipartUploadSettings,
      Boolean.box(signAnonymousRequests)
    )
}

object S3Settings {
  private final val log = LoggerFactory.getLogger(getClass)
  val ConfigPath = "alpakka.s3"

  /**
   * Reads from the given config.
   */
  def apply(c: Config): S3Settings = {

    val bufferType = c.getString("buffer") match {
      case "memory" =>
        MemoryBufferType

      case "disk" =>
        val diskBufferPath = c.getString("disk-buffer-path")
        DiskBufferType(Paths.get(diskBufferPath))

      case other =>
        throw new IllegalArgumentException(s"Buffer type must be 'memory' or 'disk'. Got: [$other]")
    }

    val maybeProxy = for {
      host <- Try(c.getString("proxy.host")).toOption if host.nonEmpty
    } yield {
      Proxy(
        host,
        c.getInt("proxy.port"),
        Uri.httpScheme(c.getBoolean("proxy.secure"))
      )
    }

    val maybeForwardProxy =
      if (c.hasPath("forward-proxy") && c.hasPath("forward-proxy.host") && c.hasPath("forward-proxy.port"))
        Some(ForwardProxy(c.getConfig("forward-proxy")))
      else None

    if (c.hasPath("path-style-access"))
      log.warn(
        "The deprecated 'path-style-access' property was used to specify access style. Please use 'access-style' instead."
      )

    val deprecatedPathAccessStyleSetting = Try(c.getString("path-style-access")).toOption

    val accessStyle = deprecatedPathAccessStyleSetting match {
      case None | Some("") =>
        c.getString("access-style") match {
          case "virtual" => VirtualHostAccessStyle
          case "path" => PathAccessStyle
          case other =>
            throw new IllegalArgumentException(s"'access-style' must be 'virtual' or 'path'. Got: [$other]")
        }
      case Some("true") | Some("force") => PathAccessStyle
      case Some("false") => VirtualHostAccessStyle
      case Some(other) =>
        throw new IllegalArgumentException(
          s"'path-style-access' must be 'false', 'true' or 'force'. Got: [$other]. Prefer using access-style instead."
        )
    }

    val endpointUrl = if (c.hasPath("endpoint-url")) {
      Option(c.getString("endpoint-url"))
    } else {
      None
    }.orElse(maybeProxy.map(p => s"${p.scheme}://${p.host}:${p.port}"))

    if (endpointUrl.isEmpty && accessStyle == PathAccessStyle)
      log.warn(
        s"""It appears you are attempting to use AWS S3 with path-style access.
          |Amazon had planned not to support path-style access to buckets created after September 30, 2020;
          |see (https://aws.amazon.com/blogs/aws/amazon-s3-path-deprecation-plan-the-rest-of-the-story/).
          |(Update, Sept 23, 2020) AWS has delayed this change indefinitely to give customers more time to
          |migrate to virtual host-style access.
          |
          |Enable virtual host-style access by unsetting `$ConfigPath.path-style-access`,
          |and leaving `$ConfigPath.access-style` on the default `virtual`.
          |
          |If your S3 provider is not AWS, you need to set `$ConfigPath.endpoint-url`.""".stripMargin
      )

    val regionProvider = {
      val regionProviderPath = "aws.region.provider"

      val staticRegionProvider = new AwsRegionProvider {
        lazy val getRegion: Region = Region.of(c.getString("aws.region.default-region"))
      }

      if (c.hasPath(regionProviderPath)) {
        c.getString(regionProviderPath) match {
          case "static" =>
            staticRegionProvider

          case _ =>
            new DefaultAwsRegionProviderChain()
        }
      } else {
        new DefaultAwsRegionProviderChain()
      }
    }

    val credentialsProvider = {
      val credProviderPath = "aws.credentials.provider"

      if (c.hasPath(credProviderPath)) {
        c.getString(credProviderPath) match {
          case "default" =>
            DefaultCredentialsProvider.create()

          case "static" =>
            val aki = c.getString("aws.credentials.access-key-id")
            val sak = c.getString("aws.credentials.secret-access-key")
            val tokenPath = "aws.credentials.token"
            val creds: AwsCredentials = if (c.hasPath(tokenPath)) {
              AwsSessionCredentials.create(aki, sak, c.getString(tokenPath))
            } else {
              AwsBasicCredentials.create(aki, sak)
            }
            StaticCredentialsProvider.create(creds)

          case "anon" =>
            AnonymousCredentialsProvider.create()

          case _ =>
            DefaultCredentialsProvider.create()
        }
      } else {
        DefaultCredentialsProvider.create()
      }
    }

    val apiVersion = Try(c.getInt("list-bucket-api-version") match {
      case 1 => ApiVersion.ListBucketVersion1
      case 2 => ApiVersion.ListBucketVersion2
    }).getOrElse(ApiVersion.ListBucketVersion2)
    val validateObjectKey = c.getBoolean("validate-object-key")

    val retrySettings = RetrySettings(c.getConfig("retry-settings"))

    val multipartUploadConfig = c.getConfig("multipart-upload")
    val multipartUploadSettings = MultipartUploadSettings(
      RetrySettings(multipartUploadConfig.getConfig("retry-settings"))
    )

    val signAnonymousRequests = c.getBoolean("sign-anonymous-requests")

    new S3Settings(
      bufferType,
      credentialsProvider,
      regionProvider,
      accessStyle,
      endpointUrl,
      apiVersion,
      maybeForwardProxy,
      validateObjectKey,
      retrySettings,
      multipartUploadSettings,
      signAnonymousRequests
    )
  }

  /**
   * Java API: Reads from the given config.
   */
  def create(c: Config): S3Settings = apply(c)

  /** Scala API */
  def apply(
      bufferType: BufferType,
      credentialsProvider: AwsCredentialsProvider,
      s3RegionProvider: AwsRegionProvider,
      listBucketApiVersion: ApiVersion
  ): S3Settings = new S3Settings(
    bufferType,
    credentialsProvider,
    s3RegionProvider,
    accessStyle = VirtualHostAccessStyle,
    endpointUrl = None,
    listBucketApiVersion,
    forwardProxy = None,
    validateObjectKey = true,
    RetrySettings.default,
    MultipartUploadSettings(RetrySettings.default),
    signAnonymousRequests = true
  )

  /** Java API */
  def create(
      bufferType: BufferType,
      credentialsProvider: AwsCredentialsProvider,
      s3RegionProvider: AwsRegionProvider,
      listBucketApiVersion: ApiVersion
  ): S3Settings = apply(
    bufferType,
    credentialsProvider,
    s3RegionProvider,
    listBucketApiVersion
  )

  /**
   * Scala API: Creates [[S3Settings]] from the [[com.typesafe.config.Config Config]] attached to an actor system.
   */
  def apply()(implicit system: ClassicActorSystemProvider): S3Settings = apply(system.classicSystem)

  /**
   * Scala API: Creates [[S3Settings]] from the [[com.typesafe.config.Config Config]] attached to an [[akka.actor.ActorSystem]].
   */
  def apply(system: ActorSystem): S3Settings = apply(system.settings.config.getConfig(ConfigPath))

  /**
   * Java API: Creates [[S3Settings]] from the [[com.typesafe.config.Config Config]] attached to an actor system.
   */
  def create(system: ClassicActorSystemProvider): S3Settings = apply(system.classicSystem)

  /**
   * Java API: Creates [[S3Settings]] from the [[com.typesafe.config.Config Config]] attached to an [[akka.actor.ActorSystem]].
   */
  def create(system: ActorSystem): S3Settings = apply(system)
}

sealed trait BufferType {
  def path: Option[Path]

  /** Java API */
  def getPath: java.util.Optional[Path] = path.toJava
}

case object MemoryBufferType extends BufferType {
  def getInstance: BufferType = MemoryBufferType
  override def path: Option[Path] = None
}

final class DiskBufferType private (filePath: Path) extends BufferType {
  override val path: Option[Path] = Some(filePath).filterNot(_.toString.isEmpty)
}
case object DiskBufferType {
  def apply(path: Path): DiskBufferType = new DiskBufferType(path)

  /** Java API */
  def create(path: Path): DiskBufferType = DiskBufferType(path)
}
