package com.bluelabs.s3stream

import akka.http.scaladsl.marshallers.xml.ScalaXmlSupport._
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model.Uri.Query
import akka.http.scaladsl.model.headers.Host
import akka.http.scaladsl.model.{HttpMethods, HttpRequest, RequestEntity, Uri}
import akka.util.ByteString

import scala.concurrent.{ExecutionContext, Future}

object HttpRequests {
  def initiateMultipartUploadRequest(s3Location: S3Location): HttpRequest = {
    HttpRequest(method = HttpMethods.POST)
      .withHeaders(Host(requestHost(s3Location)))
      .withUri(requestUri(s3Location).withQuery(Query("uploads")))
  }

  def uploadPartRequest(upload: MultipartUpload, partNumber: Int, payload: ByteString): HttpRequest = {
    HttpRequest(method = HttpMethods.PUT)
      .withHeaders(Host(requestHost(upload.s3Location)))
      .withUri(requestUri(upload.s3Location).withQuery(Query("partNumber" -> partNumber.toString, "uploadId" -> upload.uploadId)))
      .withEntity(payload)
  }

  def completeMultipartUploadRequest(upload: MultipartUpload, parts: Seq[(Int, String)])(implicit ec: ExecutionContext): Future[HttpRequest] = {
    val payload = <CompleteMultipartUpload>
        {
          parts.map{case (partNumber, etag) => <Part><PartNumber>{partNumber}</PartNumber><ETag>{etag}</ETag></Part>}
        }
      </CompleteMultipartUpload>
    for {
      entity <- Marshal(payload).to[RequestEntity]
    } yield {
      HttpRequest(method = HttpMethods.POST)
        .withHeaders(Host(requestHost(upload.s3Location)))
        .withUri(requestUri(upload.s3Location).withQuery(Query("uploadId" -> upload.uploadId)))
        .withEntity(entity)
    }
  }


  def requestHost(s3Location: S3Location): Uri.Host = Uri.Host(s"${s3Location.bucket}.s3.amazonaws.com")

  def requestUri(s3Location: S3Location): Uri = Uri(s"/${s3Location.key}").withHost(requestHost(s3Location)).withScheme("https")
}
