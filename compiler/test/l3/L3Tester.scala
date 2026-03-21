package l3

import java.io.{ ByteArrayOutputStream, PrintStream }
import java.nio.file.Path

import scala.util.Using.{resource => using}

import SymbolicCL3TreeModule.Tree

object L3Tester {
  def compile[T](backEnd: Tree => Either[String, T])
             (inFileNames: Seq[String]): Either[String, T] = {
    val basePath = Path.of(System.getProperty("user.dir"))
    Right(inFileNames)
      .flatMap(L3FileReader.readFilesExpandingModules(basePath, _))
      .flatMap(p => L3Parser.parse(p._1, p._2))
      .flatMap(CL3NameAnalyzer)
      .flatMap(backEnd)
  }

  def compileAndRun(backEnd: Tree => TerminalPhaseResult)
                   (inFileNames: Seq[String]): Either[String, String] = {
    def outputCapturingBackend(t: Tree): Either[String, String] = {
      val outBS = new ByteArrayOutputStream()
      using(new PrintStream(outBS)) { outPS =>
        val out0 = System.out
        try {
          System.setOut(outPS)
          backEnd(t)
            .flatMap((retCode, _) =>
              Either.cond(retCode == 0,
                          outBS.toString("UTF-8"),
                          s"non-zero return code (${retCode})"))
        } finally {
          System.setOut(out0)
        }
      }
    }

    compile(outputCapturingBackend)(inFileNames)
  }

  val backEnd1 = (
    CL3Interpreter
  )

  val backEnd2 = (
    CL3ToCPSTranslator
      andThen HighCPSInterpreter
  )
}
