package l3

/**
  * Literal values for the CL₃ language.
  *
  * @author Michel Schinz <Michel.Schinz@epfl.ch>
  */

enum CL3Literal {
  override def toString: String = this match {
    case IntLit(i) => i.toString
    case CharLit(c) => "'"+ new String(Character.toChars(c)) +"'"
    case BooleanLit(v) => if (v) "#t" else "#f"
    case UnitLit => "#u"
  }

  case IntLit(value: L3Int)
  case CharLit(value: L3Char)
  case BooleanLit(value: Boolean)
  case UnitLit
}
