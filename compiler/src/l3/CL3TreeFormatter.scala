package l3

import org.typelevel.paiges.Doc

class CL3TreeFormatter[T <: CL3TreeModule](protected val treeModule: T) {
  import Formatter.par, treeModule._

  def toDoc(tree: treeModule.Tree): Doc = tree match {
    case Let(bdgs, body) =>
      val bdgsDoc =
        par(1, bdgs map { (n, v) => par(1, Doc.str(n), toDoc(v)) })
      par("let", 2, bdgsDoc, toDoc(body))
    case LetRec(funs, body) =>
      def funToDoc(fun: Fun): Doc =
        Doc.str(fun.name)
           / par("fun", 2, par(1, fun.args map Doc.str), toDoc(fun.body))
      val funsDoc = par(1, funs map { f => par(1, funToDoc(f)) })
      par("letrec", 2, funsDoc, toDoc(body))
    case If(c, t, e) =>
      par("if", 2, toDoc(c), toDoc(t), toDoc(e))
    case App(fun, args) =>
      par(1, (fun +: args) map toDoc)
    case Halt(arg) =>
      par("halt", 2, toDoc(arg))
    case Prim(prim, args) =>
      par(1, Doc.text(s"@$prim") +: (args map toDoc))
    case Ident(name) =>
      Doc.str(name)
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
