package l3

import java.io.PrintWriter
import java.nio.file.Path
import scala.annotation.unused

import l3.SymbolicCL3TreeModule.Tree

object Main {
  def main(args: Array[String]): Unit = {
    // val stats = new Statistics()
    val backEnd: Tree => TerminalPhaseResult = (
      // CL3Interpreter
      CL3ToCPSTranslator
        // `andThen` treePrinter("---------- After translation to CPS")
        `andThen` HighCPSOptimizer
        // `andThen` treePrinter("---------- After high optimization")
        // `andThen` HighCPSInterpreter
        `andThen` CPSValueRepresenter
        // `andThen` treePrinter("---------- After value representation")
        // `andThen` LowCPSInterpreter
        `andThen` CPSHoister
        // `andThen` treePrinter("---------- After hoisting")
        `andThen` FlatCPSOptimizer
        // `andThen` (new FlatCPSInterpreter(stats.log))
        // `andThen` treePrinter("---------- After low optimization")
        // `andThen` FlatCPSInterpreter
        `andThen` CPSConstantNamer
        // `andThen` treePrinter("---------- After constant naming")
        `andThen` CPSRegisterAllocator
        // `andThen` treePrinter("---------- After register allocation")
        `andThen` CPSToASMTranslator
        // `andThen` seqPrinter("---------- After translation to assembly")
        `andThen` ASMLabelResolver
        // `andThen` seqPrinter("---------- After label resolution")
        // `andThen` ASMInterpreter
        `andThen` ASMFileWriter(Option(System.getProperty("l3.out-asm-file"))
                                .getOrElse("out.l3a"))
    )

    val basePath = Path.of(System.getProperty("user.dir"))
    Either.cond(! args.isEmpty, args.toIndexedSeq, "no input file given")
      .flatMap(L3FileReader.readFilesExpandingModules(basePath, _))
      .flatMap(p => L3Parser.parse(p._1, p._2))
      .flatMap(CL3NameAnalyzer)
      .flatMap(backEnd) match {
        case Right((retCode, maybeMsg)) =>
          maybeMsg foreach println
          // println(stats)
          sys.exit(retCode)
        case Left(errMsg) =>
          println(s"Error: $errMsg")
          sys.exit(1)
      }
  }

  private lazy val outPrintWriter = new PrintWriter(System.out, true)

  @unused
  private def treeChecker[T](using c: Checker[T]) =
    passThrough(c)

  @unused
  private def treePrinter[T](msg: String)(using f: Formatter[T]): T => T =
    passThrough { tree =>
      outPrintWriter.println(msg)
      f.toDoc(tree).writeTo(80, outPrintWriter)
      outPrintWriter.println()
    }

  @unused
  private def seqPrinter[T](msg: String): Seq[T] => Seq[T] =
    passThrough { program =>
      outPrintWriter.println(msg)
      program foreach outPrintWriter.println
    }

  private def passThrough[T](f: T => Unit): T => T = { (t: T) => f(t); t }
}
