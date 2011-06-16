package com.github.philcali.cronish.dsl
package app

class App extends xsbti.MainResult {
  def printHelp = {
    println("""
  cronish [-h] | [text] | [-t task text]
    -h prints this help
    -t run commandline task for english
    defaults to parsing english to cron
""")
  }

  def run (configuration: xsbti.AppConfiguration) = {
    val args = configuration.arguments

    if (args.contains("-h") || args.length == 0) printHelp
    else {
      args.mkString(" ") match {
        case Task(cmd, syntax) => syntax.cronOption match {
          case Left(msg) => println("Oops: %s".format(msg))
          case Right(cron) => 
            job {
              val rt = Runtime.getRuntime()
              rt.exec("%s".format(cmd))
            } describedAs "Running %s %s".format(cmd, syntax) runs cron
        }
        case syntax => println(syntax.cronOption.fold(e => e, _.full)) 
      }
    }
    Exit(0)
  }

  val Task = """-t\s+(.+)\s+([every|Every].+)""".r

  case class Exit(val code: Int) extends xsbti.Exit
}
