package com.philipcali.cron.dsl
package test

import com.github.philcali.scalendar._

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

  "Cron syntax" should "support repition" in {
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

  /**
      Terms:
      1. `Potentials`: what the given date *could* be. 
        In a repition or list, the data appears more cyclic.
        In an astericks, it's simply the current time, or the next
        possible one in a cycle.
      2. `Actuals`: what the given date *is*.
        Numeric values

      It is my belief that `Actuals` are less common than `Potentials`.
      The very cron definition is actually setting *occurrances* in the
      future.
      
      The algorithm:
      1. With the time given, convert cron string into a list of potentials or
        actuals. Potentials are cyclic, meaning the next possible value for a field,
        may the first possible value plus the next value of it's parent field.
      2. Day of weeks are handled completely different. Having delved into this,
        I believe that the reason the day of week field is last in the cron field,
        is because it's accountted for separately. For example:
        "Every Friday on the last day in every month at midnight".crons equals
        0 0 L * 5: The last day of the month should be handled first, before finding
        the Friday. It's not very likely that the last of every month will *be*
        a Friday.
  */
  "A cron" should "be able to determine its next run" in {
    val tests = List[(String, Scalendar => Scalendar)](
      "Every day at midnight" -> { now => Scalendar.beginDay(now) + (1 day) },
      "Every 1st day in every month" -> { now => 
        Scalendar.beginDay(now).day(1) + (1 month)
      },
      "Every month on Wednesday at midnight" -> { now =>
        // the weekend means, the next run is on a Monday 
        now.day.inWeek match {
          case n if n >= 4 => (Scalendar.beginWeek(now) + (1 week)).inWeek(Day.Wednesday) 
          case _ => Scalendar.beginDay(now).inWeek(Day.Wednesday)
        } 
      }
    )

    tests.foreach { test => val (crons, expected) = test
      val cron = crons.cron

      // Cron doesn't work with millisecond, so neither will we
      val now = Scalendar.now

      val result = Scalendar(now.time + cron.nextFrom(now))
      result should be === expected(now)
    }
  }
}
