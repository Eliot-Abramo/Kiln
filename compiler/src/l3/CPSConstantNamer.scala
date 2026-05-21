package l3

import l3.FlatCPSTreeModule._
import l3.{ CPSValuePrimitive => CPS }

object CPSConstantNamer extends (Program => Program) {
  def apply(prog: Program): Program =
    LetF(prog.funs map (f => Fun(f.name, f.retC, f.args, rewrite(f.body))),
         rewrite(prog.body))

  private def rewrite(body: Body, litSubst: Subst[Atom] = emptySubst): Body = {
    val newLitSubst = atomsFor(body)
      .removedAll(litSubst.keySet)
      .collect { case l: Literal if ! fitsInNUnsignedBits(5)(l) => l }
      .map(a => a -> Symbol.fresh(s"c${a}"))
      .toMap
    val fullLitSubst = litSubst ++ newLitSubst

    val body1: Body = body match {
      case LetC(cnts, body) =>
        LetC(cnts.map(c => Cnt(c.name, c.args, rewrite(c.body, fullLitSubst))),
             rewrite(body, fullLitSubst))
      case LetP(name, prim, args, body) =>
        LetP(name, prim, args map fullLitSubst, rewrite(body, fullLitSubst))
      case AppF(fun, retC, args) =>
        AppF(fullLitSubst(fun), retC, args map fullLitSubst)
      case AppC(cnt, args) =>
        AppC(cnt, args map fullLitSubst)
      case If(cond, args, thenC, elseC) =>
        If(cond, args map fullLitSubst, thenC, elseC)
      case Halt(arg) =>
        Halt(fullLitSubst(arg))
    }

    newLitSubst.toSeq
      .sortBy(_._1)
      .foldRight(body1) { case ((l, n), t) => LetP(n, CPS.Id, Seq(l), t) }
  }

  private def atomsFor(body: Body): Set[Atom] = body match {
    case LetC(cnts, body) =>
      (body +: (cnts map (_.body)))
        .flatMap(allAtoms)
        .groupBy(identity)
        .filter(_._2.length > 1)
        .keySet
    case other =>
      ownAtoms(other)
  }

  private def ownAtoms(body: Body): Set[Atom] = body match {
    case LetC(_, _) =>
      Set.empty
    case LetP(_, _, args, _) =>
      args.toSet
    case AppF(fun, _, args) =>
      Set(fun) ++ args.toSet
    case AppC(_, args) =>
      args.toSet
    case If(_, args, _, _) =>
      args.toSet
    case Halt(a) =>
      Set(a)
  }

  private def descendentAtoms(body: Body): Set[Atom] = body match {
    case LetC(cnts, body) =>
      allAtoms(body) ++ cnts.flatMap(c => allAtoms(c.body))
    case LetP(_, _, _, body) =>
      allAtoms(body)
    case _: (AppF | AppC | If | Halt) =>
      Set.empty
  }

  private def allAtoms(body: Body): Set[Atom] =
    ownAtoms(body) ++ descendentAtoms(body)
}
