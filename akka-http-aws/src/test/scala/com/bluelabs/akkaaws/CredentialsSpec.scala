package com.bluelabs.akkaaws

import java.time.LocalDate

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, ActorMaterializerSettings}
import akka.testkit.TestKit
import com.bluelabs.akkaaws
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.{FlatSpec, FlatSpecLike, Matchers}

class CredentialsSpec(_system: ActorSystem) extends TestKit(_system) with FlatSpecLike with Matchers with ScalaFutures {
  def this() = this(ActorSystem("SignerSpec"))

  implicit val defaultPatience =
    PatienceConfig(timeout =  Span(2, Seconds), interval = Span(5, Millis))

  implicit val materializer = ActorMaterializer(ActorMaterializerSettings(system).withDebugLogging(true))

  behavior of "AWS Credential lookups"

  it should "extract credentials from the environment" in {
    val fakeEnvironmentMap = Map(
      "AWS_ACCESS_KEY" -> "Test", "AWS_SECRET_KEY" -> "Ing",
      "AWS_ACCESS_KEY_ID" -> "Hello", "AWS_SECRET_ACCESS_KEY" -> "World")
    AWSCredentials.getEnvironmentCredentials(fakeEnvironmentMap) shouldBe Some(AWSCredentials("Hello", "World"))
  }

  it should "fall back to secondary environment credentials" in {
    val fakeEnvironmentMap = Map("AWS_ACCESS_KEY" -> "Test", "AWS_SECRET_KEY" -> "Ing")
    AWSCredentials.getEnvironmentCredentials(fakeEnvironmentMap) shouldBe Some(AWSCredentials("Test", "Ing"))
  }

  it should "lookup credentials in a config file" in {
    val credentialFileUrl = getClass.getResource("/testAWSCredentials.txt")
    val credentials = AWSCredentials.getConfigCredentials(Some(credentialFileUrl.getPath))
    credentials shouldBe Some(AWSCredentials("defaultAccessKeyis20", "default/Secret/AccessKeyMustBe40Characte"))
  }

  it should "lookup alternate credential profiles" in {
    val credentialFileUrl = getClass.getResource("/testAWSCredentials.txt")
    val credentials = AWSCredentials.getConfigCredentials(Some(credentialFileUrl.getPath), "Test2")
    credentials shouldBe Some(AWSCredentials("Test2zAccessKeyyyyyy", "Test2/Secret/AccessKeythisalsoIs40charac"))
  }

  it should "lookup credentials from java system properties" in {
    val properties = System.getProperties
    properties.setProperty("aws.accessKeyId", "AKIAIOSFODNN7EXAMPLE")
    properties.setProperty("aws.secretKey", "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY")
    val controlCredentials = Some(AWSCredentials("AKIAIOSFODNN7EXAMPLE", "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY"))
    val testCredentials = AWSCredentials.getJavaPropertyCredentials
    testCredentials shouldBe controlCredentials
  }

}
