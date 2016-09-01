package com.bluelabs.s3stream

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, ActorMaterializerSettings}
import akka.testkit.TestKit
import akka.util.ByteString
import com.bluelabs.akkaaws.AWSCredentials
import org.scalatest.{BeforeAndAfterAll, FlatSpecLike, Matchers}

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.language.postfixOps

/**
  * Created by Jason Martens <jason.martens@3dr.com> on 9/1/16.
  */
class IntegrationSpec(_system: ActorSystem) extends TestKit(_system) with FlatSpecLike with Matchers with BeforeAndAfterAll {
  def this() = this(ActorSystem("IntegrationSpec"))
  implicit val materializer = ActorMaterializer(ActorMaterializerSettings(system).withDebugLogging(true))
  // This bucket has open access, but deletes everything after 1 day
  val bucketName = "3dr-publictest"
  val creds = AWSCredentials("", "")
  val stream: S3Stream = new S3Stream(creds)

  "s3stream" should "upload small files with a simple put" in {
    val bs = ByteString("Hello World")
    Await.ready(stream.putObject(S3Location(bucketName, "helloworld.txt"), bs), 10 seconds)
  }

  it should "validate the MD5" in {
    val data = ByteString("Check Integrity!")
    val digest = java.security.MessageDigest.getInstance("MD5").digest(data.toArray[Byte])
    val base64 = new sun.misc.BASE64Encoder().encode(digest)
    Await.ready(stream.putObject(S3Location(bucketName, "checkintegrity.txt"), data, Some(base64)), 10 seconds)
  }

  it should "fail if the MD5 is invalid" in {
    val data = ByteString("DEADBEEFDEADBEEFDEADBEEF")
    val s3location = S3Location(bucketName, "nope.txt")
    intercept[UploadFailedException] {
      Await.result(stream.putObject(s3location, data, Some("Q2hlY2sgSW50ZWdyaXR5IQ==")), 10 seconds)
    }
  }

  override def afterAll(): Unit = {
    system.terminate()
  }

}
