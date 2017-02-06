package com.bluelabs.akkaaws

import java.security.MessageDigest

import akka.stream._
import akka.stream.scaladsl._
import akka.stream.stage._
import akka.util.ByteString
import com.bluelabs.akkaaws.StreamUtils.ChecksumFailure

import scala.concurrent.Future

object StreamUtils {

  case class ChecksumFailure(msg: String) extends RuntimeException(msg)

  def digest(algorithm: String = "SHA-256"): Sink[ByteString, Future[ByteString]] = {
    Flow[ByteString].fold(MessageDigest.getInstance(algorithm)){
      case (digest: MessageDigest, bytes:ByteString) => {digest.update(bytes.asByteBuffer); digest}}
      .map {case md: MessageDigest => ByteString(md.digest())}
      .toMat(Sink.head[ByteString])(Keep.right)
  }
}

/**
  * Terminates the stream if the checksum fails
  * Copied from http://doc.akka.io/docs/akka/2.4.4/scala/stream/stream-cookbook.html#Calculating_the_digest_of_a_ByteString_stream
  *
  * @param checksum The known checksum to compare the resulting digest to
  * @param algorithm The digest algorithm to use (SHA-256, SHA-1, MD5)
  * @param maxBufferSize The maximum size to buffer before propagating backpressure
  */
class DigestValidator(val checksum: Array[Byte], val algorithm: String, val maxBufferSize: Long = 5 * 1024 * 1024 * 4)
  extends GraphStage[FlowShape[ByteString, ByteString]] {

  val in = Inlet[ByteString]("DigestValidator.in")
  val out = Outlet[ByteString]("DigestValidator.out")
  override val shape = FlowShape.of(in, out)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
    val digest = MessageDigest.getInstance(algorithm)
    var buffer = ByteString.empty
    override def preStart(): Unit = pull(in)

    private def pushAndPull(): Unit = {
      // Grab new elements if available
      if (!isClosed(in) && isAvailable(in)) {
        val chunk = grab(in)
        digest.update(chunk.toArray)
        buffer = buffer ++ chunk
        if (buffer.size < maxBufferSize) {
          pull(in)
        }
      }

      //
      if (isAvailable(out) && !isClosed(out) && buffer.nonEmpty) {
        push(out, buffer)
        buffer = ByteString.empty
        // If the pull was delayed because the buffer was full, another pull is necessary so the stream doesn't stall
        if (!hasBeenPulled(in) && buffer.size < maxBufferSize) pull(in)
      }

      // If true, we have received the last chunk and can calculate the checksum
      if (isClosed(in) && buffer.isEmpty) {
        if (digest.digest() sameElements checksum) {
          completeStage()
        }
        else {
          failStage(ChecksumFailure(s"Checksum failed for stream"))
        }
      }
    }

    setHandler(out, new OutHandler {
      override def onPull(): Unit = {
        pushAndPull()
      }
    })

    setHandler(in, new InHandler {
      override def onPush(): Unit = {
        pushAndPull()
      }

      override def onUpstreamFinish(): Unit = {
        pushAndPull()
      }
    })
  }
}
