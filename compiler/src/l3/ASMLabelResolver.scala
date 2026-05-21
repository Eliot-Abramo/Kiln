package l3

import l3.{ LabeledASMInstructionModule => L }
import l3.{ PCRelativeASMInstructionModule => R }

/**
  * Label resolution for the ASM language. Translates a program in
  * which addresses are represented as symbolic labels to one where
  * they are represented as PC-relative (or absolute, in some cases)
  * addresses.
  *
  * @author Michel Schinz <Michel.Schinz@epfl.ch>
  */

object ASMLabelResolver extends (L.Program => R.Program) {
  def apply(labeledProgram: L.Program): R.Program =
    resolve(fixedPoint(labeledProgram)(expand))

  private def expand(program: L.Program): L.Program = {
    val indexedProgram = program.zipWithIndex
    val labelAddr = labelMap(indexedProgram)

    indexedProgram.foldRight(Seq(): L.Program) {
      case ((L.LabeledInstruction(labels, instruction), addr), expanded) =>

        def labelFits(l: L.Label) = fitsInNSignedBits(11)(labelAddr(l) - addr)

        def expandJump(jumpI: L.Label => L.Instruction, l: Symbol) = {
          val l2 = Symbol.fresh("longJump")
          L.nl(jumpI(l2)) +: L.nl(L.JUMP_D(l)) +: L.labeled(l2, expanded)
        }

        L.labeled(labels, instruction match {
          case L.JLT(a, b, l) if !labelFits(l) => expandJump(L.JLE(b, a, _), l)
          case L.JLE(a, b, l) if !labelFits(l) => expandJump(L.JLT(b, a, _), l)
          case L.JEQ(a, b, l) if !labelFits(l) => expandJump(L.JNE(a, b, _), l)
          case L.JNE(a, b, l) if !labelFits(l) => expandJump(L.JEQ(a, b, _), l)
          // TODO: LDLO
          case other => L.nl(other) +: expanded
        })
    }
  }

  private def resolve(program: L.Program): R.Program = {
    val indexedProgram = program.zipWithIndex
    val labelAddr = labelMap(indexedProgram)

    for ((labeledInstr, addr) <- indexedProgram) yield {
      def delta(l: L.Label): Bits32 = labelAddr(l) - addr
      labeledInstr.instruction match {
        case L.ADD(a, b, c)           => R.ADD(a, b, c)
        case L.SUB(a, b, c)           => R.SUB(a, b, c)
        case L.MUL(a, b, c)           => R.MUL(a, b, c)
        case L.DIV(a, b, c)           => R.DIV(a, b, c)
        case L.MOD(a, b, c)           => R.MOD(a, b, c)
        case L.LSL(a, b, c)           => R.LSL(a, b, c)
        case L.LSR(a, b, c)           => R.LSR(a, b, c)
        case L.AND(a, b, c)           => R.AND(a, b, c)
        case L.OR(a, b, c)            => R.OR(a, b, c)
        case L.XOR(a, b, c)           => R.XOR(a, b, c)
        case L.JLT(a, b, l)           => R.JLT(a, b, delta(l))
        case L.JLE(a, b, l)           => R.JLE(a, b, delta(l))
        case L.JEQ(a, b, l)           => R.JEQ(a, b, delta(l))
        case L.JNE(a, b, l)           => R.JNE(a, b, delta(l))
        case L.JUMP_I(a)              => R.JUMP_I(a)
        case L.JUMP_D(l)              => R.JUMP_D(delta(l))
        case L.CALL_I(a, b)           => R.CALL_I(a, b)
        case L.CALL_D(a, l)           => R.CALL_D(a, delta(l))
        case L.RET(a)                 => R.RET(a)
        case L.HALT(a)                => R.HALT(a)
        case L.LDLO(a, s: Bits32)     => R.LDLO(a, s)
        case L.LDLO(a, l: L.Label)    => R.LDLO(a, labelAddr(l) << 2)
        case L.LDHI(a, u)             => R.LDHI(a, u)
        case L.MOVE(a, b)             => R.MOVE(a, b)
        case L.ARGS(a, b, c)          => R.ARGS(a, b, c)
        case L.FRAME(s)               => R.FRAME(s)
        case L.BALO(a, b, t)          => R.BALO(a, b, t)
        case L.BSIZ(a, b)             => R.BSIZ(a, b)
        case L.BTAG(a, b)             => R.BTAG(a, b)
        case L.BGET(a, b, c)          => R.BGET(a, b, c)
        case L.BSET(a, b, c)          => R.BSET(a, b, c)
        case L.BREA(a)                => R.BREA(a)
        case L.BWRI(a)                => R.BWRI(a)
      }
    }
  }

  private def labelMap(program: Seq[(L.LabeledInstruction, Int)])
      : Map[L.Label, Bits32] =
    (for ((L.LabeledInstruction(labels, _), a) <- program; l <- labels)
     yield (l, a)).toMap
}
