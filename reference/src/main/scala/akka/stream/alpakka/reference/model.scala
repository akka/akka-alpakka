/*
 * Copyright (C) 2016-2018 Lightbend Inc. <http://www.lightbend.com>
 */

package akka.stream.alpakka.reference

// rename Java imports if the name clashes with the Scala name
import java.time.{Duration => JavaDuration}
import java.util.{List => JavaList}
import java.util.function.Predicate

import akka.util.ByteString

import scala.annotation.varargs
import scala.collection.immutable
import scala.concurrent.duration.Duration
import scala.concurrent.duration._

private[reference] object Testo {
  val aaa = 1
}

/**
 * Settings class constructor is private and not exposed as API.
 * Adding or removing arguments to methods with default values is not binary
 * compatible. However, since the constructor is private, it will be possible
 * to add or remove attributes without introducing binary incompatibilities.
 */
final class SourceSettings private (
    val clientId: Option[String] = None,
    val authentication: Authentication = Authentication.None,
    val pollInterval: Duration = 5.seconds
) {

  /**
   * Immutable setter which can be used from both Java and Scala, even if the
   * attribute is stored in a Scala specific class.
   */
  def withClientId(clientId: String): SourceSettings = copy(clientId = Some(clientId))

  /**
   * Separate setters for every attribute enables easy evolution of settings classes by allowing
   * deprecation and addition of attributes.
   */
  def withAuthentication(authentication: Authentication): SourceSettings = copy(authentication = authentication)

  /**
   * For attributes that uses Java or Scala specific classes, a setter is added for both APIs.
   */
  def withPollInterval(pollInterval: Duration): SourceSettings = copy(pollInterval = pollInterval)

  /**
   * Java API
   *
   * Start method documentation text with "Java API" to make it easy to notice
   * Java specific methods when browsing generated API documentation.
   */
  def withPollInterval(pollInterval: JavaDuration): SourceSettings = {
    import scala.compat.java8.DurationConverters._
    copy(pollInterval = pollInterval.toScala)
  }

  /**
   * Private copy method for internal use only.
   */
  private def copy(clientId: Option[String] = clientId,
                   authentication: Authentication = authentication,
                   pollInterval: Duration = pollInterval) =
    new SourceSettings(clientId, authentication, pollInterval)

  override def toString: String =
    s"""SourceSettings(
       |  clientId       = $clientId
       |  authentication = $authentication
       |  pollInterval   = $pollInterval
       |)""".stripMargin
}

object SourceSettings {

  /**
   * Factory method for Scala.
   */
  def apply(): SourceSettings = new SourceSettings()

  /**
   * Java API
   *
   * Factory method for Java.
   */
  def create(): SourceSettings = SourceSettings()
}

/**
 * Use sealed for closed class hierarchies.
 * abstract class instead of trait for visibility inside companion object from Java.
 */
sealed abstract class Authentication
object Authentication {

  /**
   * Make singleton objects extend an abstract class with the same name.
   * This makes it possible to refer to the object type without `.type`.
   */
  sealed abstract class None extends Authentication
  case object None extends None

  /**
   * Java API
   */
  def createNone: None = None

  final class Provided private (
      verifier: String => Boolean = _ => false
  ) extends Authentication {
    def verify(credentials: String) = verifier(credentials)

    def withVerifier(verifier: String => Boolean): Provided =
      copy(verifier = verifier)

    /**
     * Java API
     *
     * Because of Scala 2.11 support where Scala function is not a functional interface,
     * we need to provide a setter that accepts Java's functional interface.
     *
     * A different name is needed because after type erasure functional interfaces
     * become ambiguous in Scala 2.12.
     */
    def withVerifierPredicate(verifier: Predicate[String]): Provided = {
      import scala.compat.java8.FunctionConverters._
      copy(verifier = verifier.asScala)
    }

    private def copy(verifier: String => Boolean = verifier) =
      new Provided(verifier)

    override def toString: String =
      s"""Authentication.Provided(
         |  verifier       = $verifier
         |)""".stripMargin
  }

  object Provided {

    /**
     * This is only accessible from Scala, because of the nested objects.
     */
    def apply(): Provided = new Provided()
  }

  /**
   * Java API
   *
   * Factory method needed to access nested object.
   */
  def createProvided() = Provided()
}

/**
 * Use "Read" in message data types to signify that the message was read from outside.
 */
final class ReferenceReadMessage private (
    val data: immutable.Seq[ByteString] = immutable.Seq.empty
) {
  def withData(data: immutable.Seq[ByteString]): ReferenceReadMessage =
    copy(data = data)

  /**
   * Java API
   *
   * If the model class is only meant to be consumed (as opposed to created) from the user API,
   * create getters (as opposed to setters) for Java API.
   */
  def getData(): JavaList[ByteString] = {
    import scala.collection.JavaConverters._
    data.asJava
  }

  private def copy(data: immutable.Seq[ByteString] = data) =
    new ReferenceReadMessage(data)
}

object ReferenceReadMessage {
  def apply(): ReferenceWriteMessage = new ReferenceWriteMessage()
}

/**
 * Use "Write" in message data types to signify that the messages is to be written to outside.
 */
final class ReferenceWriteMessage private (
    val data: immutable.Seq[ByteString] = immutable.Seq.empty
) {
  def withData(data: immutable.Seq[ByteString]): ReferenceWriteMessage =
    copy(data = data)

  /**
   * When settings class has an attribute of Scala collection type,
   * use varargs annotation to generate a Java API varargs method.
   */
  @varargs
  def withData(data: ByteString*): ReferenceWriteMessage =
    copy(data = data.to[immutable.Seq])

  private def copy(data: immutable.Seq[ByteString] = data) =
    new ReferenceWriteMessage(data)

  override def toString: String =
    s"""ReferenceWriteMessage(
       |  data       = $data
       |)""".stripMargin
}

object ReferenceWriteMessage {

  def apply(): ReferenceWriteMessage = new ReferenceWriteMessage()

  def create(): ReferenceWriteMessage = ReferenceWriteMessage()
}
