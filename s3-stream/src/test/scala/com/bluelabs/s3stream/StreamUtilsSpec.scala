package com.bluelabs.s3stream

import java.security.{DigestInputStream, MessageDigest}

import akka.actor.ActorSystem
import akka.stream.scaladsl.{Source, StreamConverters}
import akka.stream.testkit.scaladsl.TestSink
import akka.stream.{ActorMaterializer, ActorMaterializerSettings}
import akka.testkit.TestKit
import akka.util.ByteString
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{FlatSpecLike, Matchers}

import scala.concurrent.Future

class StreamUtilsSpec(_system: ActorSystem) extends TestKit(_system) with FlatSpecLike with Matchers with ScalaFutures {
  def this() = this(ActorSystem("StreamUtilsSpec"))

  implicit val materializer = ActorMaterializer(ActorMaterializerSettings(system).withDebugLogging(true))

  "counter" should "increment starting from 0" in {
    val testSource = StreamUtils.counter()
    testSource.runWith(TestSink.probe[Int]).request(2).expectNext(0, 1)
  }

  it should "allow specifying an initial value" in {
    val testSource = StreamUtils.counter(5)
    testSource.runWith(TestSink.probe[Int]).request(3).expectNext(5, 6, 7)
  }

}
