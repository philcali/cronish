package cronish
package dsl

import java.util.concurrent.{
  Executors,
  TimeUnit
}
import TimeUnit.MILLISECONDS

import akka.actor.{ Actor, ActorSystem, Props }

import scalendar._
import conversions._
import Logging._

object Scheduled {
  protected val pool = ActorSystem("CronTasks")

  private val crons = collection.mutable.ListBuffer[Scheduled]()

  def apply(task: CronTask,
            definition: Cron,
            delay: Long = 15,
            stopper: StopGap = Infinite): Scheduled = {
    val ros = new Scheduled(task, definition, delay, stopper)
    crons += ros
    ros
  }

  @deprecated("Use Scheduled.stop instead")
  def destroy(old: Scheduled) = stop(old)

  def stop(old: Scheduled) = crons -= old

  @deprecated("Use Scheduled.shutdown instead")
  def destroyAll = shutdown()

  def shutdown() = {
    crons foreach (_.stop)
    pool.shutdown()
  }

  def active = crons.toList

  java.lang.Runtime.getRuntime().addShutdownHook(new Thread {
    override def run() { shutdown() }
  })
}

class CronTask(work: => Unit,
           val description: Option[String] = None,
           val startHandler: Option[Function0[Unit]] = None,
           val errHandler: Option[(Exception => Unit)] = None,
           val exitHandler: Option[Function0[Unit]] = None) extends Runnable {

  def run() = try {
    work
  } catch {
    case e: Exception =>
      errHandler.map(_.apply(e))
  }

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

sealed trait StopGap {
  def check: Option[Int]
  def times(limit: Int) = new Limited(limit)
}

class Limited(initial: Int) extends StopGap {
  private var limit = initial

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
    val stopGap: StopGap) { parent =>

  private val timer = Executors.newScheduledThreadPool(1)

  private val handler = Scheduled.pool.actorOf(Props(new Handler))

  private case object Stop
  private case object Execute

  private class Handler extends Actor {
    override def preStart() {
      if (delay <= 0) parent.initStart else {
        try {
          parent.timer.schedule(new Runnable {
            def run() = parent.initStart
          }, delay, MILLISECONDS)
        } catch {
          case e: Exception => info("Scheduled task was restarted")
        }
      }
    }

    def receive = {
      case Stop => context.stop(self)
      case Execute =>
        parent.stopGap.check
              .map { _ => parent.task.run(); parent.schedule }
              .orElse(Some(parent.stop))
    }
  }

  def stop(): Unit = {
    timer.shutdown()
    task.exitHandler.map(_.apply())
    Scheduled.stop(parent)
    handler ! Stop
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

  private def interval: Runnable = new Runnable {
    def run() = {
      handler ! Execute
    }
  }

  private def schedule = try {
    timer.schedule(interval, definition.next, MILLISECONDS)
  } catch {
    case e: Exception =>
      severe("Scheduled task was rejected: %s".format(e.getMessage))
  }

  private def initStart() = {
    task.startHandler.map(_.apply())
    schedule
  }

  private def preserve[A](block: => A): A = {
    stop; block
  }
}
