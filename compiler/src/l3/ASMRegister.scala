package l3

enum ASMRegister private (name: String) {
  override def toString: String = name

  case Link extends ASMRegister("Lk")
  case Local(index: Bits32) extends ASMRegister(s"R${index}")
  case Const(index: Bits32) extends ASMRegister(s"C${index}")
}

object ASMRegister {
  val locals = (0 until (7 * 32)).map(Local.apply)
  val consts = (0 until (1 * 32)).map(Const.apply)
  val C0 = consts(0)
}
