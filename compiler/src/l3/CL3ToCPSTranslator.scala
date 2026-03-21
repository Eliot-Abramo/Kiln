package l3

import SymbolicCL3TreeModule.{Tree as CL3Tree}
import HighCPSTreeModule.*
import CL3Literal.*
import L3ValuePrimitive.Id
import L3TestPrimitive.Eq

object CL3ToCPSTranslator extends (CL3Tree => Tree) {
  def apply(tree: CL3Tree): Tree =
    nonTail(tree) {_ => Halt(IntLit(L3Int(0))) }
}
