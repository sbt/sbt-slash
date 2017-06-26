package sbt
package sbtslash

import sbt._
import Keys.{ sessionSettings, thisProject }
import java.io.File
import BasicCommandStrings._
import complete.{ DefaultParsers, Parser }
import CommandStrings.{ MultiTaskCommand, ShowCommand }
import Aggregation.{ KeyValue, Values }
import Def.ScopedKey
import Act.{ key, defaultConfigurations, select, extraAxis, toAxis, examplesStrict, examples => examples2,
  nonEmptyConfig, filterStrings }
import SlashDisplay.showRelativeKey

object SlashParser {
  import DefaultParsers._

  private val GlobalString = "*"
  private val GlobalIdent = "Global"
  private val ZeroIdent = "Zero"
  private val ThisBuildIdent = "ThisBuild"

  // new separator for unified shell syntax. this allows optional whitespace around /.
  private val spacedSlash: Parser[Unit] = token(OptSpace ~> '/' <~ OptSpace).examples("/").map(_ => ())
  private val spacedComma = token(OptSpace ~ ',' ~ OptSpace)
  /** Parses a single letter, according to Char.isUpper, into a Char. */
  private lazy val Upper = charClass(_.isUpper, "upper")
  /** Parses a non-symbolic Scala-like identifier.  The identifier must start with [[Upper]] and contain zero or more [[ScalaIDChar]]s after that.*/
  private lazy val CapitalizedID = identifier(Upper, ScalaIDChar)

  def act = Command.customHelp(actParser, actHelp)
  private def actHelp = (s: State) => CommandStrings.showHelp ++ CommandStrings.multiTaskHelp ++ BuiltinCommands.keysHelp(s)

  // borrowed from sbt
  private def actParser(s: State): Parser[() => State] = requireSession(s, actParser0(s))
  private def actParser0(state: State): Parser[() => State] =
    {
      val extracted = Project extract state
      import extracted.{ showKey, structure }
      import Aggregation.evaluatingParser
      actionParser.flatMap { action =>
        val akp = aggregatedKeyParser(extracted)
        def evaluate(kvs: Seq[ScopedKey[_]]): Parser[() => State] = {
          val preparedPairs = anyKeyValues(structure, kvs)
          val showConfig = Aggregation.defaultShow(state, showTasks = action == ShowAction)
          evaluatingParser(state, structure, showConfig)(preparedPairs) map { evaluate =>
            () => {
              val keyStrings = preparedPairs.map(pp => showKey(pp.key)).mkString(", ")
              state.log.debug("Evaluating tasks: " + keyStrings)
              evaluate()
            }
          }
        }
        action match {
          case SingleAction => akp flatMap evaluate
          case ShowAction | MultiAction =>
            rep1sep(akp, token(Space)).flatMap(kvss => evaluate(kvss.flatten))
        }
      }
    }

  // borrowed from sbt
  // the index should be an aggregated index for proper tab completion
  private def scopedKeyAggregated(current: ProjectRef, defaultConfigs: Option[ResolvedReference] => Seq[String], structure: BuildStructure): KeysParser =
    for {
      selected <- scopedKeySelected(structure.index.aggregateKeyIndex, current, defaultConfigs, structure.index.keyMap, structure.data)
    } yield Aggregation.aggregate(selected.key, selected.mask, structure.extra)

  // borrowed from sbt
  private def scopedKeySelected(index: KeyIndex, current: ProjectRef, defaultConfigs: Option[ResolvedReference] => Seq[String],
    keyMap: Map[String, AttributeKey[_]], data: Settings[Scope]): Parser[ParsedKey] =
    scopedKeyFull(index, current, defaultConfigs, keyMap) flatMap { choices =>
      select(choices, data)(showRelativeKey(current, index.buildURIs.size > 1))
    }

  private def scopedKeyFull(index: KeyIndex, current: ProjectRef, defaultConfigs: Option[ResolvedReference] => Seq[String], keyMap: Map[String, AttributeKey[_]]): Parser[Seq[Parser[ParsedKey]]] =
    {
      def taskKeyExtra(proj: Option[ResolvedReference], confAmb: ParsedAxis[String], baseMask: ScopeMask): Seq[Parser[ParsedKey]] =
        for {
          conf <- configs(confAmb, defaultConfigs, proj, index)
        } yield for {
          taskAmb <- taskAxis(conf, index.tasks(proj, conf), keyMap)
          task = resolveTask(taskAmb)
          key <- key(index, proj, conf, task, keyMap)
          extra <- extraAxis(keyMap, IMap.empty)
        } yield {
          val mask = baseMask.copy(task = taskAmb.isExplicit, extra = true)
          new ParsedKey(makeScopedKey(proj, conf, task, extra, key), mask)
        }

      def fullKey =
        for {
          rawProject <- optProjectRef(index, current)
          proj = resolveProject(rawProject, current)
          confAmb <- configIdent(index.configs(proj), index.configs(proj) map SlashScope.guessConfigIdent)
          partialMask = ScopeMask(rawProject.isExplicit, confAmb.isExplicit, false, false)
        } yield taskKeyExtra(proj, confAmb, partialMask)

      val globalIdent = token(GlobalIdent ~ spacedSlash) ^^^ ParsedGlobal
      def globalKey =
        for {
          g <- globalIdent
        } yield taskKeyExtra(None, ParsedZero, ScopeMask(true, true, false, false))

      globalKey | fullKey
    }


