package com.bluelabs.akkaaws

import java.io.{File, FileInputStream}
import java.security.{DigestInputStream, MessageDigest}

import akka.actor.ActorSystem
import akka.stream.scaladsl.{Sink, Source, StreamConverters}
import akka.stream.testkit.scaladsl.TestSink
import akka.stream.{ActorMaterializer, ActorMaterializerSettings}
import akka.testkit.TestKit
import akka.util.ByteString
import com.bluelabs.akkaaws.StreamUtils.ChecksumFailure
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.{FlatSpecLike, Matchers}

import scala.concurrent._
import scala.concurrent.duration._

class StreamUtilsSpec(_system: ActorSystem) extends TestKit(_system) with FlatSpecLike with Matchers with ScalaFutures {
  def this() = this(ActorSystem("StreamUtilsSpec"))

  implicit val materializer = ActorMaterializer(ActorMaterializerSettings(system).withDebugLogging(true))

  implicit val defaultPatience =
    PatienceConfig(timeout =  Span(5, Seconds), interval = Span(30, Millis))

  def getTestObjectDigest(resource: String, algorithm: String): Array[Byte] = {
    val testDigest = MessageDigest.getInstance(algorithm)
    val dis: DigestInputStream = new DigestInputStream(getClass.getResourceAsStream(resource), testDigest)

    val buffer = new Array[Byte](1024)
    var bytesRead: Int = dis.read(buffer)
    while (bytesRead > -1) {
      bytesRead = dis.read(buffer)
    }

    dis.getMessageDigest.digest()
  }



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
    val controlDigest = getTestObjectDigest("/testdata.txt", "SHA-256")

    whenReady(flow) { result =>
      result should contain theSameElementsInOrderAs controlDigest
    }
  }

  it should "complete a stream if the checksum validator passes" in {
    val algorithms = Seq("SHA-256", "SHA-1", "MD5")
    algorithms.foreach{algorithm =>
      val controlDigest = getTestObjectDigest("/testdata.txt", algorithm)

      val probe = StreamConverters.fromInputStream(() => getClass.getResourceAsStream("/testdata.txt"))
        .via(new DigestValidator(controlDigest, algorithm))
        .fold(ByteString.empty)(_ ++ _)
        .runWith(TestSink.probe)
        .request(1)
      probe.expectNext()
      probe.expectComplete()
    }
  }

  it should "fail a stream if the checksum validator fails" in {
    val algorithms = Seq("SHA-256", "SHA-1", "MD5")
    algorithms.foreach{algorithm =>
      val probe = StreamConverters.fromInputStream(() => getClass.getResourceAsStream("/testdata.txt"))
        .via(new DigestValidator(new Array[Byte](0), algorithm))
        .fold(ByteString.empty)(_ ++ _)
        .runWith(TestSink.probe)
        .request(1)
      probe.expectError(ChecksumFailure("Checksum failed for stream"))
    }
  }

  it should "process the stream correctly if the buffer fills up" in {
    val algorithms = Seq("SHA-256", "SHA-1", "MD5")
    algorithms.foreach{algorithm =>
      val controlDigest = getTestObjectDigest("/testdata.txt", algorithm)

      val probe = StreamConverters.fromInputStream(() => getClass.getResourceAsStream("/testdata.txt"))
        .via(new DigestValidator(controlDigest, algorithm, 4 * 1024))
        .fold(ByteString.empty)(_ ++ _)
        .runWith(TestSink.probe)
        .request(1)
      probe.expectNext()
      probe.expectComplete()
    }
  }

}
