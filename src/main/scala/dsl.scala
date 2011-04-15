package com.philipcali.cron
package dsl

import scala.util.parsing.combinator._
import com.github.philcali.scalendar.{Month, Day}

object CronSyntax {
  implicit def string2cron(syntax: String) = new CronSyntax(syntax)
}

case class Cron(minute: String, hour: String, dmonth: String, month: String, dweek: String) {
  override def toString = List(minute, hour, dmonth, month, dweek) mkString (" ")
}

class CronSyntax(syntax: String) extends RegexParsers {
  private def valueMap[A](vals: Set[A]) =
    vals.map(_ toString) /*++ vals.map(_.toString.substring(0,3))*/

  // Mapping month names, with month keyword
  def monthnames = valueMap(Month.values) 

  // Mapping day names, with day keyword
  def daynames = valueMap(Day.values)
  def daynamesShort = daynames.map(_.substring(0,3))

  def isWeekend(day: Day.Value) = {
    val name = day.toString
    name == "Sunday" || name == "Saturday"
  } 

  def isWeekday(day: Day.Value) = !isWeekend(day)

  def field(name: String) = name ^^ (_ => Map(name -> "*"))

  val every = "Every" | "every"
  val other = "other".r
  val lists = "through" | "to"
  val minuteKey = field("minute")
  val dayKey = field("day") 
  val monthKey = field("month")
  val hourKey = field("hour")

  // Values
  def monthValue = (monthnames).mkString("|").r 
  def dayValue = (daynames).mkString("|").r
  def hourValue = minuteValue 
  def minuteValue = number 
  def timeValue = hourValue~":"~minuteValue ^^ {
    case hours~":"~minutes => (hours, if(minutes.startsWith("0")) minutes.substring(1,2) else minutes)
  }

  // Descriptors
  def noon = "noon" ^^ (_ => ("12", "0"))
  def midnight = "midnight" ^^ (_ => ("0", "0"))
  def weekday = "weekday" ^^ (_ => "1-5")
  def weekend = "weekend" ^^ (_ => "0,6")

  // Connectors
  val at = "at".r
  val the = "the".r
  val number = """\d{1,2}""".r
  val st = number <~ "st"
  val nd = number <~ "nd"
  val th = number <~ "th"

  def otherWhat(key: String) = {
    other ~> key ^^ {
      case key => Map(key -> "*", key+"Modifier" -> "/2")
    }
  }

  def timesWhat(key: String) = {
    number ~ (key+"s") ^^ {
      case times ~ keys => Map(key -> "*", key+"Modifier" -> ("/"+times))
    }
  }

  def hourConnector = at ~> (timeValue | midnight | noon) ^^ {
    case (hours, minutes) => Map("hour" -> hours, "minute" -> minutes)
  }
  
  def numberDayOfMonth = (st | nd | th | "last") <~ "day" ^^ {
    case number if number == "last" => Map("day" -> "L")
    case number => Map("day" -> number)
  }

  def dayConnector = "on" ~> (dayValue | the ~> (weekend | weekday | "last day of week")) ^^ {
    case day if day == "last day of week" => Map("dweek" -> "L")
    case day => 
      val found = Day.values.find(_.toString == day) match {
        case Some(d) => (d.id - 1).toString
        case None => day
      }
      Map("dweek" -> found)
  }

  def dayOfMonthConnector = "on the" ~> numberDayOfMonth

  def monthConnector = "in" ~> monthValue ^^ {
    case month => Map("month" -> Month.values.find(_.toString == month).get.id.toString)
  }

  def connector = hourConnector | dayConnector | monthConnector | dayOfMonthConnector
  // Connectors can be strung together
  // ex: at midnight in June on Sonday
  def connectors = rep(connector)

  // Incremental Syntax
  def minuteIncrement = every ~> (minuteKey | otherWhat("minute") | timesWhat("minute")) 

  def hourIncrement = every ~> (hourKey | otherWhat("hour") | timesWhat("hour"))

  def dayGeneralIncrement = every ~> (dayKey | otherWhat("day") | timesWhat("day")) 

  def dayMIcrement = every ~> numberDayOfMonth

  def dayIncrement = dayGeneralIncrement | dayMIcrement 

  def monthIncrement = every ~> (monthKey | otherWhat("month") | timesWhat("month"))

  def increment = minuteIncrement | hourIncrement | dayIncrement | monthIncrement

  def incrementers = increment ~ connectors ^^ {
    case incremented ~ values => 
    val first = incremented.asInstanceOf[Map[String, String]]
    val second = values.asInstanceOf[List[Map[String,String]]]
    applyModifers(second.foldLeft(Map[String,String]()) { (acc, m) =>
      acc ++ m
    } ++ first)
  }

  def applyModifers(result: Map[String, String]) = {
    val (mods, fin) = result.partition(_._1.endsWith("Modifier"))
    for((k, v) <- fin) yield((k, v + mods.getOrElse(k+"Modifier", "")))
  }

  // Something like this for the finished result:
  def finished = incrementers

  def cron = cronOption match {
    case Left(crond) => crond
    case Right(msg) => error(msg)
  }

  def crons = cron.toString

  def cronOption = {
    parseAll(finished, syntax) match {
      case Success(parsed, _) =>
        Left(Cron(
          parsed.getOrElse("minute", "*"),
          parsed.getOrElse("hour", "*"),
          parsed.getOrElse("day", "*"),
          parsed.getOrElse("month", "*"),
          parsed.getOrElse("dweek", "*")
        ))
      case Failure(msg, _) =>
        Right(msg) 
      case Error(msg, _) =>
        Right(msg)
    }
  }
}

