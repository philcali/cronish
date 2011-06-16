import sbt._

import Keys._

object General {
  val settings = Defaults.defaultSettings ++ Seq (
    organization := "com.github.philcali",
    version := "0.0.1",
    scalaVersion := "2.9.0",
    crossScalaVersions := Seq("2.9.0", "2.8.1", "2.8.0")
  )
}

object Cronish extends Build {
  lazy val cronish = Project (
    "cronish",
    file("core"),
    settings = General.settings ++ Seq (
      libraryDependencies += "com.github.philcali" %% "scalendar" % "0.0.5",
      libraryDependencies <+= (scalaVersion) {
        case v if v contains "2.8" =>  
          "org.scalatest" % "scalatest" % "1.3" % "test"
        case _ =>
          "org.scalatest" %% "scalatest" % "1.4.1" % "test"
      }
    )
  )

  lazy val cronishApp = Project (
    "cronish-app",
    file("app"),
    settings = General.settings ++ Seq (
      libraryDependencies += "org.scala-tools.sbt" % "launcher-interface" % "0.7.7" % "provided" from "http://databinder.net/repo/org.scala-tools.sbt/launcher-interface/0.7.7/jars/launcher-interface.jar"
    )
  ) dependsOn cronish

  lazy val cronishPlugin = Project (
    "cronish-sbt",
    file("plugin"),
    settings = General.settings
  ) dependsOn cronish
}
