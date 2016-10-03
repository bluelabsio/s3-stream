package com.bluelabs.akkaaws

import akka.http.scaladsl.model.headers.{ModeledCustomHeaderCompanion, ModeledCustomHeader}

import scala.util.{Failure, Success, Try}


object AwsHeaders {

  sealed abstract class ServerSideEncryptionAlgorithm(val name: String)
  object ServerSideEncryptionAlgorithm {
    case object AES256 extends ServerSideEncryptionAlgorithm("AES256")
    case object KMS extends ServerSideEncryptionAlgorithm("aws:kms")

    def fromString(raw: String): Try[ServerSideEncryptionAlgorithm] = raw match {
      case "AES256" => Success(AES256)
      case "aws:kms" => Success(KMS)
      case invalid => Failure(new IllegalArgumentException(s"$invalid is not a valid server side encryption algorithm."))
    }
  }

  object `X-Amz-Server-Side-Encryption` extends ModeledCustomHeaderCompanion[`X-Amz-Server-Side-Encryption`] {
    override def name: String = "X-Amz-Server-Side-Encryption"
    override def parse(value: String): Try[`X-Amz-Server-Side-Encryption`] =
      ServerSideEncryptionAlgorithm.fromString(value).map(new `X-Amz-Server-Side-Encryption`(_))
  }

  final case class `X-Amz-Server-Side-Encryption`(algorithm: ServerSideEncryptionAlgorithm) extends ModeledCustomHeader[`X-Amz-Server-Side-Encryption`] {

    override def companion: ModeledCustomHeaderCompanion[`X-Amz-Server-Side-Encryption`] = `X-Amz-Server-Side-Encryption`

    override def value(): String = algorithm.name

    override def renderInResponses(): Boolean = true

    override def renderInRequests(): Boolean = true
  }

  object `X-Amz-Server-Side-Encryption-Aws-Kms-Key-Id` extends ModeledCustomHeaderCompanion[`X-Amz-Server-Side-Encryption-Aws-Kms-Key-Id`] {
    override def name: String = "X-Amz-Server-Side-Encryption-Aws-Kms-Key-Id"
    override def parse(value: String): Try[`X-Amz-Server-Side-Encryption-Aws-Kms-Key-Id`] =
      Success(new `X-Amz-Server-Side-Encryption-Aws-Kms-Key-Id`(value))
  }

  final case class `X-Amz-Server-Side-Encryption-Aws-Kms-Key-Id`(id: String) extends ModeledCustomHeader[`X-Amz-Server-Side-Encryption-Aws-Kms-Key-Id`] {

    override def companion: ModeledCustomHeaderCompanion[`X-Amz-Server-Side-Encryption-Aws-Kms-Key-Id`] = `X-Amz-Server-Side-Encryption-Aws-Kms-Key-Id`

    override def value(): String = id

    override def renderInResponses(): Boolean = true

    override def renderInRequests(): Boolean = true
  }

  // TODO add `x-amz-server-side-encryption-context` header.

}
