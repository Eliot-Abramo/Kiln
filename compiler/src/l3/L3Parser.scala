package l3

import fastparse._
import NominalCL3TreeModule._
import CL3Literal._

/**
  * Parsing (including lexical analysis) for the L₃ language.
  *
  * @author Michel Schinz <Michel.Schinz@epfl.ch>
  */

//lexing + parsing + immediate desugaring of surface L3
object L3Parser {
  //returns a nominal CL3 tree
  def parse(programText: String,
            indexToPosition: Int => Position): Either[String, Tree] = {
    val parser = new S(indexToPosition)
    fastparse.parse(programText, parser.program(using _)) match {
      case Parsed.Success(program, _) =>
        Right(program)
      case Parsed.Failure(lp, index, _) =>
        Left(s"${indexToPosition(index)}: parse error (expected: $lp)")
    }
  }

  // Equivalent to fastparse.P but also provides position to wrapped parser
  private inline def IP[T](using Conversion[Int, Position])
                          (inline b: => P[Position ?=> T])
                          (using ctx: P[?]): P[T] =
    P(b map { _(using ctx.index) })

  // Lexical analysis (for which whitespace is significant)
  private class L(indexToPosition: Int => Position) {
    import NoWhitespace._

    private given Conversion[Int, Position] = indexToPosition(_: Int)

    // Literals
    private def sign[p: P] = P(CharIn("+\\-"))
    private def prefix2[p: P] = IgnoreCase("#b")
    private def prefix16[p: P] = IgnoreCase("#x")
    private def digit2[p: P] = CharIn("0-1")
    private def digit10[p: P] = CharIn("0-9")
    private def digit16[p: P] = CharIn("0-9a-fA-F")
    private def unicodeChar[p: P] = P(
      CharPred(!Character.isHighSurrogate(_))
        | (CharPred(Character.isHighSurrogate)
             ~ CharPred(Character.isLowSurrogate)))

    private def integer2[p: P] = P(
      (prefix2 ~/ digit2.rep(1).!)
        .map { Integer.parseInt(_, 2) }
        .filter { L3Int.canConvertFromIntUnsigned }
        .map { L3Int.ofIntUnsigned })

    private def integer16[p: P] = P(
      (prefix16 ~/ digit16.rep(1).!)
        .map { Integer.parseInt(_, 16) }
        .filter { L3Int.canConvertFromIntUnsigned }
        .map { L3Int.ofIntUnsigned })

    private def integer10[p: P] = P(
      (sign.? ~ digit10 ~/ digit10.rep).!
        .map { Integer.parseInt(_, 10) }
        .filter { L3Int.canConvertFromInt })
      .map(L3Int.apply)

    private def integer[p: P] = IP(
      (integer2 | integer10 | integer16)
        .map { v => Lit(IntLit(v)) })

    private def blockTag[p: P] = IP(
      ("#_" ~/ identStr.!)
        .map { n =>
          Lit(IntLit(L3Int.ofIntUnsigned(BlockTag.resolve(n)))) })

    private def string[p: P] = IP(
      ("\"" ~/ CharPred(c => c != '\n' && c != '"').rep.! ~ "\"")
        .map { s => sStringLit(s) })

    private def char[p: P] = IP(
      ("'" ~/ unicodeChar.! ~ "'")
        .map { c => Lit(CharLit(c.codePointAt(0))) })

    private def bool[p: P] = IP(
      StringIn("#t", "#f").!
        .map { v => Lit(BooleanLit(v == "#t")) })

    private def unit[p: P] = IP(
      "#u".map { _ => Lit(UnitLit) })

    def literal[p: P] = P(integer | blockTag | string | char | bool | unit)

    // Identifiers
    private def identStart[p: P] = P(CharIn("!$%&*+\\-./:<=>?^_|~a-zA-Z"))
    private def identCont[p: P] = P(identStart | digit10)
    private def identSuffix[p: P] = P("@" ~ digit10.rep(1))

