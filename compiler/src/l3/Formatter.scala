package l3

import org.typelevel.paiges.Doc

/**
  * Utility methods for formatting.
  *
  * @author Michel Schinz <Michel.Schinz@epfl.ch>
  */

trait Formatter[-T] {
  def toDoc(value: T): Doc
}

object Formatter {
  def par(nest: Int, ds: Iterable[Doc]): Doc =
    (Doc.char('(') + Doc.intercalate(Doc.line, ds).nested(nest) + Doc.char(')'))
      .grouped
  def par(nest: Int, d1: Doc): Doc = par(nest, Seq(d1))
  def par(nest: Int, d1: Doc, d2: Doc): Doc = par(nest, Seq(d1, d2))

  def par(tag: String, nest: Int, d1: Doc, ds: Doc*): Doc =
    par(nest, (Doc.text(tag) space d1.aligned) +: ds)
}
