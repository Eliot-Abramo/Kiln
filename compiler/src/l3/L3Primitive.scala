package l3

/**
  * A class for L₃ primitives.
  *
  * @author Michel Schinz <Michel.Schinz@epfl.ch>
  */

//defines primitive operations and classifies them as either
//  value producing
//  test producing

sealed trait L3Primitive(val name: String, val arity: Int) {
  override def toString: String = name
}

//a value primitive is translated to LetP(name,prim,args,body)
//arity = number of arguments a primitive must take, mental check to validate
//use of primitives. i.e. IntAdd has a=2 -> (@ + a b), 3 arguments
//                        ByteRead has a=0 -> (@ byte-read) 1 argument
enum L3ValuePrimitive(n: String, a: Int) extends L3Primitive(n, a) {
  case BlockAlloc extends L3ValuePrimitive("block-alloc", 2)
  case BlockTag extends L3ValuePrimitive("block-tag", 1)
  case BlockLength extends L3ValuePrimitive("block-length", 1)
  case BlockGet extends L3ValuePrimitive("block-get", 2)
  case BlockSet extends L3ValuePrimitive("block-set!", 3)

  case IntAdd extends L3ValuePrimitive("+", 2)
  case IntSub extends L3ValuePrimitive("-", 2)
  case IntMul extends L3ValuePrimitive("*", 2)
  case IntDiv extends L3ValuePrimitive("/", 2)
  case IntMod extends L3ValuePrimitive("%", 2)

  case IntShiftLeft extends L3ValuePrimitive("shift-left", 2)
  case IntShiftRight extends L3ValuePrimitive("shift-right", 2)
  case IntBitwiseAnd extends L3ValuePrimitive("and", 2)
  case IntBitwiseOr extends L3ValuePrimitive("or", 2)
  case IntBitwiseXOr extends L3ValuePrimitive("xor", 2)

  case ByteRead extends L3ValuePrimitive("byte-read", 0)
  case ByteWrite extends L3ValuePrimitive("byte-write", 1)

  case IntToChar extends L3ValuePrimitive("int->char", 1)
  case CharToInt extends L3ValuePrimitive("char->int", 1)

  //only exist in CPS-level evaluation, lets you bind an atom to a fresh 
  //name: LetP(x, Id, Seq(atom), body)
  case Id extends L3ValuePrimitive("id", 1)
}

//a test primitive is used by CPS If(cond,args,thenC, elseC)
enum L3TestPrimitive(n: String, a: Int) extends L3Primitive(n, a) {
  case BlockP extends L3TestPrimitive("block?", 1)
  case IntP extends L3TestPrimitive("int?", 1)
  case CharP extends L3TestPrimitive("char?", 1)
  case BoolP extends L3TestPrimitive("bool?", 1)
  case UnitP extends L3TestPrimitive("unit?", 1)

  case IntLt extends L3TestPrimitive("<", 2)
  case IntLe extends L3TestPrimitive("<=", 2)

  case Eq extends L3TestPrimitive("=", 2)
}

object L3Primitive {
  export L3ValuePrimitive.*, L3TestPrimitive.*

  def isDefinedAt(name: String): Boolean =
    byName.isDefinedAt(name)

  def isDefinedAt(name: String, arity: Int): Boolean =
    byName.get(name).exists(_.arity == arity)

  def apply(name: String): L3Primitive =
      byName(name)

  private val byName: Map[String, L3Primitive] =
    (L3ValuePrimitive.values ++ L3TestPrimitive.values)
      .map(p => p.name -> p)
      .toMap
}
