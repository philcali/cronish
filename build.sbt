name := "cronish"

organization := "com.github.philcali"

version := "0.1.5"

parallelExecution in Test := false

scalaVersion := "2.12.13"

crossScalaVersions := Seq(
  "2.12.13",
  "2.11.12"
)

resolvers += "Typesafe Repository" at "https://repo.typesafe.com/typesafe/releases/"

scalacOptions ++= Seq("-feature", "-language:implicitConversions", "-language:postfixOps")

libraryDependencies ++= Seq(
    "com.github.philcali" %% "scalendar" % "0.1.5",
    "org.scala-lang.modules" %% "scala-parser-combinators" % "1.0.5",
    "com.typesafe.akka" %% "akka-actor" % "2.4.17",
    "org.scalatest" %% "scalatest" % "3.0.1" % "test"
  )

publishMavenStyle := true

publishArtifact in Test := false

pomIncludeRepository := { x => false }

pomExtra := (
  <url>https://github.com/philcali/cronish</url>
  <licenses>
    <license>
      <name>The MIT License</name>
      <url>http://www.opensource.org/licenses/mit-license.php</url>
      <distribution>repo</distribution>
    </license>
  </licenses>
  <scm>
    <url>git@github.com:philcali/cronish.git</url>
    <connection>scm:git:git@github.com:philcali/cronish.git</connection>
  </scm>
  <developers>
    <developer>
      <id>philcali</id>
      <name>Philip Cali</name>
      <url>http://philcalicode.blogspot.com/</url>
    </developer>
  </developers>
)
