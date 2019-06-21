/*
 * Copyright (C) 2016-2019 Lightbend Inc. <http://www.lightbend.com>
 */

package akka.stream.alpakka.googlecloud.storage.impl

import akka.annotation.InternalApi
import akka.http.scaladsl.HttpExt
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.{FormData, HttpMethods, HttpRequest}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.Materializer
import akka.stream.alpakka.googlecloud.storage.impl.GoogleTokenApi.{AccessTokenExpiry, OAuthResponse}
import pdi.jwt.{Jwt, JwtAlgorithm, JwtClaim, JwtTime}
import spray.json.{DefaultJsonProtocol, RootJsonFormat}

import java.time.Clock
import scala.concurrent.Future

@InternalApi
private[impl] class GoogleTokenApi(http: => HttpExt, settings: TokenApiSettings) {
  implicit val clock: Clock = Clock.systemUTC

  protected val encodingAlgorithm: JwtAlgorithm.RS256.type = JwtAlgorithm.RS256

  def now: Long = JwtTime.nowSeconds

  private val oneHour = 3600

  private def generateJwt(clientEmail: String, privateKey: String): String = {
    val claim =
      JwtClaim(content = s"""{"scope":"${settings.scope}","aud":"${settings.url}"}""", issuer = Option(clientEmail))
        .expiresIn(oneHour)
        .issuedNow
    Jwt.encode(claim, privateKey, encodingAlgorithm)
  }

  def getAccessToken(clientEmail: String, privateKey: String)(
      implicit materializer: Materializer
  ): Future[AccessTokenExpiry] = {
    import SprayJsonSupport._
    import materializer.executionContext

    val expiresAt = now + oneHour
    val jwt = generateJwt(clientEmail, privateKey)

    val requestEntity = FormData(
      "grant_type" -> "urn:ietf:params:oauth:grant-type:jwt-bearer",
      "assertion" -> jwt
    ).toEntity

    for {
      response <- http.singleRequest(HttpRequest(HttpMethods.POST, settings.url, entity = requestEntity))
      result <- Unmarshal(response.entity).to[OAuthResponse]
    } yield {
      AccessTokenExpiry(
        accessToken = result.access_token,
        expiresAt = expiresAt
      )
    }
  }
}

@InternalApi
private[googlecloud] object GoogleTokenApi {
  case class AccessTokenExpiry(accessToken: String, expiresAt: Long)
  case class OAuthResponse(access_token: String, token_type: String, expires_in: Int)

  import DefaultJsonProtocol._
  implicit val oAuthResponseJsonFormat: RootJsonFormat[OAuthResponse] = jsonFormat3(OAuthResponse)
}
