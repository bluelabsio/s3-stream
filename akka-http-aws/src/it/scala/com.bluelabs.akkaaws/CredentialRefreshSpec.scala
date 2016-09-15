package com.bluelabs.akkaaws

import java.time.{ZoneId, ZonedDateTime}

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, ActorMaterializerSettings}
import akka.testkit.TestKit
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.{FlatSpecLike, Matchers}

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
    AWSCredentials.getEC2InstanceCredentials(timeout = 2 seconds) match {
      case Some(credentials: RoleCredentials) =>
        val originalToken = credentials.sessionToken
        // Loop until we get a new token, indicating that the credentials were refreshed
        println(s"Starting refresh test at ${ZonedDateTime.now(ZoneId.of("UTC"))}")
        while (originalToken.compareTo(credentials.sessionToken) == 0) {
            println(s"Credentials unchanged at ${ZonedDateTime.now(ZoneId.of("UTC"))}")
            Thread.sleep(600 * 1000)
        }
        println(s"Credentials updated at ${ZonedDateTime.now(ZoneId.of("UTC"))}")
        credentials.sessionToken should not be originalToken
      case nope => fail("Got bad credentials")
    }
  }
}
