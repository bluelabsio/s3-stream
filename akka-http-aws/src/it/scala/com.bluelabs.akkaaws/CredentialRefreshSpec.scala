package com.bluelabs.akkaaws

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpMethods, HttpRequest}
import akka.stream.{ActorMaterializer, ActorMaterializerSettings}
import akka.testkit.TestKit
import org.scalatest.concurrent.AbstractPatienceConfiguration.PatienceConfig
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.{FlatSpecLike, Matchers}

import scala.concurrent.{Await, TimeoutException}
import scala.concurrent.duration._
import scala.language.postfixOps

class CredentialRefreshSpec(_system: ActorSystem) extends TestKit(_system) with FlatSpecLike with Matchers with ScalaFutures {
  def this() = this(ActorSystem("SignerSpec"))

  implicit val defaultPatience =
    PatienceConfig(timeout =  Span(2, Seconds), interval = Span(5, Millis))

  implicit val materializer = ActorMaterializer(ActorMaterializerSettings(system).withDebugLogging(true))
  implicit val executionContext = system.dispatcher

  behavior of "EC2 Refreshing Credentials"

  it should "refresh automatically after it expires" in {
    val credentials: SessionCredentials = AWSCredentials.getEC2InstanceCredentials()
    val originalToken = credentials.sessionToken
    // Loop until we get a new token, indicating that the credentials were refreshed
    println(s"Starting refresh test at ${ZonedDateTime.now(ZoneId.of("UTC"))}")
    while (originalToken.compareTo(credentials.sessionToken) == 0) {
      println(s"Credentials unchanged at ${ZonedDateTime.now(ZoneId.of("UTC"))}")
      sleep 600
    }
    println(s"Credentials updated at ${ZonedDateTIme.now(ZoneId.of("UTC"))}")
    credentials.sessionToken shouldNotBe originalToken
  }
}
