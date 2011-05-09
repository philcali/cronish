package com.philipcali.cron

package object dsl {
  implicit def string2cron(syntax: String) = new Cronish(syntax)

  // Predefined Constants ... should I be using an Enumeration?
  val Hourly = "Every hour at 0:00".cron
  val Daily = "Every day at midnight".cron
  val Weekly = "Every month on Sunday at midnight".cron
  val Monthly = "Every 1st day in every month at midnight".cron 
  val Yearly = "Every year on the 1st day in January at midnight".cron

  def task(action: => Option[String]) = new jobs.CronTask {
    def run() = action
  }
}
