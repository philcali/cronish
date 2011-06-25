# Cronish SBT Plugin

This plugin adds interval task execution in the sbt console. With both
sbt task execution and / or shell execution.

## Installation

In your `project/plugins/build.sbt` definition, place the following line:
  
    libraryDependencies += "com.github.philcali" %% "cronish-sbt" % "0.0.1"

In your `build.sbt` or `project/Build.scala`, you must include `SbtCronish.cronishSettings`.

## Cronish Tasks

The important tasks in this regard being:

    cronish-add-[sh|sbt] <command> runs <cronish>

An example of the two below.

  1. `cronish-add-sh echo "Maybe you should get off now" runs every day at 5pm`
  2. `cronish-add-sbt publish-local runs every Friday at midnight`

The `cronish-list` task will print out all the active runs for the session.

## Convenience Methods

The plugin comes with the `add` object with three convenience methods:

  * `sh (cmd: String): CronTask` for basic shell commands
  * `sbt (cmd: String, st: State): CronTask` for sbt commands
  * `> (work: ProcessBuilder): CronTask` for advanced commands 

## Interjecting Intervals

With the convenience methods mentioned above, it's very easy to
interject interval commands in both settings and tasks. Let's pretend
you wanted to inject a `publish-local` interval automatically, after
your first update.

    val publishNightly= TaskKey[Unit]("publish-nightly")

    override val settings = Seq (
      publishNightly <<= (state, streams) map { (st, s) =>
        add sbt ("publish-local", st) runs "every day at midnight"
        s.log.info("Injecting nightly publish-local command")
      }, 
      update <<= update dependsOn publishNightly
    )

## Initializing Cronish Tasks 

You can use the `cronishTasks` setting to configure cronish tasks upon
initialization. Because this is a setting, you are limited to shell
commands, and `ProcessBuilder`s. Here's an example:

    cronishTasks := Seq (
      add sh "echo Take a break, dude" runs hourly,
      add > "wget http://google.com" #> new File("google.html") runs daily,
      job ("echo get off dude" !) runs "every day at 5pm"
    )

That's all there is to it.
