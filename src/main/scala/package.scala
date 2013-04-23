package cronish

/**
 * Provides showtcuts for creating tasks and schedules
 */
package object dsl {
  implicit def string2cron(syntax: String) = Cronish(syntax)

  private[dsl] class RichLimitInt(limit: Int) {
    def times = new Limited(limit)
  }

  implicit def int2stopgap(limit: Int) = new RichLimitInt(limit)

  /**
   * every hour at 0:00
   */
  val hourly = "Every hour at 0:00".cron

  /**
   * every day at midnight
   */
  val daily = "Every day at midnight".cron

  /**
   * every month on Sunday at midnight
   */
  val weekly = "Every month on Sunday at midnight".cron

  /**
   * every 1st day in every month at midnight
   */
  val monthly = "Every 1st day in every month at midnight".cron

  /**
   * every Every year on the 1st day in January at midnight
   */
  val yearly = "Every year on the 1st day in January at midnight".cron

/**
 * create a [[CronTask]] from an anonymous function.
 *
 * {{
 *   val payroll = task {
 *       println("You have just been paid... Finally!")
 *   }
 * }}
 */
  def task[A](action: => A) = new CronTask(action)

  /**
   * alias for the task function
   */
  def job[A](action: => A) = task(action)
}
