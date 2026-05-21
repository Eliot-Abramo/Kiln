package l3

import collection.mutable.{ Map => MutableMap }
import scala.annotation.tailrec

import CPSValuePrimitive._
import l3.RegisterCPSTreeModule as R
import RegisterCPSTreeModule.{ Program => _, _ }
import LabeledASMInstructionModule._

/**
  * An ASM code generator for CPS/L₃.
  *
  * @author Michel Schinz <Michel.Schinz@epfl.ch>
  */

object CPSToASMTranslator extends (R.Program => Program) {
  def apply(prog: R.Program): Program = {
    given Map[Symbol, ASMRegister] = Map()

    val funs = prog.funs map { case Fun(nameA, _, _, body) =>
      val name = nameA.asInstanceOf[Symbol]
      name -> labeled(name, linearize(body, prelude(body)))
    }

    linearize(prog.body, prelude(prog.body)) ++ glue(funs)
  }

  private def glue(funs: Seq[(Symbol, Program)]): Program = {
    @tailrec
    def loop(w: Seq[Symbol], fs: Map[Symbol, Program])
        : Map[Symbol, Program] = w match {
      case n +: ns =>
        fs(n).last match {
          case LabeledInstruction(l, JUMP_D(m)) if m != n && fs.contains(m) =>
            val nm = fs(n).init ++ labeled(l, fs(m))
            loop(n +: ns.diff(Seq(m)), fs - m + (n -> nm))
          case _ =>
            loop(ns, fs)
        }
      case Seq() =>
        fs
    }

    val gluedFuns = loop(funs.map(_._1), funs.toMap)
    funs.flatMap(p => gluedFuns.getOrElse(p._1, Seq()))
  }

  private def prelude(body: Body): Program = {
    def usedRegs(body: Body): Set[ASMRegister.Local] = {
      def regIn(a: Atom): Set[ASMRegister.Local] = a match {
        case r: ASMRegister.Local => Set(r)
        case _ => Set.empty
      }

      def regsIn(aa: Seq[Atom]): Set[ASMRegister.Local] =
        aa.flatMap(regIn).toSet

      (body: @unchecked) match {
        case LetC(cnts, body) =>
          cnts.foldLeft(usedRegs(body)) { (rs, c) =>
            rs | regsIn(c.args) | usedRegs(c.body)
          }
        case LetP(_, ByteWrite | BlockSet, args, body) =>
          regsIn(args) | usedRegs(body)
        case LetP(r: ASMRegister.Local, _, args, body) =>
          Set(r) | regsIn(args) | usedRegs(body)
        case AppF(f, retC, args) =>
          regIn(f) | regIn(retC) | regsIn(args)
        case AppC(c, args) =>
          regIn(c) | regsIn(args)
        case If(_, args, tc, ec) =>
          regsIn(args) | regIn(tc) | regIn(ec)
        case Halt(arg) =>
          regIn(arg)
      }
    }

    usedRegs(body)
      .map(_.index)
      .maxOption
      .map(maxR => nl(FRAME(maxR + 1)))
      .toSeq
  }

  private val conts = MutableMap[Symbol, Body]()

