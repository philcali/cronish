package com.philipcali.cron

package object dsl {
  implicit def string2cron(syntax: String) = new Cronish(syntax)
}
