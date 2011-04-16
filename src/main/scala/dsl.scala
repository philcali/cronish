package com.philipcali.cron
package dsl

import scala.util.parsing.combinator._
import com.github.philcali.scalendar._
import math.abs

object FieldModifier {
  def unapply(field: String) = {
    if(!field.contains("/")) None
    else {
      Some(field.split("/")(1).toInt)
    }
  }
}

object FieldList {
  def unapply(field: String) = {
    if(!field.contains("-")) None
    else {
      val fs = field.split("-")
      Some((fs(0).toInt, fs(1).toInt))
    }
  }
}

object FieldReps {
  def unapply(field: String) = {
    if(!field.contains(",")) None
    else {
      Some(field.split(",").map(_.toInt))
    }
  }
}

trait FieldValue {
  val value: Int
  def get: FieldValue 
}

case class Actual(value: Int) extends FieldValue {
  def get = Actual(value)
}

case class Potential(value: Int) extends FieldValue {
  // Flatten
  def get = Actual(0)
}

case class Cron (second: String, 
                 minute: String, 
                 hour: String, 
                 dmonth: String, 
                 month: String, 
                 dweek: String,
                 year: String) {
  override def toString = List(minute, hour, dmonth, month, dweek) mkString (" ")

  private def pullDateValue(field: String, now: Scalendar, 
                            valued: Scalendar => Int, modifier: Int => conversions.Evaluated) = {
    field match {
      case "*" => Potential(valued(now)) 
      case FieldModifier(mod) => Actual(valued(now + modifier(mod)))
      case FieldList(start, end) => 
        if(start > valued(now)) Actual(start) else Actual(end)
      case FieldReps(fields) => 
        fields.find(_ >= valued(now)) match {
          case Some(f) => Actual(f)
          case None => Potential(valued(now))
        }
      case _ => Actual(field.toInt)
    }
  }

  private def conversionPlan(ix: Int) = ix match {
    case 0 => (x: Long) => x / 1000
    case 1 => (x: Long) => x / 1000 / 60
    case 2 => (x: Long) => x / 1000 / 60 / 60
    case 3 | 5 => (x: Long) => x / 1000 / 60 / 60 / 24
    case 4 => (x: Long) => x / 1000 / 60 / 60 / 24 / 30
    case 6 => (x: Long) => x / 1000 / 60 / 60 / 24 / 365
  }

  private def createCal(fields: List[FieldValue]) = {
      Scalendar(second = fields(0).value,
                minute = fields(1).value,
                hour = fields(2).value,
                day = fields(3).value,
                month = fields(4).value,
                year = fields(6).value)
  }

  def next = {
    val now = Scalendar.now

    // Smallest to largest
    val fields = List(pullDateValue(second, now, _.second.value, _.seconds),
                      pullDateValue(minute, now, _.minute.value, _.minutes),
                      pullDateValue(hour, now, _.hour.value, _.hours),
                      pullDateValue(dmonth, now, _.day.value, _.days),
                      pullDateValue(month, now, _.month.value, _.months),
                      pullDateValue(dweek, now, (_.day.inWeek - 1), _.days),
                      pullDateValue(year, now, _.year.value, _.years))

    // First attempt
    val attempt = createCal(fields) 
    val difference = (now to attempt).delta.milliseconds
  
    // A zero or negative difference means we need to find the smallest potential
    // which will make the difference positive, and flatten the the potentials smaller
    // than that one
    val indexed = fields.zipWithIndex

    val incrementer = if(difference <= 0) 1 else 0

    val next = indexed.filter(_._1.isInstanceOf[Potential]).find { izd =>
      val (potential, ix) = izd
      if (conversionPlan(ix) (abs(difference)) < 1) true else false 
    } match {
      case Some((_, ix)) => createCal(indexed.map { thing =>
          val (p, i) = thing
          if(i < ix) if(i == 3) Actual(1) else p.get 
          else if(i == ix) Actual(p.value + incrementer) 
          else {
            println(i)
            Actual(p.value) 
          }
      })
      case None => attempt
    }

    // If it's negative now, then there's nothing we can do about it
    (now to next).delta.milliseconds
  }
}

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

  def connector = timeConnector | dayConnector | yearConnector | monthConnector | dayOfMonthConnector

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
  def cron = cronOption match {
    case Left(crond) => crond
    case Right(msg) => error(msg)
  }

  def crons = cron.toString

  // Safe conversion
  def cronOption = {
    parseAll(incrementers, syntax) match {
      case Success(parsed, _) =>
        Left(Cron(
          parsed.getOrElse("second", "0"),
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
