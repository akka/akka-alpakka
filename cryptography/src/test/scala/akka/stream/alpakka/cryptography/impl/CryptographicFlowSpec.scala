/*
 * Copyright (C) 2016-2018 Lightbend Inc. <http://www.lightbend.com>
 */

package akka.stream.alpakka.cryptography.impl

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}
import akka.util.ByteString
import javax.crypto.{Cipher, KeyGenerator, SecretKey}
import org.scalacheck.{Arbitrary, Gen}
import akka.stream.alpakka.cryptography.scaladsl.CryptographicFlows._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import org.scalatest._
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.prop.PropertyChecks

class CryptographicFlowSpec
    extends WordSpec
    with Matchers
    with ScalaFutures
    with PropertyChecks
    with IntegrationPatience {

  implicit val as: ActorSystem = ActorSystem()
  implicit val actorMaterializer: ActorMaterializer = ActorMaterializer()

  "Flows" can {
    "Encryption flows" should {
      "Be able to encrypt and decrypt bytestrings" in {
        forAll(minSuccessful(50), sizeRange(20)) { (key: SecretKey, toEncrypt: List[String]) =>
          val src: Source[ByteString, NotUsed] = Source(toEncrypt.map(ByteString.apply))
          val encryptionCipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
          encryptionCipher.init(Cipher.ENCRYPT_MODE, key)

          val decryptionCipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
          decryptionCipher.init(Cipher.DECRYPT_MODE, key)

          val res: Future[ByteString] = src
            .via(cipherFlow(encryptionCipher))
            .via(cipherFlow(decryptionCipher))
            .runWith(Sink.fold(ByteString.empty)(_ concat _))

          whenReady(res) { s =>
            s.utf8String shouldBe toEncrypt.mkString("")
          }
        }
      }
    }
  }

  implicit def arbitrarySecretKey: Arbitrary[SecretKey] = Arbitrary {
    Gen.resultOf((_: Unit) => {
      val keyGenerator = KeyGenerator.getInstance("AES")
      keyGenerator.generateKey()
    })
  }
}
