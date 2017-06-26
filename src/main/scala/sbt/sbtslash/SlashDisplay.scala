package sbt
package sbtslash

object SlashDisplay {
  // from Def.scala
  def showRelativeKey(current: ProjectRef, multi: Boolean, keyNameColor: Option[String] = None): Show[ScopedKey[_]] = new Show[ScopedKey[_]] {
    def apply(key: ScopedKey[_]) =
      SlashScope.display(key.scope,
        Def.colored(key.key.label, keyNameColor),
        ref => displayRelative(current, multi, ref, true))
  }

  private[sbt] def displayRelative(current: ProjectRef, multi: Boolean, project: Reference,
    trailingSlash: Boolean): String = {
    val slash = if (trailingSlash) "/" else ""
    project match {
      case BuildRef(current.build)      => "ThisBuild" + slash
      case `current`                    => if (multi) current.project + slash else ""
      case ProjectRef(current.build, x) => x + slash
      case _                            => Reference.display(project) + slash
    }
  }
}
