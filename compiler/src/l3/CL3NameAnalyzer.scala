package l3

import l3.{ NominalCL3TreeModule => N }
import l3.{ SymbolicCL3TreeModule => S }

/**
  * Name analysis for the CL₃ language. Translates a tree in which
  * identifiers are simple strings into one in which identifiers are
  * symbols (i.e. globally-unique names).
  *
  * @author Michel Schinz <Michel.Schinz@epfl.ch>
  */

object CL3NameAnalyzer extends (N.Tree => Either[String, S.Tree]) {
  def apply(tree: N.Tree): Either[String, S.Tree] =
    try {
      Right(rewrite(tree)(using Map.empty))
    } catch {
      case NameAnalysisError(msg) =>
        Left(msg)
    }

  private type Env = Map[String, Symbol]

  private final case class NameAnalysisError(msg: String) extends Exception(msg)
  private def error(msg: String)(using pos: Position): Nothing =
    throw NameAnalysisError(s"${pos}: ${msg}")

  private def rewrite(tree: N.Tree)(using env: Env): S.Tree = {
    given Position = tree.pos
    tree match {
      case N.Let(bdgs, body) =>
        val syms = checkUnique(bdgs map (_._1)) map Symbol.fresh
        S.Let(syms zip (bdgs map { b => rewrite(b._2) }),
              rewrite(body)(using augmented(env, syms)))
      case N.LetRec(funs, body) =>
        val syms = checkUnique(funs map (_.name)) map Symbol.fresh
        val env1 = augmented(env, syms)
        S.LetRec((syms zip funs) map {case (s,f) => rewriteF(s, f , env1)},
                 rewrite(body)(using env1))
      case N.If(cond, thenE, elseE) =>
        S.If(rewrite(cond), rewrite(thenE), rewrite(elseE))
      case N.App(N.Ident(fun), args) if env contains altName(fun, args.length)=>
        S.App(S.Ident(env(altName(fun, args.length))), args map rewrite)
      case N.App(fun, args) =>
        S.App(rewrite(fun), args map rewrite)
      case N.Prim(p, args) if L3Primitive.isDefinedAt(p, args.length) =>
        S.Prim(L3Primitive(p), args map rewrite)
      case N.Halt(arg) =>
        S.Halt(rewrite(arg))
      case N.Ident(name) if env contains name =>
        S.Ident(env(name))
      case N.Lit(value) =>
        S.Lit(value)

      case N.Prim(p, _) if L3Primitive.isDefinedAt(p) =>
        error(s"incorrect number of arguments for @$p")
      case N.Prim(p, _) =>
        error(s"unknown primitive $p")
      case N.Ident(name) =>
        error(s"unknown identifier $name")
    }
  }

  private def rewriteF(funSym: Symbol, fun: N.Fun, env: Env): S.Fun = {
    given Position = fun.pos
    val argsSyms = checkUnique(fun.args) map Symbol.fresh
    S.Fun(funSym, argsSyms, rewrite(fun.body)(using augmented(env, argsSyms)))
  }

  private def checkUnique(names: Seq[String])(using Position): Seq[String] = {
    for (n <- names diff names.distinct)
      error(s"repeated definition of $n")
    names
  }

  private def altName(name: String, arity: Int): String =
    s"$name@$arity"

  private def augmented(env: Env, symbols: Seq[Symbol]): Env =
    env ++ (symbols map { s => (s.name, s) })
}