    def identStr[p: P] = P(
      (identStart ~/ identCont.rep ~/ identSuffix.?).!)
    def identifier[p: P] = IP(
      identStr.map(Ident.apply))

    // Keywords
    def kDef[p: P] = P("def" ~ !identCont)
    def kDefrec[p: P] = P("defrec" ~ !identCont)
    def kFun[p: P] = P("fun" ~ !identCont)
    def kLet[p: P] = P("let" ~ !identCont)
    def kLet_*[p: P] = P("let*" ~ !identCont)
    def kLetrec[p: P] = P("letrec" ~ !identCont)
    def kRec[p: P] = P("rec" ~ !identCont)
    def kBegin[p: P] = P("begin" ~ !identCont)
    def kCond[p: P] = P("cond" ~ !identCont)
    def kIf[p: P] = P("if" ~ !identCont)
    def kAnd[p: P] = P("and" ~ !identCont)
    def kOr[p: P] = P("or" ~ !identCont)
    def kNot[p: P] = P("not" ~ !identCont)
    def kHalt[p: P] = P("halt" ~ !identCont)
    def kPrim[p: P] = P("@")
  }

  // Syntactic analysis (for which whitespace and comments are ignored)
  private class S(indexToPosition: Int => Position) {
    val lexer = new L(indexToPosition)
    import lexer._

    private given Whitespace = { implicit ctx: ParsingRun[?] =>
      import NoWhitespace._
      (CharIn(" \t\n\r")
         | (";" ~ CharPred(c => c != '\n' && c != '\r').rep)).rep
    }
    private given Conversion[Int, Position] = indexToPosition(_: Int)

    def program[p: P]: P[Tree] =
      P("" ~ topExpr ~ End) // The initial "" allows leading whitespace

    private def topExpr[p: P]: P[Tree] = P(defP | defrecP | exprP)

    private def defP[p: P] = IP(
      (par(kDef ~ identStr ~ expr) ~ topExpr)
        .map { (n, v, p) => Let(Seq((n, v)), p) })
    private def defrecP[p: P] = IP(
      (par(kDefrec ~ identStr ~ anonFun) ~ topExpr)
        .map { case (n, (a, b), p) => LetRec(Seq(Fun(n, a, b)), p) })
    private def exprP[p: P] = IP(
      (expr ~ topExpr.?)
        .map { (e, p) => sBegin(e +: p.toSeq) })

    private def expr[p: P]: P[Tree] = P(
      fun | let | let_* | letrec | rec | begin | cond | if_ | and | or | not
        | halt | app | prim | literal | identifier)
    private def exprs[p: P] = expr.rep
    private def exprs1[p: P] = expr.rep(1)
    private def implicitBegin[p: P] = IP(exprs1.map(sBegin))

    private def anonFun[p: P] = P(
      par("fun" ~ par(identStr.rep) ~ implicitBegin))
    private def funDef[p: P] = IP(
      par(identStr ~ anonFun)
        .map { case (n, (a, e)) => Fun(n, a, e) })
    private def binding[p: P] = P(
      par(identStr ~ expr))
    private def bindings[p: P] = P(
      par(binding.rep))

    private def fun[p: P] = IP(
      anonFun.map(sFun))
    private def let[p: P] = IP(
      par(kLet ~/ bindings ~ implicitBegin).map(Let.apply))
    private def let_*[p: P] = IP(
      par(kLet_* ~/ bindings ~ implicitBegin).map(sLet_*))
    private def letrec[p: P]= IP(
      par(kLetrec ~/ par(funDef.rep) ~ implicitBegin).map(LetRec.apply))
    private def rec[p: P] = IP(
      par(kRec ~/ identStr ~ bindings ~ implicitBegin).map(sRec))
    private def begin[p: P] = IP(
      par(kBegin ~/ exprs1).map(sBegin))

