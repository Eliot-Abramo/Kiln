package l3

import scala.collection.mutable.{ Map => MutableMap }
import SymbolicCL3TreeModule._
import IO._
import l3.L3Primitive._
import CL3Literal._

/**
  * A tree-based interpreter for the CL₃ language.
  *
  * @author Michel Schinz <Michel.Schinz@epfl.ch>
  */

object CL3Interpreter extends (Tree => TerminalPhaseResult) {
  def apply(program: Tree): TerminalPhaseResult = try {
    eval(program)(using Map.empty)
    Right(0, None)
  } catch {
    case EvalHlt(retCode) => Right((retCode, None))
    case EvalErr(msg, trace) => Left((msg +: trace).mkString("\n"))
  }

  // Values
  private type Value = CL3Literal | BlockV | FunctionV
  private case class BlockV(tag: L3BlockTag, contents: Array[Value]) {
    override def toString: String = s"<${tag}>[${contents mkString ","}]"
  }
  private case class FunctionV(args: Seq[Symbol], body: Tree, env: Env) {
    override def toString: String = "<function>"
  }

  // Environment
  private type Env = PartialFunction[Symbol, Value]

  // Error/halt handling (termination)
  private case class EvalErr(msg: String, trace: Seq[String]) extends Exception
  private case class EvalHlt(retCode: Int) extends Exception

  private def error(pos: Position, msg: String): Nothing =
    throw EvalErr(msg, Seq(s"  at $pos"))
  private def halt(r: Int): Nothing =
    throw EvalHlt(r)

  private def validIndex(a: Array[Value], i: L3Int): Boolean =
    a.isDefinedAt(i.toInt)

  private final def eval(tree: Tree)(using env: Env): Value = tree match {
    case Let(bdgs, body) =>
      eval(body)(using Map.from(bdgs map { (n, e) => n -> eval(e) }) orElse env)

    case LetRec(funs, body) =>
      val recEnv = MutableMap[Symbol, Value]()
      val env1 = recEnv orElse env
      for (Fun(name, args, body) <- funs)
        recEnv(name) = BlockV(l3.BlockTag.Function,
                              Array(FunctionV(args, body, env1)))
      eval(body)(using env1)

    case If(cond, thenE, elseE) =>
      eval(cond) match {
        case BooleanLit(false) => eval(elseE)
        case _ => eval(thenE)
      }

    case App(fun, args) =>
      eval(fun) match {
        case BlockV(_, Array(FunctionV(cArgs, cBody, cEnv))) =>
          if (args.length != cArgs.length)
            error(tree.pos,
                  s"expected ${cArgs.length} arguments, got ${args.length}")
          try {
            eval(cBody)(using Map.from(cArgs zip (args map eval)) orElse cEnv)
          } catch {
            case EvalErr(msg, trace) =>
              throw EvalErr(msg, s"  at ${fun.pos}" +: trace)
          }
        case _ => error(fun.pos, "function value expected")
      }

    case Prim(p, args) => (p, args map eval) match {
      case (BlockAlloc, Seq(IntLit(t), IntLit(s))) =>
        BlockV(t.toInt, Array.fill(s.toInt)(UnitLit))
      case (BlockP, Seq(BlockV(_, _))) => BooleanLit(true)
      case (BlockP, Seq(_)) => BooleanLit(false)
      case (BlockTag, Seq(BlockV(t, _))) => IntLit(L3Int(t))
      case (BlockLength, Seq(BlockV(_, c))) => IntLit(L3Int(c.length))
      case (BlockGet, Seq(BlockV(_, v), IntLit(i))) if validIndex(v, i) =>
        v(i.toInt)
      case (BlockSet, Seq(BlockV(_, v), IntLit(i), o)) if validIndex(v, i) =>
        v(i.toInt) = o; UnitLit

      case (IntP, Seq(IntLit(_))) => BooleanLit(true)
      case (IntP, Seq(_)) => BooleanLit(false)

      case (IntAdd, Seq(IntLit(v1), IntLit(v2))) => IntLit(v1 + v2)
      case (IntSub, Seq(IntLit(v1), IntLit(v2))) => IntLit(v1 - v2)
      case (IntMul, Seq(IntLit(v1), IntLit(v2))) => IntLit(v1 * v2)
      case (IntDiv, Seq(IntLit(v1), IntLit(v2))) => IntLit(v1 / v2)
      case (IntMod, Seq(IntLit(v1), IntLit(v2))) => IntLit(v1 % v2)

      case (IntShiftLeft, Seq(IntLit(v1), IntLit(v2))) => IntLit(v1 << v2)
      case (IntShiftRight, Seq(IntLit(v1), IntLit(v2))) => IntLit(v1 >> v2)
      case (IntBitwiseAnd, Seq(IntLit(v1), IntLit(v2))) => IntLit(v1 & v2)
      case (IntBitwiseOr, Seq(IntLit(v1), IntLit(v2))) => IntLit(v1 | v2)
      case (IntBitwiseXOr, Seq(IntLit(v1), IntLit(v2))) => IntLit(v1 ^ v2)

      case (IntLt, Seq(IntLit(v1), IntLit(v2))) => BooleanLit(v1 < v2)
      case (IntLe, Seq(IntLit(v1), IntLit(v2))) => BooleanLit(v1 <= v2)
      case (Eq, Seq(v1, v2)) => BooleanLit(v1 == v2)

      case (IntToChar, Seq(IntLit(i))) if Character.isValidCodePoint(i.toInt) =>
        CharLit(i.toInt)

      case (CharP, Seq(CharLit(_))) => BooleanLit(true)
      case (CharP, Seq(_)) => BooleanLit(false)

      case (ByteRead, Seq()) => IntLit(L3Int(readByte()))
      case (ByteWrite, Seq(IntLit(c))) => writeByte(c.toInt); UnitLit

      case (CharToInt, Seq(CharLit(c))) => IntLit(L3Int(c))

      case (BoolP, Seq(BooleanLit(_))) => BooleanLit(true)
      case (BoolP, Seq(_)) => BooleanLit(false)

      case (UnitP, Seq(UnitLit)) => BooleanLit(true)
      case (UnitP, Seq(_)) => BooleanLit(false)

      case (p, vs) =>
        error(tree.pos,
              s"""cannot apply primitive $p to values ${vs.mkString(", ")}""")
    }

    case Halt(arg) => eval(arg) match {
      case IntLit(c) => halt(c.toInt)
      case c => error(tree.pos, s"halt with code $c")
    }

    case Ident(n) => env(n)
    case Lit(l) => l
  }
}
