package l3

object CPSValueRepresenter extends (HighCPSTreeModule.Tree => LowCPSTreeModule.Tree) {

  private val H = HighCPSTreeModule
  private val L = LowCPSTreeModule

  import CL3Literal.*
  import CPSValuePrimitive as CpsVP
  import CPSTestPrimitive as CpsTP

  private type Subst[T] = Map[T, T]

  private case class KnownFun(worker: Symbol, freeVars: Seq[Symbol])
  private type KnownEnv = Map[Symbol, KnownFun]

  private case class LocalFun(source: H.Fun,
                              worker: Symbol,
                              wrapper: Symbol,
                              env: Symbol,
                              freeVars: Seq[Symbol],
                              freeVarArgs: Seq[Symbol])

  def apply(tree: HighCPSTreeModule.Tree): LowCPSTreeModule.Tree =
    transform(tree)(using Map.empty, Map.empty)

  private def rewriteName(n: Symbol)(using subst: Subst[Symbol]): Symbol =
    subst.getOrElse(n, n)

  private def distinct(ns: Seq[Symbol]): Seq[Symbol] =
    ns.foldLeft(Vector.empty[Symbol]) { (acc, n) =>
      if (acc.contains(n)) acc else acc :+ n
    }

  private def freeAtom(a: H.Atom, bound: Set[Symbol]): Seq[Symbol] = a match {
    case n: Symbol if !bound(n) => Seq(n)
    case _                      => Seq.empty
  }

  private def transformAtom(a: H.Atom)(using subst: Subst[Symbol]): L.Atom = a match {
    case n: Symbol         => rewriteName(n)
    case IntLit(i)         => (i.toInt << 1) | 1
    case CharLit(c)        => (c << 3) | 0x6
    case BooleanLit(true)  => 0x1A
    case BooleanLit(false) => 0x0A
    case UnitLit           => 0x02
  }

  private def letPStar(bindings: Seq[(Symbol, CPSValuePrimitive, Seq[L.Atom])],
                       body: L.Tree): L.Tree =
    bindings.foldRight(body) { case ((name, prim, args), acc) =>
      L.LetP(name, prim, args, acc)
    }

  private def tempLetP(prim: CPSValuePrimitive, args: Seq[L.Atom])
                      (body: Symbol => L.Tree): L.Tree = {
    val t = Symbol.fresh("t")
    L.LetP(t, prim, args, body(t))
  }

  private def retagInt(v: Symbol, name: Symbol, body: L.Tree): L.Tree =
    tempLetP(CpsVP.ShiftLeft, Seq(v, 1)) { shifted =>
      L.LetP(name, CpsVP.Add, Seq(shifted, 1), body)
    }

  private def untagInt(v: L.Atom)(body: Symbol => L.Tree): L.Tree =
    tempLetP(CpsVP.ShiftRight, Seq(v, 1))(body)

  private def analyze(tree: H.Tree, bound: Set[Symbol])
                     (using known: KnownEnv): Seq[Symbol] = tree match {
    case H.LetP(name, _, args, body) =>
      distinct(args.flatMap(freeAtom(_, bound)) ++ analyze(body, bound + name))

    case H.LetC(cnts, body) =>
      val cntFree = cnts.flatMap(cnt => analyze(cnt.body, bound ++ cnt.args))
      distinct(analyze(body, bound) ++ cntFree)

    case H.LetF(funs, body) =>
      val localInfos = analyzeLocalFuns(funs)
      val localKnown = known ++ localInfos.map(info =>
        info.source.name -> KnownFun(info.worker, info.freeVars))
      val bodyFree = analyze(body, bound ++ funs.map(_.name))(using localKnown)
      distinct(bodyFree ++ localInfos.flatMap(_.freeVars).filterNot(bound))

    case H.AppF(fun, _, args) =>
      val argsFree = args.flatMap(freeAtom(_, bound))
      fun match {
        case n: Symbol if known.contains(n) =>
          distinct(argsFree ++ known(n).freeVars.filterNot(bound))
        case _ =>
          distinct(freeAtom(fun, bound) ++ argsFree)
      }

    case H.AppC(_, args) =>
      distinct(args.flatMap(freeAtom(_, bound)))

    case H.If(_, args, _, _) =>
      distinct(args.flatMap(freeAtom(_, bound)))

    case H.Halt(arg) =>
      freeAtom(arg, bound)
  }

