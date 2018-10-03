/*
 * Copyright (C) 2016-2018 Lightbend Inc. <http://www.lightbend.com>
 */

package akka.stream.alpakka.mqtt.streaming.impl

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpec}

class ServerSpec extends WordSpec with Matchers with BeforeAndAfterAll {

  val testKit = ActorTestKit()
  override def afterAll(): Unit = testKit.shutdownTestKit()

  "publisher" should {
    "match topic filters" in {
      Publisher.matchTopicFilter("sport/tennis/player1", "sport/tennis/player1") shouldBe true

      Publisher.matchTopicFilter("sport/tennis/player1/#", "sport/tennis/player1") shouldBe true
      Publisher.matchTopicFilter("sport/tennis/player1/#", "sport/tennis/player1/ranking") shouldBe true
      Publisher.matchTopicFilter("sport/tennis/player1/#", "sport/tennis/player1/score/wimbledon") shouldBe true

      Publisher.matchTopicFilter("sport/#", "sport") shouldBe true
      Publisher.matchTopicFilter("#", "sport") shouldBe true
      Publisher.matchTopicFilter("sport/tennis/#", "sport/tennis") shouldBe true
      Publisher.matchTopicFilter("sport/tennis#", "sport/tennis") shouldBe false
      Publisher.matchTopicFilter("sport/tennis/#/ranking", "sport/tennis/player1/ranking") shouldBe false

      Publisher.matchTopicFilter("sport/tennis/+", "sport/tennis/player1") shouldBe true
      Publisher.matchTopicFilter("sport/tennis/+", "sport/tennis/player1/tranking") shouldBe false

      Publisher.matchTopicFilter("sport/+", "sport") shouldBe false
      Publisher.matchTopicFilter("sport/+", "sport/") shouldBe true

      Publisher.matchTopicFilter("+", "sport") shouldBe true
      Publisher.matchTopicFilter("+/tennis/#", "sport/tennis") shouldBe true
      Publisher.matchTopicFilter("sport+", "sport") shouldBe false
    }
  }
}
