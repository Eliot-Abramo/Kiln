package l3

/**
  * A module for ASM instructions.
  *
  * @author Michel Schinz <Michel.Schinz@epfl.ch>
  */

trait ASMInstructionModule {
  type Label

  enum Instruction {
    case ADD(a: ASMRegister, b: ASMRegister, c: ASMRegister)
    case SUB(a: ASMRegister, b: ASMRegister, c: ASMRegister)
    case MUL(a: ASMRegister, b: ASMRegister, c: ASMRegister)
    case DIV(a: ASMRegister, b: ASMRegister, c: ASMRegister)
    case MOD(a: ASMRegister, b: ASMRegister, c: ASMRegister)

    case LSL(a: ASMRegister, b: ASMRegister, c: ASMRegister)
    case LSR(a: ASMRegister, b: ASMRegister, c: ASMRegister)
    case AND(a: ASMRegister, b: ASMRegister, c: ASMRegister)
    case OR(a: ASMRegister, b: ASMRegister, c: ASMRegister)
    case XOR(a: ASMRegister, b: ASMRegister, c: ASMRegister)

    case JLT(a: ASMRegister, b: ASMRegister, d: Label)
    case JLE(a: ASMRegister, b: ASMRegister, d: Label)
    case JEQ(a: ASMRegister, b: ASMRegister, d: Label)
    case JNE(a: ASMRegister, b: ASMRegister, d: Label)
    case JUMP_I(a: ASMRegister)
    case JUMP_D(d: Label)

    case CALL_I(a: ASMRegister, b: ASMRegister)
    case CALL_D(a: ASMRegister, d: Label)
    case RET(r: ASMRegister)
    case HALT(r: ASMRegister)

    case LDLO(a: ASMRegister, s: Bits32 | Label)
    case LDHI(a: ASMRegister, u: Bits32)
    case MOVE(a: ASMRegister, b: ASMRegister)
    case ARGS(a: ASMRegister, b: ASMRegister, c: ASMRegister)
    case FRAME(s: Bits32)

    case BALO(a: ASMRegister, b: ASMRegister, c: ASMRegister)
    case BSIZ(a: ASMRegister, b: ASMRegister)
    case BTAG(a: ASMRegister, b: ASMRegister)
    case BGET(a: ASMRegister, b: ASMRegister, c: ASMRegister)
    case BSET(a: ASMRegister, b: ASMRegister, c: ASMRegister)

    case BREA(a: ASMRegister)
    case BWRI(a: ASMRegister)
  }
  export Instruction.*

  type Program
}

/**
 * A module for ASM instructions labeled explicitly by a symbol.
 */
object LabeledASMInstructionModule extends ASMInstructionModule {
  type Label = Symbol

  sealed case class LabeledInstruction(labels: Set[Label],
                                       instruction: Instruction) {
    override def toString: String =
      labels.map(l => s"$l:\n").mkString + "        " + instruction
  }

  def nl(i: Instruction): LabeledInstruction =
    LabeledInstruction(Set.empty, i)
  def labeled(labels: Set[Label], code: Program): Program =
    code match {
      case Seq(LabeledInstruction(labels1, i1), rest*) =>
        LabeledInstruction(labels1 ++ labels, i1) +: rest
    }
  def labeled(label: Label, code: Program): Program =
    labeled(Set(label), code)

  type Program = Seq[LabeledInstruction]
}

/**
  * A module for ASM instructions labeled implicitly by their
  * position.
  */
object PCRelativeASMInstructionModule extends ASMInstructionModule {
  type Label = Bits32
  type Program = Seq[Instruction]
}