  private def analyzeLocalFuns(funs: Seq[H.Fun])
                              (using known: KnownEnv): Seq[LocalFun] = {
    val skeletons = funs.map { fun =>
      LocalFun(
        source = fun,
        worker = Symbol.fresh(s"${fun.name.name}.worker"),
        wrapper = Symbol.fresh(s"${fun.name.name}.wrapper"),
        env = Symbol.fresh("env"),
        freeVars = Seq.empty,
        freeVarArgs = Seq.empty
      )
    }

    @annotation.tailrec
    def fixpoint(guess: Map[Symbol, Seq[Symbol]]): Map[Symbol, Seq[Symbol]] = {
      val localKnown = known ++ skeletons.map(skel =>
        skel.source.name -> KnownFun(skel.worker, guess(skel.source.name)))

      val next = skeletons.map { skel =>
        skel.source.name -> analyze(skel.source.body, skel.source.args.toSet)(using localKnown)
      }.toMap

      if (next == guess) next else fixpoint(next)
    }

    val freeVarsByFun = fixpoint(skeletons.map(skel => skel.source.name -> Seq.empty[Symbol]).toMap)

    skeletons.map { skel =>
      val freeVars = freeVarsByFun(skel.source.name)
      skel.copy(
        freeVars = freeVars,
        freeVarArgs = freeVars.map(_ => Symbol.fresh("fv"))
      )
    }
  }

  private def transform(tree: H.Tree)
                       (using subst: Subst[Symbol], known: KnownEnv): L.Tree = tree match {
    case H.LetF(funs, body) =>
      transformLetF(funs, body)

    case H.LetC(cnts, body) =>
      L.LetC(
        cnts.map(cnt => L.Cnt(cnt.name, cnt.args, transform(cnt.body))),
        transform(body)
      )

    case H.LetP(name, prim, args, body) =>
      transformLetP(name, prim, args.map(transformAtom), transform(body))

    case H.AppF(fun, retC, args) =>
      fun match {
        case n: Symbol if known.contains(n) =>
          val callee = known(n)
          val fullArgs = args.map(transformAtom) ++ callee.freeVars.map(v => transformAtom(v))
          L.AppF(callee.worker, retC, fullArgs)
        case _ =>
          val clos = transformAtom(fun)
          tempLetP(CpsVP.BlockGet, Seq(clos, 0)) { code =>
            L.AppF(code, retC, clos +: args.map(transformAtom))
          }
      }

    case H.AppC(cnt, args) =>
      L.AppC(cnt, args.map(transformAtom))

    case H.If(cond, args, thenC, elseC) =>
      transformIf(cond, args.map(transformAtom), thenC, elseC)

    case H.Halt(arg) =>
      untagInt(transformAtom(arg))(L.Halt(_))
  }

  private def transformLetF(funs: Seq[H.Fun], body: H.Tree)
                           (using subst: Subst[Symbol], known: KnownEnv): L.Tree = {
    val infos = analyzeLocalFuns(funs)
    val localKnown = known ++ infos.map(info =>
      info.source.name -> KnownFun(info.worker, info.freeVars))

    val lowFuns = infos.flatMap { info =>
      val workerSubst = subst ++ (info.freeVars zip info.freeVarArgs)

      val worker = L.Fun(
        info.worker,
        info.source.retC,
        info.source.args ++ info.freeVarArgs,
        transform(info.source.body)(using workerSubst, localKnown)
      )

      val extractedBindings = info.freeVarArgs.zipWithIndex.map { case (arg, index) =>
        (arg, CpsVP.BlockGet, Seq(info.env, index + 1))
      }

      val wrapper = L.Fun(
        info.wrapper,
        info.source.retC,
        info.env +: info.source.args,
        letPStar(extractedBindings,
          L.AppF(info.worker, info.source.retC, info.source.args ++ info.freeVarArgs))
      )

      Seq(worker, wrapper)
    }

    val allocBindings = infos.map { info =>
      (info.source.name,
       CpsVP.BlockAlloc,
       Seq(l3.BlockTag.Function, info.freeVars.length + 1))
    }

    val initBindings = infos.flatMap { info =>
      val codeInit = Seq(
        (Symbol.fresh("t"), CpsVP.BlockSet, Seq(info.source.name, 0, info.wrapper))
      )
      val envInits = info.freeVars.zipWithIndex.map { case (freeVar, index) =>
        (Symbol.fresh("t"),
         CpsVP.BlockSet,
         Seq(info.source.name, index + 1, transformAtom(freeVar)))
      }
      codeInit ++ envInits
    }

    L.LetF(
      lowFuns,
      letPStar(
        allocBindings,
        letPStar(initBindings, transform(body)(using subst, localKnown))
      )
    )
  }

