package com.github.philcali.cronish 
package dsl

import scala.util.parsing.combinator._

// For Calendar
import com.github.philcali.scalendar._
import implicits._

class Cronish (syntax: String) extends RegexParsers {
  // Helper functions
  private def valueMap[A](vals: Set[A]) = vals.map(_ toString) 

  def monthnames = valueMap(Month.values) 

  def daynames = valueMap(Day.values)

  def field(name: String) = name ^^ (_ => Map(name -> "*"))

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

  // General keywords
  val every = "Every" | "every"
  val other = "other"
  val lists = "through" | "to" | "-"
  val number = """\d{1,2}""".r
  val repitition = ", and" | "," | "and"

  // Time Keywords
  val noon = "noon" ^^ (_ => ("12", "0", "0"))
  val midnight = "midnight" ^^ (_ => ("0", "0", "0"))

  // Hour suffixes
  val am = number <~ "am" ^^ { 
    case hour => (hour, "0", "0") 
  }
  val pm = number <~ "pm" ^^ {
    case hour => if(hour.toInt < 12) ((hour.toInt + 12).toString, "0", "0") 
                 else (hour, "0", "0") 
  }

  // Day of week Keywords
  val weekday = "weekday" ^^ (_ => "1-5")
  val weekend = "weekend" ^^ (_ => "0,6")

  // Day of month suffixes
  val st = number <~ "st"
  val nd = number <~ "nd"
  val rd = number <~ "rd"
  val th = number <~ "th"

  // Field keywords
  val fields = List("second", "minute", "hour", "day", "month", "year")

  val fieldIncrementers = Map() ++ fields.map { f =>
    val keyParser = field(f)
    (f, every ~> (keyParser | otherWhat(f) | timesWhat(f)))
  }

  // Time related values
  def hourValue = am | pm
  def smallValue = """\d{2}""".r 
  def clockValue = number~":"~smallValue ^^ {
    case hours~":"~minutes => (hours, (if(minutes.startsWith("0")) minutes.substring(1,2) 
                                      else minutes), "0")
  }
  def fullClockValue = clockValue~":"~smallValue ^^ {
    case (hours, minutes, _)~":"~seconds => (hours, minutes, seconds)
  }

  def timeValue = hourValue | fullClockValue | clockValue 

  def timeSymbol = (timeValue | midnight | noon) ^^ {
    case (hours, minutes, seconds) => 
      Map("hour" -> hours, "minute" -> minutes, "second" -> seconds)
  }

  // Month Values
  def monthValue = (monthnames).mkString("|").r ^^ {
    case month => Map("month" -> Month.values.find(_.toString == month).get.id.toString)
  }

  // Year Values
  def yearValue = """\d{4}""".r ^^ { case year => Map("year" -> year) }

  // Day Values
  def dayValue = (daynames).mkString("|").r ^^ {
    case day => (Day.values.find(_.toString == day).get.id - 1).toString
  }

  def dayOfMonth = (st | nd | rd | th | "last") ^^ {
    case number if number == "last" => Map("day" -> "L")
    case number => Map("day" -> number)
  }

  def dayOfWeek = (dayValue | "the" ~> (weekend | weekday | "last day of week")) ^^ {
    case day if day == "last day of week" => Map("dweek" -> "L")
    case day => Map("dweek" -> day)
  }

  // Connectors
  private def genreps(key: String, value: Parser[Map[String,String]]) = {
    rep1sep(value, repitition) ^^ {
      case keys => Map(key -> keys.map(_.apply(key)).distinct.mkString(","))
    }
  }

  private def genlists(key: String, value: Parser[Map[String,String]]) = {
    value ~ lists ~ value ^^ {
      case start ~ lists ~ end => Map(key -> (start(key) + "-" + end(key)))
    }
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

  def timeConnector = "at" ~> (timeLists | timeRep | timeSymbol | timeIncrement)

  def dayConnector = "on" ~> (genlists("dweek", dayOfWeek)
                            | genreps("dweek", dayOfWeek)
                            | dayOfWeek 
                            | dayIncrement)

  def dayOfMonthConnector = "on the" ~> (genlists("day", dayOfMonth) <~ "day"
                                      | genreps("day", dayOfMonth) <~ "days"
                                      | dayOfMonth <~ "day") 

  def monthConnector = "in" ~> (genlists("month", monthValue) 
                              | genreps("month", monthValue) 
                              | monthValue 
                              | fieldIncrementers("month"))

  def yearConnector = "in the year" ~> (genlists("year", yearValue)
                                      | genreps("year", yearValue)
                                      | yearValue 
                                      | fieldIncrementers("year"))

  def connector = (timeConnector 
                 | dayConnector 
                 | yearConnector 
                 | monthConnector 
                 | dayOfMonthConnector)

  def connectors = rep(connector) ^^ {
    case values => values.foldLeft(Map[String,String]())(_ ++ _)
  }

  // Incremental Syntax
  def timeIncrement = (fieldIncrementers("second") 
                     | fieldIncrementers("minute") 
                     | fieldIncrementers("hour")) 
  def dayMIncrement = every ~> dayOfMonth <~ "day" 
  def dayWIncrement = every ~> dayValue ^^ {
    case day => Map("dweek" -> day)
  }
  def dayIncrement = fieldIncrementers("day") | dayMIncrement | dayWIncrement 

  def increment = (timeIncrement 
                 | dayIncrement 
                 | fieldIncrementers("year") 
                 | fieldIncrementers("month")) 

  def incrementers = increment ~ connectors ^^ {
    case incremented ~ values => applyModifers(values ++ incremented)
  }

  // Conversion
  private def applyModifers(result: Map[String, String]) = {
    val (mods, fin) = result.partition(_._1.endsWith("Modifier"))
    for((k, v) <- fin) yield((k, v + mods.getOrElse(k+"Modifier", "")))
  }

  // Public conversions
  def cron = cronOption fold ({ msg =>
    throw new IllegalArgumentException(msg)
  }, a => a) 

  def crons = cron.toString

  // Safe conversion
  def cronOption = {
    parseAll(incrementers, syntax) match {
      case Success(parsed, _) =>
        Right(Cron(
          parsed.getOrElse("second", "0"),
          parsed.getOrElse("minute", "*"),
          parsed.getOrElse("hour", "*"),
          parsed.getOrElse("day", "*"),
          parsed.getOrElse("month", "*"),
          parsed.getOrElse("dweek", "*"),
          parsed.getOrElse("year", "*")
        ))
      case Failure(msg, _) =>
        Left(msg) 
      case Error(msg, _) =>
        Left(msg)
    }
  }
}
