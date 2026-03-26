package l3

/**
  * A module for CPS trees.
  *
  * @author Michel Schinz <Michel.Schinz@epfl.ch>
  */

sealed trait CPSTreeModule {
  type Name
  type Literal
  type ValuePrimitive
  type TestPrimitive

  type Atom = Name | Literal

  type Body <: Tree
  type Program <: Tree

  enum Tree {
    case LetF(funs: Seq[Fun], body: Body)
    case LetC(cnts: Seq[Cnt], body: Body)
    case LetP(name: Name, prim: ValuePrimitive, args: Seq[Atom], body: Body)
    case AppF(fun: Atom, retC: Name, args: Seq[Atom])
    case AppC(cnt: Name, args: Seq[Atom])
    case If(cond: TestPrimitive, args: Seq[Atom], thenC: Name, elseC: Name)
    case Halt(arg: Atom)
  }
  export Tree.*

  case class Fun(name: Name, retC: Name, args: Seq[Name], body: Body)
  case class Cnt(name: Name, args: Seq[Name], body: Body)
}

trait SymbolicNames extends CPSTreeModule {
  type Name = Symbol
}

trait HighValues extends CPSTreeModule {
  type Literal = CL3Literal
  type ValuePrimitive = L3ValuePrimitive
  type TestPrimitive = L3TestPrimitive
}
trait LowValues extends CPSTreeModule {
  type Literal = Bits32
  type ValuePrimitive = CPSValuePrimitive
  type TestPrimitive = CPSTestPrimitive
}

trait NestedTree extends CPSTreeModule {
  type Body = Tree
  type Program = Tree
}
trait FlatTree extends CPSTreeModule {
  type Body = LetC | LetP | AppF | AppC | If | Halt
  type Program = LetF
}

/**
  * Module for "high-level" CPS trees: the full L3 literals and
  * primitives are available.
  */
object HighCPSTreeModule extends NestedTree with HighValues with SymbolicNames

/**
  * Module for "low-level" CPS trees: the only literal values are
  * integers, and the primitives work on integers and/or pointers to
  * heap-allocated blocks.
  */
object LowCPSTreeModule extends NestedTree with LowValues with SymbolicNames

/**
 * Module for hoisted CPS trees: program contains exactly one `LetF`,
 * at the root of the tree, defining all the functions of the program.
 */
object FlatCPSTreeModule extends FlatTree with LowValues with SymbolicNames