  def makeScopedKey(proj: Option[ResolvedReference], conf: Option[String], task: Option[AttributeKey[_]], extra: ScopeAxis[AttributeMap], key: AttributeKey[_]): ScopedKey[_] =
    ScopedKey(Scope(toAxis(proj, Global), toAxis(conf map ConfigKey.apply, Global), toAxis(task, Global), extra), key)

  def config(confs: Set[String]): Parser[ParsedAxis[String]] =
    {
      val sep = ':' !!! "Expected ':' (if selecting a configuration)"
      token((GlobalString ^^^ ParsedZero | value(examples2(ID, confs, "configuration"))) <~ sep) ?? Omitted
    }

  // New configuration parser that's able to parse configuration ident trailed by slash.
  private[sbt] def configIdent(confs: Set[String], idents: Set[String]): Parser[ParsedAxis[String]] =
    {
      val oldSep: Parser[Char] = ':'
      val sep: Parser[Unit] = spacedSlash !!! "Expected '/'"
      token(
        ((GlobalString ^^^ ParsedZero) <~ oldSep)
          | ((GlobalString ^^^ ParsedZero) <~ sep)
          | ((ZeroIdent ^^^ ParsedZero) <~ sep)
          | (value(examples2(ID, confs, "configuration")) <~ oldSep)
          | (value(examples2(CapitalizedID, idents, "configuration ident") map { SlashScope.unguessConfigIdent }) <~ sep)
      ) ?? Omitted
    }

  def configs(explicit: ParsedAxis[String], defaultConfigs: Option[ResolvedReference] => Seq[String], proj: Option[ResolvedReference], index: KeyIndex): Seq[Option[String]] =
    explicit match {
      case Omitted            => None +: defaultConfigurations(proj, index, defaultConfigs).flatMap(nonEmptyConfig(index, proj))
      case ParsedZero         => None :: Nil
      case ParsedGlobal       => None :: Nil
      case pv: ParsedValue[x] => Some(pv.value) :: Nil
    }
 
  def taskAxis(d: Option[String], tasks: Set[AttributeKey[_]], allKnown: Map[String, AttributeKey[_]]): Parser[ParsedAxis[AttributeKey[_]]] =
    {
      val taskSeq = tasks.toSeq
      def taskKeys(f: AttributeKey[_] => String): Seq[(String, AttributeKey[_])] = taskSeq.map(key => (f(key), key))
      val normKeys = taskKeys(_.label)
      val valid = allKnown ++ normKeys ++ taskKeys(_.rawLabel)
      val suggested = normKeys.map(_._1).toSet
      val keyP = filterStrings(examples2(ID, suggested, "key"), valid.keySet, "key") map valid
      (token(value(keyP) | GlobalString ^^^ ParsedZero | ZeroIdent ^^^ ParsedZero) <~ (token("::".id) | spacedSlash)) ?? Omitted
    }

  def resolveTask(task: ParsedAxis[AttributeKey[_]]): Option[AttributeKey[_]] =
    task match {
      case ParsedZero | ParsedGlobal | Omitted        => None
      case t: ParsedValue[AttributeKey[_]] @unchecked => Some(t.value)
    }

  def projectRef(index: KeyIndex, currentBuild: URI): Parser[ParsedAxis[ResolvedReference]] =
    {
      val global = token(GlobalString ~ spacedSlash) ^^^ ParsedZero
      val zeroIdent = token(ZeroIdent ~ spacedSlash) ^^^ ParsedZero
      val thisBuildIdent = value(token(ThisBuildIdent ~ spacedSlash) ^^^ BuildRef(currentBuild))
      val trailing = spacedSlash !!! "Expected '/' (if selecting a project)"
      global | zeroIdent | thisBuildIdent |
        value(resolvedReferenceIdent(index, currentBuild, trailing)) |
        value(resolvedReference(index, currentBuild, trailing))
    }

  private def resolvedReferenceIdent(index: KeyIndex, currentBuild: URI, trailing: Parser[_]): Parser[ResolvedReference] = {
    def projectID(uri: URI) = token(DQuoteChar ~> examplesStrict(ID, index projects uri, "project ID") <~ DQuoteChar <~ OptSpace <~ ")" <~ trailing)
    def projectRef(uri: URI) = projectID(uri) map { id => ProjectRef(uri, id) }

    val uris = index.buildURIs
    val resolvedURI = Uri(uris).map(uri => Scope.resolveBuild(currentBuild, uri))

    val buildRef = token("ProjectRef(" ~> OptSpace ~> "uri(" ~> OptSpace ~> DQuoteChar ~>
      resolvedURI <~ DQuoteChar <~ OptSpace <~ ")" <~ spacedComma)
    buildRef flatMap { uri =>
      projectRef(uri)
    }
  }

