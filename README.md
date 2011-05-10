# Cron DSL

Making use of Scala parser combinators to build a cron dsl library.

## Basic Syntax

A typical cron job is said to run *every something* where "something" represents
a single field in cron or multiple fields strung together. An example would be:

*Every day at midnight*

This dsl library makes that assumption as well... in fact all are legitimate cron definitions:

    "Every day at midnight".crons == "0 0 * * *"
    "Every 15 minutes at midnight on the weekend".crons == "*/15 0 * * 0,6"
    "Every other minute in July at noon on the weekday".crons == "*/2 12 * 7 1-5"
    "Every 1st day in April at midnight".crons == "0 0 1 4 *"
    "Every day on the weekday at 3:30".crons == "30 3 * * 1-5"

Let's take this excerpt from the [cron] article on Wikipedia:

    In 2003 CE on the 11th to 26th of each month in January to June every third minute starting from 2 past 1am, 9am and 10pm

Using cronish, we can almost write this verbatim...

    "Every 3 minutes in the year 2003 on the 11th to 26th day in January to June at 1am, 9am, and 10pm".cron

    Cron("0", "*/3", "1,9,22", "11-26", "1-6", "*", "2003")

## Determining the Next Run

A `Cron` object created from an expression, or created manually, can determine its next run from now, or a 
specific time in the future.

## Creating a Cron Job

Cron jobs are created via dsl language as well. The syntax borrows heavily from
sbt task creation.

    val payroll = task {
      println("You have just been paid... Finally!")
    }

    // Yes... that's how you run it 
    payroll executes "every Friday on the last day in every month"

[cron]: http://en.wikipedia.org/wiki/Cron#Examples_2
