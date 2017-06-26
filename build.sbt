lazy val commonSettings: Seq[Setting[_]] = Seq(
  version in ThisBuild := "0.1.0",
  organization in ThisBuild := "com.eed3si9n"
)

lazy val root = (project in file(".")).
  settings(commonSettings).
  settings(
    sbtPlugin := true,
    name := "sbt-slash",
    description := "sbt plugin to create a single fat jar",
    licenses := Seq("Apache v2" -> url("https://github.com/sbt/sbt-assembly/blob/master/LICENSE")),
    scalacOptions := Seq("-deprecation", "-unchecked"),
    libraryDependencies ++= Seq(
    ),
    ScriptedPlugin.scriptedSettings,
    scriptedLaunchOpts := { scriptedLaunchOpts.value ++
      Seq("-Xmx1024M", "-XX:MaxPermSize=256M", "-Dplugin.version=" + version.value)
    },
    scriptedBufferLog := false,
    publishMavenStyle := false,
    bintrayOrganization := None,
    bintrayRepository := "sbt-plugins"
  )
