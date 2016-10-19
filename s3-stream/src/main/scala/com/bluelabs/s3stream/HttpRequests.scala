package com.bluelabs.s3stream

import com.bluelabs.akkaaws.AwsHeaders.ServerSideEncryptionAlgorithm.{KMS, AES256}

import scala.collection.immutable
import scala.concurrent.{ExecutionContext, Future}
import akka.http.scaladsl.marshallers.xml.ScalaXmlSupport._
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.Uri.Query
import akka.http.scaladsl.model.headers._
import akka.util.ByteString
import com.bluelabs.akkaaws.AwsHeaders._

object HttpRequests {

  def s3Request(s3Location: S3Location, method: HttpMethod = HttpMethods.GET, uriFn: (Uri => Uri) = identity): HttpRequest = {
    HttpRequest(method)
      .withHeaders(Host(requestHost(s3Location)))
      .withUri(uriFn(requestUri(s3Location)))
  }

  def initiateMultipartUploadRequest(s3Location: S3Location, metadata: Metadata): HttpRequest = {
    s3Request(s3Location, HttpMethods.POST, _.withQuery(Query("uploads")))
      .withEntity(metadata.contentType, ByteString.empty)
      .mapHeaders(_ ++ metadataHeaders(metadata))
  }


  def metadataHeaders(metadata: Metadata): immutable.Iterable[HttpHeader] = {
    metadata.serverSideEncryption match {
      case ServerSideEncryption.Aes256 =>
        List(new `X-Amz-Server-Side-Encryption`(AES256))
      case ServerSideEncryption.Kms(keyId, context) =>
        List(
          new `X-Amz-Server-Side-Encryption`(KMS),
          new `X-Amz-Server-Side-Encryption-Aws-Kms-Key-Id`(keyId)
        ) ++ (if(context.isEmpty) {
          List.empty
        } else {
          List(`X-Amz-Server-Side-Encryption-Context`(context))
        })
      case ServerSideEncryption.None =>
        Nil
    }
  }

  def getRequest(s3Location: S3Location): HttpRequest = {
    s3Request(s3Location)
  }

  def uploadPartRequest(upload: MultipartUpload, partNumber: Int, payload: ByteString, metadata: Metadata): HttpRequest = {
    s3Request(upload.s3Location,
      HttpMethods.PUT,
      _.withQuery(Query("partNumber" -> partNumber.toString, "uploadId" -> upload.uploadId))
    ).withEntity(metadata.contentType, payload).mapHeaders(_ ++ metadataHeaders(metadata))
  }

  def completeMultipartUploadRequest(upload: MultipartUpload, parts: Seq[(Int, String)])(implicit ec: ExecutionContext): Future[HttpRequest] = {
    val payload = <CompleteMultipartUpload>
      {parts.map { case (partNumber, etag) => <Part><PartNumber>{partNumber}</PartNumber><ETag>{etag}</ETag></Part>}}
    </CompleteMultipartUpload>
    for {
      entity <- Marshal(payload).to[RequestEntity]
    } yield {
      s3Request(upload.s3Location,
        HttpMethods.POST,
        _.withQuery(Query("uploadId" -> upload.uploadId))
      ).withEntity(entity)
    }
  }


  def requestHost(s3Location: S3Location): Uri.Host = Uri.Host(s"${s3Location.bucket}.s3.amazonaws.com")

  def requestUri(s3Location: S3Location): Uri = Uri(s"/${s3Location.key}").withHost(requestHost(s3Location)).withScheme("https")
}
