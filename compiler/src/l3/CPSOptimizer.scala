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
    val simplifiedTree = fixedPoint(tree)(shrink)
    val maxSize = size(simplifiedTree) * 3 / 2
    fixedPoint(simplifiedTree, 8)(`inline`(_, maxSize))
  }

  private case class Count(applied: Int = 0,
                           appliedIncorrectly: Boolean = false,
                           asValue: Int = 0)

  private case class State(
    census: Map[Name, Count],
    aSubst: Subst[Atom] = emptySubst,
    cSubst: Subst[Name] = emptySubst,
    eInvEnv: Map[(ValuePrimitive, Seq[Atom]), Atom] = Map.empty,
    cEnv: Map[Name, Cnt] = Map.empty,
    fEnv: Map[Name, Fun] = Map.empty) {

    def dead(s: Name): Boolean =
      ! census.contains(s)
    def appliedOnce(s: Name): Boolean =
      census.get(s).contains(Count(applied = 1))

    def hasFun(fun: Name, actualArgs: Seq[Atom]): Boolean =
      fEnv.get(fun).exists(_.args.length == actualArgs.length)
    def hasCnt(cnt: Name, actualArgs: Seq[Atom]): Boolean =
      cEnv.get(cnt).exists(_.args.length == actualArgs.length)

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

  private def shrink(tree: Tree, s: State): Tree = ???

  // (Non-shrinking) inlining

  private def inline(tree: Tree, maxSize: Int): Tree = {
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

    val fibonacci = Seq(1, 2, 3, 5, 8, 13)

    val trees = LazyList.iterate((0, tree), fibonacci.length){ (i, tree) =>
      val funLimit = fibonacci(i)
      val cntLimit = i

      def inlineT(tree: Tree)(using s: State): Tree = ???

      (i + 1, fixedPoint(inlineT(tree)(using State(census(tree))))(shrink))
    }

    trees.takeWhile{ (_, tree) => size(tree) <= maxSize }.last._2
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

  protected val blockAlloc: ValuePrimitive
  protected val blockTag: ValuePrimitive
  protected val blockLength: ValuePrimitive

  protected val identity: ValuePrimitive

  protected val leftNeutral: Set[(Literal, ValuePrimitive)]
  protected val rightNeutral: Set[(ValuePrimitive, Literal)]
  protected val leftAbsorbing: Set[(Literal, ValuePrimitive)]
  protected val rightAbsorbing: Set[(ValuePrimitive, Literal)]

  protected val sameArgReduce: PartialFunction[(ValuePrimitive, Atom), Atom]
  protected val sameArgReduceC: PartialFunction[TestPrimitive, Boolean]

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

  protected val impure: ValuePrimitive => Boolean = ???

  protected val unstable: ValuePrimitive => Boolean = ???

  protected val blockAlloc: ValuePrimitive = ???
  protected val blockTag: ValuePrimitive = ???
  protected val blockLength: ValuePrimitive = ???

  protected val identity: ValuePrimitive = ???

  protected val leftNeutral: Set[(Literal, ValuePrimitive)] = ???
  protected val rightNeutral: Set[(ValuePrimitive, Literal)] = ???

  protected val leftAbsorbing: Set[(Literal, ValuePrimitive)] = ???
  protected val rightAbsorbing: Set[(ValuePrimitive, Literal)] = ???

  protected val sameArgReduce: PartialFunction[(ValuePrimitive, Atom), Atom] =
    ???

  protected val sameArgReduceC: PartialFunction[TestPrimitive, Boolean] = ???

  protected val vEvaluator: PartialFunction[(ValuePrimitive, Seq[Atom]),
                                            Literal] = ???

  protected val cEvaluator: PartialFunction[(TestPrimitive, Seq[Atom]),
                                            Boolean] = ???
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

  protected val blockAlloc: ValuePrimitive = BlockAlloc
  protected val blockTag: ValuePrimitive = BlockTag
  protected val blockLength: ValuePrimitive = BlockLength

  protected val identity: ValuePrimitive = Id

  protected val leftNeutral: Set[(Literal, ValuePrimitive)] =
    Set((0, Add), (1, Mul), (~0, And), (0, Or), (0, XOr))
  protected val rightNeutral: Set[(ValuePrimitive, Literal)] =
    Set((Add, 0), (Sub, 0), (Mul, 1), (Div, 1),
        (ShiftLeft, 0), (ShiftRight, 0),
        (And, ~0), (Or, 0), (XOr, 0))

  protected val leftAbsorbing: Set[(Literal, ValuePrimitive)] =
    Set((0, Mul), (0, Div),
        (0, ShiftLeft), (0, ShiftRight),
        (0, And), (~0, Or))
  protected val rightAbsorbing: Set[(ValuePrimitive, Literal)] =
    Set((Mul, 0), (And, 0), (Or, ~0))

  protected val sameArgReduce: PartialFunction[(ValuePrimitive, Atom), Atom] = {
    case (And | Or, a) => a
    case (Sub | Mod | XOr, _) => 0
    case (Div, _) => 1
  }

  protected val sameArgReduceC: PartialFunction[TestPrimitive, Boolean] = {
    case Le | Eq => true
    case Lt => false
  }

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
