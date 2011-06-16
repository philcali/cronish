package com.github.philcali
package cronish
package dsl

import java.util.{Timer, TimerTask}

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

  def active = crons.toList
}

class CronTask(val description: Option[String], work: => Unit) {
  def run() = work

  def runs(definition: String) = executes(definition)
  def runs(definition: Cron) = executes(definition)

  def executes(definition: String): Scheduled = executes(definition.cron)
  def executes(definition: Cron): Scheduled = Scheduled(this, definition)

  def describedAs(something: String) = new CronTask(Some(something), work)
}

private [dsl] class Scheduled(
    val task: CronTask, 
    val definition: Cron, 
    delay: Long) { 
  protected val timer = new Timer

  def stop() = { timer.cancel(); Scheduled.destroy(this) }

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
    def run() = {
      task.run()
      schedule
    }
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

  private def start() = if (delay <= 0) schedule else {
    timer.schedule(new TimerTask {
      def run() = schedule
    }, delay)
  }

  private def preserve[A](block: => A): A = {
    stop(); block
  }

  start()
}
