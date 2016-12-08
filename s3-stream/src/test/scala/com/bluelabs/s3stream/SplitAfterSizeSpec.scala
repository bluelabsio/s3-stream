package com.bluelabs.s3stream

import akka.testkit.TestKit
import akka.stream.ActorMaterializerSettings
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import akka.stream.ActorMaterializer
import akka.actor.ActorSystem
import org.scalatest.Matchers
import org.scalatest.FlatSpecLike
import akka.stream.scaladsl.Source
import akka.stream.scaladsl.Flow
import akka.util.ByteString
import akka.stream.scaladsl.Sink

class SplitAfterSizeSpec (_system: ActorSystem) extends TestKit(_system) with FlatSpecLike with Matchers with BeforeAndAfterAll with ScalaFutures {

  def this() = this(ActorSystem("SplitAfterSizeSpec"))

  implicit val materializer = ActorMaterializer(ActorMaterializerSettings(system).withDebugLogging(true))
  
  "SplitAfterSize" should "yield a single empty substream on no input" in {
    Source.empty[ByteString].via(
      SplitAfterSize(10)(Flow[ByteString]).concatSubstreams
    ).runWith(Sink.seq).futureValue should be (Seq.empty)
  }
  
  it should "start a new stream after the element that makes it reach a maximum, but not split the element itself" in {
    Source(Vector(ByteString(1,2,3,4,5), ByteString(6,7,8,9,10,11,12), ByteString(13,14))).via(
      SplitAfterSize(10)(Flow[ByteString]).prefixAndTail(10).map { case (prefix,tail) => prefix }.concatSubstreams
    ).runWith(Sink.seq).futureValue should be (Seq(
      Seq(ByteString(1,2,3,4,5), ByteString(6,7,8,9,10,11,12)),
      Seq(ByteString(13,14))
    ))
  }
  
}