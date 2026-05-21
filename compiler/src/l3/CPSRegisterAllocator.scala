package l3

import l3.{ FlatCPSTreeModule => F }
import l3.{ RegisterCPSTreeModule => R }
import l3.{ CPSValuePrimitive => CPS }

/**
  * A simple register allocator for CPS/L₃.
  *
  * Parallel-move algorithm taken from "Tilting at windmills with Coq"
  * by Rideau et al.
  *
  * @author Michel Schinz <Michel.Schinz@epfl.ch>
  */

object CPSRegisterAllocator extends (F.Program => R.Program) {
  def apply(prog: F.Program): R.Program = {
    val rLink = ASMRegister.Link
    R.LetF(prog.funs map { case F.Fun(name, retC, args, body) =>
             val rArgs = funArgRegs(args.length)
             val s = State()
               .withAssignedReg(retC, rLink)
               .withAssignedRegs(args, rArgs)
             R.Fun(name, rLink, rArgs, rewrite(body, s))
           },
           rewrite(prog.body, State()))
  }

  private def rewrite(tree: F.Body, s: State): R.Body = tree match {
    case F.LetC(cnts, body) =>
      s.withContinuations(cnts) { s =>
        val s1 = cnts.foldLeft(s) { case (s, F.Cnt(name, args, body)) =>
          s.withFreshRegsFor(args, body) { (rs, s) =>
            s.withCntArgs(name, rs)
          }
        }
        R.LetC(cnts map (rewrite(_, s1)), rewrite(body, s1))
      }

    case F.LetP(name, CPS.Id, Seq(l: F.Literal), body) =>
      s.withFreshRegFor(name, body) { (r, s) =>
        R.LetP(r, CPS.Id, Seq(l), rewrite(body, s))
      }

    case F.LetP(name, prim, args, body) =>
      s.withFreshRegFor(name, body) { (r, s) =>
        s.withRegsContaining(args, tree) { (rArgs, s) =>
          R.LetP(r, prim, rArgs, rewrite(body, s))
        }
      }

    case appF @ F.AppF(fun: F.Name, retC, args) if s.isTailCall(appF) =>
      s.withRegsContaining(args, tree) { (rArgs, s) =>
        val dstRegs = funArgRegs(rArgs.size)
        s.rOrL(fun) match {
          case funReg: ASMRegister if dstRegs.contains(funReg) =>
            // funReg would be overwritten by the arguments, move it too
            val dstRegs1 = funArgRegs(rArgs.size + 1)
            s.withParallelCopy(dstRegs1, rArgs :+ funReg, tree)(
              R.AppF(dstRegs1.last, s.rOrL(retC), rArgs))
          case funRegOrLabel =>
            s.withParallelCopy(dstRegs, rArgs, tree)(
              R.AppF(funRegOrLabel, s.rOrL(retC), rArgs))
        }
      }

    case F.AppF(fun: F.Name, retC, args) =>
      s.withRegsContaining(args, tree) { (rArgs, s) =>
        R.AppF(s.rOrL(fun), s.rOrL(retC), rArgs)
      }

    // Invalid AppF, will fail during execution
    case F.AppF(fun: F.Literal, retC, args) =>
      s.withRegContaining(fun, tree) { (rFun, s) =>
        s.withRegsContaining(args, tree) { (rArgs, s) =>
          R.AppF(rFun, s.rOrL(retC), rArgs)
        }
      }

    case F.AppC(cont, args) =>
      s.withRegsContaining(args, tree) { (rArgs, s) =>
        val rOutC = s.cArgs.getOrElse(cont, rArgs)
        s.withParallelCopy(rOutC, rArgs, tree)(R.AppC(s.rOrL(cont), rOutC))
      }

    case F.If(cond, args, thenC, elseC) =>
      s.withRegsContaining(args, tree) { (rArgs, _) =>
        R.If(cond, rArgs, thenC, elseC) }

    case F.Halt(arg) =>
      s.withRegContaining(arg, tree) { (rArg, _) => R.Halt(rArg) }
  }

  private def rewrite(cnt: F.Cnt, s: State): R.Cnt =
    R.Cnt(cnt.name, s.cArgs(cnt.name), rewrite(cnt.body, s))

  private def funArgRegs(arity: Int): Seq[ASMRegister] =
    ASMRegister.locals.take(arity)

