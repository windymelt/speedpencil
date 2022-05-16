import Dependencies._

val scalaV = "2.13.8"
val akkaV = "2.6.19"
val akkaHttpV = "10.2.9"
val utestV = "0.7.4"
val scalaJsDomV = "1.0.0"
val circeV = "0.14.1"

ThisBuild / scalaVersion := scalaV
ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / organization := "com.example"
ThisBuild / organizationName := "example"

lazy val root = (project in file("."))
  .aggregate(backend, frontend)

lazy val backend = (project in file("backend"))
  .settings(commonSettings: _*)
  .settings(
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-stream" % akkaV,
      "com.typesafe.akka" %% "akka-http" % akkaHttpV,
      scalaTest % Test
    ),
    resourceGenerators in Compile += Def.task {
      val f1 = (fastOptJS in Compile in frontend).value.data
      Seq(f1, new File(f1.getPath + ".map"))
    }.taskValue,
    watchSources ++= (watchSources in frontend).value,
    javaOptions ++= Seq(
      "-Xms100m",
      "-Xmx1g"
    )
  )
  .dependsOn(sharedJvm)

lazy val frontend =
  project
    .in(file("frontend"))
    .enablePlugins(ScalaJSPlugin)
    .settings(commonSettings: _*)
    .settings(
      scalaJSUseMainModuleInitializer := true,
      testFrameworks += new TestFramework("utest.runner.Framework"),
      libraryDependencies ++= Seq(
        "org.scala-js" %%% "scalajs-dom" % scalaJsDomV,
        "com.lihaoyi" %%% "utest" % utestV % "test"
      )
    )
    .dependsOn(sharedJs)

lazy val shared =
  (crossProject(JSPlatform, JVMPlatform).crossType(CrossType.Pure) in file(
    "shared"
  ))
    .settings(
      scalaVersion := scalaV,
      libraryDependencies ++= Seq(
        "io.circe" %%% "circe-core",
        "io.circe" %%% "circe-generic",
        "io.circe" %%% "circe-parser"
      ).map(_ % circeV)
    )

lazy val sharedJvm = shared.jvm
lazy val sharedJs = shared.js

def commonSettings = Seq(
  scalaVersion := scalaV,
  scalacOptions ++= Seq(
    "-deprecation",
    "-feature",
    "-encoding",
    "utf8",
    "-unchecked",
    "-Xlint"
  )
)
