package cronish

import scalendar._
import java.util.Calendar._

case class Cron (second: String, 
                 minute: String, 
                 hour: String, 
                 dmonth: String, 
                 month: String, 
                 dweek: String,
                 year: String) {

  def full = List(second, minute, hour, dmonth, month, dweek, year).mkString(" ")

  // Helpful extractors for understanding Cron
  object FieldModifier {
    def unapply(field: String) = {
      if(!field.contains("/")) None
      else {
        val Array(value, mod) = field.split("/")
        Some((value, mod.toInt))
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

  object FieldNumber {
    def unapply(field: String) = {
      if (field.contains("#")) {
        val Array(day, number) = field.split("#").map(_.toInt)
        Some((day, number))
      } else None
    }
  }

  object FieldLast {
    def unapply(field: String) = {
      if (field.contains("L")) {
        Some(field.split("L")(0).toInt)
      } else None
    }
  }

  trait Fields {
    val now: Scalendar
    val field: String
    val everything: Seq[Int]
    val fieldType: Int

    lazy val under = pullValue 

    def isPotential = under.isInstanceOf[Potential] 
    def isNotDefined = field == "*"

    def valued(cal: Scalendar) = cal.cal.get(fieldType) 
    def handler(cal: Scalendar, amount: Int) = cal.set(fieldType, amount) 
    def modifier(amount: Int) = conversions.Evaluated(fieldType, amount)

    def evaluate(cal: Scalendar) = handler(cal, under.value)
    def evaluateHead(cal: Scalendar) = handler(cal, under.get.value)
    def evaluateNext(cal: Scalendar) = handler(cal, under.next.value)

    def pullValue: FieldValue

    def resetWith(cal: Scalendar): Fields
  } 

  trait BaseFieldEval extends Fields {
    def pullValue = field match {
      case "*" => Potential(valued(now), everything)
      case "L" => Potential(everything.last, List(everything.last)) 
      case FieldModifier(value, mod) if value == "*" =>
        val valr = valued(now)
        val modder = valr % mod
        val n = if(modder == 0) valr + mod else valr - modder + mod
        Potential(n, everything)
      case FieldModifier(value, mod) => Potential(value.toInt, everything)
      case FieldList(fields) => 
        fields.find(_ >= valued(now)) match {
          case Some(f) => Potential(f, fields)
          case None => Potential(fields.head, fields)
        }
      case FieldNumber(value, mod) => Potential(value, everything)
      case FieldLast(value) => Potential(value, everything)
      case _ => Actual(field.toInt)
    }
  }

  case class SecondField(field: String, now: Scalendar) extends BaseFieldEval {
    val fieldType = SECOND
    val everything = (0 to 59)
    
    def resetWith(cal: Scalendar) = SecondField(field, cal)
  }

  case class MinuteField(field: String, now: Scalendar) extends BaseFieldEval {
    val fieldType = MINUTE
    val everything = (0 to 59)

    def resetWith(cal: Scalendar) = MinuteField(field, cal)
  }

  case class HourField(field: String, now: Scalendar) extends BaseFieldEval {
    val fieldType = HOUR_OF_DAY
    val everything = (0 to 23)

    def resetWith(cal: Scalendar) = HourField(field, cal)
  }

  case class DayField(field: String, now: Scalendar) extends BaseFieldEval {
    val fieldType = DATE
    val everything = {
      val endofmonth = now.day(1) + 1.month - 1.day
      (1 to endofmonth.day.value)
    }

    def resetWith(cal: Scalendar) = DayField(field, cal)
  }

  case class MonthField(field: String, now: Scalendar) extends BaseFieldEval {
    val fieldType = MONTH
    val everything = (1 to 12)

    override def valued(cal: Scalendar) = super.valued(cal) + 1
    override def handler(cal: Scalendar, amount: Int) = 
      super.handler(cal, amount - 1)

    // Bumping the month requires us to zero roll the days 
    override def evaluateNext(cal: Scalendar) = 
      super.evaluateNext(cal.day(1))

    def resetWith(cal: Scalendar) = MonthField(field, cal)
  }

  case class YearField(field: String, now: Scalendar) extends BaseFieldEval {
    val fieldType = YEAR
    val everything = {
      val year = now.year.value
      (year to year + 1)
    }

    def resetWith(cal: Scalendar) = YearField(field, cal)
  }

  case class DayOfWeekField(field: String, now: Scalendar) extends BaseFieldEval {
    val fieldType = DAY_OF_WEEK
    val everything = (0 to 6)

    def resetWith(cal: Scalendar) = DayOfWeekField(field, now)

    private def findAll(cal: Scalendar, day: Int) = {
      val begin = cal.day(1)
      begin to (begin + 1.month) by 1.day filter(_.inWeek == day)
    }
    override def valued(cal: Scalendar) = super.valued(cal) - 1
    override def handler(cal: Scalendar, amount: Int) =
      super.handler(cal, amount + 1)

    override def evaluate(cal: Scalendar) = internalEval(cal)
    override def evaluateHead(cal: Scalendar) = internalEval(cal.day(1))
    override def evaluateNext(cal: Scalendar) = internalEval(cal)

    def internalEval(cal: Scalendar) = field match {
      // Everything indicates that we will use the day field
      case "*" => cal
      // A regular last means we simply use the last day of the week
      case "L" => cal.inWeek(Day.Saturday)
      // Any defined list of fields means we're shooting for week increments
      case FieldList(fields) => 
        fields.find(f => cal.inWeek(f + 1) > cal) match {
          case Some(f) => cal.inWeek(f + 1)
          case None => 
            val test = cal.inWeek(fields.head + 1) + 1.week
            if (test.month != cal.month) findAll(cal, fields.head + 1).head.start
            else test
        }
      // Modifiers are special syntaxes for day of week
      case FieldModifier(value, mod) => 
        val day = value.toInt + 1
        val weeks = cal.calendarMonth.by(1 week).foldLeft(0) { (in, _) => in + 1 }
        // Find the first instance of the day
        val begin = cal.day(1) + 1.week
        begin to (begin + 1.week) by 1.day find (_.inWeek == day) map { d =>
          // Modify the found day by the modifier (in weeks)
          (0 until weeks / mod).map(e => d.start + (mod * e).weeks)
            .find(_ >= cal) match {
            case Some(t) => t
            case None => 
              val test = cal.inWeek(day)
              if (test > cal) test + (mod - 1).week else test + mod.week
          }
        } getOrElse cal
      // Field numbers are another special syntax
      case FieldNumber(value, mod) =>
        val day = value + 1
        // Count the number of times the day appears in the month
        val occur = findAll(cal, day)
        // There doesn't exists this day
        if (mod > occur.size) occur.head.start
        // There exists this day
        else occur.zipWithIndex.find(_._2 + 1 == mod) match {
          case Some((d, _)) => d.start
          case None => cal 
        }
      // Field Last are yet another special day of week syntax
      case FieldLast(value) =>
        val day = value + 1
        val begin = cal.day(1)
        val last = begin + 1.month - 1.day
        val attempt = last.inWeek(day)
        if (attempt.month != last.month) attempt - 1.week
        else attempt 
      // A static number
      case _ =>
        val day = field.toInt + 1
        val attempt = if(day <= cal.inWeek && cal <= now) cal.inWeek(day) + 1.week 
                      else cal.inWeek(day)
        if (attempt.month != now.month) findAll(attempt, day).head.start 
        else attempt 
    }
  }

  // Actuals and Potentials are field values
  trait FieldValue {
    val value: Int
    def get: FieldValue
    def next: FieldValue
  }
  case class Actual(value: Int) extends FieldValue {
    def get = Actual(value)
    def next = get
  }
  case class Potential(value: Int, cycle: Seq[Int]) extends FieldValue {
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

  def next = nextFrom(Scalendar.now)

  def nextTime = {
    val now = Scalendar.now
    Scalendar(now.time + nextFrom(now))
  }

  def nextFrom(now: Scalendar) = {
    val dmonthField = DayField(dmonth, now)
    val dweekField = DayOfWeekField(dweek, now)
    val dayField = if (dweekField.isNotDefined) dmonthField else dweekField 

    val fields = List (
      SecondField(second, now),
      MinuteField(minute, now),
      HourField(hour, now),
      dayField,
      MonthField(month, now),
      YearField(year, now)
    )

    val indexed = fields.zipWithIndex

    // Fill in values from calendar
    val attempt = fields.foldLeft(now.millisecond(0)) { (in, field) =>
      field.resetWith(in).evaluate(in)
    }

    lazy val rolled = fields.foldLeft(attempt) { (in, field) => 
      field.evaluateHead(in) 
    } 

    // Not good enough for a first attempt
    // If the first attempt works, then we use it
    val next = if (attempt <= now) {
      // Find a game changing potential, if one exists
      val interest = indexed.filter(_._1.isPotential).find {
        case (field, ix) => 
          val test = field.evaluateNext(attempt) 
          test > attempt && test > now
      }

      // If a game changing potential was found, then we use it
      // otherwise we get the head of each potential
      interest match {
        case Some((f, ix)) => indexed.reverse.foldLeft(attempt) { 
          (in, dup) => dup match {
            case (field, i) if i < ix => field.resetWith(in).evaluateHead(in)
            case (field, i) if i == ix => field.evaluateNext(in)
            case (field, i) => field.evaluate(in)
          }
        }
        case None => fields.foldLeft(attempt) { (in, field) =>
          field.evaluateHead(in)
        } 
      }
    } else if (rolled > now) rolled else attempt 

    (now to next).delta.milliseconds
  }
}
