package com.philipcali.cron
package jobs

import dsl.string2cron
import java.util.{Timer, TimerTask}

trait CronTask {
  def run() 
  def executes(definition: String): Scheduled = executes(definition.cron)
  def executes(definition: Cron): Scheduled = Scheduled(this, definition)
}

case class Scheduled(task: CronTask, definition: Cron) {
  private val timer = new Timer

  def stop() = timer.cancel()  

  private def interval: TimerTask = new TimerTask {
    def run() = task run()
  }
 
  private def schedule = try {
    timer.schedule(interval, definition.next)
  } catch {
    case e: IllegalArgumentException => 
      println("Given a negative time")
    case e: IllegalStateException => 
      println("Tried to schedule a task after task to ordered to stoped.")
    case e: Exception => 
      println("Cron Execution Error: %s" format(e.getMessage))
  }

  // Initiates
  schedule
}
