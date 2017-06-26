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

In addition to the current parser, this plugin adds `<project-id>/<config-ident>/intask/key` where `<config-ident>` is the Scala identifier notation for the configurations like `Compile` and `Test`.

These examples work both from the shell

```scala
> show Global/cancelable
> ThisBuild/scalaVersion
> Test/compile
> root/Compile/compile/scalacOptions
```

The output of the `inspect` command is copy-pastable

```
> inspect compile
[info] Task: sbt.inc.Analysis
[info] Description:
[info]  Compiles sources.
[info] Provided by:
[info]  {file:/xxx/}hellotest/compile:compile
[info] Defined at:
[info]  (sbt.Defaults) Defaults.scala:327
[info] Dependencies:
[info]  Compile/manipulateBytecode
[info]  Compile/incCompileSetup
[info] Reverse dependencies:
[info]  Compile/products
[info]  Compile/printWarnings
[info]  Compile/discoveredSbtPlugins
[info]  Compile/discoveredMainClasses
[info] Delegates:
[info]  Compile/compile
[info]  compile
[info]  ThisBuild/Compile/compile
[info]  ThisBuild/compile
[info]  Zero/Compile/compile
[info]  Global/compile
[info] Related:
[info]  Test/compile
```

The unified slash syntax also works in `build.sbt`

```scala
lazy val root = (project in file("."))
  .settings(
    Global / cancelable := true,
    ThisBuild / scalaVersion := "2.11.11",
    Test / test := (),
    Compile / scalacOptions += "-deprecation",
    Compile / console / scalacOptions += "-Ywarn-numeric-widen"
  )
```

  [contrib]: https://contributors.scala-lang.org/t/unification-of-sbt-shell-notation-and-build-sbt-dsl/913
