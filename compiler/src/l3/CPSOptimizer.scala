package l3

import scala.collection.mutable.{ Map => MutableMap }
import scala.reflect.TypeTest
import scala.annotation.tailrec

abstract class CPSOptimizer[T <: SymbolicNames]
  (protected val treeModule: T)
  (using TypeTest[treeModule.Atom, treeModule.Literal],
         TypeTest[treeModule.Tree, treeModule.Body]) {
  import treeModule._

  protected def rewrite(tree: Program): Tree = {
    val shrunk = fixedPoint(tree)(shrink)
    val maxSize = size(shrunk) * 3 / 2

    @tailrec
    def loop(current: Tree, round: Int): Tree = {
      if (round > maxInliningRounds) current
      else {
        val next = fixedPoint(inlineRound(current, round))(shrink)
        if (next == current) current
        else if (size(next) > maxSize) current
        else loop(next, round + 1)
      }
    }
    loop(shrunk, 1)
  }

  private val maxInliningRounds = 8

  private case class Count(applied: Int = 0,
                           appliedIncorrectly: Boolean = false,
                           asValue: Int = 0)

  private case class State(
    census: Map[Name, Count],
    aSubst: Subst[Atom] = emptySubst,
    cSubst: Subst[Name] = emptySubst,
    eInvEnv: Map[(ValuePrimitive, Seq[Atom]), Atom] = Map.empty,
    cEnv: Map[Name, Cnt] = Map.empty,
    fEnv: Map[Name, Fun] = Map.empty,
    // block tracking blocks whose tag identifies them as write once at init can have their block-set!/block-get pairs short-circuited.
    bAlloc: Map[Atom, (Atom, Atom)] = Map.empty,
    bSlots: Map[(Atom, Atom), Atom] = Map.empty) {

    def dead(s: Name): Boolean =
      ! census.contains(s)
    def appliedOnce(s: Name): Boolean =
      census.get(s).exists(c =>
        c.applied == 1 && !c.appliedIncorrectly && c.asValue == 0)

    def hasFun(fun: Name, arity: Int): Boolean =
      fEnv.get(fun).exists(_.args.length == arity)
    def hasCnt(cnt: Name, arity: Int): Boolean =
      cEnv.get(cnt).exists(_.args.length == arity)

    def withASubst(from: Atom, to: Atom): State =
      copy(aSubst = aSubst + (from -> aSubst(to)))
    def withASubst(from: Seq[Name], to: Seq[Atom]): State =
      copy(aSubst = aSubst ++ (from zip to.map(aSubst)))

    def withCSubst(from: Name, to: Name): State =
      copy(cSubst = cSubst + (from -> cSubst(to)))

    def withExp(atom: Atom, prim: ValuePrimitive, args: Seq[Atom]): State =
      copy(eInvEnv = eInvEnv + ((prim, args) -> atom))

    def withCnts(cnts: Seq[Cnt]): State =
      copy(cEnv = cEnv ++ (cnts.map(_.name) zip cnts))
    def withFuns(funs: Seq[Fun]): State =
      copy(fEnv = fEnv ++ (funs.map(_.name) zip funs))

    def withBlockAlloc(b: Atom, tag: Atom, sz: Atom): State =
      copy(bAlloc = bAlloc + (b -> (tag, sz)))
    def withBlockSlot(b: Atom, i: Atom, v: Atom): State =
      copy(bSlots = bSlots + ((b, i) -> v))
  }

  // Needed for the construction of trees containing other trees,
  // which requires checking (dynamically here) it is indeed a subtype of Body.
  given Conversion[Tree, Body] = {
    case body: Body => body
    case other => sys.error(s"${other} is not a Body")
  }

  // Shrinking optimizations

  private def shrink(tree: Tree): Tree =
    shrink(tree, State(census(tree)))

  private def shrink(tree: Tree, s: State): Tree = tree match {

    case LetP(name, prim, args, body) =>
      val args1 = args map s.aSubst

      if (prim == identity)
        shrink(body, s.withASubst(name, args1.head))
      else if (vEvaluator.isDefinedAt((prim, args1)))
        shrink(body, s.withASubst(name, vEvaluator((prim, args1))))
      //block-tag/block-length on a known allocation, tag and size are fixed at allocation time so can fold directly
      else if (prim == blockTag && args1.length == 1
               && s.bAlloc.contains(args1.head))
        shrink(body, s.withASubst(name, s.bAlloc(args1.head)._1))
      else if (prim == blockLength && args1.length == 1
               && s.bAlloc.contains(args1.head))
        shrink(body, s.withASubst(name, s.bAlloc(args1.head)._2))
      //block-get whose value we already know from prior block-set! at init or previous block-get & whose
      //result is cached, only valid for immutable-tagged blocks
      else if (prim == blockGet && args1.length == 2
               && s.bSlots.contains((args1.head, args1(1))))
        shrink(body, s.withASubst(name, s.bSlots((args1.head, args1(1)))))
      else args1 match {
        case Seq(l: Literal, a) if leftNeutral((l, prim)) =>
          shrink(body, s.withASubst(name, a))
        case Seq(a, l: Literal) if rightNeutral((prim, l)) =>
          shrink(body, s.withASubst(name, a))
        case Seq(l: Literal, _) if leftAbsorbing((l, prim)) =>
          shrink(body, s.withASubst(name, l))
        case Seq(_, l: Literal) if rightAbsorbing((prim, l)) =>
          shrink(body, s.withASubst(name, l))
        case Seq(a, b) if a == b && sameArgReduce.isDefinedAt((prim, a)) =>
          shrink(body, s.withASubst(name, sameArgReduce((prim, a))))
        case _ =>
          //CSE, pure and stable prims only
          val cseArgs = canonicalArgs(prim, args1)
          if (!unstable(prim) && !impure(prim) && s.eInvEnv.contains((prim, cseArgs)))
            shrink(body, s.withASubst(name, s.eInvEnv((prim, cseArgs))))
          //DCE, drop unused, side-effect-free bindings
          else if (s.dead(name) && !impure(prim))
            shrink(body, s)
          else {
            val s1 = trackBlock(s, name, prim, args1)
            val s2 = if (!unstable(prim) && !impure(prim))
                       s1.withExp(name, prim, cseArgs)
                     else s1
            LetP(name, prim, args1, shrink(body, s2))
          }
      }

    case LetC(cnts, body) =>
      val live = cnts.filter(c => !s.dead(c.name))
      val (toInline, toKeep) = live.partition(c => s.appliedOnce(c.name))
      val s1 = s.withCnts(toInline)
      val keptShrunk = toKeep.map { c =>
        Cnt(c.name, c.args, shrink(c.body, s1))
      }
      val bodyShrunk = shrink(body, s1)
      if (keptShrunk.isEmpty) bodyShrunk else LetC(keptShrunk, bodyShrunk)

    case LetF(funs, body) =>
      val live = funs.filter(f => !s.dead(f.name))
      val (toInline, toKeep) = live.partition(f => s.appliedOnce(f.name))
      val s1 = s.withFuns(toInline)
      val keptShrunk = toKeep.map { f =>
        Fun(f.name, f.retC, f.args, shrink(f.body, s1))
      }
      val bodyShrunk = shrink(body, s1)
      if (keptShrunk.isEmpty) bodyShrunk else LetF(keptShrunk, bodyShrunk)

    case AppF(fun, retC, args) =>
      val fun1 = s.aSubst(fun)
      val retC1 = s.cSubst(retC)
      val args1 = args map s.aSubst
      fun1 match {
        case n: Name if s.hasFun(n, args1.length) =>
          val f = s.fEnv(n)
          val s2 = s.withASubst(f.args, args1).withCSubst(f.retC, retC1)
          shrink(f.body, s2)
        case _ =>
          AppF(fun1, retC1, args1)
      }

    case AppC(cnt, args) =>
      val cnt1 = s.cSubst(cnt)
      val args1 = args map s.aSubst
      if (s.hasCnt(cnt1, args1.length)) {
        val c = s.cEnv(cnt1)
        shrink(c.body, s.withASubst(c.args, args1))
      } else {
        AppC(cnt1, args1)
      }

    case If(cond, args, thenC, elseC) =>
      val args1 = args map s.aSubst
      val thenC1 = s.cSubst(thenC)
      val elseC1 = s.cSubst(elseC)

      if (thenC1 == elseC1)
        shrink(AppC(thenC1, Seq.empty), s)
      else if (cEvaluator.isDefinedAt((cond, args1))) {
        val taken = if (cEvaluator((cond, args1))) thenC1 else elseC1
        shrink(AppC(taken, Seq.empty), s)
      } else if (blockP.contains(cond) && args1.length == 1 && s.bAlloc.contains(args1.head)) {
        shrink(AppC(thenC1, Seq.empty), s)
      } else args1 match {
        case Seq(a, b) if a == b && sameArgReduceC.isDefinedAt(cond) =>
          val taken = if (sameArgReduceC(cond)) thenC1 else elseC1
          shrink(AppC(taken, Seq.empty), s)
        case _ =>
          If(cond, args1, thenC1, elseC1)
      }

    case Halt(arg) =>
      Halt(s.aSubst(arg))
  }

  private def trackBlock(s: State, name: Name,
                         prim: ValuePrimitive,
                         args: Seq[Atom]): State =
    if (prim == blockAlloc && args.length == 2)
      s.withBlockAlloc(name, args.head, args(1))
    else if (prim == blockSet && args.length == 3
             && immutableBlock(s, args.head))
      s.withBlockSlot(args.head, args(1), args(2))
    else if (prim == blockGet && args.length == 2
             && immutableBlock(s, args.head))
      s.withBlockSlot(args.head, args(1), name)
    else
      s

  private def immutableBlock(s: State, b: Atom): Boolean =
    s.bAlloc.get(b).exists((tag, _) => isImmutableTag(tag))

  private def canonicalArgs(prim: ValuePrimitive, args: Seq[Atom]): Seq[Atom] =
    if (commutative(prim) && args.length == 2 && atomKey(args(1)) < atomKey(args.head))
      Seq(args(1), args.head)
    else
      args

  private def atomKey(atom: Atom): String = atom match {
    case n: Name    => "N:" + n.toString
    case l: Literal => "L:" + l.toString
    case other      => other.toString
  }

  // (Non-shrinking) inlining

  private def inlineRound(tree: Tree, round: Int): Tree = {
    val funLimit = fibonacciLimit(round)
    val cntLimit = round

    def copyT(t: Tree, subV: Subst[Atom], subC: Subst[Name]): Tree = t match {
      case LetF(funs, body) =>
        val names = funs map (_.name)
        val names1 = names map (_.copy())
        val subV1 = subV ++ (names zip names1)
        LetF(funs map (copyF(_, subV1, subC)), copyT(body, subV1, subC))
      case LetC(cnts, body) =>
        val names = cnts map (_.name)
        val names1 = names map (_.copy())
        val subC1 = subC ++ (names zip names1)
        LetC(cnts map (copyC(_, subV, subC1)), copyT(body, subV, subC1))
      case LetP(name, prim, args, body) =>
        val name1 = name.copy()
        LetP(name1, prim, args map subV,
          copyT(body, subV + (name -> name1), subC))
      case AppF(fun, retC, args) =>
        AppF(subV(fun), subC(retC), args map subV)
      case AppC(cnt, args) =>
        AppC(subC(cnt), args map subV)
      case If(cond, args, thenC, elseC) =>
        If(cond, args map subV, subC(thenC), subC(elseC))
      case Halt(arg) =>
        Halt(subV(arg))
    }

    def copyF(fun: Fun, subV: Subst[Atom], subC: Subst[Name]): Fun = {
      val retC1 = fun.retC.copy()
      val subC1 = subC + (fun.retC -> retC1)
      val args1 = fun.args map (_.copy())
      val subV1 = subV ++ (fun.args zip args1)
      val funName1 = subV(fun.name).asInstanceOf[Name]
      Fun(funName1, retC1, args1, copyT(fun.body, subV1, subC1))
    }

    def copyC(cnt: Cnt, subV: Subst[Atom], subC: Subst[Name]): Cnt = {
      val args1 = cnt.args map (_.copy())
      val subV1 = subV ++ (cnt.args zip args1)
      Cnt(subC(cnt.name), args1, copyT(cnt.body, subV1, subC))
    }

    def inlineT(tree: Tree)(using s: State): Tree = tree match {
      case LetF(funs, body) =>
        //candidates are picked before rewriting this group, lets calls inside sibling functions be inlined too
        val small = funs.filter(f => size(f.body) <= funLimit)
        val s1 = s.withFuns(small)
        val funs1 = funs.map { f =>
          Fun(f.name, f.retC, f.args, inlineT(f.body)(using s1))
        }
        LetF(funs1, inlineT(body)(using s1))

      case LetC(cnts, body) =>
        //same idea for continuations
        val small = cnts.filter(c => size(c.body) <= cntLimit)
        val s1 = s.withCnts(small)
        val cnts1 = cnts.map { c =>
          Cnt(c.name, c.args, inlineT(c.body)(using s1))
        }
        LetC(cnts1, inlineT(body)(using s1))

      case LetP(name, prim, args, body) =>
        LetP(name, prim, args, inlineT(body))

      case AppF(fun, retC, args) =>
        fun match {
          case n: Name if s.hasFun(n, args.length) =>
            val f = s.fEnv(n)
            val subV: Subst[Atom] = emptySubst[Atom] ++ (f.args zip args)
            val subC: Subst[Name] = emptySubst[Name] + (f.retC -> retC)
            copyT(f.body, subV, subC)
          case _ =>
            AppF(fun, retC, args)
        }

      case AppC(cnt, args) =>
        if (s.hasCnt(cnt, args.length)) {
          val c = s.cEnv(cnt)
          val subV: Subst[Atom] = emptySubst[Atom] ++ (c.args zip args)
          copyT(c.body, subV, emptySubst[Name])
        } else {
          AppC(cnt, args)
        }

      case If(cond, args, thenC, elseC) =>
        If(cond, args, thenC, elseC)

      case Halt(arg) =>
        Halt(arg)
    }

    inlineT(tree)(using State(census(tree)))
  }

  private def fibonacciLimit(round: Int): Int = {
    @tailrec
    def loop(i: Int, a: Int, b: Int): Int =
      if (i <= 1) a else loop(i - 1, b, a + b)

    loop(round, 1, 2)
  }

  // Census computation
  private def census(tree: Tree): Map[Name, Count] = {
    val census = MutableMap[Name, Count]().withDefault(_ => Count())
    val rhs = MutableMap[Name, (Int, Tree)]()

    def incAppUse(atom: Atom, argsCount: Int): Unit = atom match {
      case n: Name =>
        val currCount = census(n)
        census(n) = if rhs.get(n).map(_._1) == Some(argsCount)
        then currCount.copy(applied = currCount.applied + 1)
        else currCount.copy(appliedIncorrectly = true)
        rhs.remove(n).foreach((_, b) => addToCensus(b))
      case _: Literal =>
    }

    def incValUse(atom: Atom): Unit = atom match {
      case n: Name =>
        val currCount = census(n)
        census(n) = currCount.copy(asValue = currCount.asValue + 1)
        rhs.remove(n).foreach((_, b) => addToCensus(b))
      case _: Literal =>
    }

    @tailrec
    def addToCensus(tree: Tree): Unit = tree match {
      case LetF(funs, body) =>
        rhs ++= (funs map { f => (f.name, (f.args.length, f.body)) })
        addToCensus(body)
      case LetC(cnts, body) =>
        rhs ++= (cnts map { c => (c.name, (c.args.length, c.body)) })
        addToCensus(body)
      case LetP(_, _, args, body) =>
        args foreach incValUse; addToCensus(body)
      case AppF(fun, retC, args) =>
        incAppUse(fun, args.length); incValUse(retC); args foreach incValUse
      case AppC(cnt, args) =>
        incAppUse(cnt, args.length); args foreach incValUse
      case If(_, args, thenC, elseC) =>
        args foreach incValUse; incValUse(thenC); incValUse(elseC)
      case Halt(arg) =>
        incValUse(arg)
    }

    addToCensus(tree)
    census.toMap
  }

  private def size(tree: Tree): Int = tree match {
    case LetF(fs, body) => fs.map(_.body).map(size).sum + size(body)
    case LetC(cs, body) => cs.map(_.body).map(size).sum + size(body)
    case LetP(_, _, _, body) => size(body) + 1
    case _: (AppF | AppC | If | Halt) => 1
  }

  protected val impure: ValuePrimitive => Boolean
  protected val unstable: ValuePrimitive => Boolean
  protected val commutative: Set[ValuePrimitive]

  protected val blockAlloc: ValuePrimitive
  protected val blockTag: ValuePrimitive
  protected val blockLength: ValuePrimitive
  protected val blockGet: ValuePrimitive
  protected val blockSet: ValuePrimitive

  //tags identifying blocks written exactly once (at init) and never afterwards
  protected val isImmutableTag: Atom => Boolean

  protected val identity: ValuePrimitive

  protected val leftNeutral: Set[(Literal, ValuePrimitive)]
  protected val rightNeutral: Set[(ValuePrimitive, Literal)]
  protected val leftAbsorbing: Set[(Literal, ValuePrimitive)]
  protected val rightAbsorbing: Set[(ValuePrimitive, Literal)]

  protected val sameArgReduce: PartialFunction[(ValuePrimitive, Atom), Atom]
  protected val sameArgReduceC: PartialFunction[TestPrimitive, Boolean]
  protected val blockP: Option[TestPrimitive]

  protected val vEvaluator: PartialFunction[(ValuePrimitive, Seq[Atom]),
                                            Literal]
  protected val cEvaluator: PartialFunction[(TestPrimitive, Seq[Atom]),
                                            Boolean]
}

