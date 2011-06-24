# Cronish App

The console cronish app serves two functions:

  1. `cronish every 5 seconds` outputs `*/5 * * * * *`
  2. `cronish -t ls -l every 5 seconds` outputs `Running ls -l every 5 seconds`

So the first function is interpreting the cronish syntax into cron syntax,
and the second function acts as a unix type cron.
