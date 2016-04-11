# s3-stream
Akka Streaming Client for S3 and Supporting Libraries

# Components

## s3-stream

A library built around Akka-Http to stream an upload to S3. The initialization request will be sent, incoming stream will be chunked and uploaded, then the completion request is sent.

### Usage

Add to your `build.sbt`:

```
resolvers += Resolver.jcenterRepo
libraryDependencies += "com.bluelabs" %% "s3-stream" % "0.0.3"
```

Then in your application:

```scala

  implicit val system = ActorSystem()
  implicit val mat: Materializer = ActorMaterializer()
  implicit val ec = mat.executionContext

  val creds = AWSCredentials("KEYGOESHERE", "SECRETGOESHERE")
  val stream: S3Stream = new S3Stream(creds)

  val input: Source[ByteString, Future[IOResult]] = StreamConverters.fromInputStream(...whatever) // Or something else to generate a stream of ByteStrings
  val sink: Sink[ByteString, Future[CompleteMultipartUploadResult]] = stream.multipartUpload(S3Location("bucketGoesHere", "keygoeshere"))

  val result: Future[CompleteMultipartUploadResult] = input.runWith(sink)

```

## akka-http-aws

Sign Akka-HTTP requests using AWS credentials, following the
[AWS Signature v4](http://docs.aws.amazon.com/AmazonS3/latest/API/sig-v4-authenticating-requests.html) process.

### Usage

Add to your `build.sbt`:

```
resolvers += Resolver.jcenterRepo
libraryDependencies += "com.bluelabs" %% "akka-http-aws" % "0.0.3"
```

Then in your application:

```scala

val credentials = AWSCredentials("KEYGOESHERE", "SECRETGOESHERE")
val signingKey = SigningKey(credentials, CredentialScope(LocalDate.now(), region, "s3"))
val req = HttpRequest(... with assorted params ...)
val signedRequest = Signer.signedRequest(req, signingKey)

```
