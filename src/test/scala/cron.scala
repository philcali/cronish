package com.github.philcali
package cronish 
package dsl
package test

import scalendar._

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
    "Every month on the last Saturday".crons should be === "* * * * 6L"
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

  it should "support the special day of week syntax" in {
    "every month on the last Friday at midnight".crons should be === "0 0 * * 5L"
    "every month on the 2nd Friday at midnight".crons should be === "0 0 * * 5#2"
    "every last Friday at midnight".crons should be === "0 0 * * 5L"
    "every other Friday at midnight".crons should be === "0 0 * * 5/2"
  }

  it should "support the special time keywords" in {
    "every midnight".crons should be === "every day at midnight".crons
    "every midnight on the last Friday".crons should be === "0 0 * * 5L"
  }

  "Predefined crons" should "be correct" in {
    hourly.toString should be === "0 * * * *"
    daily.toString should be === "0 0 * * *"
    weekly.toString should be === "0 0 * * 0"
    monthly.toString should be === "0 0 1 * *"
    yearly.toString should be === "0 0 1 1 *"
  }

  "A cron task" should "be able to be built fluently" in {
    val test = task {
      println("Building something leet")
    }

    test.isInstanceOf[CronTask] should be === true
    
    val testjob = test executes "every second"

    testjob.isInstanceOf[Scheduled] should be === true
    testjob.stop()
  }

  it should "run at said increments" in {
    // Counter for our program
    var runs = 0

    val example = task {
      runs += 1
    }
    
    val exampleJob = example executes "every second"

    // We'll sleep for a second
    Thread.sleep(1 * 1000)

    runs should be === 1
    exampleJob.stop()
  }

  it should "be able to be stopped at any time" in {
    var counter = 0
    task { counter += 1 } executes "every second" stop()
    counter should be === 0
  }

  it should "be able to have a user defined description" in {
    val nodesc = job (println("Will it happen?")) runs "every second"
    
    val expected = "A really cool description"
    val other = job {
      println("Blah blah")
    } describedAs expected executes daily
 
    nodesc.task.description should be === None
    other.task.description should be === Some(expected)
    
    nodesc.stop()
    other.stop()
  }
 
  it should "be able to perform delayed starts" in {
    var counter = 0
    val countTask = job { counter += 1 }
    val delayed = countTask runs "every second" in 100.milliseconds 

    counter should be === 0
    Thread.sleep(1100)
    delayed.stop()
    counter should be === 1

    import Scalendar.now
    val other = countTask executes "every second" starting now
    Thread.sleep(1000)
    other.stop()
    counter should be === 2
  }

  it should "be able to be reset and delayed fluently" in {
    val one = job(println("End of the World")) runs "every second" 
    
    val reseted = one.reset() 

    val delayed = reseted in 4.minutes
    
    delayed.stop()
  }

  "Cron Management" should "be completely hidden from user" in {
    job(println("Tell like it is")) describedAs "Tell him" runs hourly
    job(println("Tell like it is")) describedAs "later" runs daily 
    job(println("Tell like it is")) describedAs "on" runs monthly
    job(println("Tell like it is")) describedAs "dude" runs yearly

    val active = Scheduled.active
    val expected = "Tell him later on dude"
    active.map(_.task.description.get).mkString(" ") should be === expected 
    
    active.foreach(_.stop())
  }
 
  "A cron" should "be able to determine its next run" in {
    val tests = List[(String, Scalendar => Scalendar)](
      "Every day at midnight" -> { now => Scalendar.beginDay(now) + (1 day) },
      "Every 1st day in every month at midnight" -> { now => 
        Scalendar.beginDay(now).day(1) + (1 month)
      },
      "Every last day in every month at midnight" -> { now =>
        val last = Scalendar.beginDay(now).day(1) + 1.month - 1.day
        if (now > last) last + 1.day + 1.month - 1.day
        else last
      },
      "Every month on Wednesday at midnight" -> { now =>
        now.day.inWeek match {
          case n if n >= 4 => 
            (Scalendar.beginWeek(now) + (1 week)).inWeek(Day.Wednesday) 
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
      "Every midnight on the last Friday in every month" -> { now =>
        def lastFriday(month: Int) = {
          val current = now.day(1).month(month) 
          val working = Scalendar.beginDay(current).day(1) + (1 month) - (1 day)
          if(working.inWeek >= 6) working.inWeek(6)
          else working.inWeek(6) - 1.week
        }
        val attempt = lastFriday(now.month.value)
        if (attempt < now) lastFriday((now + 1.month).month.value) 
        else attempt
      }
    )

    for (test <- tests; val (crons, expected) = test) {
      val cron = crons.cron

      val n = Scalendar.now
      val millis = cron.nextFrom(n)

      val result = Scalendar(n.time + millis)

      result should be === expected(n)
    }
  }
}
