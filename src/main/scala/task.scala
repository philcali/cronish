package com.github.philcali
package cronish
package dsl

import java.util.{Timer, TimerTask}

import actors.Actor

import scalendar._
import conversions._
import Logging._

object Scheduled {
  private val crons = collection.mutable.ListBuffer[Scheduled]()

  def apply(task: CronTask, definition: Cron, delay: Long = 0): Scheduled = { 
    val ros = new Scheduled(task, definition, delay)
    crons += ros
    ros
  }

  def destroy(old: Scheduled) = crons -= old

  def destroyAll = crons foreach (_.stop)

  def active = crons.toList
}

class CronTask(val description: Option[String], work: => Unit) {
  def run() = work

  def runsAndDescribedAs(definition: String) = {
    new CronTask(Some("A job that runs %s" format(definition)), work)
  }

  def runs(definition: String) = executes(definition)
  def runs(definition: Cron) = executes(definition)

  def executes(definition: String): Scheduled = executes (definition.cron)
  def executes(definition: Cron): Scheduled = Scheduled(this, definition)

  def describedAs(something: String) = new CronTask(Some(something), work)
}

final class Scheduled private (
    val task: CronTask, 
    val definition: Cron, 
    delay: Long) extends Actor { parent => 

  private case object Stop
  private case object Execute

  // Executing
  private var executing = true

  // Daemon Pulsar
  private val timer = new Timer(true)

  def stop(): Unit = parent ! Stop 

  def act = {
    delayedStart

    loopWhile (executing) {
      react {
        case Stop => 
          executing = false 
          timer.cancel()
          Scheduled.destroy(this) 
        case Execute => 
          task.run()
          schedule
      }
    }
  }

  // Reset the job
  def reset() = preserve {
    Scheduled(task, definition, delay)
  }

  def starting(date: Scalendar) = preserve {
    val now = Scalendar.now
    val larger = if (date < now) now else date 
    Scheduled(task, definition, larger.time - now.time) 
  }

  def in(d: Long) = preserve {
    Scheduled(task, definition, d)
  }

  def in(d: Evaluated) = preserve {
    Scheduled(task, definition, d.milliseconds)
  }

  private def interval: TimerTask = new TimerTask {
    def run() = parent ! Execute
  }
 
  private def schedule = try {
    timer.schedule(interval, definition.next) 
  } catch {
    case e: IllegalArgumentException => 
      info("Given cron scheduler a negative time: %s".format(definition.full))
    case e: IllegalStateException => 
      info("Tried to initiate cron task after scheduler stopped.")
    case e: Exception => 
      severe("Cron execution error: %s".format(e.getMessage))
  }

  private def delayedStart() = if (delay <= 0) schedule else {
    timer.schedule(new TimerTask {
      def run() = schedule
    }, delay)
  }

  private def preserve[A](block: => A): A = {
    stop(); block
  }

  start()
}
