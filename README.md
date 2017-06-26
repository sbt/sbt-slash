sbt-slash
=========

sbt-slash is a sbt plugin that introduces unified slash syntax to both the sbt shell and build.sbt.
See also [Unification of sbt shell notation and build.sbt DSL][contrib]

![slash](slash.jpg?raw=true)

setup
-----

This is an auto plugin, so you need sbt 0.13.5+. Put this in `project/plugins.sbt` or `~/.sbt/0.13/plugins/slash.sbt`:

```scala
addSbtPlugin("com.eed3si9n" % "sbt-slash" % "0.1.0")
```

usage
-----

These examples work both from the shell and in build.sbt.

```
Global / cancelable
ThisBuild / scalaVersion
Test / test
root / Compile / compile / scalacOptions
```

  [contrib]: https://contributors.scala-lang.org/t/unification-of-sbt-shell-notation-and-build-sbt-dsl/913
