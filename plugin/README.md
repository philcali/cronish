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

## Initializing Cronish Tasks 

You can use the `cronishTasks` setting to configure cronish tasks upon
initialization. Here's an example:

    cronishTasks := Seq (
      job("echo Maybe you should get off now" !) runs "every day at 5pm"
    )

That's all there is to it.
