/*
 * Copyright (C) 2016-2018 Lightbend Inc. <http://www.lightbend.com>
 */

package docs.scaladsl

import akka.NotUsed
import akka.stream.alpakka.reference.scaladsl.Reference
import akka.stream.alpakka.reference.{Authentication, ReferenceReadMessage, ReferenceWriteMessage, SourceSettings}
import akka.stream.scaladsl.{Flow, Source}
import org.scalatest.WordSpec

import scala.concurrent.Future

/**
 * Append "Spec" to every Scala test suite.
 */
class ReferenceSpec extends WordSpec {

  "reference connector" should {

    /**
     * Type annotations not generally needed on local variables.
     * However it allows to check if the types are really what we want.
     */
    "compile settings" in {
      val providedAuth: Authentication.Provided =
        Authentication.Provided().withVerifier(c => true)

      val noAuth: Authentication.None =
        Authentication.None

      val settings: SourceSettings = SourceSettings()

      settings.withAuthentication(providedAuth)
      settings.withAuthentication(noAuth)
    }

    "compile source" in {
      // #source
      val settings: SourceSettings = SourceSettings()

      val source: Source[ReferenceReadMessage, Future[NotUsed]] =
        Reference.source(settings)
      // #source
    }

    "compile flow" in {
      // #flow
      val flow: Flow[ReferenceWriteMessage, ReferenceWriteMessage, NotUsed] =
        Reference.flow()
      // #flow
    }

  }

}
