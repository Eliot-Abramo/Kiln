package l3

import scala.collection.mutable.{Buffer, Map as MutableMap}

import FlatCPSTreeModule._

final class Statistics {
  private var funCount = 0
  private var cntCount = 0
  private val nodes = MutableMap[String, Int]()
  private val tPrims = MutableMap[String, Int]()
  private val vPrims = MutableMap[String, Int]()

  private def inc[K](m: MutableMap[K, Int], k: K): Unit =
    m.put(k, m.getOrElse(k, 0) + 1)

  def log(tree: Tree): Unit = {
    inc(nodes, tree.getClass.getSimpleName)

    tree match {
      case LetF(fs, _) =>
        funCount += fs.length
      case LetC(cs, _) =>
        cntCount += cs.length
      case LetP(_, p, _, _) =>
        inc(vPrims, p.toString)
      case If(p, _, _, _) =>
        inc(tPrims, p.toString)
      case _ =>
        // nothing to do
    }
  }

  override def toString: String = {
    val lines = Buffer.empty[String]

    for ((label, map) <- Seq(("Nodes", nodes),
                             ("Value primitives", vPrims),
                             ("Test primitives", tPrims))
         if map.nonEmpty) {
      lines += label
      lines += "=" * label.length
      for ((n, o) <- map.toSeq.sortBy((_, o) => -o))
        lines += f"${o}%,9d  ${n}%s"
      lines += ""
    }

    lines += f"    Functions defined: ${funCount}%,7d"
    lines += f"Continuations defined: ${cntCount}%,7d"

    lines.mkString("\n")
  }
}
