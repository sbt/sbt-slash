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

  def displayConfig(config: ConfigKey): String = guessConfigIdent(config.name) + "/"

  def display(scope: Scope, sep: String): String = displayMasked(scope, sep, showProject, ScopeMask())

  def display(scope: Scope, sep: String, showProject: Reference => String): String = displayMasked(scope, sep, showProject, ScopeMask())

  def displayMasked(scope: Scope, sep: String, showProject: Reference => String, mask: ScopeMask): String =
    displayMasked(scope, sep, showProject, mask, false)

  /**
   * unified / style introduced in sbt 0.13.16 / sbt 1.0
   * By defeault, sbt will no longer display the Zero-config,
   * so `name` will render as `name` as opposed to `{uri}proj/Zero/name`.
   * Technically speaking an unspecified configuration axis defaults to
   * the scope delegation (first configuration defining the key, then Zero).
   */
  def displayMasked(scope: Scope, sep: String, showProject: Reference => String, mask: ScopeMask, showZeroConfig: Boolean): String =
    {
      import scope.{ config, extra }
      val zeroConfig = if (showZeroConfig) "Zero/" else ""
      val configPrefix = config.foldStrict(displayConfig, zeroConfig, "./")
      val taskPrefix = scope.task.foldStrict(_.label + "/", "", "./")
      val extras = extra.foldStrict(_.entries.map(_.toString).toList, Nil, Nil)
      val postfix = if (extras.isEmpty) "" else extras.mkString("(", ", ", ")")
      if (scope == GlobalScope) "Global/" + sep + postfix
      else mask.concatShow(projectPrefix(scope.project, showProject), configPrefix, taskPrefix, sep, postfix)
    }

  def showProject = (ref: Reference) => Reference.display(ref) + "/"

  def projectPrefix(project: ScopeAxis[Reference], show: Reference => String = showProject): String =
    project.foldStrict(show, "Zero/", "./")

}
