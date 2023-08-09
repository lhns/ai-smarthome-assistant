ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "2.13.11"

lazy val root = (project in file("."))
  .settings(
    name := "ai-smarthome-assistant",
    libraryDependencies ++= Seq(
      "com.softwaremill.sttp.openai" %% "core" % "0.0.7",
      "com.softwaremill.sttp.client4" %% "http4s-backend" % "4.0.0-M1",
      "org.http4s" %% "http4s-jdk-http-client" % "0.9.1",
      "org.http4s" %% "http4s-client" % "0.23.23",
      "org.bidib.com.github.markusbernhardt" % "proxy-vole" % "1.1.0",
    ),
  )
