# Cron DSL

Making use of Scala parser combinators to build a cron dsl library.

## The Projects

The main project is split up into separate sub-projects:

  * [core] is the `cronish` artifact: the underlying library
  * [app] is the `cronish-app` artifact: the conscripted program
  * [plugin] is the `cronish-sbt` artifact: the sbt plugin (interval execution)

Follow the links for the appropriate README / usage.

[app]: https://github.com/philcali/cronish/tree/master/app
[plugin]: https://github.com/philcali/cronish/tree/master/plugin
[core]: https://github.com/philcali/cronish/tree/master/core
