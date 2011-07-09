package com.github.philcali
package cronish.dsl
package app

import monido._

import actors.Actor
import scala.io.Source.{fromFile => open}
import util.matching.Regex

import java.io.{BufferedReader, InputStreamReader}

class App extends xsbti.AppMain {

  case class Add(line: String)
  case class Remove(line: String)
  case object Stop

  // Application extractors
  object Task extends Regex("""\s*(.+)\s+runs\s+([every|Every].+)""") {
    def apply(cmd: String, crons: String) = "%s runs %s".format(cmd, crons)
  }

  object Pop extends Regex("""-r\s+(\d+)""")

  object Daemon {
    def unapply(input: String) = {
      if(input.contains("-d")) {
        if(input.contains("-t")) Some(true) else Some(false)
      } else None 
    } 
  }

  class CronishDaemon(deleteOnExit: Boolean) extends Actor {
    private var executing = true  

    def act = {
      load
      
      val monitor = FileMonido(conf) {
        case ModifiedOrCreated(file) =>
          val described = open(conf).getLines.toList
          described.diff(list).foreach(this ! Add(_))
          list.diff(described).foreach(this ! Remove(_))
        case Deleted(path) => this ! Stop
      }

      loopWhile(executing) {
        react {
          case Stop => monitor.kill; shutdown() 
          case Add(line) => launch(line)
          case Remove(line) => 
            Scheduled.active.find(_.task.description == Some(line)).map(_.stop)
        }
      }
    }

    private def shutdown() {
      Scheduled.destroyAll
      if (deleteOnExit) new java.io.File(conf).delete
      executing = false
    }

    def list = Scheduled.active.map(_.task.description.get) 

    def load = open(conf).getLines.foreach(launch)
  }

  val conf = {
    val loc = new java.io.File(System.getProperty("user.home") + "/.cronishtab")
    if(!loc.exists) {
      loc.createNewFile
    }
    loc.getAbsolutePath
  }

  def launch(line: String) = { 
    val Task(cmd, syntax) = line
    syntax.cronOption.fold(println, { cron =>
      job {
        val rt = Runtime.getRuntime()
        val pr = rt.exec(cmd)
        val in = new BufferedReader(new InputStreamReader(pr.getInputStream))
        def read(in: BufferedReader): Unit = in.readLine match {
          case line: String => println(line); read(in)
          case _ => 
        }
        read(in)
      } describedAs Task(cmd, syntax) runs syntax 
    })
  }

  def printHelp = {
    println("""
  cronish [-h] | [-d] | [-t] | [-l] | [-r index] | [text] | [task runs text]
    -h prints this help
    -d runs as a daemon
    -t delete task tab on daemon end (to be used with -d)
    -l lists active tasks 
    -r stops a task 
    run commandline task for english 
    defaults to parsing english to cron
""")
  }

  def run (configuration: xsbti.AppConfiguration) = {
    val args = configuration.arguments

    if (args.contains("-h") || args.length == 0) printHelp
    else {
      args.mkString(" ") match {
        case Daemon(deleteOnExit) =>
          val daemon = new CronishDaemon(deleteOnExit)
          daemon.start
          println("Press Enter to quit")
          Console.readLine
          daemon ! Stop
        case Pop(ix) => 
          val lines = open(conf).getLines.zipWithIndex.toList

          val writer = new java.io.FileWriter(conf)
          for((line, i) <- lines; if i != ix.toInt) {
            writer.write(line + "\n")
          }
          writer.close()
          println("Successfully removed %s".format(ix))
        case Task(cmd, syntax) => 
          val writer = new java.io.FileWriter(conf, true)
          writer.write(Task(cmd, syntax) + "\n")
          writer.close()
          println("Successfully wrote %s" format (Task(cmd, syntax)))
        case list if list.contains("-l") => 
          open(conf).getLines.zipWithIndex.foreach { tup =>
            println("{%d}: %s".format(tup._2, tup._1))
          }
        case syntax => println(syntax.cronOption.fold(e => e, { c =>
            "%s next run is %s".format(c.full, c.nextTime)
          }))
      }
    }

    Exit(0)
  }

  case class Exit(val code: Int) extends xsbti.Exit
}
