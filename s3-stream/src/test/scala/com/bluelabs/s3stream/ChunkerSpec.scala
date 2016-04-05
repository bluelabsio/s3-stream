package com.bluelabs.s3stream

import akka.actor.ActorSystem
import akka.stream.scaladsl.Keep
import akka.stream.testkit.scaladsl.{TestSink, TestSource}
import akka.stream.{ActorMaterializer, ActorMaterializerSettings}
import akka.testkit.TestKit
import akka.util.ByteString
import org.scalatest.{BeforeAndAfterAll, FlatSpecLike, Matchers}

class ChunkerSpec(_system: ActorSystem) extends TestKit(_system) with FlatSpecLike with Matchers with BeforeAndAfterAll {

  def this() = this(ActorSystem("ChunkerSpec"))

  implicit val materializer = ActorMaterializer(ActorMaterializerSettings(system).withDebugLogging(true))

  "A Chunker" should "resize larger chunks into smaller ones" in {
    val bytes = ByteString(1, 2, 3, 4, 5, 6)
    val (pub, sub) = TestSource.probe[ByteString]
      .via(new Chunker(2))
      .toMat(TestSink.probe[ByteString])(Keep.both)
      .run()

    pub.sendNext(bytes)
    sub.request(3)
    sub.expectNext(ByteString(1, 2), ByteString(3, 4), ByteString(5, 6))
  }

  it should "send the leftover bytes" in {
    val bytes = ByteString(1, 2, 3, 4, 5, 6, 7)
    val (pub, sub) = TestSource.probe[ByteString]
      .via(new Chunker(2))
      .toMat(TestSink.probe[ByteString])(Keep.both)
      .run()

    pub.sendNext(bytes)
    pub.sendComplete()
    sub.request(4)
    sub.expectNext(ByteString(1, 2), ByteString(3, 4), ByteString(5, 6), ByteString(7))
  }

  it should "resize smaller chunks into larger ones" in {
    val (pub, sub) = TestSource.probe[ByteString]
      .via(new Chunker(2))
      .toMat(TestSink.probe[ByteString])(Keep.both)
      .run()

    pub.sendNext(ByteString(1))
    pub.sendNext(ByteString(2))
    pub.sendNext(ByteString(3))
    pub.sendNext(ByteString(4))
    pub.sendNext(ByteString(5))
    pub.sendNext(ByteString(6))
    pub.sendNext(ByteString(7))
    pub.sendComplete()
    sub.request(4)
    sub.expectNext(ByteString(1, 2), ByteString(3, 4), ByteString(5, 6), ByteString(7))
  }

  it should "send bytes on complete" in {
    val (pub, sub) = TestSource.probe[ByteString]
      .via(new Chunker(10))
      .toMat(TestSink.probe[ByteString])(Keep.both)
      .run()

    pub.sendNext(ByteString(1))
    pub.sendNext(ByteString(2))
    pub.sendNext(ByteString(3))
    pub.sendNext(ByteString(4))
    pub.sendNext(ByteString(5))
    pub.sendNext(ByteString(6))
    pub.sendNext(ByteString(7))
    pub.sendComplete()
    sub.request(1)
    sub.expectNext(ByteString(1, 2, 3, 4, 5, 6, 7))
    sub.expectComplete()
  }

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

}
