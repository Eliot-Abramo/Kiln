package l3

import org.typelevel.paiges.Doc

class CPSTreeFormatter[T <: CPSTreeModule](protected val treeModule: T) {
  import Formatter.par, treeModule._

  def toDoc(tree: Tree): Doc = {
    def pullLets(tree: Tree): (Seq[(Name, Doc)], Doc) = tree match {
      case LetF(funs, body) =>
        val (bdgs, bodyDoc) = pullLets(body)
        (funs.map(f => (f.name, toDoc(f))) ++ bdgs, bodyDoc)
      case LetC(cnts, body) =>
        val (bdgs, bodyDoc) = pullLets(body)
        (cnts.map(c => (c.name, toDoc(c))) ++ bdgs, bodyDoc)
      case LetP(name, prim, args, body) =>
        val (bdgs, bodyDoc) = pullLets(body)
        val pDoc = par(1, Doc.text(s"@$prim") +: args.map(Doc.str))
        ((name, pDoc) +: bdgs, bodyDoc)
      case other =>
        (Seq(), toDoc(other))
    }

    tree match {
      case _: (LetF | LetC | LetP) =>
        val (bdgs, bodyDoc) = pullLets(tree)
        val tag = if (bdgs.length > 1) "let*" else "let"
        val bdgsDoc = par(1, bdgs.map(b => par(1, Doc.str(b._1), b._2)))
        par(tag, 2, bdgsDoc, bodyDoc)
      case AppF(fun, retC, args) =>
        par(1, (fun +: retC +: args).map(Doc.str))
      case AppC(cont, args) =>
        par(1, (cont +: args).map(Doc.str))
      case If(p, args, thenC, elseC) =>
        val pDoc = par(1, Doc.text(s"@$p") +: args.map(Doc.str))
        par("if", 2, pDoc, Doc.str(thenC), Doc.str(elseC))
      case Halt(arg) =>
        par("halt", 2, Doc.str(arg))
    }
  }

  def toDoc(cnt: Cnt): Doc =
    par("cnt", 2, par(1, cnt.args.map(Doc.str)), toDoc(cnt.body))

  def toDoc(fun: Fun): Doc =
    par("fun", 2, par(1, (fun.retC +: fun.args).map(Doc.str)), toDoc(fun.body))
}

given HighCPSTreeFormatter: Formatter[HighCPSTreeModule.Program] =
  new CPSTreeFormatter(HighCPSTreeModule)
    with Formatter[HighCPSTreeModule.Tree]
