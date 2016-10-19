package com.bluelabs.s3stream


sealed trait ServerSideEncryption

object ServerSideEncryption {

  case object None extends ServerSideEncryption

  case object Aes256 extends ServerSideEncryption

  case class Kms(keyId: String, context: Map[String, String] = Map.empty) extends ServerSideEncryption
}
