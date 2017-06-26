 lazy val root = (project in file("."))
  .settings(
    Global / cancelable := true,
    ThisBuild / scalaVersion := "2.11.11",
    Test / test := (),
    console / scalacOptions += "-deprecation",
    Compile / console / scalacOptions += "-Ywarn-numeric-widen",
    projA / Compile / console / scalacOptions += "-feature",
    Zero / Zero / name := "foo"
  )

lazy val projA = (project in file("a"))
