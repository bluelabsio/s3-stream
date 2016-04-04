package com.bluelabs.akkaaws

import java.security.{DigestInputStream, MessageDigest}

import akka.actor.ActorSystem
import akka.stream.scaladsl.{Source, StreamConverters}
import akka.stream.{ActorMaterializer, ActorMaterializerSettings}
import akka.testkit.TestKit
import akka.util.ByteString
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{FlatSpecLike, Matchers}

import scala.concurrent.Future

class StreamUtilsSpec(_system: ActorSystem) extends TestKit(_system) with FlatSpecLike with Matchers with ScalaFutures {
  def this() = this(ActorSystem("StreamUtilsSpec"))

  implicit val materializer = ActorMaterializer(ActorMaterializerSettings(system).withDebugLogging(true))


  "digest" should "calculate the digest of a short string" in {
    val bytes: Array[Byte] = "abcdefghijklmnopqrstuvwxyz".getBytes()
    val flow: Future[ByteString] = Source.single(ByteString(bytes)).runWith(StreamUtils.digest())

    val testDigest = MessageDigest.getInstance("SHA-256").digest(bytes)
    whenReady(flow) { result =>
      result should contain theSameElementsInOrderAs testDigest
    }
  }

  it should "calculate the digest of a file" in {
    val input = StreamConverters.fromInputStream(() => getClass.getResourceAsStream("/testdata.txt"))
    val flow: Future[ByteString] = input.runWith(StreamUtils.digest())

    val testDigest = MessageDigest.getInstance("SHA-256")
    val dis: DigestInputStream = new DigestInputStream(getClass.getResourceAsStream("/testdata.txt"), testDigest)

    val buffer = new Array[Byte](1024)

    var bytesRead: Int = dis.read(buffer)
    while (bytesRead > -1) {
      bytesRead = dis.read(buffer)
    }

    whenReady(flow) { result =>
      result should contain theSameElementsInOrderAs dis.getMessageDigest.digest()
    }

  }

}
