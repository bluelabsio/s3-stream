package com.bluelabs.akkaaws

import java.io.File
import java.time.{ZoneId, ZonedDateTime}

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpMethods, HttpRequest, StatusCodes}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Sink
import akka.util.ByteString
import fastparse.all._

import scala.language.postfixOps
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext}
import scala.io.Source

sealed trait AWSCredentials {
  def accessKeyId: String
  def secretAccessKey: String
}

case class BasicCredentials(accessKeyId: String, secretAccessKey: String) extends AWSCredentials

case class AWSCredentialLookupFailure(msg: String) extends RuntimeException(msg)

object AWSCredentials {
  private val AWSKeyIdLength = 20
  private val AWSSecretKeyLength = 40
  private val letters = P( CharIn('a' to 'z') | CharIn('A' to 'Z') )
  private val numbers = P( CharIn('0' to '9') )
  private val newline = P( "\n" | "\r\n" )
  private val whitespace = P( " " | "\t" )
  private val profileName: Parser[String] = P( "[" ~ (letters | numbers).rep(1).! ~ "]" )
  private val keyIdValue: Parser[String] = P( (letters | numbers).rep(AWSKeyIdLength).! )
  private val secretKeyValue: Parser[String] = P( (letters | numbers | "/").rep(AWSSecretKeyLength).! )
  private val credentialParser = P( profileName ~ newline ~
    "aws_access_key_id" ~ whitespace.? ~ "=" ~ whitespace.? ~ keyIdValue ~/ whitespace.rep(0) ~ newline.rep(1) ~
    "aws_secret_access_key" ~ whitespace.? ~ "=" ~ whitespace.? ~ secretKeyValue ~/ whitespace.rep(0) ~ newline.rep(1))
  private val credentialFileParser: Parser[Seq[(String, String, String)]] = (credentialParser ~/ whitespace.rep(0) ~ newline.rep(0) ).rep ~ End

  def apply(accessKeyId: String, secretAccessKey: String): AWSCredentials =
    BasicCredentials(accessKeyId, secretAccessKey)


  /**
    * Lookup credentials from the system environment.
    * First looks for the AWS_ACCESS_KEY_ID and AWS_SECRET_ACCESS_KEY, and if those are not found looks for
    * AWS_ACCESS_KEY and AWS_SECRET_KEY pair.
    * @param environmentMap Map of keys & values to look for credentials in. Defaults to sys.env.
    * @return AWSCredentials
    */
  def getEnvironmentCredentials(environmentMap: Map[String, String] = sys.env): Option[AWSCredentials] = {
    val (accessKeyId, secretAccessKey) =
      if (environmentMap.contains("AWS_ACCESS_KEY_ID") && environmentMap.contains("AWS_SECRET_ACCESS_KEY"))
        (environmentMap.get("AWS_ACCESS_KEY_ID"), environmentMap.get("AWS_SECRET_ACCESS_KEY"))

      else if (environmentMap.contains("AWS_ACCESS_KEY") && environmentMap.contains("AWS_SECRET_KEY"))
        (environmentMap.get("AWS_ACCESS_KEY"), environmentMap.get("AWS_SECRET_KEY"))

      else (None, None)

    if (accessKeyId.isEmpty || secretAccessKey.isEmpty) None
    else Some(BasicCredentials(accessKeyId.get, secretAccessKey.get))
  }

  /**
    * Look for credentials in the AWS config file, normally found in $HOME/.aws/credentials
    * @param credentialFile Optional path to a config file to use for the lookup
    * @param profile Optional name of the credential set in the config file to use
    * @return AWSCredentials
    */
  def getConfigCredentials(credentialFile: Option[String] = None, profile: String = "default"): Option[AWSCredentials] = {
    lazy val defaultCredentialFilePath =
      s"${System.getProperty("user.home")}${File.separator}.aws${File.separator}credentials"
    val credentialPath = credentialFile.getOrElse(defaultCredentialFilePath)
    val credentialFileContents = Source.fromFile(credentialPath).mkString
    credentialFileParser.parse(credentialFileContents) match {
      case Parsed.Success(profiles, _) =>
        profiles.find { case (credentialProfile, _, _) => credentialProfile.compare(profile) == 0 }
          .flatMap{ case (p, keyId, keySecret) => Some(BasicCredentials(keyId, keySecret))}
      case Parsed.Failure(last, index, extra) =>
        None
    }
  }

  /**
    * Look for credentials specified in the aws.accessKeyId and aws.secretKey java properties
    * @return AWSCredentials
    */
  def getJavaPropertyCredentials: Option[AWSCredentials] = {
    try {
      Some(BasicCredentials(System.getProperty("aws.accessKeyId"), System.getProperty("aws.secretKey")))
    } catch {
      case ex: NullPointerException => None
      case ex: SecurityException => None
    }
  }

