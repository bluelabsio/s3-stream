import sbt._

object Dependencies {
  // Versions
  lazy val akkaVersion = "2.4.4"
  lazy val scalatestVersion = "2.2.6"

  val akka = "com.typesafe.akka" %% "akka-actor" % akkaVersion
  val akkaStream = "com.typesafe.akka" %% "akka-stream" % akkaVersion
  val akkaHttpCore = "com.typesafe.akka" %% "akka-http-core" % akkaVersion
  val akkaHttpExperimental = "com.typesafe.akka" %% "akka-http-experimental" % akkaVersion
  val akkaTestkit = "com.typesafe.akka" %% "akka-testkit" % akkaVersion
  val akkaStreamTestkit = "com.typesafe.akka" %% "akka-stream-testkit" % akkaVersion
  val akkaHttpXML = "com.typesafe.akka" %% "akka-http-xml-experimental" % akkaVersion

  val sprayJson = "io.spray" %%  "spray-json" % "1.3.2"

  val scalatest = "org.scalatest" %% "scalatest" % scalatestVersion

  val logback = "ch.qos.logback" % "logback-classic" % "1.1.3"
  val scalaLogging = "com.typesafe.scala-logging" %% "scala-logging" % "3.1.0"

  val fastparse = "com.lihaoyi" %% "fastparse" % "0.3.7"

  val awsSignatureDeps = Seq(akkaHttpCore, logback, scalaLogging, fastparse, sprayJson,
    scalatest % Test, akkaStreamTestkit % Test)

  val s3StreamDeps = Seq(akkaHttpCore, akkaStream, akkaHttpExperimental, akkaHttpXML,
    akkaTestkit % Test, akkaStreamTestkit % Test, scalatest % Test)

}

