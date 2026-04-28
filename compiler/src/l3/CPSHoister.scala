package l3

object CPSHoister
    extends (LowCPSTreeModule.Program => FlatCPSTreeModule.Program) {

  private val S = LowCPSTreeModule
  private val T = FlatCPSTreeModule

  private case class Hoisted(funs: Seq[T.Fun], body: T.Body)

  def apply(tree: LowCPSTreeModule.Program): FlatCPSTreeModule.Program = {
    val Hoisted(funs, body) = hoist(tree)
    T.LetF(funs, body)
  }

  private def hoist(tree: S.Tree): Hoisted = tree match {
    case S.LetP(name, prim, args, body) =>
      val Hoisted(funs, body1) = hoist(body)
      Hoisted(funs, T.LetP(name, prim, args, body1))

    case S.LetC(cnts, body) =>
      val hoistedCnts = cnts.map { cnt =>
        val Hoisted(funs, body1) = hoist(cnt.body)
        (T.Cnt(cnt.name, cnt.args, body1), funs)
      }
      val Hoisted(bodyFuns, body1) = hoist(body)
      Hoisted(
        hoistedCnts.flatMap(_._2) ++ bodyFuns,
        T.LetC(hoistedCnts.map(_._1), body1)
      )

    case S.LetF(funs, body) =>
      val hoistedFuns = funs.map { fun =>
        val Hoisted(innerFuns, body1) = hoist(fun.body)
        (T.Fun(fun.name, fun.retC, fun.args, body1), innerFuns)
      }
      val Hoisted(bodyFuns, body1) = hoist(body)
      Hoisted(
        hoistedFuns.map(_._1) ++ hoistedFuns.flatMap(_._2) ++ bodyFuns,
        body1
      )

    case S.AppF(fun, retC, args) =>
      Hoisted(Seq.empty, T.AppF(fun, retC, args))

    case S.AppC(cnt, args) =>
      Hoisted(Seq.empty, T.AppC(cnt, args))

    case S.If(cond, args, thenC, elseC) =>
      Hoisted(Seq.empty, T.If(cond, args, thenC, elseC))

    case S.Halt(arg) =>
      Hoisted(Seq.empty, T.Halt(arg))
  }
}
