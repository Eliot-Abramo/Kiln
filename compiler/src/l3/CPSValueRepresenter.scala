package l3

object CPSValueRepresenter extends (HighCPSTreeModule.Tree => LowCPSTreeModule.Tree) {

  private val H = HighCPSTreeModule
  private val L = LowCPSTreeModule

  import CL3Literal.*
  import CPSValuePrimitive as CpsVP
  import CPSTestPrimitive as CpsTP

  def apply(tree: HighCPSTreeModule.Tree): LowCPSTreeModule.Tree = transform(tree)

  //atom
  private def transformAtom(a: H.Atom): L.Atom = a match {
    case n: Symbol              => n
    case IntLit(i)              => (i.toInt << 1) | 1
    case CharLit(c)             => (c << 3) | 0x6
    case BooleanLit(true)       => 0x1A
    case BooleanLit(false)      => 0x0A
    case UnitLit                => 0x02
  }

  //tree
  private def transform(tree: H.Tree): L.Tree = tree match {
    case H.LetF(funs, body) =>
      L.LetF(
        funs.map(f => L.Fun(f.name, f.retC, f.args, transform(f.body))),
        transform(body))

    case H.LetC(cnts, body) =>
      L.LetC(
        cnts.map(c => L.Cnt(c.name, c.args, transform(c.body))),
        transform(body))

    case H.LetP(name, prim, args, body) =>
      transformLetP(name, prim, args.map(transformAtom), transform(body))

    case H.AppF(fun, retC, args) =>
      L.AppF(transformAtom(fun), retC, args.map(transformAtom))

    case H.AppC(cnt, args) =>
      L.AppC(cnt, args.map(transformAtom))

    case H.If(cond, args, thenC, elseC) =>
      transformIf(cond, args.map(transformAtom), thenC, elseC)

    case H.Halt(arg) =>
      untagInt(transformAtom(arg)) { t =>
        L.Halt(t)
      }
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

  //primitive values
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

      //bitwise
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
        tempLetP(CpsVP.ByteRead, Seq()) { t =>
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
        untagInt(args(1)) { idx =>
          L.LetP(name, CpsVP.BlockGet, Seq(args(0), idx), body)
        }

      case BlockSet =>
        untagInt(args(1)) { idx =>
          tempLetP(CpsVP.BlockSet, Seq(args(0), idx, args(2))) { _ =>
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