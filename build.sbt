name := "cronish"

organization := "com.github.philcali"

version := "0.1.3"

parallelExecution in Test := false

scalaVersion := "2.10.0"

scalacOptions <++= scalaVersion map {
  case sv if sv startsWith "2.10" => Seq("-language:postfixOps")
  case _ => Nil
}

crossScalaVersions := Seq(
  "2.10.0",
  "2.9.2", "2.9.1", "2.9.0-1", "2.9.0"
)

resolvers += "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/"

scalacOptions <++= scalaVersion map {
  case sv if sv startsWith "2.10" =>
    Seq("-feature", "-language:implicitConversions")
  case _ => Nil
}

libraryDependencies <+= (organization) (_ %% "scalendar" % "0.1.4")

libraryDependencies <++= scalaVersion {
  case sv if sv startsWith "2.10" => Seq(
    "com.typesafe.akka" %% "akka-actor" % "2.1.0",
    "org.scalatest" %% "scalatest" % "1.9" % "test"
  )
  case _ => Seq(
    "com.typesafe.akka" % "akka-actor" % "2.0.5",
    "org.scalatest" %% "scalatest" % "1.8" % "test"
  )
}

publishTo <<= version { v =>
  val nexus = "https://oss.sonatype.org/"
  if (v.trim.endsWith("SNAPSHOT"))
    Some("snapshots" at nexus + "content/repositories/snapshots")
  else
    Some("releases" at nexus + "service/local/staging/deploy/maven2")
}

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
