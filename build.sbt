publishTo := Some("Scala Tools Nexus" at 
                  "http://nexus.scala-tools.org/content/repositories/releases/")

credentials += Credentials(Path.userHome / ".ivy2" / ".credentials")

name := "cronish"

organization := "com.github.philcali"

version := "0.1.0"

parallelExecution in Test := false

crossScalaVersions := Seq("2.9.1", "2.9.0-1", "2.9.0", "2.8.1", "2.8.0")

libraryDependencies <+= (organization) (_ %% "scalendar" % "0.1.0")

libraryDependencies <+= (scalaVersion) {
  case v if v contains "2.8" =>  
    "org.scalatest" % "scalatest" % "1.3" % "test"
  case _ =>
    "org.scalatest" %% "scalatest" % "1.6.1" % "test"
}
