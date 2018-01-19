package akka.stream.alpakka.cryptography.impl

import java.security.{PrivateKey, PublicKey}
import javax.crypto.{Cipher, SecretKey}

import akka.util.ByteString

object CryptographicFlows {

  private def symmetricEncryption(secretKey: SecretKey)(in: ByteString): ByteString = {
    val cipher = Cipher.getInstance(secretKey.getAlgorithm)
    cipher.init(Cipher.ENCRYPT_MODE, secretKey)
    val encrypted = cipher.doFinal(in.toArray)

    ByteString(encrypted)
  }

  private def symmetricDecryption(secretKey: SecretKey)(toDecode: ByteString): ByteString = {
    val cipher = Cipher.getInstance(secretKey.getAlgorithm)
    cipher.init(Cipher.DECRYPT_MODE, secretKey)
    val decrypted = cipher.doFinal(toDecode.toArray)

    ByteString(decrypted)
  }

  private def asymmetricEncryption(publicKey: PublicKey)(in: ByteString): ByteString = {
    val cipher = Cipher.getInstance(publicKey.getAlgorithm)
    cipher.init(Cipher.PUBLIC_KEY, publicKey)
    val encrypted = cipher.doFinal(in.toArray)

    ByteString(encrypted)
  }

  private def asymmetricDecryption(privateKey: PrivateKey)(toDecode: ByteString): ByteString = {
    val cipher = Cipher.getInstance(privateKey.getAlgorithm)
    cipher.init(Cipher.DECRYPT_MODE, privateKey)
    val decrypted = cipher.doFinal(toDecode.toArray)

    ByteString(decrypted)
  }
}