  private case class State(cLiveVars: Map[F.Name, Set[F.Name]] = Map.empty,
                           regs: Map[F.Name, ASMRegister] = Map.empty,
                           cArgs: Map[F.Name, Seq[ASMRegister]] = Map.empty) {
    def withAssignedReg(name: F.Name, reg: ASMRegister) =
      copy(regs = regs + (name -> reg))
    def withAssignedRegs(names: Seq[F.Name], regs: Seq[ASMRegister]) = {
      require(names.length == regs.length)
      copy(regs = this.regs ++ (names zip regs))
    }

    def withCntArgs(name: F.Name, args: Seq[ASMRegister]) =
      copy(cArgs = cArgs + (name -> args))

    def withContinuations[T](cnts: Seq[F.Cnt])(body: State => T): T = {
      val emptyCLiveVars = Map.from(cnts map { c => c.name -> Set[F.Name]() })
      val cLiveVars1 = fixedPoint(emptyCLiveVars) { cLiveVarsApprox =>
        val s1 = copy(cLiveVars = cLiveVars ++ cLiveVarsApprox)
        cnts.map(c => c.name -> (s1.liveVariables(c.body) -- c.args)).toMap
      }
      body(copy(cLiveVars = cLiveVars ++ cLiveVars1))
    }

    def withFreshRegFor[T](name: F.Name, cont: F.Body)
                       (body: (ASMRegister, State) => T): T =
      withFreshRegsFor(Seq(name), cont) { case (Seq(r), s) => body(r, s) }

    def withFreshRegsFor[T](names: Seq[F.Name], cont: F.Body)
                        (body: (Seq[ASMRegister], State) => T): T = {
      val live = liveVariables(cont) flatMap regs.get
      val free = ASMRegister.locals
        .filterNot(live)
        .take(names.length)
      assert(free.length == names.length,
             s"not enough local registers (${names.length} requested)")
      body(free, withAssignedRegs(names, free))
    }

    def withRegContaining(atom: F.Atom, cont: F.Body)
                         (body: (ASMRegister, State) => R.Body): R.Body =
      atom match {
        case name: F.Name =>
          (regs get name map (body(_, this))) getOrElse {
            withFreshRegFor(name, cont) { (r, s) =>
              R.LetP(r, CPS.Id, Seq(name), body(r, s)) }
          }
        case l: F.Literal =>
          body(ASMRegister.consts(l), this)
      }

    def withRegsContaining(atoms: Seq[F.Atom], cont: F.Body)
                          (body: (Seq[ASMRegister], State) => R.Body): R.Body =
      atoms match {
        case Seq() =>
          body(Seq(), this)
        case Seq(n, ns*) =>
          withRegContaining(n, cont) { (rN, s) =>
            withRegsContaining(ns, cont) { (rNs, s) => body(rN +: rNs, s) } }
      }

    def withParallelCopy
        (toS: Seq[ASMRegister], fromS: Seq[ASMRegister], cont: F.Body)
        (body: R.Body): R.Body = {
      type Move = (ASMRegister, ASMRegister)

      def splitMove(t: Seq[Move], d: ASMRegister)
          : Option[(Seq[Move], ASMRegister, Seq[Move])] =
        t span (_._1 != d) match {
          case (_, Seq())            => None
          case (pre, (_, r) +: post) => Some(pre, r, post)
        }

      def loop(toMove: Seq[Move], beingMoved: Seq[Move], moved: Seq[Move])
          : Seq[Move] = {
        (toMove, beingMoved, moved) match {
          case (Seq(), Seq(), m) =>
            m.reverse
          case ((s, d) +: tl, b @ Seq(), m) if s == d =>
            loop(tl, b, m)
          case (t +: ts, Seq(), m) =>
            loop(ts, Seq(t), m)
          case (t, (sd @ (_, d)) +: b, m) =>
            splitMove(t, d) match {
              case Some((t1, r, t2)) =>
                loop(t1 ++ t2, (d, r) +: sd +: b, m)
              case None =>
                b match {
                  case Seq() =>
                    loop(t, Seq(), sd +: m)
                  case _ if b.last._1 == d =>
                    val temp = Symbol.fresh("parMoveTmp")
                    withFreshRegFor(temp, cont) { (tmp, _) =>
                      loop(t, b.init :+ ((tmp, b.last._2)), sd +: (d, tmp) +: m)
                    }
                  case _ =>
                    loop(t, b, sd +: m)
                }
            }
        }
      }
      val moves = loop(fromS zip toS, Seq.empty, Seq.empty)
      moves.foldRight(body) { case ((s, d), b) => R.LetP(d, CPS.Id, Seq(s), b) }
    }

    def rOrL(name: F.Name): R.Name =
      regs.getOrElse(name, name)

    def isTailCall(appF: F.AppF): Boolean =
      regs.get(appF.retC).contains(ASMRegister.Link)

    def liveVariables(tree: F.Body): Set[F.Name] = tree match {
      case F.LetC(cnts, body) =>
        val s1 = copy(cLiveVars = cLiveVars ++ cnts.map(c => c.name -> Set()))
        s1.liveVariables(body) ++ cnts.flatMap(c => s1.liveVariables(c.body))
      case F.LetP(name, _, args, body) =>
        liveVariables(body) ++ liveVariables(args) - name
      case F.AppF(fun, retC, args) =>
        cLiveVars.getOrElse(retC, Set()) ++ liveVariables(fun +: args)
      case F.AppC(cont, args) =>
        cLiveVars.getOrElse(cont, Set()) ++ liveVariables(args)
      case F.If(_, args, thenC, elseC) =>
        cLiveVars(thenC) ++ cLiveVars(elseC) ++ liveVariables(args)
      case F.Halt(arg) =>
        liveVariables(Seq(arg))
    }

    def liveVariables(atoms: Seq[F.Atom]): Set[F.Name] =
      atoms.collect { case n: F.Name => n }.toSet

  }
}
