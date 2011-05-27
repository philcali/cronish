package com.github.philcali.cronish

import com.github.philcali.scalendar.{
  Scalendar,
  Imports,
  conversions
}
import Imports._

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
    Scalendar(fields(5).value,
              fields(4).value,
              fields(3).value,
              fields(2).value,
              fields(1).value,
              fields(0).value, 0)
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
                 dmon: String, mon: String, y: String, t: Scalendar): Scalendar = {

      // Get a days test
      val taway = pullDateValue(mon, t, (1 to 12), _.month.value, _.months)

      // Smallest to largest
      val fields = List(
         pullDateValue(sec, t, (0 to 59), _.second.value, _.seconds),
         pullDateValue(min, t, (0 to 59), _.minute.value, _.minutes),
         pullDateValue(h, t, (0 to 23), _.hour.value, _.hours),
         pullDateValue(dmon, t, everyday(now.month(taway.value)), _.day.value, _.days),
         taway,
         pullDateValue(y, t, everyyear(now), _.year.value, _.years)
      )

      // First attempt
      val attempt = createCal(fields) 
      val difference = (t to attempt).delta.milliseconds

      val completed = fields.foldLeft(true)(_ && _.isInstanceOf[Actual])

      if (completed) {
        def applyDay(dvalue: Int) = {
          val test = attempt.inWeek(dvalue + 1)
          (attempt to test).delta.milliseconds >= 0
        }
        pullDateValue(dweek, attempt, (0 to 6), _.inWeek - 1, _.weeks) match {
          case Actual(value) if !applyDay(value) => attempt.inWeek(value + 1) + 1.week
          case Actual(value) => 
            val test = attempt.inWeek(value + 1) - 1.week
            if ((t to test).delta.milliseconds < 0) attempt.inWeek(value + 1)
            else test
          case Potential(_, _, cycle) => cycle.find(applyDay) match {
            case Some(day) => attempt.inWeek(day + 1)
            case None => attempt.inWeek(cycle.head + 1) + 1.week
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
        evaluate(changed(0), changed(1), changed(2), changed(3), changed(4), changed(5), t)
      }
    }

    val next = evaluate(
      second,
      minute,
      hour,
      dmonth,
      month,
      year, now
    )

    // If it's negative now, then there's nothing we can do about it
    (now to next).delta.milliseconds
  }
}
