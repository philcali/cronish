package com.philipcali.cron
package dsl

import scala.util.parsing.combinator._
import com.github.philcali.scalendar._
import math.abs

case class Cron (second: String, 
                 minute: String, 
                 hour: String, 
                 dmonth: String, 
                 month: String, 
                 dweek: String,
                 year: String) {

  // Helpful extractors for understanding Cron
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
      if(field.contains(",")) {
        Some(field.split(",").map(_.toInt).toList)
      } else if(field.contains("-")) {
        val Array(start, end) = field.split("-").map(_.toInt)
        Some((start to end).toList)
      } else None
    }
  }


  // Actuals and Potentials are field values
  trait FieldValue {
    val value: Int
    def field: String
    def get: FieldValue
    def next: FieldValue
  }
  case class Actual(value: Int) extends FieldValue {
    def field = value.toString
    def get = Actual(value)
    def next = get
  }
  case class Potential(value: Int, original: String, cycle: Seq[Int]) extends FieldValue {
    def field = original
    def get = Actual(cycle.head)
    def next = {
      if(value == cycle.last) get else {
        val ci = cycle.indexOf(value)
        Actual(cycle(ci + 1))
      }
    }
  }

  override def toString = 
    List(minute, hour, dmonth, month, dweek) mkString (" ")

  private def pullDateValue(field: String, now: Scalendar,
                            everything: Seq[Int], 
                            valued: Scalendar => Int, 
                            modifier: Int => conversions.Evaluated) = {
    field match {
      case "*" => Potential(valued(now), field, everything)
      case "L" => Potential(everything.last, field, List(everything.last)) 
      case FieldModifier(mod) => Actual(valued(now + modifier(mod)))
      case FieldList(fields) => 
        fields.find(_ >= valued(now)) match {
          case Some(f) => Potential(f, field, fields)
          case None => Potential(fields.head, field, fields)
        }
      case _ => Actual(field.toInt)
    }
  }

  private def createCal(fields: Seq[FieldValue]) = {
    Scalendar(second = fields(0).value,
              minute = fields(1).value,
              hour = fields(2).value,
              day = fields(3).value,
              month = fields(4).value,
              year = fields(5).value)  
  }

  private def everyday(now: Scalendar) = {
    val first = now.day(1)
    val last = (first + 1.month) - 1.day
    first.day.value to last.day.value
  }

  private def everyyear(now: Scalendar) = {
    now.year.value to (now.year.value + 1)
  }

  def next = nextFrom(Scalendar.now)

  def nextTime = {
    val now = Scalendar.now
    Scalendar(now.time + nextFrom(now))
  }

  def nextFrom(now: Scalendar) = {

    def evaluate(sec: String, min: String, h: String, 
                 dmon: String, mon: String, y: String): Scalendar = {

      // Get a days test
      val taway = pullDateValue(mon, now, (1 to 12), _.month.value, _.months)

      // Smallest to largest
      val fields = List(
         pullDateValue(sec, now, (0 to 59), _.second.value, _.seconds),
         pullDateValue(min, now, (0 to 59), _.minute.value, _.minutes),
         pullDateValue(h, now, (0 to 23), _.hour.value, _.hours),
         pullDateValue(dmon, now, everyday(now.month(taway.value)), _.day.value, _.days),
         taway,
         pullDateValue(y, now, everyyear(now), _.year.value, _.years)
      )

      // First attempt
      val attempt = createCal(fields) 
      val difference = (now to attempt).delta.milliseconds

      val completed = fields.foldLeft(true)(_ && _.isInstanceOf[Actual])

      if (completed) {
        def applyDay(dvalue: Int) = {
          val test = attempt.inWeek(dvalue + 1)
          (attempt to test).delta.milliseconds >= 0
        }
        pullDateValue(dweek, attempt, (0 to 6), _.inWeek - 1, _.weeks) match {
          case Actual(value) if !applyDay(value) => attempt.inWeek(value + 1) + 1.week
          case Actual(value) => 
            val test = attempt.inWeek(value + 1) - (1 week)
            if((now to test).delta.milliseconds < 0) attempt.inWeek(value + 1)
            else test
          case Potential(_, _, cycle) => cycle.find(applyDay) match {
            case Some(day) => attempt.inWeek(day + 1)
            case None => attempt.inWeek(cycle.head) + 1.week
          }
        }
      } else {
        val indexed = fields.zipWithIndex
        val interest = indexed.filter(_._1.isInstanceOf[Potential]).find { 
          case (field, ix) => 
            val test = createCal(indexed.map {
              case (f, i) if i < ix => f.get
              case (f, i) if i == ix => f.next
              case (f, _) => Actual(f.value)
            })
            (attempt to test).delta.milliseconds >= 0
        }
        val changed = interest match {
          case Some(id) =>
            indexed.map {
              case (f, ix) if ix == id._2 => f.next.field
              case (f, ix) if ix > id._2 => Actual(f.value).field
              case (f, _) => f.field
            }
          case None => indexed.map( f => f._1.get.field)
        }
        evaluate(changed(0), changed(1), changed(2), changed(3), changed(4), changed(5))
      }
    }

    val next = evaluate(
      second,
      minute,
      hour,
      dmonth,
      month,
      year
    )

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

  def connector = timeConnector 
                | dayConnector 
                | yearConnector 
                | monthConnector 
                | dayOfMonthConnector

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
  def cron = cronOption fold (error(_), a => a) 

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
