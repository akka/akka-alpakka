/*
 * Copyright (C) 2016-2018 Lightbend Inc. <http://www.lightbend.com>
 */

package akka.stream.alpakka.cryptography.scaladsl

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}
import akka.util.ByteString
import javax.crypto.{Cipher, KeyGenerator, SecretKey}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{Matchers, WordSpec}
import akka.stream.alpakka.cryptography.scaladsl.CryptographicFlows._

import scala.concurrent.Future

class ExampleSpec extends WordSpec with Matchers with ScalaFutures {

  "Cryptography" should {
    "Provide a scala example of encrypting and decrypting byte-strings" in {
      //#init-actor-system
      implicit val actorSystem: ActorSystem = ActorSystem()
      implicit val materialize: ActorMaterializer = ActorMaterializer()

      //#init-key-and-ciphers
      val keyGenerator = KeyGenerator.getInstance("AES")
      val randomKey: SecretKey = keyGenerator.generateKey()

      val encryptionCipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
      encryptionCipher.init(Cipher.ENCRYPT_MODE, randomKey)

      val decryptionCipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
      decryptionCipher.init(Cipher.DECRYPT_MODE, randomKey)

      val toEncrypt = List("Some", "string", "for ", "you")

      val sourceOfUnencryptedData: Source[ByteString, NotUsed] = Source(toEncrypt.map(ByteString.apply))

      //#scala-encrypt
      val sourceOfEncryptedData: Source[ByteString, NotUsed] = sourceOfUnencryptedData
        .via(cipherFlow(encryptionCipher))

      //#scala-decrypt
      val sourceOfDecryptedData: Source[ByteString, NotUsed] =
        sourceOfEncryptedData.via(cipherFlow(decryptionCipher))

      val resultOfDecryption: Future[ByteString] =
        sourceOfDecryptedData
          .runWith(Sink.fold(ByteString.empty)(_ concat _))

      whenReady(resultOfDecryption) { r =>
        r.utf8String shouldBe toEncrypt.mkString("")
      }
    }
  }
}
