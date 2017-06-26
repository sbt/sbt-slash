package sbt
package sbtslash

import sbt._
import Keys._
import CommandStrings.InspectCommand

object SlashSyntaxPlugin extends AutoPlugin {
  override def requires = plugins.JvmPlugin
  override def trigger = allRequirements

  override lazy val globalSettings: Seq[Def.Setting[_]] = slashSettings

  object autoImport extends SlashSyntax {
    val Zero = Global
  }

  lazy val slashSettings: Seq[Def.Setting[_]] = Vector(
    onLoad := { s =>
      val ks0 = s.definedCommands
      val ks = ks0 map {
        case c: ArbitraryCommand =>
          val h = c.help(s)
          h.brief.toList match {
            case ("show <key>", _) :: xs => SlashParser.act 
            case _                       => c
          }
        case c: SimpleCommand =>
          if (c.name == InspectCommand) SlashInspect.inspect
          else c
        case x => x
      }
      s.copy(definedCommands = ks)
    }
  )
}