  private def transformLetP(name: Symbol, prim: L3ValuePrimitive,
                            args: Seq[L.Atom], body: L.Tree): L.Tree = {
    import L3ValuePrimitive.*

    prim match {
      case IntAdd =>
        tempLetP(CpsVP.Add, Seq(args(0), args(1))) { t =>
          L.LetP(name, CpsVP.Sub, Seq(t, 1), body)
        }

      case IntSub =>
        tempLetP(CpsVP.Sub, Seq(args(0), args(1))) { t =>
          L.LetP(name, CpsVP.Add, Seq(t, 1), body)
        }

      case IntMul =>
        tempLetP(CpsVP.Sub, Seq(args(0), 1)) { t1 =>
          untagInt(args(1)) { t2 =>
            tempLetP(CpsVP.Mul, Seq(t1, t2)) { t3 =>
              L.LetP(name, CpsVP.Add, Seq(t3, 1), body)
            }
          }
        }

      case IntDiv =>
        untagInt(args(0)) { t1 =>
          untagInt(args(1)) { t2 =>
            tempLetP(CpsVP.Div, Seq(t1, t2)) { t3 =>
              retagInt(t3, name, body)
            }
          }
        }

      case IntMod =>
        untagInt(args(0)) { t1 =>
          untagInt(args(1)) { t2 =>
            tempLetP(CpsVP.Mod, Seq(t1, t2)) { t3 =>
              retagInt(t3, name, body)
            }
          }
        }

      case IntShiftLeft =>
        tempLetP(CpsVP.Sub, Seq(args(0), 1)) { t1 =>
          untagInt(args(1)) { t2 =>
            tempLetP(CpsVP.ShiftLeft, Seq(t1, t2)) { t3 =>
              L.LetP(name, CpsVP.Add, Seq(t3, 1), body)
            }
          }
        }

      case IntShiftRight =>
        untagInt(args(0)) { t1 =>
          untagInt(args(1)) { t2 =>
            tempLetP(CpsVP.ShiftRight, Seq(t1, t2)) { t3 =>
              retagInt(t3, name, body)
            }
          }
        }

      case IntBitwiseAnd =>
        L.LetP(name, CpsVP.And, Seq(args(0), args(1)), body)

      case IntBitwiseOr =>
        L.LetP(name, CpsVP.Or, Seq(args(0), args(1)), body)

      case IntBitwiseXOr =>
        tempLetP(CpsVP.XOr, Seq(args(0), args(1))) { t =>
          L.LetP(name, CpsVP.Or, Seq(t, 1), body)
        }

      //char
      case CharToInt =>
        L.LetP(name, CpsVP.ShiftRight, Seq(args(0), 2), body)

      case IntToChar =>
        tempLetP(CpsVP.ShiftLeft, Seq(args(0), 2)) { t =>
          L.LetP(name, CpsVP.Add, Seq(t, 2), body)
        }

      //io
      case ByteRead =>
        tempLetP(CpsVP.ByteRead, Seq.empty) { t =>
          retagInt(t, name, body)
        }

      case ByteWrite =>
        untagInt(args(0)) { t1 =>
          tempLetP(CpsVP.ByteWrite, Seq(t1)) { _ =>
            L.LetP(name, CpsVP.Id, Seq(0x02), body)
          }
        }

      //block
      case BlockAlloc =>
        untagInt(args(0)) { t1 =>
          untagInt(args(1)) { t2 =>
            L.LetP(name, CpsVP.BlockAlloc, Seq(t1, t2), body)
          }
        }

      case BlockTag =>
        tempLetP(CpsVP.BlockTag, Seq(args(0))) { t =>
          retagInt(t, name, body)
        }

      case BlockLength =>
        tempLetP(CpsVP.BlockLength, Seq(args(0))) { t =>
          retagInt(t, name, body)
        }

      case BlockGet =>
        untagInt(args(1)) { index =>
          L.LetP(name, CpsVP.BlockGet, Seq(args(0), index), body)
        }

      case BlockSet =>
        untagInt(args(1)) { index =>
          tempLetP(CpsVP.BlockSet, Seq(args(0), index, args(2))) { _ =>
            L.LetP(name, CpsVP.Id, Seq(0x02), body)
          }
        }

      case Id =>
        L.LetP(name, CpsVP.Id, Seq(args(0)), body)
    }
  }

  private def transformIf(cond: L3TestPrimitive, args: Seq[L.Atom],
                          thenC: Symbol, elseC: Symbol): L.Tree = {
    import L3TestPrimitive.*

    cond match {
      case IntLt =>
        L.If(CpsTP.Lt, Seq(args(0), args(1)), thenC, elseC)

      case IntLe =>
        L.If(CpsTP.Le, Seq(args(0), args(1)), thenC, elseC)

      case Eq =>
        L.If(CpsTP.Eq, Seq(args(0), args(1)), thenC, elseC)

      case BlockP =>
        tempLetP(CpsVP.And, Seq(args(0), 3)) { t =>
          L.If(CpsTP.Eq, Seq(t, 0), thenC, elseC)
        }

      case IntP =>
        tempLetP(CpsVP.And, Seq(args(0), 1)) { t =>
          L.If(CpsTP.Eq, Seq(t, 1), thenC, elseC)
        }

      case CharP =>
        tempLetP(CpsVP.And, Seq(args(0), 7)) { t =>
          L.If(CpsTP.Eq, Seq(t, 6), thenC, elseC)
        }

      case BoolP =>
        tempLetP(CpsVP.And, Seq(args(0), 0xF)) { t =>
          L.If(CpsTP.Eq, Seq(t, 0xA), thenC, elseC)
        }

      case UnitP =>
        tempLetP(CpsVP.And, Seq(args(0), 0xF)) { t =>
          L.If(CpsTP.Eq, Seq(t, 0x02), thenC, elseC)
        }
    }
  }
}