  /**
    * Get the credentials from EC2 instance metadata.
    * @param role Optional role name to lookup. Will determine the role assigned to the machine if not given.
    * @param timeout How long to block when attempting to lookup the role and instance credentials.
    * @return AWSCredentials which will refresh when they expire. Note this will occasionally block a thread
    *         (about once every 24 hours) to lookup new credentials.
    */
  def getEC2InstanceCredentials(role: Option[String] = None, timeout: FiniteDuration = 300 milliseconds)
                               (implicit ec: ExecutionContext, s: ActorSystem, m: ActorMaterializer): Option[AWSCredentials] = {
    try {
      Some(SessionCredentials(role, timeout))
    } catch {
      case ex: ParseError => None
    }
  }

  /**
    * Returns the first located credentials, in the order:
    * Environment -> Config File -> Java Properties -> EC2 Instance Role
    *
    * Throws AWSCredentialLookupFailure if no credentials can be located
    * @return AWSCredentials
    */
  def getCredentialsFromChain(implicit ec: ExecutionContext, s: ActorSystem, m: ActorMaterializer): AWSCredentials = {
   getEnvironmentCredentials().getOrElse(
     getConfigCredentials().getOrElse(
       getJavaPropertyCredentials.getOrElse(
         getEC2InstanceCredentials().getOrElse(throw AWSCredentialLookupFailure("Failed to locate any credentials"))
       )
     )
   )
  }
}


/**
  * Self-refreshing credentials obtained from an EC2 instance's metadata
  */
case class SessionCredentials(role: Option[String], timeout: FiniteDuration = 300 milliseconds)
                            (implicit ec: ExecutionContext, s: ActorSystem, m: ActorMaterializer) extends AWSCredentials {
  private case class CredentialResponse(AccessKeyId: String, SecretAccessKey: String, Token: String, Expiration: ZonedDateTime)
  private case class InstanceProfileName(InstanceProfileArn: String)

  import spray.json._
  private case object formats extends DefaultJsonProtocol {

    implicit object zonedDateTimeFormat extends JsonFormat[ZonedDateTime] {
      override def write(obj: ZonedDateTime): JsValue = {
        JsString(obj.toString)
      }

      override def read(json: JsValue): ZonedDateTime = {
        ZonedDateTime.parse(json.convertTo[String])
      }
    }

    implicit val credentialResponseFormat = jsonFormat4(CredentialResponse)
    implicit val instanceProfileNameFormat = jsonFormat1(InstanceProfileName)
  }
  import formats._

  private def getEC2Role(implicit ec: ExecutionContext, s: ActorSystem, m: ActorMaterializer) : Some[String] = {
    val roleRequest = HttpRequest(HttpMethods.GET, "http://169.254.169.254/latest/meta-data/iam/info")
    val roleRequestFuture = Http().singleRequest(roleRequest).flatMap{
      case response if response.status == StatusCodes.OK =>
        response.entity.dataBytes
          .fold(ByteString.empty)(_ ++ _)
          .map(_.utf8String.parseJson.convertTo[InstanceProfileName])
          .map(ipn => Some(ipn.InstanceProfileArn.split('/').last))
          .runWith(Sink.head)
      case fail =>
        throw AWSCredentialLookupFailure("Failed to get the role for this machine")
    }
    Await.result(roleRequestFuture, timeout)
  }


  private def getInstanceSecurityCredentials(role: String = "default")
                                            (implicit ec: ExecutionContext, s: ActorSystem, m: ActorMaterializer) : CredentialResponse = {
    val credentialURI = s"""http://169.254.169.254/latest/meta-data/iam/security-credentials/$role"""
    val credentialRequest = HttpRequest(method = HttpMethods.GET, uri = credentialURI)
    val credentialFuture = Http().singleRequest(credentialRequest).flatMap {
      case response if response.status == StatusCodes.OK =>
        response.entity.dataBytes
          .fold(ByteString.empty)(_ ++ _)
          .map(_.utf8String.parseJson.convertTo[CredentialResponse])
          .runWith(Sink.head)
      case fail =>
        throw AWSCredentialLookupFailure("Failed to get credentials from metadata")
    }

    // I know... Await is bad. However, this will only be called once at startup, then every 24 hours afterwards
    // so I think the convenience of not returning a Future here is worth it.
    Await.result(credentialFuture, timeout)
  }

  val instanceRole = if (role.isDefined) role.get else {
    val instanceRole = getEC2Role
    if (instanceRole.isDefined) instanceRole.get
    else throw AWSCredentialLookupFailure("Failed to find an instance role")
  }

  // Refreshing credentials are stored in this var
  private var credentials: CredentialResponse = getInstanceSecurityCredentials(instanceRole)

  def isExpired: Boolean = {
    // Refresh the credentials 5 minutes before they expire
    val now = ZonedDateTime.now(ZoneId.of("UTC"))
    credentials.Expiration.isBefore(now.plusMinutes(5))
  }

  override def accessKeyId: String = {
    if (isExpired) credentials = getInstanceSecurityCredentials(instanceRole)
    credentials.AccessKeyId
  }

  override def secretAccessKey: String = {
    if (isExpired) credentials = getInstanceSecurityCredentials(instanceRole)
    credentials.SecretAccessKey
  }

  def sessionToken: String = {
    if (isExpired) credentials = getInstanceSecurityCredentials(instanceRole)
    credentials.Token
  }
}
