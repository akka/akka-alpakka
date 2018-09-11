/*
 * Copyright (C) 2016-2018 Lightbend Inc. <http://www.lightbend.com>
 */

package akka.stream.alpakka.dynamodb

import akka.actor.ActorSystem
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain
import com.typesafe.config.ConfigFactory
import org.scalatest.{Matchers, WordSpecLike}

class DynamoSettingsSpec extends WordSpecLike with Matchers {

  "DynamoSettings" should {
    "read the reference config from an actor system" in {
      val system = ActorSystem()
      val settings = DynamoSettings(system)

      settings.region should be("us-east-1")
      settings.host should be("localhost")
      settings.port should be(8001)
      settings.parallelism should be(4)
      settings.credentialsProvider shouldBe a[DefaultAWSCredentialsProviderChain]
    }

    "use the DefaultAWSCredentialsProviderChain if the config defines an incomplete akka.stream.alpakka.dynamodb.credentials" in {
      val config = ConfigFactory.parseString("""
          |region = "eu-west-1"
          |host = "localhost"
          |port: 443
          |parallelism = 32
          |credentials {
          |  access-key-id = "dummy-access-key"
          |}
        """.stripMargin)

      val settings = DynamoSettings(config)
      settings.credentialsProvider shouldBe a[DefaultAWSCredentialsProviderChain]
    }

    "read static aws credentials from a config that defines an akka.stream.alpakka.dynamodb.credentials" in {
      val configWithStaticCredentials =
        // #static-creds
        """
          | akka.stream.alpakka.dynamodb {
          |  region = "eu-west-1"
          |  host = "localhost"
          |  port: 443
          |  parallelism = 32
          |  credentials {
          |    access-key-id = "dummy-access-key"
          |    secret-key-id = "dummy-secret-key"
          |  }
          |}""".stripMargin
      // #static-creds

      val config = ConfigFactory.parseString(configWithStaticCredentials).getConfig("akka.stream.alpakka.dynamodb")

      val settings = DynamoSettings(config)
      val credentials = settings.credentialsProvider.getCredentials
      credentials.getAWSAccessKeyId should be("dummy-access-key")
      credentials.getAWSSecretKey should be("dummy-secret-key")

    }
  }
}
