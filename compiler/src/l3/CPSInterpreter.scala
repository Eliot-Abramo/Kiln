package l3

import scala.annotation.tailrec
import scala.collection.mutable.{ Map => MutableMap }
import IO._
import CL3Literal._
import scala.reflect.TypeTest

/**
  * A tree-based interpreter for the CPS languages.
  *
  * @author Michel Schinz <Michel.Schinz@epfl.ch>
  */

sealed trait CPSInterpreter[M <: CPSTreeModule]
  (protected val treeModule: M)
  (using TypeTest[treeModule.Atom, treeModule.Literal],
         TypeTest[treeModule.Atom, treeModule.Name])
  (log: treeModule.Tree => Unit = { (_: treeModule.Tree) => () }) {

  import treeModule._

  def apply(tree: Program): TerminalPhaseResult =
    Right((eval(tree, emptyEnv), None))

  protected type Value = Literal | BlockV | FunV | CntV
  protected type BlockV
  protected case class FunV(retC: Name, args: Seq[Name], body: Tree, env: Env)
  protected case class CntV(args: Seq[Name], body: Tree, env: Env)

  protected type Env = PartialFunction[Name, Value]
  protected val emptyEnv: Env = Map.empty

  @tailrec
  private def eval(tree: Tree, env: Env): Int = {
    def resolve(a: Atom): Value = a match {
      case n: Name => env(n)
      case l: Literal => l
    }

    log(tree)

    tree match {
      case LetF(funs, body) =>
        val recEnv = MutableMap[Name, Value]()
        val env1 = recEnv orElse env
        for (Fun(name, retC, args, body) <- funs)
          recEnv(name) = wrapFunV(FunV(retC, args, body, env1))
        eval(body, env1)

      case LetC(cnts, body) =>
        val recEnv = MutableMap[Name, Value]()
        val env1 = recEnv orElse env
        for (Cnt(name, args, body) <- cnts)
          recEnv(name) = CntV(args, body, env1)
        eval(body, env1)

      case LetP(name, prim, args, body) =>
        eval(body, Map(name->evalValuePrim(prim, args map resolve)) orElse env)

      case AppF(fun, retC, args) =>
        val FunV(fRetC, fArgs, fBody, fEnv) = unwrapFunV(resolve(fun))
        assume(fArgs.length == args.length)
        val rArgs = args map resolve
        val env1 = ((fRetC +: fArgs) zip (env(retC) +: rArgs)).toMap orElse fEnv
        eval(fBody, env1)

      case AppC(cnt, args) =>
        val CntV(cArgs, cBody, cEnv) = env(cnt): @unchecked
        assume(cArgs.length == args.length)
        eval(cBody, (cArgs zip (args map resolve)).toMap orElse cEnv)

      case If(cond, args, thenC, elseC) =>
        val cnt = if (evalTestPrim(cond, args map resolve)) thenC else elseC
        val CntV(Seq(), cBody, cEnv) = env(cnt): @unchecked
        eval(cBody, cEnv)

      case Halt(name) =>
        extractInt(resolve(name))
    }
  }

  protected val intExtractor: PartialFunction[Value, Int]
  private def extractInt(v: Value): Int =
    intExtractor.applyOrElse(v, v => sys.error(
      s"expected integer value, got ${v}"))

  protected def wrapFunV(funV: FunV): Value

  protected val funVExtractor: PartialFunction[Value, FunV]
  private def unwrapFunV(v: Value): FunV =
    funVExtractor.applyOrElse(v, v => sys.error(
      s"expected function value, got: ${v}"))

  protected val vEvaluator: PartialFunction[(ValuePrimitive, Seq[Value]), Value]
  private def evalValuePrim(p: ValuePrimitive, args: Seq[Value]): Value =
    vEvaluator.applyOrElse((p, args), (p, as) => sys.error(
      s"cannot apply value primitive ${p} to values ${as.mkString(", ")}"))

  protected val cEvaluator: PartialFunction[(TestPrimitive,Seq[Value]), Boolean]
  private def evalTestPrim(p: TestPrimitive, args: Seq[Value]): Boolean =
    cEvaluator.applyOrElse((p, args), (p, as) => sys.error(
      s"cannot apply test primitive ${p} to values ${as.mkString(", ")}"))
}

