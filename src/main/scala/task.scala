package com.github.philcali.cronish 
package jobs

import dsl.string2cron
import java.util.{Timer, TimerTask}

import com.github.philcali.scalendar._
import conversions._

class CronTask(val description: Option[String], work: => Unit) {
  def run() = work

  def runs(definition: String) = executes(definition)
  def runs(definition: Cron) = executes(definition)

  def executes(definition: String): Scheduled = executes(definition.cron)
  def executes(definition: Cron): Scheduled = Scheduled(this, definition)

  def describedAs(something: String) = new CronTask(Some(something), work)
}

object Scheduled {
  private val crons = collection.mutable.ListBuffer[Scheduled]()

  def apply(task: CronTask, definition: Cron): Scheduled = 
    apply(task, definition, 0)

  def apply(task: CronTask, definition: Cron, delay: Long) = {
    val ros = new Scheduled(task, definition, delay)
    crons += ros
    ros
  }

  def destroy(old: Scheduled) = crons -= old

  def active = crons.toList
}

class Scheduled(val task: CronTask, val definition: Cron, delay: Long) {
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
      println("Given cron scheduler a negative time")
    case e: IllegalStateException => 
      println("Tried to initiate cron task after scheduler stopped.")
    case e: Exception => 
      println("Cron Execution Error: %s" format(e.getMessage))
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
