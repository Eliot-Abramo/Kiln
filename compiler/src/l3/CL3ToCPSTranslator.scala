package l3

import SymbolicCL3TreeModule.{Tree as CL3Tree}
import HighCPSTreeModule.*
import CL3Literal.*
import L3ValuePrimitive.Id
import L3TestPrimitive.Eq

object CL3ToCPSTranslator extends (CL3Tree => Tree) {
  def apply(tree: CL3Tree): Tree =
    nonTail(tree) { _ => Halt(IntLit(L3Int(0))) }

  private def nonTail(tree: CL3Tree)(c: Atom => Tree): Tree = tree match {
    case CL3Tree.Ident(name) =>
      c(name)

    case CL3Tree.Lit(value) =>
      c(value)

    case CL3Tree.Let(bindings, body) =>
      def loop(bs: Seq[(Symbol, CL3Tree)]): Tree = bs match {
        case Seq() =>
          nonTail(body)(c)

        case (name, expr) +: rest =>
          nonTail(expr) { a =>
            LetP(name, Id, Seq(a), loop(rest))
          }
      }
      loop(bindings)

    case CL3Tree.LetRec(funs, body) =>
      LetF(
        funs map { fun =>
          val k = Symbol.fresh("c")
          Fun(fun.name, k, fun.args, tail(fun.body, k))
        },
        nonTail(body)(c)
      )

    case CL3Tree.App(fun, args) =>
      nonTail(fun) { f =>
        nonTailSeq(args) { as =>
          val k = Symbol.fresh("c")
          val x = Symbol.fresh("v")
          LetC(
            Seq(Cnt(k, Seq(x), c(x))),
            AppF(f, k, as)
          )
        }
      }

    case CL3Tree.If(condE, thenE, elseE) =>
      val k = Symbol.fresh("c")
      val x = Symbol.fresh("r")
      val kt = Symbol.fresh("ct")
      val kf = Symbol.fresh("ce")
      LetC(
        Seq(
          Cnt(k, Seq(x), c(x)),
          Cnt(kt, Seq(), tail(thenE, k)),
          Cnt(kf, Seq(), tail(elseE, k))
        ),
        cond(condE, kt, kf)
      )

    case CL3Tree.Prim(p: L3ValuePrimitive, args) =>
      nonTailSeq(args) { as =>
        val x = Symbol.fresh("v")
        LetP(x, p, as, c(x))
      }

    case CL3Tree.Prim(_: L3TestPrimitive, _) =>
      given Position = tree.pos
      nonTail(CL3Tree.If(tree, CL3Tree.Lit(BooleanLit(true)),
                                CL3Tree.Lit(BooleanLit(false))))(c)

    case CL3Tree.Halt(arg) =>
      nonTail(arg) { a => Halt(a) }
  }

  private def tail(tree: CL3Tree, c: Symbol): Tree = tree match {
    case CL3Tree.Ident(name) =>
      AppC(c, Seq(name))

    case CL3Tree.Lit(value) =>
      AppC(c, Seq(value))

    case CL3Tree.Let(bindings, body) =>
      def loop(bs: Seq[(Symbol, CL3Tree)]): Tree = bs match {
        case Seq() =>
          tail(body, c)

        case (name, expr) +: rest =>
          nonTail(expr) { a =>
            LetP(name, Id, Seq(a), loop(rest))
          }
      }
      loop(bindings)

    case CL3Tree.LetRec(funs, body) =>
      LetF(
        funs map { fun =>
          val k = Symbol.fresh("c")
          Fun(fun.name, k, fun.args, tail(fun.body, k))
        },
        tail(body, c)
      )

    case CL3Tree.App(fun, args) =>
      nonTail(fun) { f =>
        nonTailSeq(args) { as =>
          AppF(f, c, as)
        }
      }

    case CL3Tree.If(condE, thenE, elseE) =>
      val kt = Symbol.fresh("ct")
      val kf = Symbol.fresh("ce")
      LetC(
        Seq(
          Cnt(kt, Seq(), tail(thenE, c)),
          Cnt(kf, Seq(), tail(elseE, c))
        ),
        cond(condE, kt, kf)
      )

    case CL3Tree.Prim(p: L3ValuePrimitive, args) =>
      nonTailSeq(args) { as =>
        val x = Symbol.fresh("v")
        LetP(x, p, as, AppC(c, Seq(x)))
      }

    case CL3Tree.Prim(_: L3TestPrimitive, _) =>
      given Position = tree.pos
      tail(CL3Tree.If(tree, CL3Tree.Lit(BooleanLit(true)),
                             CL3Tree.Lit(BooleanLit(false))), c)

    case CL3Tree.Halt(arg) =>
      nonTail(arg) { a => Halt(a) }
  }

  private def cond(tree: CL3Tree, ct: Symbol, ce: Symbol): Tree = tree match {
    case CL3Tree.Lit(BooleanLit(false)) =>
      AppC(ce, Seq())

    case CL3Tree.Lit(_) =>
      AppC(ct, Seq())

    case CL3Tree.Prim(p: L3TestPrimitive, args) =>
      nonTailSeq(args) { as =>
        If(p, as, ct, ce)
      }

    case CL3Tree.If(e, CL3Tree.Lit(tl), CL3Tree.Lit(el)) =>
      val k1 = tl match {
        case BooleanLit(false) => ce
        case _                 => ct
      }
      val k2 = el match {
        case BooleanLit(false) => ce
        case _                 => ct
      }
      cond(e, k1, k2)

    case CL3Tree.If(e, CL3Tree.Lit(tl), elseE) =>
      val k = Symbol.fresh("ac")
      val litTarget = tl match {
        case BooleanLit(false) => ce
        case _                 => ct
      }
      LetC(
        Seq(Cnt(k, Seq(), cond(elseE, ct, ce))),
        cond(e, litTarget, k)
      )

    case CL3Tree.If(e, thenE, CL3Tree.Lit(el)) =>
      val k = Symbol.fresh("ac")
      val litTarget = el match {
        case BooleanLit(false) => ce
        case _                 => ct
      }
      LetC(
        Seq(Cnt(k, Seq(), cond(thenE, ct, ce))),
        cond(e, k, litTarget)
      )

    case other =>
      nonTail(other) { v =>
        If(Eq, Seq(v, BooleanLit(false)), ce, ct)
      }
  }

  private def nonTailSeq(trees: Seq[CL3Tree])(c: Seq[Atom] => Tree): Tree =
    trees match {
      case Seq() =>
        c(Seq())

      case head +: rest =>
        nonTail(head) { a =>
          nonTailSeq(rest) { as =>
            c(a +: as)
          }
        }
    }
}