object HighCPSInterpreter extends CPSInterpreter(HighCPSTreeModule)()
    with (HighCPSTreeModule.Program => TerminalPhaseResult) {
  import L3Primitive._

  protected case class BlockV(tag: L3BlockTag, contents: Array[Value])

  protected val intExtractor = { case IntLit(i) => i.toInt }

  protected def wrapFunV(funV: FunV): Value =
    BlockV(l3.BlockTag.Function, Array(funV))
  protected val funVExtractor = {
    case BlockV(id, Array(funV: FunV)) if id == l3.BlockTag.Function => funV
  }

  protected val vEvaluator = {
    case (BlockAlloc, Seq(IntLit(t), IntLit(s))) =>
      BlockV(t.toInt, Array.fill(s.toInt)(UnitLit))
    case (BlockTag, Seq(BlockV(t, _))) => IntLit(L3Int(t))
    case (BlockLength, Seq(BlockV(_, c))) => IntLit(L3Int(c.length))
    case (BlockGet, Seq(BlockV(_, v), IntLit(i))) => v(i.toInt)
    case (BlockSet, Seq(BlockV(_, v), IntLit(i), o)) =>
      v(i.toInt) = o; UnitLit

    case (IntAdd, Seq(IntLit(v1), IntLit(v2))) => IntLit(v1 + v2)
    case (IntSub, Seq(IntLit(v1), IntLit(v2))) => IntLit(v1 - v2)
    case (IntMul, Seq(IntLit(v1), IntLit(v2))) => IntLit(v1 * v2)
    case (IntDiv, Seq(IntLit(v1), IntLit(v2))) => IntLit(v1 / v2)
    case (IntMod, Seq(IntLit(v1), IntLit(v2))) => IntLit(v1 % v2)
    case (IntToChar, Seq(IntLit(v))) => CharLit(v.toInt)

    case (IntShiftLeft, Seq(IntLit(v1), IntLit(v2))) => IntLit(v1 << v2)
    case (IntShiftRight, Seq(IntLit(v1), IntLit(v2))) => IntLit(v1 >> v2)
    case (IntBitwiseAnd, Seq(IntLit(v1), IntLit(v2))) => IntLit(v1 & v2)
    case (IntBitwiseOr, Seq(IntLit(v1), IntLit(v2))) => IntLit(v1 | v2)
    case (IntBitwiseXOr, Seq(IntLit(v1), IntLit(v2))) => IntLit(v1 ^ v2)

    case (ByteRead, Seq()) => IntLit(L3Int(readByte()))
    case (ByteWrite, Seq(IntLit(c))) => writeByte(c.toInt); UnitLit
    case (CharToInt, Seq(CharLit(c))) => IntLit(L3Int(c))

    case (Id, Seq(v)) => v
  }

  protected val cEvaluator =  {
    case (BlockP, Seq(BlockV(_, _))) => true
    case (BlockP, Seq(_)) => false

    case (IntP, Seq(IntLit(_))) => true
    case (IntP, Seq(_)) => false
    case (IntLt, Seq(IntLit(v1), IntLit(v2))) => v1 < v2
    case (IntLe, Seq(IntLit(v1), IntLit(v2))) => v1 <= v2

    case (CharP, Seq(CharLit(_))) => true
    case (CharP, Seq(_)) => false

    case (BoolP, Seq(BooleanLit(_))) => true
    case (BoolP, Seq(_)) => false

    case (UnitP, Seq(UnitLit)) => true
    case (UnitP, Seq(_)) => false

    case (Eq, Seq(v1, v2)) => v1 == v2
  }
}

sealed trait LowValuesCPSInterpreter extends CPSInterpreter[? <: LowValues] {
  import treeModule._
  import CPSValuePrimitive._
  import CPSTestPrimitive._

  protected case class BlockV(addr: Bits32,
                              tag: L3BlockTag,
                              contents: Array[Value])

  private var nextBlockAddr = 0
  protected def allocBlock(tag: L3BlockTag, contents: Array[Value]): BlockV = {
    val block = BlockV(nextBlockAddr, tag, contents)
    nextBlockAddr += 4
    block
  }

  given Conversion[Value, Bits32] = {
    case BlockV(addr, _, _) => addr
    case value: Literal     => value
    case v: (FunV | CntV)   => sys.error(s"cannot convert $v to bits")
  }

  protected val intExtractor = { case i: Literal => i }

  protected val vEvaluator = {
    case (Add, Seq(v1, v2)) => v1 + v2
    case (Sub, Seq(v1, v2)) => v1 - v2
    case (Mul, Seq(v1, v2)) => v1 * v2
    case (Div, Seq(v1, v2)) => v1 / v2
    case (Mod, Seq(v1, v2)) => v1 % v2

    case (ShiftLeft, Seq(v1, v2)) => v1 << v2
    case (ShiftRight, Seq(v1, v2)) => v1 >> v2
    case (And, Seq(v1, v2)) => v1 & v2
    case (Or, Seq(v1, v2)) => v1 | v2
    case (XOr, Seq(v1, v2)) => v1 ^ v2

    case (ByteRead, Seq()) => readByte()
    case (ByteWrite, Seq(c)) => writeByte(c); 0

    case (BlockAlloc, Seq(t, s)) => allocBlock(t, Array.fill(s)(0))
    case (BlockTag, Seq(BlockV(_, t, _))) => t
    case (BlockLength, Seq(BlockV(_, _, c))) => c.length
    case (BlockGet, Seq(BlockV(_, _, c), i)) => c(i)
    case (BlockSet, Seq(BlockV(_, _, c), i, v)) => c(i) = v; 0

    case (Id, Seq(o)) => o
  }

  protected val cEvaluator = {
    case (Lt, Seq(v1, v2)) => v1 < v2
    case (Le, Seq(v1, v2)) => v1 <= v2
    case (Eq, Seq(v1, v2)) => v1 == v2
  }
}

object LowCPSInterpreter
    extends LowValuesCPSInterpreter with CPSInterpreter(LowCPSTreeModule)()
    with (LowCPSTreeModule.Program => TerminalPhaseResult) {

  protected def wrapFunV(funV: FunV): Value =
    allocBlock(BlockTag.Function, Array(funV))
  protected val funVExtractor = {
    case BlockV(_, id, Array(funV: FunV)) if id == l3.BlockTag.Function => funV
  }
}

class FlatCPSInterpreter(log: FlatCPSTreeModule.Tree => Unit)
    extends LowValuesCPSInterpreter with CPSInterpreter(FlatCPSTreeModule)(log)
    with (FlatCPSTreeModule.Program => TerminalPhaseResult) {

  protected def wrapFunV(funV: FunV): Value = funV
  protected val funVExtractor = { case funV: FunV => funV }
}
object FlatCPSInterpreter extends FlatCPSInterpreter(_ => ())
