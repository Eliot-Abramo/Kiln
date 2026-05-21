package l3

import utest._

import SymbolicCL3TreeModule.Tree

trait SyntheticTests {
  val backEnd: Tree => TerminalPhaseResult

  def compileAndRun(inFileNames: String*): Either[String, String] =
    L3Tester.compileAndRun(backEnd)(inFileNames)

  // Validate the output of a self-validating test.
  def isValidTestResult(s: String): Boolean =
    s.nonEmpty && (s == (s(0) +: ('A' to s(0))).mkString)

  def testSelfValidatingOutput(using path: utest.framework.TestPath) = {
    val testFileName = "../tests/" + path.value.last.split(" ")(0) + ".l3"
    assertMatch(compileAndRun(testFileName)) {
      case Right(s: String) if isValidTestResult(s) =>
    }
  }

  val tests = Tests {
    test("primitives") {
      // Block primitives
      test("prim-blockp (@block?)"){ testSelfValidatingOutput }
      test("prim-block-alloc (@block-alloc-...)"){ testSelfValidatingOutput }
      test("prim-block-tag (@block-tag)"){ testSelfValidatingOutput }
      test("prim-block-length (@block-length)"){ testSelfValidatingOutput }
      test("prim-block-get-set (@block-[sg]et)"){ testSelfValidatingOutput }

      // Integer primitives
      test("prim-intp (@int?)"){ testSelfValidatingOutput }
      test("prim-add (@+)"){ testSelfValidatingOutput }
      test("prim-sub (@-)"){ testSelfValidatingOutput }
      test("prim-mul (@*)"){ testSelfValidatingOutput }
      test("prim-div (@/)"){ testSelfValidatingOutput }
      test("prim-mod (@%)"){ testSelfValidatingOutput }
      test("prim-shift-left (@shift-left)"){ testSelfValidatingOutput }
      test("prim-shift-right (@shift-right)"){ testSelfValidatingOutput }
      test("prim-and (@and)"){ testSelfValidatingOutput }
      test("prim-or (@or)"){ testSelfValidatingOutput }
      test("prim-xor (@xor)"){ testSelfValidatingOutput }
      test("prim-lt (@<)"){ testSelfValidatingOutput }
      test("prim-le (@<=)"){ testSelfValidatingOutput }
      test("prim-int-to-char (@int->char)"){ testSelfValidatingOutput }

      // Character primitives
      test("prim-charp (@char?)"){ testSelfValidatingOutput }
      test("prim-char-to-int (@char->int)"){ testSelfValidatingOutput }

      // Boolean primitives
      test("prim-boolp (@bool?)"){ testSelfValidatingOutput }

      // Unit primitives
      test("prim-unitp (@unit?)"){ testSelfValidatingOutput }

      // Primitives on arbitrary values
      test("prim-eq (@=)"){ testSelfValidatingOutput }
    }

    test("expressions") {
      test("expr-let (let …)"){ testSelfValidatingOutput }
      test("expr-lets (let* …)"){ testSelfValidatingOutput }
      test("expr-letrec (letrec …)"){ testSelfValidatingOutput }
      test("expr-rec (rec …)"){ testSelfValidatingOutput }
      test("expr-fun (fun …)"){ testSelfValidatingOutput }
      test("expr-begin (begin …)"){ testSelfValidatingOutput }
      test("expr-if (if …)"){ testSelfValidatingOutput }
      test("expr-cond (cond …)"){ testSelfValidatingOutput }
      test("expr-and (and …)"){ testSelfValidatingOutput }
      test("expr-or (or …)"){ testSelfValidatingOutput }
      test("expr-not (not …)"){ testSelfValidatingOutput }
    }

    test("statements") {
      test("stmt-def (def …)"){ testSelfValidatingOutput }
      test("stmt-defrec (defrec …)"){ testSelfValidatingOutput }
      test("stmt-halt (halt …)"){ testSelfValidatingOutput }
    }
  }
}

object SyntheticTests1 extends TestSuite with SyntheticTests {
  val backEnd = L3Tester.backEnd1
}

object SyntheticTests2 extends TestSuite with SyntheticTests {
  val backEnd = L3Tester.backEnd2
}

object SyntheticTests3 extends TestSuite with SyntheticTests {
  val backEnd = L3Tester.backEnd3
}

object SyntheticTests4 extends TestSuite with SyntheticTests {
  val backEnd = L3Tester.backEnd4
}

object SyntheticTests5 extends TestSuite with SyntheticTests {
  val backEnd = L3Tester.backEnd5
}

object SyntheticTests6 extends TestSuite with SyntheticTests {
  val backEnd = L3Tester.backEnd6
}