  def resolvedReference(index: KeyIndex, currentBuild: URI, trailing: Parser[_]): Parser[ResolvedReference] =
    {
      def projectID(uri: URI) = token(examplesStrict(ID, index projects uri, "project ID") <~ trailing)
      def projectRef(uri: URI) = projectID(uri) map { id => ProjectRef(uri, id) }

      val uris = index.buildURIs
      val resolvedURI = Uri(uris).map(uri => Scope.resolveBuild(currentBuild, uri))
      val buildRef = token('{' ~> resolvedURI <~ '}').?

      buildRef flatMap {
        case None      => projectRef(currentBuild)
        case Some(uri) => projectRef(uri) | token(trailing ^^^ BuildRef(uri))
      }
    }
  def optProjectRef(index: KeyIndex, current: ProjectRef): Parser[ParsedAxis[ResolvedReference]] =
    projectRef(index, current.build) ?? Omitted
  def resolveProject(parsed: ParsedAxis[ResolvedReference], current: ProjectRef): Option[ResolvedReference] =
    parsed match {
      case Omitted                   => Some(current)
      case ParsedGlobal | ParsedZero => None
      case pv: ParsedValue[rr]       => Some(pv.value)
    }

  // borrowed from sbt
  private[this] final class ActAction
  private[this] final val ShowAction, MultiAction, SingleAction = new ActAction
  private[this] def actionParser: Parser[ActAction] =
    token(
      ((ShowCommand ^^^ ShowAction) |
        (MultiTaskCommand ^^^ MultiAction)) <~ Space
    ) ?? SingleAction

  def scopedKeyParser(state: State): Parser[ScopedKey[_]] = scopedKeyParser(Project extract state)
  def scopedKeyParser(extracted: Extracted): Parser[ScopedKey[_]] = scopedKeyParser(extracted.structure, extracted.currentRef)
  def scopedKeyParser(structure: BuildStructure, currentRef: ProjectRef): Parser[ScopedKey[_]] =
    scopedKey(structure.index.keyIndex, currentRef, structure.extra.configurationsForAxis, structure.index.keyMap, structure.data)
  
  // this does not take aggregation into account
  def scopedKey(index: KeyIndex, current: ProjectRef, defaultConfigs: Option[ResolvedReference] => Seq[String],
    keyMap: Map[String, AttributeKey[_]], data: Settings[Scope]): Parser[ScopedKey[_]] =
    scopedKeySelected(index, current, defaultConfigs, keyMap, data).map(_.key)

  // borrowed from sbt
  type KeysParser = Parser[Seq[ScopedKey[T]] forSome { type T }]
  private def aggregatedKeyParser(state: State): KeysParser = aggregatedKeyParser(Project extract state)
  private def aggregatedKeyParser(extracted: Extracted): KeysParser = aggregatedKeyParser(extracted.structure, extracted.currentRef)
  private def aggregatedKeyParser(structure: BuildStructure, currentRef: ProjectRef): KeysParser =
    scopedKeyAggregated(currentRef, structure.extra.configurationsForAxis, structure)
  def keyValues[T](state: State)(keys: Seq[ScopedKey[T]]): Values[T] = keyValues(Project extract state)(keys)
  def keyValues[T](extracted: Extracted)(keys: Seq[ScopedKey[T]]): Values[T] = keyValues(extracted.structure)(keys)
  def keyValues[T](structure: BuildStructure)(keys: Seq[ScopedKey[T]]): Values[T] =
    keys.flatMap { key =>
      getValue(structure.data, key.scope, key.key) map { value => KeyValue(key, value) }
    }
  private[this] def anyKeyValues(structure: BuildStructure, keys: Seq[ScopedKey[_]]): Seq[KeyValue[_]] =
    keys.flatMap { key =>
      getValue(structure.data, key.scope, key.key) map { value => KeyValue(key, value) }
    }

  private[this] def getValue[T](data: Settings[Scope], scope: Scope, key: AttributeKey[T]): Option[T] =
    if (java.lang.Boolean.getBoolean("sbt.cli.nodelegation")) data.getDirect(scope, key) else data.get(scope, key)

  // borrowed from sbt
  private def requireSession[T](s: State, p: => Parser[T]): Parser[T] =
    if (s get sessionSettings isEmpty) failure("No project loaded")
    else p

  sealed trait ParsedAxis[+T] {
    final def isExplicit = this != Omitted
  }
  final object ParsedZero extends ParsedAxis[Nothing]
  final object ParsedGlobal extends ParsedAxis[Nothing]
  final object Omitted extends ParsedAxis[Nothing]
  final class ParsedValue[T](val value: T) extends ParsedAxis[T]
  def value[T](t: Parser[T]): Parser[ParsedAxis[T]] = t map { v => new ParsedValue(v) }
}
