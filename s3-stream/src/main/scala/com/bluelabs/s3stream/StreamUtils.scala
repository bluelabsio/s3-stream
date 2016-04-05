package com.bluelabs.s3stream

import akka.NotUsed
import akka.stream.scaladsl.Source

object StreamUtils {
  def counter(initial: Int = 0): Source[Int, NotUsed] = {
    Source.unfold(initial)((i: Int) => Some(i + 1, i))
  }
}
