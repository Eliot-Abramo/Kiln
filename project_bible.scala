//Main program is compiler/src/Main.scala
//For tests same thing but compiler/test/l3/l3Tester.scala

/**
  * Runtime step by step:
    1. read and expand .l3m modules 
        (L3FileReader.readFilesExpandingModules)

    2. Parse source text
        L3Parser.parse

    3. Perform name analysis
        CL3NameAnalyzer

    4. Back-end, either:
        CL3Interpreter or
        CL3ToCPSTranslator and then HighCPSInterpreter
  */

// Path A interpret CL3 directly
// Path B translate to CL3 then CPS and interpret CPS
// If translator correct, should be the same for both paths

/********************************************************************
  * Language Crash course
********************************************************************/
//Surface L3 has:
    def 
    defrec
    fun
    let*
    begin
    cond
    and
    or
    not
    //string literals
    //block tags like #_foo

    //The parser desugures this immediately 

//CL3 has:
    Let
    LetRec
    If
    App
    Prim
    Halt
    Ident
    Lit

    //core source IR after parsing/desugaring but before the CPS.
    //language of the CL3Interpreter

//CPS (Continuation-passing intermediate representation) has:
    LetF
    LetC
    LetP
    AppF
    AppC
    If
    Halt

    //makes control flow explicit
    //functions receive an extra return continuation, continuations are named,
    //conditionals branch to continuations
    //primitive values are bound with LetP

/********************************************************************
  * Abstract Syntax Trees (AST) theory
********************************************************************/
//typed tree representation of a program
(if (@ < x 0)
    (@ 0 - x)
    x)
// becomes:
If(
    Prim(IntLt, Seq(Ident(x), Lit(0))),
    Prim(IntSub, Seq(Lit(0), Ident(x))),
    Ident(x)
)
//tree structure not text

/********************************************************************
  * CPS In 1 paragraph
********************************************************************/
//instead of returning a value, you send the result to a continuation
f(x,k) //where k says what to do with the result
//whole point is to make it explicit what happens next and first-class in the IR

/********************************************************************
  * Scala Quick Crash more then course
********************************************************************/
//a singleton module
object Main
//means one globally available module value, often used for utilities
//passes, interpreters. object  = namespace + singleton

//reusable abstract type / mixin
trait Formatter[-T]
//contains abstract members, concrete members, be mixed into classes or objects

class CPSTreeChecker[T <: CPSTreeModule](...)
//Generic parameter, T must be a subtype of CPSTreeModule

enum CL3Literal {
    case IntLit(value: L3Int)
    case ChatLit(value: L3Char)
    case BooleanLit(value: Boolean)
    case UnitLit
}
//it is a typed sum type
//basically a clean ADT

//Pattern matching
tree match {
    case Let(bdgs, body) => ....
    case If(cond, thenE, elseE) => ...
}
//inspect tree node kind, recurse struturally, rebuild or interpet

//union types A | B
type Value = CL3Literal | BlockV | FunctionV
//a value can be one of several possible runtime forms

def rewrite(tree: N.tree)(using env: Env): S.Tree
//means env is an implicit contextual parameter and:
given Position = tree.pos 
//creates a contextual value available to nested calls

opaque type L3Int = Int
//means runtime representation = Int
//but outsidde the defining scope it behaves like its own abstract type

extension (x: L3Int){
  def +(y: L3Int): L3Int = ...
}
//defines methods 'as if' they belonged to L3Int

//partial function is not defined for all inputs
type Env = PartialFunction[Symbol, Value]
//This lets the environment behave like a mapping that may be undefined for
//same names. 

Map(name->value) orElse any
//first try the new binding, otherwise fallback to the older environment

///map,flatMap, either, what?

CL3ToCPSTranslator andThen HighCPSInterpreter
//run translator and then pass its result to CPS interpreter

export Tree.*
//makes enum cases available directly from the module

import scala.collection.mutable.{Map => MutableMap}
//import mutable Map, rename it locally to MutableMap

//type tests in CPS code:
using TypeTest[treeModule.Atom, treeModule.Name]
//because Atom = Name | Literal, the code needs runtime evidence to distinguish
//atom is a name, atom is a literal
//supports safe-ish pattern matching on abstract union members

/********************************************************************
  * L3 syntax -> CL3 Desugaring
********************************************************************/
//L3
(fun (x y) body)
//CL3
LetRec(Seq(Fun(freshFunName, Seq(x, y) body)), Ident(freshFunName))

//L3
(let* ((x e1) (y e2)) body)
//CL3
Let(Seq((x, e1)),
  Let(Seq((y,e2)), body))

//L3
(begin e1 e2 e3)
//CL3
Let(Seq((fresh, e1)),
  Let(Seq(fresh, e2)),
  e3)

//L3
(rec f ((x a) (y b)) body)
//CL3
//define recursive function f(x,y)=body, immediately apply it to a,b

//L3
or
//CL3
//desugars with temporary variable to preserve evaluation once, short-circuiting,
//returning the original thruthy value

//L3
(not e)
//CL3
If(e, false, true)

//L3
//cond
//CL3
//chain of nested If

//L3
string literals
//CL3
//not a primitive in CL3, becomes:
//allocate block tagged _string, fill with chars, return the block

/********************************************************************
  * CL3
********************************************************************/
Let(bindings, body)
//evaluate each binding expression in the current environment, then evaluate
//body in the extended environment.
//In the interpreter, the binding expressions are evaluated before the new names
//are in scope. So Let is non-recursive

LetRec(functions, body)
//build recursive closures, function names are availabe:
//inside all function bodies, inside the final body

If(cond, thenE, elseE)
//only #f counts as false, everything else is true
//if condition evaluates to BooleanLit(false) evaluate else

App(fun, args)
//Evaluate the function expression, it must evaluate to a function block whose
//payload is a FunctionV. Then evaluate args and call body in captured environment
//extended with argument bindings

Prim(prim, args)
//Apply built in primitives

Halt(args)
//Evaluate args and stop program, arg must be an integer

Ident(name)
//look up in environment

Lit(value)
//return literal directly

/********************************************************************
  * CPS
********************************************************************/
//CPS does not have general expression nesting

LetP(name, prim, args, body)
//Evaluate primitive now, bind result to name, continue with body

LetF(funs, body)
//bind recursive functions, continue

LetC(cnts, body)
//bind continuations, continue

AppF(fun, retC, args)
//call function with target function value, return continuation name,
//value arguments
//function no loger 'return normally', they call retC

AppC(cnt, args)
//jump to continuation

If(testPrim, args, thenC, elseC)
//evaluate a test primitive and jump to one of the two continuations

Halt(args)
//stop with integer result
//this shape is stricter than CL3 


/********************************************************************
  * Type Casting
********************************************************************/