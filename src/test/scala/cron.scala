package com.philipcali.cron.dsl
package test

import CronSyntax._
import org.scalatest.FlatSpec
import org.scalatest.matchers.ShouldMatchers

class CronTest extends FlatSpec with ShouldMatchers {
  "Cron dialect" should "be parsable" in {
    val crond = "Every day at midnight".cron
    println(crond)
    //crond should be === "0 0 * * *"
  }
}
