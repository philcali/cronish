package com.philipcali.cron
package test

import com.github.philcali.scalendar._
import dsl._

import org.scalatest.FlatSpec
import org.scalatest.matchers.ShouldMatchers

class CronTest extends FlatSpec with ShouldMatchers {
  "Cron dialect" should "be parsable" in {
    "every day at midnight".crons should be === "0 0 * * *" 
    "every 15 minutes at noon".crons should be === "*/15 12 * * *"
    "every 2nd day in April at 3:30".crons should be === "30 3 2 4 *"
    "every day on Monday at 3:30".crons should be === "30 3 * * 1"
    "every day on the weekday at 3:30".crons should be === "30 3 * * 1-5"
    "every 3 days in July on the weekend at 6:57".crons should be === "57 6 */3 7 0,6"
  }

  "Cron connectors" should "be interchangable" in {
    "Every other month on the weekday at midnight".crons should be === "0 0 * */2 1-5"
    "Every other month at midnight on the weekday".crons should be === "0 0 * */2 1-5"
  }

  "Cron syntax" should "support the 'last' keyword" in {
    "Every month on the last day of week".crons should be === "* * * * L"
    "Every last day".crons should be === "* * L * *"
    evaluating { "Every last month".cron } should produce [Exception]
  }

  it should "support repitition" in {
    "every day in April, June, and August at midnight".crons should be === "0 0 * 4,6,8 *"
    "every month at midnight and noon on Sunday".crons should be === "0 0,12 * * 0"
    "every 3 hours on Monday, Wednesday, and Friday".crons should be === "* */3 * * 1,3,5"
    "every month on the 1st, 3rd, 4th, and 8th days".crons should be === "* * 1,3,4,8 * *"
    "every day at 1am, 4am, 12pm, and 1pm".crons should be === "0 1,4,12,13 * * *"
  }

  it should "support lists (-)" in {
    "every day in January through October".crons should be === "* * * 1-10 *"
    "every day in January to October".crons should be === "* * * 1-10 *"
    "every day in January-October".crons should be === "* * * 1-10 *"
    "every month on Wednesday through Saturday".crons should be === "* * * * 3-6"
    "every month on Wednesday to Saturday".crons should be === "* * * * 3-6"
    "every month on Wednesday-Saturday".crons should be === "* * * * 3-6"
    "every other day at midnight to noon".crons should be === "0 0-12 */2 * *"
    "every 3 minutes at 1am-8pm".crons should be === "*/3 1-20 * * *"
    "every 3 minutes at 1am-8pm in the year 2013".cron.year should be === "2013"
  }

  it should "support incrementals inside connectors" in {
    "every day in every month at midnight".crons should be === "0 0 * * *"
    "every last day in every month at every 4 hours".crons should be === "* */4 L * *"
    "every month on the last day at every 4 hours".crons should be === "* */4 L * *"
    "every Friday on the last day in every month at midnight".crons should be === "0 0 L * 5"
  }

  "Predefined crons" should "be correct" in {
    Hourly.toString should be === "0 * * * *"
    Daily.toString should be === "0 0 * * *"
    Weekly.toString should be === "0 0 * * 0"
    Monthly.toString should be === "0 0 1 * *"
    Yearly.toString should be === "0 0 1 1 *"
  }

  "A cron task" should "be able to be built fluently" in {
    val test = task {
      Some("Building something leet")
    }

    test.isInstanceOf[jobs.CronTask] should be === true
    
    val testjob = test executes "every second"

    testjob.isInstanceOf[jobs.Scheduled] should be === true
  }

  it should "run at said increments" in {
    // Counter for our program
    var runs = 0

    val example = task {
      runs += 1
      if(runs == 1) Some("Done") else None
    }
    
    example executes "every second"

    // We'll sleep for 3 seconds
    Thread.sleep(1 * 1000)

    runs should be === 1
  }

  it should "be able to be stopped at any time" in {
    var counter = 0
    task { counter += 1; None } executes "every second" stop()
    counter should be === 0
  }

  "A cron" should "be able to determine its next run" in {
    val tests = List[(String, Scalendar => Scalendar)](
      "Every day at midnight" -> { now => Scalendar.beginDay(now) + (1 day) },
      "Every 1st day in every month at midnight" -> { now => 
        Scalendar.beginDay(now).day(1) + (1 month)
      },
      "Every last day in every month at midnight" -> { now =>
        Scalendar.beginDay(now).day(1) + (1 month) - (1 day)
      },
      "Every month on Wednesday at midnight" -> { now =>
        now.day.inWeek match {
          case n if n >= 4 => (Scalendar.beginWeek(now) + (1 week)).inWeek(Day.Wednesday) 
          case _ => Scalendar.beginDay(now).inWeek(Day.Wednesday)
        } 
      },
      "Every month on Sunday at midnight" -> { now =>
        Scalendar.beginWeek(now) + (1 week)
      },
      "Every month at 3:30 on the weekday" -> { now =>
        val working = if(now.inWeek == 6 || now.inWeek == 7) 
                        Scalendar.beginWeek(now) + (1 week) + (1 day)
                      else Scalendar.beginDay(now) + (1 day)
        working.hour(3).minute(30) 
      },
      "Every Friday on the last day in every month at midnight" -> { now =>
        val working = Scalendar.beginDay(now).day(1) + (1 month) - (1 day)
        if(working.inWeek >= 6) working.inWeek(6)
        else working.inWeek(6) - 1.week
      }
    )

    for (test <- tests; val (crons, expected) = test) {
      val cron = crons.cron

      val now = Scalendar.now

      val result = Scalendar(now.time + cron.nextFrom(now))
      result should be === expected(now)
    }
  }
}
