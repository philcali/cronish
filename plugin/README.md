# Cronish SBT Plugin

This plugin adds interval task execution in the sbt console. With both
sbt task execution and / or shell execution.

The important tasks in this regard being:

      cronish-add-[sh|sbt] <command> runs <cronish>

An example of the two below.

  1. `cronish-add-sh echo "Maybe you should get off now" runs every day at 5pm`
  2. `cronish-add-sbt publish-local runs every Friday at midnight`
