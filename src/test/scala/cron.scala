package com.philipcali.cron.dsl
package test

import CronSyntax._
import org.scalatest.FlatSpec
import org.scalatest.matchers.ShouldMatchers

class CronTest extends FlatSpec with ShouldMatchers {
  "Cron dialect" should "be parsable" in {
    "every day at midnight".crons should be === "0 0 * * *" 
    "every 15 minutes at noon".crons should be === "*/15 12 * * *"
    "every 2nd day in April at 3:30".crons should be === "30 3 2 4 *"
    "every day on the weekday at 3:30".crons should be === "30 3 * * 1-5"
    "every 3 days in July on the weekend at 6:57".crons should be === "57 6 */3 7 0,6"
  }

  "Cron connectors" should "be interchangable" in {
    "Every other month on the weekday at midnight".crons should be === "0 0 * */2 1-5"
    "Every other month at midnight on the weekday".crons should be === "0 0 * */2 1-5"
  }
}