    private def cond[p: P] = IP(
      par(kCond ~/ par(expr ~ exprs1).rep(1)).map(sCond))
    private def if_[p: P]: P[If] = IP(
      par(kIf ~ expr ~ expr ~ expr.?)
        .map { (c, t, f) => If(c, t, f.getOrElse(Lit(UnitLit))) })
    private def and[p: P] = IP(
      par(kAnd ~/ expr.rep(2)).map(sAnd))
    private def or[p: P] = IP(
      par(kOr ~/ expr.rep(2)).map(sOr))
    private def not[p: P] = IP(
      par(kNot ~/ expr).map(sNot))

    private def app[p: P] = IP(
      par(expr ~ exprs).map(App.apply))
    private def prim[p: P] = IP(
      par(kPrim ~/ identStr ~ exprs).map(Prim.apply))
    private def halt[p: P] = IP(
      par(kHalt ~/ expr).map(Halt.apply))

    private def par[T, p: P](b: =>P[T]): P[T] = P("(" ~ b ~ ")")
  }

  // Syntactic sugar translation.
  private def freshNameGenerator(prefix: String): ()=>String = {
    var counter = 0
    () => { counter += 1; s"${prefix}#${counter}" }
  }

  private val freshFun = freshNameGenerator("fun")
  private val freshOr = freshNameGenerator("or")
  private val freshString = freshNameGenerator("string")
  private val freshBegin = freshNameGenerator("begin")

  private def sFun(args: Seq[String], body: Tree)(using Position): Tree = {
    val fName = freshFun()
    LetRec(Seq(Fun(fName, args, body)), Ident(fName))
  }
  private def sLet_*(bdgs: Seq[(String,Tree)], body: Tree)
                    (using Position): Tree =
    bdgs.foldRight(body)((b, t) => Let(Seq(b), t))
  private def sBegin(exprs: Seq[Tree])(using Position): Tree =
    exprs reduceRight { (e1, e2) => Let(Seq((freshBegin(), e1)), e2) }
  private def sRec(name: String, bdgs: Seq[(String, Tree)], body: Tree)
                  (using Position) =
    LetRec(Seq(Fun(name, bdgs map { _._1 }, body)),
           App(Ident(name), bdgs map { _._2 }))
  private def sAnd(es: Seq[Tree])(using Position): Tree =
    es reduceRight { If(_, _, Lit(BooleanLit(false))) }
  private def sOr(es: Seq[Tree])(using Position): Tree = {
    es reduceRight { (e, r) =>
      val en = freshOr()
      Let(Seq((en, e)), If(Ident(en), Ident(en), r))
    }
  }
  private def sNot(e: Tree)(using Position): Tree =
    If(e, Lit(BooleanLit(false)), Lit(BooleanLit(true)))
  private def sCond(clses: Seq[(Tree, Seq[Tree])])(using Position): Tree =
    clses.foldRight(Lit(UnitLit) : Tree){ case ((c, t), e) =>
      If(c, sBegin(t), e) }
  private def sStringLit(s: String)(using Position): Tree = {
    val b = freshString()
    val cs = codePoints(s)
    Let(Seq((b, Prim("block-alloc",
                     Seq(Lit(IntLit(L3Int(BlockTag.String))),
                         Lit(IntLit(L3Int(cs.length))))))),
        sBegin((cs.zipWithIndex map {case (c, i) =>
                  Prim("block-set!",
                       Seq(Ident(b), Lit(IntLit(L3Int(i))), Lit(CharLit(c)))) })
                 :+ Ident(b)))
  }

  private def codePoints(chars: Seq[Char]): Seq[L3Char] = chars match {
    case Seq(h, l, r*) if Character.isSurrogatePair(h, l) =>
      Character.toCodePoint(h, l) +: codePoints(r)
    case Seq(c, r*) =>
      c.toInt +: codePoints(r)
    case Seq() =>
      Seq()
  }
}
