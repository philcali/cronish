name := "cronish"

organization := "com.github.philcali"

version := "0.1.1"

parallelExecution in Test := false

crossScalaVersions := Seq("2.9.1", "2.9.0-1", "2.9.0", "2.8.1", "2.8.0")

libraryDependencies <+= (organization) (_ %% "scalendar" % "0.1.1")

libraryDependencies <+= (scalaVersion) {
  case v if v contains "2.8" =>  
    "org.scalatest" % "scalatest" % "1.3" % "test"
  case _ =>
    "org.scalatest" %% "scalatest" % "1.6.1" % "test"
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
