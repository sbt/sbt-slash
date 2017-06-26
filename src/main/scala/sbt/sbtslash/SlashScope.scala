package sbt
package sbtslash

object SlashScope {

  private[sbt] val configIdents: Map[String, String] =
    Map(
      "it" -> "IntegrationTest",
      "scala-tool" -> "ScalaTool",
      "plugin" -> "CompilerPlugin"
    )
  private[sbt] val configIdentsInverse: Map[String, String] =
    configIdents map { _.swap }

  private[sbt] def guessConfigIdent(conf: String): String =
    configIdents.applyOrElse(conf, (x: String) => x.capitalize)

  private[sbt] def unguessConfigIdent(conf: String): String =
    configIdentsInverse.applyOrElse(conf, (x: String) =>
      x.take(1).toLowerCase + x.drop(1)
    )
}
