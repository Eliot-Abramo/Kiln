package l3

/**
  * A class for value-producing primitives.
  *
  * @author Michel Schinz <Michel.Schinz@epfl.ch>
  */

enum CPSValuePrimitive(name: String) {
  override def toString: String = name

  case Add extends CPSValuePrimitive("+")
  case Sub extends CPSValuePrimitive("-")
  case Mul extends CPSValuePrimitive("*")
  case Div extends CPSValuePrimitive("/")
  case Mod extends CPSValuePrimitive("%")

  case ShiftLeft extends CPSValuePrimitive("shift-left")
  case ShiftRight extends CPSValuePrimitive("shift-right")
  case And extends CPSValuePrimitive("and")
  case Or extends CPSValuePrimitive("or")
  case XOr extends CPSValuePrimitive("xor")

  case ByteRead extends CPSValuePrimitive("byte-read")
  case ByteWrite extends CPSValuePrimitive("byte-write")

  case BlockAlloc extends CPSValuePrimitive("block-alloc")
  case BlockTag extends CPSValuePrimitive("block-tag")
  case BlockLength extends CPSValuePrimitive("block-length")
  case BlockGet extends CPSValuePrimitive("block-get")
  case BlockSet extends CPSValuePrimitive("block-set!")

  case Id extends CPSValuePrimitive("id")
}

/**
  * A class for testing primitives.
  *
  * @author Michel Schinz <Michel.Schinz@epfl.ch>
  */

enum CPSTestPrimitive(name: String) {
  override def toString: String = name

  case Lt extends CPSTestPrimitive("<")
  case Le extends CPSTestPrimitive("<=")
  case Eq extends CPSTestPrimitive("=")
}
