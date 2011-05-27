import sbt._

class Project(info: ProjectInfo) extends DefaultProject(info) {
  val scalendar = "com.github.philcali" %% "scalendar" % "0.0.4"

  // Get the right Test, for the right job
  val scalatestDefinition = buildScalaVersion match {
    case v if v.contains("2.9") => 
      "org.scalatest" % "scalatest_%s".format(v) % "1.4.1"
    case v if v.contains("2.8") => 
      "org.scalatest" % "scalatest" % "1.3"
    case _ => "org.scalatest" % "scalatest" % "1.1"
  }
  
  lazy val scalatest = scalatestDefinition % "test"
}
