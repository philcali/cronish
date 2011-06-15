name := "cronish"

organization := "com.github.philcali"

version := "0.0.1"

scalaVersion := "2.9.0"

crossScalaVersions := Seq("2.9.0", "2.8.1", "2.8.0")

libraryDependencies += "com.github.philcali" %% "scalendar" % "0.0.5"

libraryDependencies <+= (scalaVersion) {
  case v if v contains "2.8" =>  
    "org.scalatest" % "scalatest" % "1.3" % "test"
  case _ =>
    "org.scalatest" %% "scalatest" % "1.4.1" % "test"
}