object HighCPSOptimizer extends CPSOptimizer(HighCPSTreeModule)
    with (HighCPSTreeModule.Program => HighCPSTreeModule.Program) {
  import treeModule._
  import CL3Literal._, L3Primitive._

  def apply(program: Program): Program =
    rewrite(program)

  private given Conversion[L3Int, Literal] = IntLit.apply
  private given Conversion[Int, Literal] = L3Int.apply

  protected val impure: ValuePrimitive => Boolean =
    Set(BlockSet, ByteRead, ByteWrite)

  protected val unstable: ValuePrimitive => Boolean =
    Set(BlockAlloc, BlockGet, ByteRead)

  protected val commutative: Set[ValuePrimitive] =
    Set(IntAdd, IntMul, IntBitwiseAnd, IntBitwiseOr, IntBitwiseXOr)

  protected val blockAlloc: ValuePrimitive = BlockAlloc
  protected val blockTag: ValuePrimitive = L3ValuePrimitive.BlockTag
  protected val blockLength: ValuePrimitive = BlockLength
  protected val blockGet: ValuePrimitive = BlockGet
  protected val blockSet: ValuePrimitive = BlockSet

  //high level blocks can come from user code so dont trust tags here
  protected val isImmutableTag: Atom => Boolean = _ => false

  protected val identity: ValuePrimitive = Id

  protected val leftNeutral: Set[(Literal, ValuePrimitive)] =
    Set((0, IntAdd), (1, IntMul), (-1, IntBitwiseAnd),
        (0, IntBitwiseOr), (0, IntBitwiseXOr))
  protected val rightNeutral: Set[(ValuePrimitive, Literal)] =
    Set((IntAdd, 0), (IntSub, 0), (IntMul, 1), (IntDiv, 1),
        (IntShiftLeft, 0), (IntShiftRight, 0),
        (IntBitwiseAnd, -1), (IntBitwiseOr, 0), (IntBitwiseXOr, 0))

  //(0, IntDiv) is not here because folding 0/0 to 0 would silently suppress the runtime division-by-zero error
  protected val leftAbsorbing: Set[(Literal, ValuePrimitive)] =
    Set((0, IntMul),
        (0, IntShiftLeft), (0, IntShiftRight),
        (0, IntBitwiseAnd), (-1, IntBitwiseOr))
  protected val rightAbsorbing: Set[(ValuePrimitive, Literal)] =
    Set((IntMul, 0), (IntBitwiseAnd, 0), (IntBitwiseOr, -1))

  protected val sameArgReduce: PartialFunction[(ValuePrimitive, Atom), Atom] = {
    case (IntBitwiseAnd | IntBitwiseOr, a) => a
    //don't reduce x/x or x%x, if x is 0, trap error instead of silently supressing to 1 or 0
    case (IntSub | IntBitwiseXOr, _) => 0
  }

  protected val sameArgReduceC: PartialFunction[TestPrimitive, Boolean] = {
    case IntLe | Eq => true
    case IntLt => false
  }

  protected val blockP: Option[TestPrimitive] = Some(BlockP)

  protected val vEvaluator: PartialFunction[(ValuePrimitive, Seq[Atom]),
                                            Literal] = {
    case (IntAdd, Seq(IntLit(x), IntLit(y))) => IntLit(x + y)
    case (IntSub, Seq(IntLit(x), IntLit(y))) => IntLit(x - y)
    case (IntMul, Seq(IntLit(x), IntLit(y))) => IntLit(x * y)
    case (IntDiv, Seq(IntLit(x), IntLit(y))) if y.toInt != 0 => IntLit(x / y)
    case (IntMod, Seq(IntLit(x), IntLit(y))) if y.toInt != 0 => IntLit(x % y)

    case (IntShiftLeft,  Seq(IntLit(x), IntLit(y))) => IntLit(x << y)
    case (IntShiftRight, Seq(IntLit(x), IntLit(y))) => IntLit(x >> y)
    case (IntBitwiseAnd, Seq(IntLit(x), IntLit(y))) => IntLit(x & y)
    case (IntBitwiseOr,  Seq(IntLit(x), IntLit(y))) => IntLit(x | y)
    case (IntBitwiseXOr, Seq(IntLit(x), IntLit(y))) => IntLit(x ^ y)

    case (IntToChar, Seq(IntLit(x))) => CharLit(x.toInt)
    case (CharToInt, Seq(CharLit(c))) => IntLit(L3Int(c))
  }

  protected val cEvaluator: PartialFunction[(TestPrimitive, Seq[Atom]),
                                            Boolean] = {
    case (IntLt, Seq(IntLit(x), IntLit(y))) => x < y
    case (IntLe, Seq(IntLit(x), IntLit(y))) => x <= y
    case (Eq,    Seq(x: Literal, y: Literal)) => x == y

    case (IntP,   Seq(IntLit(_)))     => true
    case (CharP,  Seq(CharLit(_)))    => true
    case (BoolP,  Seq(BooleanLit(_))) => true
    case (UnitP,  Seq(UnitLit))       => true
    case (BlockP, Seq(_: Literal))    => false

    case (IntP | CharP | BoolP | UnitP, Seq(_: Literal)) => false
  }
}

