package com.philipcali.cron
package dsl

import scala.util.parsing.combinator._
import com.github.philcali.scalendar.{Month, Day}

case class Cron(second: String, 
                minute: String, 
                hour: String, 
                dmonth: String, 
                month: String, 
                dweek: String,
                year: String) {
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
  val lists = "through" | "to" | "-"
  val secondKey= field("second")
  val minuteKey = field("minute")
  val dayKey = field("day") 
  val monthKey = field("month")
  val hourKey = field("hour")
  val yearKey = field("year")

  // Values
  def monthValue = (monthnames).mkString("|").r ^^ {
    case month => Map("month" -> Month.values.find(_.toString == month).get.id.toString)
  }
  def dayValue = (daynames).mkString("|").r ^^ {
    case day => (Day.values.find(_.toString == day).get.id - 1).toString
  }
  def hourValue = am | pm
  def smallValue = """\d{2}""".r 
  def clockValue = number~":"~smallValue ^^ {
    case hours~":"~minutes => (hours, (if(minutes.startsWith("0")) minutes.substring(1,2) 
                                      else minutes), "0")
  }
  def fullClockValue = clockValue~":"~smallValue ^^ {
    case (hours, minutes, _)~":"~seconds => (hours, minutes, seconds)
  }

  def timeValue = hourValue | clockValue 
  def yearValue = """\d{4}""".r ^^ { case year => Map("year" -> year) }

  // Descriptors
  val noon = "noon" ^^ (_ => ("12", "0", "0"))
  val midnight = "midnight" ^^ (_ => ("0", "0", "0"))
  val weekday = "weekday" ^^ (_ => "1-5")
  val weekend = "weekend" ^^ (_ => "0,6")

  // Connectors
  val at = "at".r
  val the = "the".r
  val number = """\d{1,2}""".r
  val am = number <~ "am" ^^ { 
    case hour => (hour, "0", "0") 
  }
  val pm = number <~ "pm" ^^ {
    case hour => if(hour.toInt < 12) ((hour.toInt + 12).toString, "0", "0") else (hour, "0", "0") 
  }
  val st = number <~ "st"
  val nd = number <~ "nd"
  val rd = number <~ "rd"
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

  def timeSymbol = (timeValue | midnight | noon) ^^ {
    case (hours, minutes, seconds) => 
      Map("hour" -> hours, "minute" -> minutes, "second" -> seconds)
  }

  def timeRep = rep1sep(timeSymbol, repitition) ^^ {
    case times => 
      val hours = times.map(_.apply("hour")).distinct.mkString(",")
      val minutes = times.map(_.apply("minute")).distinct.mkString(",")
      Map("hour" -> hours, "minute" -> minutes)
  }


  def timeLists = timeSymbol ~ lists ~ timeSymbol ^^ {
    case start ~ lists ~ end =>
      def grabValue(key: String) = {
        if(start(key) == end(key)) start(key)
        else if(start(key).toInt > end(key).toInt) end(key) + "-" + start(key)
        else start(key) + "-" + end(key)
      }
      Map("hour" -> grabValue("hour"), 
          "minute" -> grabValue("minute"), 
          "second" -> grabValue("second")) 
  }

  def timeConnector = at ~> (timeLists | timeRep | timeSymbol | timeIncrement)

  def dayOfMonth = (st | nd | rd | th | "last") ^^ {
    case number if number == "last" => Map("day" -> "L")
    case number => Map("day" -> number)
  }

  def numberDayOfMonth = dayOfMonth <~ "day" 

  def dayConnector = "on" ~> (dayLists | dayRepitition | dayOfWeek | dayIncrement)

  def dayOfWeek = (dayValue | the ~> (weekend | weekday | "last day of week")) ^^ {
    case day if day == "last day of week" => Map("dweek" -> "L")
    case day => Map("dweek" -> day)
  }

  val repitition = (", and" | "," | "and")

  def dayRepitition = rep1sep(dayValue, repitition) ^^ {
    case days => Map("dweek" -> days.mkString(","))
  }

  def dayLists = (dayValue ~ lists ~ dayValue) ^^ {
    case start ~ lists ~ end => Map("dweek" -> (start + "-" + end))
  }

  def dayOfMonthRep = rep1sep(dayOfMonth, repitition) <~ "day" ^^ {
    case days => Map("day" -> days.map(_.apply("day")).mkString(","))
  }

  def dayOfMonthLists = (dayOfMonth ~ lists ~ dayOfMonth) <~ "day" ^^ {
    case start ~ lists ~ end => Map("day" -> (start("day") + "-" + end("day")))
  }

  def dayOfMonthConnector = "on the" ~> (dayOfMonthLists | dayOfMonthRep | numberDayOfMonth) 

  def monthConnector = "in" ~> (monthLists | monthRep| monthValue | monthIncrement)

  def monthRep = rep1sep(monthValue, repitition) ^^ {
    case months => Map("month" -> months.map(_.apply("month")).mkString(",")) 
  }

  def monthLists = monthValue ~ lists ~ monthValue ^^ {
    case start ~ lists ~ end => Map("month" -> (start("month") + "-" + end("month")))
  }

  def yearConnector = "in the year" ~> (yearLists | yearRep | yearValue | yearIncrement)

  def yearRep = rep1sep(yearValue, repitition) ^^ {
    case years => Map("year" -> years.map(_.apply("year")).distinct.mkString(","))
  }

  def yearLists = yearValue ~ lists ~ yearValue ^^ {
    case start ~ lists ~ end =>
      Map("year" -> (start("year") + "-" + end("start")))
  }

  def connector = timeConnector | dayConnector | yearConnector | monthConnector | dayOfMonthConnector

  def connectors = rep(connector) ^^ {
    case values => values.foldLeft(Map[String,String]())(_ ++ _)
  }

  // Incremental Syntax
  def secondsIncrement = every ~> (secondKey | otherWhat("second") | timesWhat("second"))

  def minuteIncrement = every ~> (minuteKey | otherWhat("minute") | timesWhat("minute")) 

  def hourIncrement = every ~> (hourKey | otherWhat("hour") | timesWhat("hour"))

  def timeIncrement = secondsIncrement | minuteIncrement | hourIncrement

  def dayGeneralIncrement = every ~> (dayKey | otherWhat("day") | timesWhat("day")) 

  def dayMIncrement = every ~> numberDayOfMonth

  def dayWIncrement = every ~> dayValue ^^ {
    case day => Map("dweek" -> day)
  }

  def dayIncrement = dayGeneralIncrement | dayMIncrement | dayWIncrement 

  def monthIncrement = every ~> (monthKey | otherWhat("month") | timesWhat("month"))

  def yearIncrement = every ~> (yearKey | otherWhat("year") | timesWhat("year"))

  def increment = timeIncrement | dayIncrement | yearIncrement | monthIncrement 

  def incrementers = increment ~ connectors ^^ {
    case incremented ~ values => applyModifers(values ++ incremented)
  }

  def applyModifers(result: Map[String, String]) = {
    val (mods, fin) = result.partition(_._1.endsWith("Modifier"))
    for((k, v) <- fin) yield((k, v + mods.getOrElse(k+"Modifier", "")))
  }

  def cron = cronOption match {
    case Left(crond) => crond
    case Right(msg) => error(msg)
  }

  def crons = cron.toString

  def cronOption = {
    parseAll(incrementers, syntax) match {
      case Success(parsed, _) =>
        Left(Cron(
          parsed.getOrElse("seconds", "0"),
          parsed.getOrElse("minute", "*"),
          parsed.getOrElse("hour", "*"),
          parsed.getOrElse("day", "*"),
          parsed.getOrElse("month", "*"),
          parsed.getOrElse("dweek", "*"),
          parsed.getOrElse("year", "*")
        ))
      case Failure(msg, _) =>
        Right(msg) 
      case Error(msg, _) =>
        Right(msg)
    }
  }
}

