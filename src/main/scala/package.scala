package com.github.philcali.cronish 

package object dsl {
  implicit def string2cron(syntax: String) = Cronish(syntax)

  // Predefined Constants ... should I be using an Enumeration?
  val hourly = "Every hour at 0:00".cron
  val daily = "Every day at midnight".cron
  val weekly = "Every month on Sunday at midnight".cron
  val monthly = "Every 1st day in every month at midnight".cron 
  val yearly = "Every year on the 1st day in January at midnight".cron

  def task[A](action: => A) = new CronTask(None, action) 

  def job[A](action: => A) = task(action)
}
