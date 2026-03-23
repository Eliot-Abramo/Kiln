package l3

import org.typelevel.paiges.Doc

//pretty printer for CL3 trees
//useful to debug and print intermediate trees
class CL3TreeFormatter[T <: CL3TreeModule](protected val treeModule: T) {
  import Formatter.par, treeModule._

  def toDoc(tree: treeModule.Tree): Doc = tree match {
    //let
    case Let(bdgs, body) =>
      val bdgsDoc =
        par(1, bdgs map { (n, v) => par(1, Doc.str(n), toDoc(v)) })
      par("let", 2, bdgsDoc, toDoc(body))

    //letrec
    case LetRec(funs, body) =>
      def funToDoc(fun: Fun): Doc =
        Doc.str(fun.name)
           / par("fun", 2, par(1, fun.args map Doc.str), toDoc(fun.body))
      val funsDoc = par(1, funs map { f => par(1, funToDoc(f)) })
      par("letrec", 2, funsDoc, toDoc(body))

    //if
    case If(c, t, e) =>
      par("if", 2, toDoc(c), toDoc(t), toDoc(e))

    //app
    case App(fun, args) =>
      par(1, (fun +: args) map toDoc)

    //halt
    case Halt(arg) =>
      par("halt", 2, toDoc(arg))

    //prim
    case Prim(prim, args) =>
      par(1, Doc.text(s"@$prim") +: (args map toDoc))

    //ident
    case Ident(name) =>
      Doc.str(name)

    //lit
    case Lit(l) =>
      Doc.str(l)
  }
}

given NominalCL3TreeFormatter: Formatter[NominalCL3TreeModule.Tree] =
  new CL3TreeFormatter(NominalCL3TreeModule)
    with Formatter[NominalCL3TreeModule.Tree]

given SymbolicCL3TreeFormatter: Formatter[SymbolicCL3TreeModule.Tree] =
  new CL3TreeFormatter(SymbolicCL3TreeModule)
    with Formatter[SymbolicCL3TreeModule.Tree]