  private def linearize(body: Body, acc: Program = Seq())
                       (using retContArg: Map[Symbol, ASMRegister]): Program = {
    import l3.{ ASMRegister => Reg }

    def contOrJump(l: Symbol): Program = conts.remove(l)
      .map(b => labeled(l, linearize(b)))
      .getOrElse(Seq(nl(JUMP_D(l))))

    def condJump(p: CPSTestPrimitive,
                 a: ASMRegister,
                 b: ASMRegister,
                 w: Boolean,
                 c: Symbol) = {
      import CPSTestPrimitive._

      (p, w) match {
        case (Lt, true)  => nl(JLT(a, b, c))
        case (Lt, false) => nl(JLE(b, a, c))
        case (Le, true)  => nl(JLE(a, b, c))
        case (Le, false) => nl(JLT(b, a, c))
        case (Eq, true)  => nl(JEQ(a, b, c))
        case (Eq, false) => nl(JNE(a, b, c))
      }
    }

    def pushArgs(args: Seq[Atom]): Program =
      if (args.isEmpty)
        Seq(nl(ARGS(ASMRegister.C0, ASMRegister.C0, ASMRegister.C0)))
      else args.grouped(3).toSeq.map {
        case Seq(r1: Reg, r2: Reg, r3: Reg) => nl(ARGS(r1, r2, r3))
        case Seq(r1: Reg, r2: Reg) => nl(ARGS(r1, r2, ASMRegister.C0))
        case Seq(r1: Reg) => nl(ARGS(r1, ASMRegister.C0, ASMRegister.C0))
      }

    (body: @unchecked) match {
      case LetC(cnts, body) =>
        conts ++= cnts map { case Cnt(name, _, body) =>
          name.asInstanceOf[Symbol] -> body
        }
        val retContArg1 = cnts.foldLeft(retContArg) {
          case (rca, Cnt(name: Symbol, Seq(a: Reg), _)) => rca + (name -> a)
          case (rca, _) => rca
        }
        linearize(body, acc)(using retContArg1)

      case LetP(a: Reg, Add, Seq(b: Reg, c: Reg), body) =>
        linearize(body, acc :+ nl(ADD(a, b, c)))
      case LetP(a: Reg, Sub, Seq(b: Reg, c: Reg), body) =>
        linearize(body, acc :+ nl(SUB(a, b, c)))
      case LetP(a: Reg, Mul, Seq(b: Reg, c: Reg), body) =>
        linearize(body, acc :+ nl(MUL(a, b, c)))
      case LetP(a: Reg, Div, Seq(b: Reg, c: Reg), body) =>
        linearize(body, acc :+ nl(DIV(a, b, c)))
      case LetP(a: Reg, Mod, Seq(b: Reg, c: Reg), body) =>
        linearize(body, acc :+ nl(MOD(a, b, c)))

      case LetP(a: Reg, ShiftLeft, Seq(b: Reg, c: Reg), body) =>
        linearize(body, acc :+ nl(LSL(a, b, c)))
      case LetP(a: Reg, ShiftRight, Seq(b: Reg, c: Reg), body) =>
        linearize(body, acc :+ nl(LSR(a, b, c)))
      case LetP(a: Reg, And, Seq(b: Reg, c: Reg), body) =>
        linearize(body, acc :+ nl(AND(a, b, c)))
      case LetP(a: Reg, Or, Seq(b: Reg, c: Reg), body) =>
        linearize(body, acc :+ nl(OR(a, b, c)))
      case LetP(a: Reg, XOr, Seq(b: Reg, c: Reg), body) =>
        linearize(body, acc :+ nl(XOR(a, b, c)))

      case LetP(a: Reg, ByteRead, Seq(), body) =>
        linearize(body, acc :+ nl(BREA(a)))
      case LetP(_, ByteWrite, Seq(a: Reg), body) =>
        linearize(body, acc :+ nl(BWRI(a)))

      case LetP(a: Reg, BlockAlloc, Seq(b: Reg, c: Reg), body) =>
        linearize(body, acc :+ nl(BALO(a, b, c)))
      case LetP(a: Reg, BlockTag, Seq(b: Reg), body) =>
        linearize(body, acc :+ nl(BTAG(a, b)))
      case LetP(a: Reg, BlockLength, Seq(b: Reg), body) =>
        linearize(body, acc :+ nl(BSIZ(a, b)))
      case LetP(a: Reg, BlockGet, Seq(b: Reg, c: Reg), body) =>
        linearize(body, acc :+ nl(BGET(a, b, c)))
      case LetP(_, BlockSet, Seq(a: Reg, b: Reg, c: Reg), body) =>
        linearize(body, acc :+ nl(BSET(c, a, b)))

      case LetP(a: Reg, Id, Seq(v: Literal), body) if fitsInNSignedBits(19)(v) =>
        linearize(body, acc :+ nl(LDLO(a, v)))
      case LetP(a: Reg, Id, Seq(v: Literal), body) =>
        val lsb16: Int = v & 0xFFFF
        val msb16: Int = v >>> 16
        linearize(body, acc :+ nl(LDLO(a, lsb16)) :+ nl(LDHI(a, msb16)))

      case LetP(a: Reg, Id, Seq(b: Reg), body) if a == b =>
        linearize(body, acc)
      case LetP(a: Reg, Id, Seq(b: Reg), body) =>
        linearize(body, acc :+ nl(MOVE(a, b)))
      case LetP(a: Reg, Id, Seq(l: Symbol), body) =>
        linearize(body, acc :+ nl(LDLO(a, l)))

      case AppF(fun: Reg, rc: Symbol, args) =>
        val r = retContArg(rc)
        (acc ++ pushArgs(args) :+ nl(CALL_I(r, fun))) ++ contOrJump(rc)
      case AppF(fun: Symbol, rc: Symbol, args) =>
        val r = retContArg(rc)
        (acc ++ pushArgs(args) :+ nl(CALL_D(r, fun))) ++ contOrJump(rc)
      case AppF(fun: Reg, ASMRegister.Link, _) =>
        acc :+ nl(JUMP_I(fun))
      case AppF(fun: Symbol, ASMRegister.Link, _) =>
        acc :+ nl(JUMP_D(fun))

      case AppC(c: Symbol, _) =>
        acc ++ contOrJump(c)
      case AppC(ASMRegister.Link, Seq(r: Reg)) =>
        acc :+ nl(RET(r))

      case If(p, Seq(a: Reg, b: Reg), thenC: Symbol, elseC: Symbol) =>
        (conts remove thenC, conts remove elseC) match {
          case (Some(thenT), Some(elseT)) =>
            val thenP = labeled(thenC, linearize(thenT))
            val elseP = labeled(elseC, linearize(elseT))
            (acc :+ condJump(p, a, b, false, elseC)) ++ thenP ++ elseP
          case (Some(thenT), None) =>
            val thenP = labeled(thenC, linearize(thenT))
            (acc :+ condJump(p, a, b, false, elseC)) ++ thenP
          case (None, Some(elseT)) =>
            val elseP = labeled(elseC, linearize(elseT))
            (acc :+ condJump(p, a, b, true, thenC)) ++ elseP
          case (None, None) =>
            acc :+ condJump(p, a, b, true, thenC) :+ nl(JUMP_D(elseC))
        }

      case Halt(arg: Reg) =>
        acc :+ nl(HALT(arg))
    }
  }
}