object FlatCPSOptimizer extends CPSOptimizer(FlatCPSTreeModule)
    with (FlatCPSTreeModule.Program => FlatCPSTreeModule.Program) {
  import treeModule._
  import CPSValuePrimitive._
  import CPSTestPrimitive._

  def apply(program: Program): Program = rewrite(program) match {
    case tree: Program => tree
    case other => LetF(Seq(), other)
  }

  protected val impure: ValuePrimitive => Boolean =
    Set(BlockSet, ByteRead, ByteWrite)

  protected val unstable: ValuePrimitive => Boolean =
    Set(BlockAlloc, BlockGet, ByteRead)

  protected val commutative: Set[ValuePrimitive] =
    Set(Add, Mul, And, Or, XOr)

  protected val blockAlloc: ValuePrimitive = BlockAlloc
  protected val blockTag: ValuePrimitive = BlockTag
  protected val blockLength: ValuePrimitive = BlockLength
  protected val blockGet: ValuePrimitive = BlockGet
  protected val blockSet: ValuePrimitive = BlockSet

  protected val isImmutableTag: Atom => Boolean = {
    case t: Int => t == l3.BlockTag.Function
    case _      => false
  }

  protected val identity: ValuePrimitive = Id

  protected val leftNeutral: Set[(Literal, ValuePrimitive)] =
    Set((0, Add), (1, Mul), (~0, And), (0, Or), (0, XOr))
  protected val rightNeutral: Set[(ValuePrimitive, Literal)] =
    Set((Add, 0), (Sub, 0), (Mul, 1), (Div, 1),
        (ShiftLeft, 0), (ShiftRight, 0),
        (And, ~0), (Or, 0), (XOr, 0))

  //(0, Div) intentionally absent for same reasons as before
  protected val leftAbsorbing: Set[(Literal, ValuePrimitive)] =
    Set((0, Mul),
        (0, ShiftLeft), (0, ShiftRight),
        (0, And), (~0, Or))
  protected val rightAbsorbing: Set[(ValuePrimitive, Literal)] =
    Set((Mul, 0), (And, 0), (Or, ~0))

  protected val sameArgReduce: PartialFunction[(ValuePrimitive, Atom), Atom] = {
    case (And | Or, a) => a
    //don't reduce x/x or x%x (like before)
    case (Sub | XOr, _) => 0
  }

  protected val sameArgReduceC: PartialFunction[TestPrimitive, Boolean] = {
    case Le | Eq => true
    case Lt => false
  }

  protected val blockP: Option[TestPrimitive] = None

  protected val vEvaluator: PartialFunction[(ValuePrimitive, Seq[Atom]),
                                            Literal] = {
    case (Add, Seq(x: Literal, y: Literal)) => x + y
    case (Sub, Seq(x: Literal, y: Literal)) => x - y
    case (Mul, Seq(x: Literal, y: Literal)) => x * y
    case (Div, Seq(x: Literal, y: Literal)) if y.toInt != 0 => x / y
    case (Mod, Seq(x: Literal, y: Literal)) if y.toInt != 0 => x % y

    case (ShiftLeft,  Seq(x: Literal, y: Literal)) => x << y
    case (ShiftRight, Seq(x: Literal, y: Literal)) => x >> y
    case (And, Seq(x: Literal, y: Literal)) => x & y
    case (Or,  Seq(x: Literal, y: Literal)) => x | y
    case (XOr, Seq(x: Literal, y: Literal)) => x ^ y
  }

  protected val cEvaluator: PartialFunction[(TestPrimitive, Seq[Atom]),
                                            Boolean] = {
    case (Lt, Seq(x: Literal, y: Literal)) => x < y
    case (Le, Seq(x: Literal, y: Literal)) => x <= y
    case (Eq, Seq(x: Literal, y: Literal)) => x == y
  }
}