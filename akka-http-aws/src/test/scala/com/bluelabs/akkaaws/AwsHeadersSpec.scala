package com.bluelabs.akkaaws

import akka.http.scaladsl.model.headers.RawHeader
import com.bluelabs.akkaaws.AwsHeaders.ServerSideEncryptionAlgorithm.AES256
import com.bluelabs.akkaaws.AwsHeaders.{`X-Amz-Server-Side-Encryption-Context`, `X-Amz-Server-Side-Encryption-Aws-Kms-Key-Id`, `X-Amz-Server-Side-Encryption`, ServerSideEncryptionAlgorithm}
import org.scalatest.{FlatSpec, Matchers}

import scala.util.{Failure, Success}


class AwsHeadersSpec extends FlatSpec with Matchers {

  "ServerSideEncryptionAlgorithm" should "parse AES256" in {
    ServerSideEncryptionAlgorithm.fromString("AES256") shouldBe Success(ServerSideEncryptionAlgorithm.AES256)
  }

  it should "parse KMS" in {
    ServerSideEncryptionAlgorithm.fromString("aws:kms") shouldBe Success(ServerSideEncryptionAlgorithm.KMS)
  }

  it should "not parse an unsupported algorithm" in {
    ServerSideEncryptionAlgorithm.fromString("Zip War AirGanon") shouldBe a[Failure[_]]
  }

  "`X-Amz-Server-Side-Encryption`" should "parse AES256 algorithm" in {
    val `X-Amz-Server-Side-Encryption`(algorithm) = `X-Amz-Server-Side-Encryption`("AES256")
    algorithm shouldBe AES256
  }

  it should "set the X-Amz-Server-Side-Encryption header" in {
    val RawHeader(key, value) = `X-Amz-Server-Side-Encryption`("AES256")
    key shouldBe "X-Amz-Server-Side-Encryption"
    value shouldBe "AES256"
  }

  "`X-Amz-Server-Side-Encryption-Aws-Kms-Key-Id`" should "parse kms key id" in {
    val `X-Amz-Server-Side-Encryption-Aws-Kms-Key-Id`(id) = `X-Amz-Server-Side-Encryption-Aws-Kms-Key-Id`("myId")
    id shouldBe "myId"
  }

  it should "set the X-Amz-Server-Side-Encryption-Aws-Kms-Key-Id header" in {
    val RawHeader(key, value) = `X-Amz-Server-Side-Encryption-Aws-Kms-Key-Id`("myId")
    key shouldBe "X-Amz-Server-Side-Encryption-Aws-Kms-Key-Id"
    value shouldBe "myId"
  }

  "`X-Amz-Server-Side-Encryption-Context`" should "parse context" in {
    val expectedContext = Map("foo"->"bar", "foo2"->"bar2")
    val `X-Amz-Server-Side-Encryption-Context`(context) = `X-Amz-Server-Side-Encryption-Context`(expectedContext)

    context shouldBe expectedContext
  }

  it should "set the X-Amz-Server-Side-Encryption-Context header" in {
    val RawHeader(key, value) = `X-Amz-Server-Side-Encryption-Context`(Map("foo"->"bar", "foo2"->"bar2"))
    key shouldBe "X-Amz-Server-Side-Encryption-Context"
    value shouldBe """{"foo":"bar","foo2":"bar2"}"""
  }

  it should "parse the raw context" in {
    val header = `X-Amz-Server-Side-Encryption-Context`.parse("""{"foo":"bar","foo2":"bar2"}""")
    header shouldBe Success(`X-Amz-Server-Side-Encryption-Context`(Map("foo"->"bar", "foo2"->"bar2")))
  }

  it should "not parse the raw context if it is not string->string" in {
    val header = `X-Amz-Server-Side-Encryption-Context`.parse("""{"foo":"bar","foo2":2}""")
    header shouldBe a[Failure[_]]
  }

}
