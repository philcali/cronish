import sbt._

class Project(info: ProjectInfo) extends DefaultProject(info) {
  val scalendar = "com.github.philcali" %% "scalendar" % "0.0.2"
  val scalatest = "org.scalatest" % "scalatest" % "1.3" % "test"
}
