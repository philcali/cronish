import sbt._

import Keys._

object General {
  val settings = Defaults.defaultSettings ++ Seq (
    organization := "com.github.philcali",
    version := "0.0.2",
    publishTo := Some("Scala Tools Nexus" at 
                      "http://nexus.scala-tools.org/content/repositories/releases/"),
    credentials += Credentials(Path.userHome / ".ivy2" / ".credentials")
  )
}

object Cronish extends Build {
  lazy val cronishRoot = Project (
    "cronish-root",
    file("."),
    settings = General.settings
  ) aggregate (cronish, cronishApp)

  lazy val cronish: Project = Project (
    "cronish",
    file("core"),
    settings = General.settings ++ Seq (
      crossScalaVersions := Seq("2.9.0", "2.8.1", "2.8.0"),
      libraryDependencies += "com.github.philcali" %% "scalendar" % "0.0.5",
      libraryDependencies <+= (scalaVersion) {
        case v if v contains "2.8" =>  
          "org.scalatest" % "scalatest" % "1.3" % "test"
        case _ =>
          "org.scalatest" %% "scalatest" % "1.4.1" % "test"
      }
    )
  )

  lazy val cronishApp: Project = Project (
    "cronish-app",
    file("app"),
    settings = General.settings ++ Seq (
      libraryDependencies ++= Seq (
        "com.github.philcali" %% "monido-core" % "0.0.3",
        "org.scala-tools.sbt" % "launcher-interface" % "0.10.0"
      )
    )
  ) dependsOn cronish

  lazy val cronishPlugin: Project = Project (
    "cronish-sbt",
    file("plugin"),
    settings = General.settings ++ Seq (
      sbtPlugin := true
    )
  ) dependsOn cronish
}
