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

  def apply(task: CronTask, 
            definition: Cron, 
            delay: Long = 15, 
            stopper: StopGap = Infinite): Scheduled = { 
    val ros = new Scheduled(task, definition, delay, stopper)
    crons += ros
    ros
  }

  def destroy(old: Scheduled) = crons -= old

  def destroyAll = crons foreach (_.stop)

  def active = crons.toList
}

class CronTask(work: => Unit,
           val description: Option[String] = None, 
           val startHandler: Option[Function0[Unit]] = None,
           val errHandler: Option[(Exception => Unit)] = None,
           val exitHandler: Option[Function0[Unit]] = None) {

  def run() = work

  def runs(definition: String) = executes(definition)
  def runs(definition: Cron) = executes(definition)

  def executes(definition: String): Scheduled = executes (definition.cron)
  def executes(definition: Cron): Scheduled = Scheduled(this, definition)

  def describedAs(something: String) = 
    new CronTask(work, Some(something), startHandler, errHandler, exitHandler)

  def starts(handler: => Unit) =
    new CronTask(work, description, Some(() => handler), errHandler, exitHandler)

  def catches(handler: Exception => Unit) =
    new CronTask(work, description, startHandler, Some(handler), exitHandler)

  def ends(handler: => Unit) =
    new CronTask(work, description, startHandler, errHandler, Some(() => handler))
}

trait StopGap {
  def check: Option[Int]
  def times(limit: Int) = new Limited(limit)
}

class Limited(var limit: Int) extends StopGap {
  require(limit > 0)

  def check = {
    limit -= 1
    if (limit == 0) None else Some(limit)
  }
}

class Timed(limit: Scalendar) extends StopGap {
  def check = {
    if (limit < Scalendar.now) None else Some(1)
  }
}

case object Infinite extends StopGap {
  def check = Some(1)
}

class Scheduled private (
    val task: CronTask, 
    val definition: Cron, 
    val delay: Long,
    val stopGap: StopGap) extends Actor { parent => 

  private case object Stop
  private case object Execute

  private var executing = true

  // Daemon Pulsar
  private val timer = new Timer(true)

  def stop(): Unit = {
    task.exitHandler.map(_.apply())
    parent ! Stop 
  }

  def act = {
    delayedStart

    loopWhile (executing) {
      react {
        case Stop => 
          executing = false 
          timer.cancel()
          Scheduled.destroy(this) 
        case Execute => 
          try {
            task.run()
          } catch {
            case e: Exception =>
              task.errHandler.map(_.apply(e))
          } finally {
            stopGap.check.map(_ => schedule).orElse(Some(stop))
          }
      }
    }
  }

  def reset() = preserve {
    Scheduled(task, definition, delay, stopGap)
  }

  def exactly(stopper: StopGap) = preserve {
    Scheduled(task, definition, delay, stopper)
  }

  def until(date: Scalendar) = preserve {
    Scheduled(task, definition, delay, new Timed(date)) 
  }

  def starting(date: Scalendar) = preserve {
    val now = Scalendar.now
    val larger = if (date < now) now else date 
    Scheduled(task, definition, larger.time - now.time, stopGap)
  }

  def in(d: Long) = preserve {
    Scheduled(task, definition, d, stopGap)
  }

  def in(d: Evaluated) = preserve {
    Scheduled(task, definition, d.milliseconds, stopGap)
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
      severe("Tried rescheduling task: %s".format(e.getMessage))
  }

  private def delayedStart() = if (delay <= 0) initStart else {
    timer.schedule(new TimerTask {
      def run() = initStart 
    }, delay)
  }

  private def initStart() = {
    task.startHandler.map(_.apply())
    schedule
  }

  private def preserve[A](block: => A): A = {
    parent ! Stop; block
  }

  start()
}
