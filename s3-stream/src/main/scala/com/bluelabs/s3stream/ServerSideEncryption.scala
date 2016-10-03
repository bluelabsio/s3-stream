package com.bluelabs.s3stream


sealed trait ServerSideEncryption

object ServerSideEncryption {

  case object None extends ServerSideEncryption

  case object Aes256 extends ServerSideEncryption

  // TODO add context
  case class Kms(keyId: String) extends ServerSideEncryption
}
