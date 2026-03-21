package l3

import java.io.IOException
import java.nio.file.Path
import java.nio.file.Files.newBufferedReader

import scala.util.Using.{resource => using}
import scala.collection.mutable.ArrayBuffer

/**
  * File reading for L₃ (both modules and source files).
  *
  * @author Michel Schinz <Michel.Schinz@epfl.ch>
  */

object L3FileReader {
  def readFilesExpandingModules(base: Path, pathNames: Seq[String])
      : Either[String, (String, Int => Position)] =
    try {
      Right(readFiles(base, expandModules(base, pathNames)))
    } catch {
      case e: IOException => Left(e.getMessage)
    }

  private def expandModules(base: Path, pathNames: Seq[String]): Seq[Path] = {
    def readModule(modulePath: Path): Seq[String] = {
      using(newBufferedReader(modulePath)) { moduleReader =>
        Iterator.continually(moduleReader.readLine)
          .takeWhile (_ != null)
          .map (_.trim)
          .filterNot { s => s.startsWith(";") || s.isEmpty }
          .toList
      }
    }

    def expand(base: Path, pathNames: Seq[String]): Seq[Path] = {
      val basePath = base.toAbsolutePath.normalize
      pathNames flatMap { pn =>
        val p = basePath.resolve(pn).normalize
        if (p.getFileName.toString.endsWith(".l3m"))
          expandModules(p.getParent, readModule(p))
        else
          Seq(p)
      }
    }
    expand(base, pathNames).distinct
  }

  private def readFiles(basePath: Path,
                        paths: Seq[Path]): (String, Int=>Position) = {
    def indexToPosition(indices: Array[Int],
                        fileLines: Int => Option[(String, Int)])
                       (index: Int): Position = {
      val p = {
        val p0 = java.util.Arrays.binarySearch(indices, index)
        // FIXME: use code-points count to get column number, not char count!
        if (p0 < 0) (-p0 - 2) else p0
      }
      fileLines(p)
        .map { (f, l) => new FilePosition(f, l, index - indices(p)) }
        .getOrElse(UnknownPosition)
    }

    val progB = new StringBuilder()
    val indicesB = ArrayBuffer.empty[Int]
    val fileLinesB = ArrayBuffer.empty[(String, Int)]
    for (path <- paths) {
      val relPath = basePath.relativize(path)
      using(newBufferedReader(path)) { fileReader =>
        Iterator.continually(fileReader.readLine)
          .takeWhile(_ != null)
          .zipWithIndex
          .foreach { (line, lineIndex) =>
            val index = progB.size
            progB ++= line; progB += '\n'
            indicesB += index
            fileLinesB += ((relPath.toString, lineIndex + 1))
        }
      }
    }
    (progB.result(), indexToPosition(indicesB.toArray, fileLinesB.toArray.lift))
  }
}
