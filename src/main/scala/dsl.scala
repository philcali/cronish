package com.philipcali.cron
package dsl

import scala.util.parsing.combinator._
import com.github.philcali.scalendar.{Month, Day}

object CronSyntax {
  implicit def string2cron(syntax: String) = new CronSyntax(syntax)
}

// I'm slowing understanding this, but Now my brain has
// had enough for one day 
class CronSyntax(syntax: String) extends RegexParsers {
  def every = """Every|every""".r ~ dayKey ~ at ~ midnight

  private def valueMap[A](vals: Set[A]) =
    vals.map(_ toString) ++ vals.map(_.toString.substring(0,3))

  // Mapping month names, with month keyword
  def monthnames = valueMap(Month.values) 

  // Mapping day names, with day keyword
  def daynames = valueMap(Day.values)

  def isWeekend(day: Day.Value) = {
    val name = day.toString
    name == "Sunday" || name == "Saturday"
  } 

  def isWeekday(day: Day.Value) = !isWeekend(day)

  // General keywords
  def sp = whiteSpace
  val other = "other".r
  val lists = "through" | "until" | "to"
  val minuteKey = "minute".r
  val minutesKey = "minute".r
  val dayKey = "day".r
  val monthKey = "month".r
  val hourKey = "hour".r
  val from = "from".r

  // Values
  def monthValue = (monthnames).mkString("|").r 
  def dayValue = (daynames).mkString("|").r
  def hourValue = minuteValue 
  def minuteValue = """\d{1,2}""".r ^^ (_.toInt)
  def timeValue = hourValue~":"~minuteValue ^^ {
    case hours~":"~minutes => (hours, minutes)
  }

  // Descriptors
  def noon = "noon" ^^ (_ => 12)
  def midnight = "midnight" ^^ (_ =>  0)
  val weekday = """weekday""".r
  val weekend = """weekend""".r 

  // Connectors
  val at = "at".r
  val the = "the".r
  def hourConnector = at ~ (timeValue | midnight | noon)
  def dayConnector = "on" ~ (dayValue | the ~ (weekend | weekday))
  def monthConnector = "in" ~ monthValue
  def connector = hourConnector | dayConnector | monthConnector

  // Incremental Syntax
  def minuteIncrement = (minuteKey 
                       | other ~ sp ~ minuteKey 
                       | minuteValue ~ sp ~ minutesKey ~ sp ~ at ~ sp ~ hourValue 
                       | minuteValue ~ sp ~ minutesKey)

  // Something like this for the finished result:
  /*
    def finished = { 
      val parsed = parsedResult.split("\\|").map(_.split("=")).toMap
      Cron(
        parsed.getOrElse("minute", "0"),
        parsed.getOrElse("hour", "0"),
        parsed.getOrElse("dMonth", "*"),
        parsed.getOrElse("month", "*"),
        parsed.getOrElse("dweek", "*")
      )

      Cron will have object creation validation
  */
  def cron = {
    parseAll(every, syntax) 
  }
}
