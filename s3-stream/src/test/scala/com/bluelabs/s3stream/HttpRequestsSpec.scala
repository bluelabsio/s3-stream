package com.bluelabs.s3stream

import akka.http.scaladsl.model.ContentTypes
import akka.http.scaladsl.model.headers.`Content-Type`
import akka.util.ByteString
import com.bluelabs.akkaaws.AwsHeaders.ServerSideEncryptionAlgorithm.{KMS, AES256}

import org.scalatest.{Matchers, FlatSpec}

class HttpRequestsSpec extends FlatSpec with Matchers {

  import HttpRequests._
  import com.bluelabs.akkaaws.AwsHeaders._

  "metadataHeaders" should "not add the contentType header when contentType is default" in {
    metadataHeaders(Metadata()) should not contain `Content-Type`(ContentTypes.`application/octet-stream`)
  }

  it should "not add the contentType header contentType is custom" in {
    val customContentType = ContentTypes.`application/json`
    metadataHeaders(Metadata(customContentType)) should not contain `Content-Type`(customContentType)
  }

  it should "not add the x-amz-server-side-encryption header when the server side encryption is None" in {
    metadataHeaders(Metadata(serverSideEncryption = ServerSideEncryption.None)) should not contain a[`X-Amz-Server-Side-Encryption`]
  }

  it should "add the x-amz-server-side-encryption with AES256 header when the server side encryption is AES256" in {
    metadataHeaders(Metadata(serverSideEncryption = ServerSideEncryption.Aes256)) should contain(`X-Amz-Server-Side-Encryption`(AES256))
  }

  it should "add the x-amz-server-side-encryption with KMZ header when the server side encryption is KMS" in {
    metadataHeaders(Metadata(serverSideEncryption = ServerSideEncryption.Kms("my-id"))) should contain(`X-Amz-Server-Side-Encryption`(KMS))
  }

  it should "add the x-amz-server-side-encryption-kms-id with KMZ header when the server side encryption is KMS" in {
    metadataHeaders(Metadata(serverSideEncryption = ServerSideEncryption.Kms("my-id"))) should contain(`X-Amz-Server-Side-Encryption-Aws-Kms-Key-Id`("my-id"))
  }

  "initiateMultipartUploadRequest" should "set the default entity contentType when it is not specified" in {
    val requestEntity = initiateMultipartUploadRequest(S3Location("test-bucket", "test-key"), Metadata()).entity

    requestEntity.contentType shouldBe ContentTypes.`application/octet-stream`
  }

  it should "set the custom entity contentType when it is specified" in {
    val customContentType = ContentTypes.`application/json`
    val requestEntity = initiateMultipartUploadRequest(S3Location("test-bucket", "test-key"), Metadata(contentType = customContentType)).entity

    requestEntity.contentType shouldBe customContentType
  }

  "uploadPartRequest" should "set the default entity contentType when it is not specified" in {
    val requestEntity = uploadPartRequest(MultipartUpload(S3Location("test-bucket", "test-key"), "my-upload-id"), 1, ByteString.empty, Metadata()).entity

    requestEntity.contentType shouldBe ContentTypes.`application/octet-stream`
  }

  it should "set the custom entity contentType when it is specified" in {
    val customContentType = ContentTypes.`application/json`
    val requestEntity = uploadPartRequest(MultipartUpload(S3Location("test-bucket", "test-key"), "my-upload-id"), 1, ByteString.empty, Metadata(contentType = customContentType)).entity

    requestEntity.contentType shouldBe customContentType
  }

}
