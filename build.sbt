publishTo := Some("Scala Tools Nexus" at 
                  "http://nexus.scala-tools.org/content/repositories/releases/")

credentials += Credentials(Path.userHome / ".ivy2" / ".credentials")

name := "cronish"

organization := "com.github.philcali"

version := "0.0.2"

libraryDependencies <+= (organization) (_ %% "scalendar" % "0.0.5")

libraryDependencies <+= (scalaVersion) {
  case v if v contains "2.8" =>  
    "org.scalatest" % "scalatest" % "1.3" % "test"
  case _ =>
    "org.scalatest" %% "scalatest" % "1.4.1" % "test"
}
