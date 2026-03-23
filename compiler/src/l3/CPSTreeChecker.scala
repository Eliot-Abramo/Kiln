package l3

import scala.reflect.TypeTest
import scala.annotation.tailrec

trait Checker[-T] extends Function[T, Unit]

/**
  * Tree checker for CPS languages. Verifies that:
  *   1. names are globally unique (no name is bound more than once),
  *   2. names are used in their scope.
  *
  * @author Michel Schinz <Michel.Schinz@epfl.ch>
  */

class CPSTreeChecker[T <: CPSTreeModule](protected val treeModule: T)
    (using TypeTest[treeModule.Atom, treeModule.Name]) {
  import treeModule._

  def apply(t: Tree): Unit = {
    val allNames = scala.collection.mutable.Set[Name]()

    def recordUniqueName(n: Name): Unit = {
      if (allNames contains n)
        error(s"Name ${n} is bound more than once (not globally unique).")
      else
        allNames += n
    }

    def checkName(n: Name, env: Set[Name]): Unit = {
      if (! (env contains n))
        error(s"Name ${n} is unbound.")
    }

    def checkAtom(a: Atom, env: Set[Name]): Unit = a match {
      case n: Name => checkName(n, env)
      case _ =>
    }

    @tailrec
    def checkT(t: Tree, cEnv: Set[Name], vEnv: Set[Name]): Unit = t match {
      case LetF(funs, body) =>
        val vEnv1 = vEnv ++ (funs map (_.name))
        funs.foreach(checkF(_, vEnv1))
        checkT(body, cEnv, vEnv1)
      case LetC(cnts, body) =>
        val cEnv1 = cEnv ++ (cnts map (_.name))
        cnts.foreach(checkC(_, cEnv1, vEnv))
        checkT(body, cEnv1, vEnv)
      case LetP(name, _, args, body) =>
        recordUniqueName(name)
        args.foreach(checkAtom(_, vEnv))
        checkT(body, cEnv, vEnv + name)
      case AppF(fun, retC, args) =>
        checkAtom(fun, vEnv)
        checkName(retC, cEnv)
        args.foreach(checkAtom(_, vEnv))
      case AppC(cnt, args) =>
        checkName(cnt, cEnv)
        args.foreach(checkAtom(_, vEnv))
      case If(_, args, thenC, elseC) =>
        args.foreach(checkAtom(_, vEnv))
        checkName(thenC, cEnv)
        checkName(elseC, cEnv)
      case Halt(arg) =>
        checkAtom(arg, vEnv)
    }

    def checkF(fun: Fun, vEnv: Set[Name]): Unit = {
      recordUniqueName(fun.name)
      recordUniqueName(fun.retC)
      fun.args.foreach(recordUniqueName)
      checkT(fun.body, Set(fun.retC), vEnv ++ fun.args)
    }

    def checkC(cnt: Cnt, cEnv: Set[Name], vEnv: Set[Name]): Unit = {
      recordUniqueName(cnt.name)
      cnt.args.foreach(recordUniqueName)
      checkT(cnt.body, cEnv, vEnv ++ cnt.args)
    }

    checkT(t, Set(), Set())
  }

  private def error(msg: String): Unit = {
    Console.println(s"Error: ${msg}")
  }
}

given HighCPSTreeChecker: Checker[HighCPSTreeModule.Program] =
  new CPSTreeChecker(HighCPSTreeModule)
    with Checker[HighCPSTreeModule.Tree]
given LowCPSTreeChecker: Checker[LowCPSTreeModule.Program] =
  new CPSTreeChecker(LowCPSTreeModule)
    with Checker[LowCPSTreeModule.Tree]
