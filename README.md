# Cron DSL

Making use of Scala parser combinators to build a cron dsl library.

## Basic Syntax

A typical cron job is said to run *every something* where "something" represents
a single field in cron or multiple fields strung together. An example would be:

*Every day at midnight*

This dsl library makes that assumption as well... in fact:

    "Every day at midnight".crons == "0 0 * * *"
    "Every 15 minutes at midnight on the weekend".crons == "*/15 0 * * 0,6"
    "Every other minute in July at noon on the weekday".crons == "*/2 12 * 7 1-5"
    "Every 1st day in April at midnight".crons == "0 0 1 4 *"
    "Every day on the weekday at 3:30".crons == "30 3 * * 1-5"

## What to expect

- Keywords like `through` and `to` to represent `-` in cron.
- The generated `Cron` object will have a `run` method to execute arbitrary code, ex:

        "Every 1st day in April at midnight".cron run {
          println("April fools!")
        }
