package l3

/**
  * A module for CL₃ trees.
  *
  * @author Michel Schinz <Michel.Schinz@epfl.ch>
  */

trait CL3TreeModule {
  type Name
  type Primitive

  enum Tree(using val pos: Position) {
    case Let(bindings: Seq[(Name, Tree)], body: Tree) (using Position)
    case LetRec(functions: Seq[Fun], body: Tree)      (using Position)
    case If(cond: Tree, thenE: Tree, elseE: Tree)     (using Position)
    case App(fun: Tree, args: Seq[Tree])              (using Position)
    case Prim(prim: Primitive, args: Seq[Tree])       (using Position)
    case Halt(arg: Tree)                              (using Position)
    case Ident(name: Name)                            (using Position)
    case Lit(value: CL3Literal)                       (using Position)
  }
  export Tree.*

  case class Fun(name: Name, args: Seq[Name], body: Tree)
                (using val pos: Position)
}

/**
  * Module for trees after parsing: names and primitives are
  * represented as strings.
  */
object NominalCL3TreeModule extends CL3TreeModule {
  type Name = String
  type Primitive = String
}

/**
  * Module for trees after name analysis: names are represented as
  * symbols (globally-unique names) and primitives as objects.
  */
object SymbolicCL3TreeModule extends CL3TreeModule {
  type Name = Symbol
  type Primitive = L3Primitive
}
