package com.philipcali.cron

import dsl.CronSyntax._

object Cron {
  def main(args: Array[String]) = {
    if(args.size < 1) {
      println("Please provide me a cron string")
      exit(0)
    }
    println(args(0).cron)
  }
}
