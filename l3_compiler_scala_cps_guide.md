# L3 / Scala / Compiler Construction Deep Guide

Authoring note: this guide is based on the actual project files in `l3/`, especially `compiler/src/l3/`. Where I explain *the intended* CL3→CPS translation, that part is an informed reconstruction from the surrounding code because `CL3ToCPSTranslator.scala` is still a stub. I am confident about the surrounding infrastructure and the required invariants, but I cannot claim the course staff intended one unique implementation strategy unless they gave you extra lecture notes.

---

## 0. What this project is, in one sentence

This project is a **small compiler pipeline for a Lisp-like language called L3**, written in **Scala 3**, with:

- a parser from source text to an abstract syntax tree,
- a name-analysis pass turning strings into unique symbols,
- a direct interpreter for the source-like core language `CL3`,
- a target intermediate representation in **CPS**,
- an interpreter for that CPS IR,
- tests comparing the direct interpreter and CPS path.

The missing piece is exactly the pass you mentioned: `CL3ToCPSTranslator.scala`.

---

## 1. Big-picture map of the repository

## Top-level layout

### `l3/README.md`
High-level repository map.

### `l3/compiler/`
The Scala compiler project.

### `l3/library/`
Standard library modules written in L3 itself.

### `l3/examples/`
Real programs in L3 (`maze`, `queens`, `sudoku`, etc.).

### `l3/tests/`
Small language tests plus expected I/O files.

---

## 2. The compilation / execution workflow

The real workflow is encoded in `compiler/src/l3/Main.scala` and mirrored in `compiler/test/l3/L3Tester.scala`.

### Runtime pipeline

1. **Read files and expand `.l3m` modules**
   - `L3FileReader.readFilesExpandingModules`
2. **Parse source text**
   - `L3Parser.parse`
3. **Perform name analysis**
   - `CL3NameAnalyzer`
4. **Back-end**
   - either:
     - `CL3Interpreter` directly, or
     - `CL3ToCPSTranslator andThen HighCPSInterpreter`

### Why there are two execution paths

The project deliberately includes two “semantics-preserving” back ends:

- **Path A**: interpret CL3 directly.
- **Path B**: translate CL3 to CPS, then interpret CPS.

If your translator is correct, both should behave the same on all tests.

That is the central engineering trick of the project:  
the CPS interpreter is already there, so the missing translation pass can be validated by comparing behavior with the CL3 interpreter.

---

## 3. What the languages are

There are really three “representational layers” here.

### 3.1 Surface L3
This is the language users write:
- `def`
- `defrec`
- `fun`
- `let*`
- `begin`
- `cond`
- `and`
- `or`
- `not`
- string literals
- block tags like `#_foo`

The parser **desugars** a lot of this immediately.

### 3.2 CL3
This is the **core source IR** after parsing/desugaring, but before CPS.
Its AST is:

- `Let`
- `LetRec`
- `If`
- `App`
- `Prim`
- `Halt`
- `Ident`
- `Lit`

This is the language interpreted by `CL3Interpreter`.

### 3.3 CPS
This is the **continuation-passing intermediate representation**.
Its AST is:

- `LetF`
- `LetC`
- `LetP`
- `AppF`
- `AppC`
- `If`
- `Halt`

This language makes control flow explicit:
- functions receive an extra return continuation,
- continuations are named,
- conditionals branch to continuations,
- primitive values are bound with `LetP`.

---

## 4. Theory you need before touching the code

## 4.1 Abstract syntax trees (ASTs)

An AST is a typed tree representation of a program.

Example:

```lisp
(if (@ < x 0) (@ - 0 x) x)
```

becomes something conceptually like:

```scala
If(
  Prim(IntLt, Seq(Ident(x), Lit(0))),
  Prim(IntSub, Seq(Lit(0), Ident(x))),
  Ident(x)
)
```

The tree is **structure**, not text.

---

## 4.2 Environments and lexical scope

The interpreters use an environment mapping names to values.

Important:
- `Let` introduces bindings.
- `LetRec` introduces **recursive** function bindings.
- lexical scope means a function remembers the environment where it was created.

In the code, function values therefore store:
- parameters,
- body,
- environment.

That is the classic **closure** idea.

---

## 4.3 Name analysis / alpha-renaming

The parser initially stores names as strings:
- `"x"`
- `"map"`
- `"foo"`

That is dangerous because two different `x`s in two scopes would look identical.

So `CL3NameAnalyzer` replaces strings with globally unique `Symbol`s:
- `x`
- `x_1`
- `x_2`

Same printed base name, but distinct unique identities.

This solves:
- shadowing cleanly,
- recursive references cleanly,
- later transformations safely.

---

## 4.4 CPS in one paragraph

In direct style, a function returns a value normally:

```scala
f(x) + 1
```

In CPS, instead of returning, you **send the result to a continuation**:

```scala
f(x, k)   // where k says what to do with the result
```

The whole point is to make “what happens next” explicit and first-class in the IR.

That helps:
- control-flow representation,
- tail-call discipline,
- low-level compilation steps,
- optimization and analysis.

Classic references on CPS include Appel’s *Compiling with Continuations* and Danvy/Filinski’s work on CPS transformation. Appel’s book presents CPS as a compiler IR, and the Scala 3 docs cover language features heavily used in this codebase such as union types, givens/using, and opaque types.

---

## 5. Scala 3 cheat sheet, but targeted to this project

This section is not generic Scala trivia. It is a “how to read this repo” cheat sheet.

## 5.1 `object`
A singleton module.

```scala
object Main
```

means:
- one globally available module value,
- often used for utilities, passes, interpreters.

You can think:
- `object` ≈ namespace + singleton.

---

## 5.2 `trait`
A reusable abstract type / mixin.

```scala
trait Formatter[-T]
```

It can contain:
- abstract members,
- concrete members,
- be mixed into classes or objects.

---

## 5.3 `class`
Standard class definition.

```scala
class CPSTreeChecker[T <: CPSTreeModule](...)
```

Generic parameter:
- `T` must be a subtype of `CPSTreeModule`.

---

## 5.4 `enum`
Scala 3 algebraic data type.

Example:

```scala
enum CL3Literal {
  case IntLit(value: L3Int)
  case CharLit(value: L3Char)
  case BooleanLit(value: Boolean)
  case UnitLit
}
```

This is extremely important:
- it is a typed sum type,
- pattern matching on it is the main way the code works.

Mentally:
- `enum` here is basically a clean ADT.

---

## 5.5 Pattern matching

```scala
tree match {
  case Let(bdgs, body) => ...
  case If(cond, thenE, elseE) => ...
}
```

This is how almost every compiler pass is written:
- inspect tree node kind,
- recurse structurally,
- rebuild or interpret.

---

## 5.6 Union types: `A | B`

Example:

```scala
type Value = CL3Literal | BlockV | FunctionV
```

A value can be one of several possible runtime forms.

Scala 3 union types explicitly express that a value may be one of multiple types. This repo uses them for interpreter values and AST atoms.

---

## 5.7 `using` / `given`

You will see:

```scala
def rewrite(tree: N.Tree)(using env: Env): S.Tree
```

This means `env` is an **implicit contextual parameter**.

And:

```scala
given Position = tree.pos
```

creates a contextual value available to nested calls.

Practical reading rule:
- whenever you see `(using something)`, imagine “hidden extra argument automatically passed around”.

In this codebase, `using` is used mainly for:
- current source position,
- formatter/checker instances,
- current environment during analysis.

Scala 3’s `given`/`using` system is the standard replacement for older implicit parameters.

---

## 5.8 `opaque type`

From `L3Ints.scala`:

```scala
opaque type L3Int = Int
```

This means:
- runtime representation = `Int`,
- but outside the defining scope it behaves like its own abstract type.

Why this is beautiful here:
- no runtime overhead,
- but stronger type separation,
- custom operations can enforce the project’s 31-bit integer discipline.

Scala 3 opaque types hide implementation details without runtime overhead. That is exactly how `L3Int` is being used here.

---

## 5.9 Extension methods

Also in `L3Ints.scala`:

```scala
extension (x: L3Int) {
  def +(y: L3Int): L3Int = ...
}
```

This defines methods “as if” they belonged to `L3Int`.

---

## 5.10 `PartialFunction`

A partial function is not defined for all inputs.

Example:

```scala
type Env = PartialFunction[Symbol, Value]
```

This lets the environment behave like a mapping that may be undefined for some names.

Also used for:
- extractors,
- primitive evaluators.

Interpretation trick:
- `orElse` chains environments nicely.

---

## 5.11 `orElse` on functions / environments

```scala
Map(name -> value) orElse env
```

This means:
- first try the new binding,
- otherwise fall back to the older environment.

This is a compact lexical-scope idiom used everywhere in the interpreters.

---

## 5.12 `map`, `flatMap`, `Either`

Compilation stages return `Either[String, T]`:
- `Left(error)` = failure
- `Right(value)` = success

Then pipeline code does:

```scala
.flatMap(L3Parser.parse(...))
.flatMap(CL3NameAnalyzer)
.flatMap(backEnd)
```

That means:
- stop automatically on first error,
- otherwise pass successful value to next phase.

---

## 5.13 Function composition: `andThen`

```scala
CL3ToCPSTranslator andThen HighCPSInterpreter
```

means:
1. run translator,
2. pass its result to CPS interpreter.

---

## 5.14 `export`
Used to re-export names for convenience.

Example:

```scala
export Tree.*
```

This makes enum cases available directly from the module.

---

## 5.15 Aliased imports

```scala
import scala.collection.mutable.{ Map => MutableMap }
```

Meaning:
- import mutable `Map`,
- rename it locally to `MutableMap`.

---

## 5.16 Type tests

In CPS code:

```scala
using TypeTest[treeModule.Atom, treeModule.Name]
```

Because `Atom = Name | Literal`, the code needs runtime evidence to distinguish:
- atom is a name,
- atom is a literal.

This supports safe-ish pattern matching on abstract union members.

---

## 6. Reading strategy for this codebase

If you want to stop feeling lost, read in this order:

1. `CL3Tree.scala`
2. `CL3Literal.scala`
3. `L3Primitive.scala`
4. `CL3Interpreter.scala`
5. `CPSTree.scala`
6. `CPSInterpreter.scala`
7. `CL3NameAnalyzer.scala`
8. `L3Parser.scala`
9. `Main.scala`
10. `CPSTreeChecker.scala`
11. `CL3ToCPSTranslator.scala` (design against everything above)

Why this order works:
- syntax tree first,
- value categories second,
- semantics third,
- target IR fourth,
- translation fifth.

---

## 7. Surface L3 syntax → CL3 desugaring

A huge source of confusion is that what you write is **not** what later passes see.

Here is what the parser rewrites.

### Anonymous function

Surface:
```lisp
(fun (x y) body)
```

Desugars to something like:
```scala
LetRec(Seq(Fun(freshFunName, Seq(x, y), body)), Ident(freshFunName))
```

So anonymous functions become:
- a `letrec`,
- binding a fresh named function,
- returning that function.

### `let*`

Surface:
```lisp
(let* ((x e1) (y e2)) body)
```

Desugars to nested `Let`s:
```scala
Let(Seq((x, e1)),
  Let(Seq((y, e2)), body))
```

### `begin`

Surface:
```lisp
(begin e1 e2 e3)
```

Desugars to sequencing through ignored bindings:
```scala
Let(Seq((fresh, e1)),
  Let(Seq((fresh, e2)),
    e3))
```

This is a common trick: sequencing in a pure-expression core language is simulated by binding and ignoring intermediate values.

### `rec`

Surface:
```lisp
(rec f ((x a) (y b)) body)
```

Desugars to:
- define recursive function `f(x,y)=body`
- immediately apply it to `a,b`

### `and`

```lisp
(and e1 e2 e3)
```

Desugars as nested `If` with short-circuiting.

### `or`

Desugars with a temporary variable to preserve:
- evaluation once,
- short-circuiting,
- returning the original truthy value.

### `not`

```lisp
(not e)
```

becomes:
```scala
If(e, false, true)
```

### `cond`

Chain of nested `If`.

### string literals

A string literal is **not** a primitive literal in CL3.
It becomes:
- allocate block tagged `_string`,
- fill with chars,
- return the block.

That detail matters a lot: strings are compiled during parsing into ordinary core constructs.

---

## 8. Semantics of CL3, precisely

CL3 nodes:

### `Let(bindings, body)`
Evaluate each binding expression in the current environment, then evaluate `body` in the extended environment.

In the interpreter, the binding expressions are evaluated before the new names are in scope. So `Let` is non-recursive.

### `LetRec(functions, body)`
Build recursive closures. Function names are available:
- inside all function bodies,
- inside the final body.

### `If(cond, thenE, elseE)`
Only `#f` counts as false. Everything else is truthy.
The code confirms that:
- if condition evaluates to `BooleanLit(false)`, choose else,
- otherwise choose then.

### `App(fun, args)`
Evaluate the function expression.
It must evaluate to a function block whose payload is a `FunctionV`.
Then evaluate args and call body in captured environment extended with argument bindings.

### `Prim(prim, args)`
Apply built-in primitives.

### `Halt(arg)`
Evaluate arg and stop program. Arg must be an integer.

### `Ident(name)`
Look up in environment.

### `Lit(value)`
Return literal directly.

---

## 9. Semantics of CPS, precisely

CPS does **not** have general expression nesting.

Instead:

### `LetP(name, prim, args, body)`
Evaluate primitive now, bind result to `name`, continue with `body`.

### `LetF(funs, body)`
Bind recursive functions, continue.

### `LetC(cnts, body)`
Bind continuations, continue.

### `AppF(fun, retC, args)`
Call function with:
- target function value,
- return continuation name,
- value arguments.

Functions no longer “return normally”; they call `retC`.

### `AppC(cnt, args)`
Jump to continuation.

### `If(testPrim, args, thenC, elseC)`
Evaluate a test primitive and jump to one of two continuations.

### `Halt(arg)`
Stop with integer result.

This shape is stricter than CL3 and that is why translation is nontrivial.

---

## 10. The single most important conceptual difference: values vs control

In CL3:
- expressions can be nested anywhere.

In CPS:
- evaluation order is made explicit,
- intermediate results are named,
- control transfer happens only via `AppF`, `AppC`, or `If`.

So when implementing the translator, your main job is:

> turn arbitrary nested expression trees into a linearized sequence of `LetP` / `LetF` / `LetC` plus tail-position control transfers.

---

## 11. Directory-by-directory theoretical purpose

## `examples/`
These are integration-scale programs.
Use them to understand the language idiomatically.

### Most useful examples
- `hello.l3`: tiny I/O sanity check.
- `printint.l3`: printing integers.
- `pow.l3`: small arithmetic + recursion.
- `queens.l3`: recursion, lists, library use.
- `maze.l3`, `unimaze.l3`: nontrivial programs stressing performance and control flow.

## `library/`
These are not compiler internals; they are L3 programs implementing a library.
They show:
- naming conventions,
- tagged-block data representations,
- what user code expects from primitives and compiler semantics.

## `tests/`
These isolate individual semantic features.
Excellent for debugging the translator.
For example:
- `expr-let.l3`
- `expr-or.l3`
- `prim-block-get-set.l3`

## `compiler/test/l3`
These test:
- direct CL3 path,
- CPS path.

If backEnd1 passes and backEnd2 fails, the bug is almost certainly your translator.

---

# 12. File-by-file deep dive: `compiler/src/l3`

This is the heart of the guide.

---

## 12.1 `package.scala`

### Purpose
Global shared type aliases and low-level utility functions.

### Key ideas
- standardizes phase result type,
- defines machine-ish integer helpers,
- provides substitution helpers,
- provides fixed-point utility.

### Walkthrough by line groups

#### Lines 1–3
```scala
package object l3 {
  type TerminalPhaseResult = Either[String, (Int, Option[String])]
```
A terminal compiler/interpreter phase either:
- fails with an error string,
- or succeeds with `(returnCode, optionalMessage)`.

This is the standardized output of interpreters/back ends.

#### Lines 4–7
```scala
  type L3BlockTag = Int
  type L3Char = Int
  export L3Ints.L3Int
```
- block tags are plain ints,
- chars are stored as Unicode code points in ints,
- `L3Int` is re-exported from `L3Ints`.

#### Lines 9–11
```scala
  type Bits32 = Int
```
A conceptual 32-bit machine word, even though the current code still uses JVM `Int`.

#### Lines 13–22
`fitsInNSignedBits` and `fitsInNUnsignedBits` are bit-width checks.
They matter because the project models L3 integers as **31-bit signed values**.

#### Lines 24–31
Substitution helpers:
- `Subst[T] = Map[T, T]`
- `emptySubst` defaults to identity
- `subst(from,to)` builds substitution maps

Even if not used yet by your translator, this is classic compiler infrastructure: renaming, replacement, rewriting.

#### Lines 33–38
`fixedPoint`
Generic repeated-application utility:
- start with `start`,
- repeatedly apply `f`,
- stop when stable.

This is textbook compiler analysis infrastructure.

---

## 12.2 `Position.scala`

### Purpose
Tracks source locations for errors.

### Structure
- `Position` trait
- `FilePosition(fileName, line, column)`
- `UnknownPosition`

### Why it matters
The parser passes positions down contextually via `using Position`.
Name analysis and interpreters can then report precise diagnostics.

### Subtlety
The current file reader counts columns by character count, not Unicode code-point count; the comment in `L3FileReader` explicitly says this is imperfect.

---

## 12.3 `Symbol.scala`

### Purpose
Represents globally unique names.

### Theory
A compiler should distinguish:
- two variables named `x` in different scopes,
- even if their textual spelling is identical.

### Structure
`Symbol` stores:
- `name: String` — human-readable base name
- lazy unique id

`Symbol.fresh(name)` generates fresh symbols using a per-name counter.

### Important subtlety
`id` is lazy:
```scala
private lazy val id = idProvider
```
The provider is only evaluated if needed, i.e. when printed. That is slightly fancy, but mostly harmless here.

### Why `copy()` exists
It duplicates the symbol object while preserving name/id behavior source.
Not heavily used here, but can be useful if unique identity object-ness vs printed identity matters in later phases.

---

## 12.4 `BlockTag.scala`

### Purpose
Maps symbolic block tag names to numeric tag codes.

### Built-in tags
- `_free-block`
- `_function`
- `_string`
- `_register_frame`

### Theory
L3 uses heap blocks for structured values.
A tag identifies the kind of block:
- function closure,
- string,
- pair/list/vector/user-defined structure, etc.

### Dynamic resolution
If a tag name is unknown, `resolve` allocates the first unused byte value in `0..255`.

That means user-defined tags are assigned automatically as compilation encounters them.

### Important implication
Tags are global process state inside the compiler run.
For a single compile that is fine.
If you were building a long-running compiler server, you would think more about determinism and lifecycle.

---

## 12.5 `L3Ints.scala`

### Purpose
Implements the project’s integer abstraction.

### Core design
```scala
opaque type L3Int = Int
```
Runtime is `Int`, but operations are constrained to the L3 notion of integer.

### Why 31-bit?
`SIZE = Integer.SIZE - 1`
So a valid `L3Int` fits in 31 signed bits.

This is a standard trick in languages that reserve one bit for tagging or pointer/value distinctions.

### `ofIntClipped`
```scala
private def ofIntClipped(v: Int): L3Int = L3Int((v << 1) >> 1)
```
This clips to signed 31 bits by:
- shifting left to discard top bit,
- arithmetic shift right to restore sign.

### Extension methods
All arithmetic and bitwise ops return clipped `L3Int`s.

That means L3 integer arithmetic wraps/truncates to the language’s integer model instead of full JVM `Int` semantics.

### Constructors
- `L3Int.apply(v)` requires signed 31-bit fit
- `L3Int.ofIntUnsigned(v)` requires unsigned fit

This unsigned path is used for things like block tags coming from hex/binary literal forms.

### Why this file matters for your translator
Mostly as background:
- literals of type `IntLit` contain `L3Int`,
- all integer semantics must preserve that representation.

---

## 12.6 `CL3Literal.scala`

### Purpose
Defines literal values in CL3 and high-level CPS.

### Cases
- `IntLit`
- `CharLit`
- `BooleanLit`
- `UnitLit`

### `toString`
Custom printer:
- integers print numerically,
- chars print `'x'`,
- booleans print `#t` / `#f`,
- unit prints `#u`

### Why it matters
This enum is shared across:
- parser output,
- CL3 interpreter,
- high-level CPS interpreter,
- tree formatters.

---

## 12.7 `L3Primitive.scala`

### Purpose
Defines primitive operations and classifies them as either:
- value-producing,
- test-producing.

### Two enums
- `L3ValuePrimitive`
- `L3TestPrimitive`

### Why this split is crucial
In CPS:
- a **value primitive** is translated to `LetP(name, prim, args, body)`
- a **test primitive** is used by CPS `If(cond, args, thenC, elseC)`

So translation must know which primitives are predicates/tests versus ordinary value producers.

### Value primitives
Examples:
- `BlockAlloc`
- `BlockGet`
- arithmetic ops
- byte I/O
- conversions
- `Id`

### Test primitives
Examples:
- `BlockP`
- `IntP`
- `CharP`
- `BoolP`
- `UnitP`
- `<`, `<=`
- `=`

### Important subtlety: `Id`
The `Id` value primitive exists only in CPS-level evaluation convenience.
It can be very useful during translation as a way to bind an atom to a fresh name uniformly:
```scala
LetP(x, Id, Seq(atom), body)
```
That is a common trick for atom-normalization.

This is one of the most useful advanced observations for implementing your translator.

---

## 12.8 `CL3Tree.scala`

### Purpose
Defines the CL3 AST, in a generic way over the kind of names and primitives stored.

### Generic trait
```scala
trait CL3TreeModule {
  type Name
  type Primitive
```

This is a neat abstraction:
- before name analysis, names are strings and primitives are strings,
- after name analysis, names are `Symbol` and primitives are `L3Primitive`.

### AST nodes
```scala
Let
LetRec
If
App
Prim
Halt
Ident
Lit
```

### `Fun`
Recursive function binding element used by `LetRec`.

### Two concrete modules

#### `NominalCL3TreeModule`
- `Name = String`
- `Primitive = String`

Used right after parsing.

#### `SymbolicCL3TreeModule`
- `Name = Symbol`
- `Primitive = L3Primitive`

Used after name analysis and everywhere downstream.

### Why this design is elegant
The tree shape is the same across phases.
Only the “annotation domain” changes.
That keeps the pipeline conceptually simple.

---

## 12.9 `CL3TreeFormatter.scala`

### Purpose
Pretty-printer for CL3 trees.

### Why it matters
When debugging the translator, being able to print intermediate trees is gold.

### Structure
`CL3TreeFormatter[T <: CL3TreeModule]` formats any CL3 tree module.

Key cases:
- `Let` → `(let (...) body)`
- `LetRec` → `(letrec (...) body)`
- `Prim` → `(@prim ...)`

### `given` instances
At the end:
- formatter for nominal trees,
- formatter for symbolic trees.

These let `Main.treePrinter` work without manual wiring.

---

## 12.10 `Formatter.scala`

### Purpose
Minimal pretty-printing abstraction built on `paiges`.

### `trait Formatter[-T]`
Anything that can convert `T` to a `Doc`.

### `Formatter.par`
Helpers to print parenthesized, nested structures with layout control.

### Why it matters
This file is tiny, but it explains why tree printers look simple elsewhere.

---

## 12.11 `IO.scala`

### Purpose
Runtime byte I/O helpers.

### Functions
- `readByte()`
- `writeByte(c)`

These back the L3 primitives:
- `@byte-read`
- `@byte-write`

### Why tiny files matter
Compilers are often mostly orchestration plus a few small semantic modules.
Do not overlook small files: they often anchor built-in runtime effects.

---

## 12.12 `L3FileReader.scala`

### Purpose
Reads source files, expands module files, concatenates sources, and provides index→position mapping.

### Major responsibilities

#### 1. Expand `.l3m` modules recursively
Module files list other files.
Expansion:
- recursively expands nested modules,
- normalizes paths,
- removes duplicates while preserving first occurrence.

#### 2. Concatenate all final `.l3` files
The parser sees a single flat program string.

#### 3. Preserve source locations
It records:
- start index of each line in concatenated string,
- originating file name and line number.

### Advanced subtlety
The parser only sees the concatenated program text, but diagnostics still need original file positions.
This file provides the bridge.

### Line-group commentary

- `readFilesExpandingModules`: phase entry point with error handling.
- `expandModules`: recursive module expansion.
- inner `readModule`: reads line-based module declarations, ignoring comments/empty lines.
- `readFiles`: concatenates file text and builds position map.

### Important note
The returned value is:
```scala
(String, Int => Position)
```
That is:
- entire source program text,
- function translating parser indices to source positions.

That is a beautiful design choice: compact and phase-friendly.

---

## 12.13 `L3Parser.scala`

### Purpose
Lexing + parsing + immediate desugaring of surface L3.

### External library
Uses `fastparse`.

### Main public function
```scala
def parse(programText: String, indexToPosition: Int => Position): Either[String, Tree]
```

Returns a **nominal CL3 tree**.

### Internal structure
There are two nested parser classes:

#### `L`
Lexical parser
Handles:
- integers,
- chars,
- strings,
- booleans,
- unit,
- identifiers,
- keywords.

Whitespace is significant here.

#### `S`
Syntactic parser
Handles:
- program,
- expressions,
- top-level defs,
- desugaring.

Whitespace/comments are ignored here.

### Extremely important parser facts

#### Top-level `def` and `defrec`
They are compiled into nested `Let` / `LetRec` around the rest of the program.
So top-level definitions become ordinary core bindings.

#### Strings
String literals are desugared immediately into block allocation and block mutation.

#### `begin`
Becomes sequencing via `Let`.

#### `or`
Uses fresh temp name to preserve value and short-circuit semantics.

### `IP`
This helper is subtle and important:
it wraps a parser and injects position information from the current parse index.

That is how every AST node gets a source position.

### Fresh name generators
Parser-generated sugar expansions need hygienic fresh names:
- `fun#1`
- `or#3`
- `string#2`
- `begin#4`

### Why this file is central
Many later “language features” do not exist as AST nodes at all.
They are already gone by the end of parsing.

---

## 12.14 `CL3NameAnalyzer.scala`

### Purpose
Resolves textual names to globally unique `Symbol`s and validates bindings.

### Input
Nominal CL3 tree.

### Output
Symbolic CL3 tree.

### Theory
This is basically:
- scope checking,
- alpha-renaming,
- primitive resolution.

### Environment
```scala
type Env = Map[String, Symbol]
```

Maps source names to unique symbols.

### Key behaviors

#### `Let`
- check bound names are unique within that `let`,
- create fresh symbols for them,
- rewrite RHS expressions in old env,
- rewrite body in augmented env.

This is correct non-recursive `let` scoping.

#### `LetRec`
- check function names unique,
- allocate fresh symbols for all function names first,
- extend environment with them,
- rewrite functions and body in that extended environment.

This is correct recursive scoping.

#### Function applications and arity-overloaded names
This is a subtle special case:

```scala
case N.App(N.Ident(fun), args) if env contains altName(fun, args.length) =>
```

If the callee is an identifier and the environment contains a name like `foo@2`,
it rewrites the application to that symbol.

This supports arity-specific alternate names.

#### Primitive resolution
If primitive name/arity matches built-ins:
- convert string primitive to `L3Primitive`.

Otherwise:
- give arity or unknown-primitive errors.

#### Unbound identifier errors
Reported with source position.

### Important helper functions

- `checkUnique`
- `altName(name, arity)`
- `augmented`

### Why this pass matters deeply for your translator
After this phase:
- all ordinary variables/functions are unique symbols,
- so your translator can safely generate fresh names and reason structurally without worrying about capture.

This is one of the reasons compiler passes are usually ordered this way.

---

## 12.15 `CL3Interpreter.scala`

### Purpose
Reference interpreter for CL3 semantics.

### Why it is vital
This is your **semantic oracle** for the source language.

If you are unsure what a CL3 construct means, this file is the truth.

### Internal value domain
```scala
type Value = CL3Literal | BlockV | FunctionV
```

Possible runtime values:
- literals,
- heap blocks,
- function closures.

### `BlockV`
```scala
case class BlockV(tag: L3BlockTag, contents: Array[Value])
```
Mutable contents array = heap object model.

### `FunctionV`
```scala
case class FunctionV(args: Seq[Symbol], body: Tree, env: Env)
```
Classic closure:
- parameter names,
- body,
- captured lexical environment.

### Environment
```scala
type Env = PartialFunction[Symbol, Value]
```

### Error handling strategy
Instead of threading result values manually, the interpreter throws:
- `EvalErr`
- `EvalHlt`

and catches them at the top level.

This is a pragmatic implementation technique, not a semantic statement.

### Per-node semantics

#### `Let`
Evaluate all RHS expressions in current env, then body in extended env.

#### `LetRec`
Create mutable recursive environment, then stuff closures into it.
This is the classic trick for recursive closures.

#### `If`
Only `BooleanLit(false)` is false.

#### `App`
Function values are represented as blocks tagged `Function`, whose payload array contains a single `FunctionV`.

This mirrors how functions are heap objects in L3.

#### `Prim`
Massive pattern match implementing built-ins.

Important distinctions:
- some primitives inspect value shape,
- some mutate blocks,
- some do I/O,
- some compare,
- some convert.

#### `Halt`
Stops only on integer literal.

### Advanced observations useful for translator design

1. **Function values are represented as function-tagged blocks**, not as a special direct value form exposed to user code.
2. **Truthiness** is “false only if exactly `#f`”.
3. **`Eq` uses Scala equality on interpreter values**.
4. **Mutable blocks mean evaluation order matters**.

That fourth point matters a lot:
your CPS translation must preserve left-to-right evaluation order.

---

## 12.16 `CPSTree.scala`

### Purpose
Defines the CPS IR.

### Generic design
Like CL3 trees, CPS trees are parameterized over:
- names,
- literals,
- primitive types.

### `Atom`
```scala
type Atom = Name | Literal
```
An atom is something already “simple enough” to be passed directly:
- a variable name,
- a literal.

This is the key normalization concept of the target IR.

### AST nodes

#### `LetF`
Bind functions.

#### `LetC`
Bind continuations.

#### `LetP`
Bind result of a value primitive.

#### `AppF`
Call function with explicit return continuation.

#### `AppC`
Call continuation.

#### `If`
Test primitive dispatch to continuations.

#### `Halt`
Terminate.

### Helper traits
- `SymbolicNames`
- `HighValues`
- `NestedTree`

These mixins define concrete flavors of CPS trees.

### `HighCPSTreeModule`
This project’s target:
- symbolic names,
- full CL3 literals,
- high-level primitive set,
- nested tree bodies.

### Why this file matters
It tells you exactly what shapes your translator is allowed to construct.

---

## 12.17 `CPSTreeFormatter.scala`

### Purpose
Pretty-printer for CPS trees.

### Important behavior
It “pulls” nested let-like prefixes into a compact pretty-printed `(let ...)` or `(let* ...)` form.

### `pullLets`
This is not changing the tree, only formatting it.
It recursively flattens leading chains of:
- `LetF`
- `LetC`
- `LetP`

for prettier output.

### Why useful
When debugging a translator, raw nested CPS can become unreadable.
This formatter makes it human-manageable.

---

## 12.18 `CPSTreeChecker.scala`

### Purpose
Sanity checker for CPS trees.

### Guarantees checked
1. bound names are globally unique,
2. all used names are in scope.

### Why this is so important
Your translator can easily generate:
- duplicate fresh names,
- unbound continuation names,
- values referenced outside scope.

This checker catches exactly those bugs.

### Core technique
Traverses tree with two environments:
- `cEnv`: continuation names in scope
- `vEnv`: value/function names in scope

### Per-node meaning

- `LetF`: function names enter value env
- `LetC`: continuation names enter continuation env
- `LetP`: result name enters value env
- `AppF`: function atom and argument atoms must be value-scoped; return continuation must be continuation-scoped
- `AppC`: continuation must be continuation-scoped
- `If`: test args must be value atoms; both branch targets must be continuations
- `Halt`: halted atom must be value atom

### Subtle point
In `checkF`, the function body starts with continuation environment containing only its own `retC`.

This encodes the CPS discipline:
a function body returns by jumping to that continuation.

---

## 12.19 `CPSInterpreter.scala`

### Purpose
Reference interpreter for CPS trees.

### Architecture
A generic abstract interpreter:
- parameterized by CPS tree module,
- parameterized by runtime representation choices,
- then specialized into `HighCPSInterpreter`.

### Core eval loop
Tail-recursive `eval(tree, env)`.

### Runtime values
Generic value domain:
```scala
Literal | BlockV | FunV | CntV
```

### `FunV`
Stores:
- return continuation parameter name,
- argument names,
- body,
- captured env.

### `CntV`
Stores:
- args,
- body,
- env.

### Semantics

#### `LetF`
Recursive closure environment for functions.

#### `LetC`
Recursive closure environment for continuations.

#### `LetP`
Evaluate value primitive; bind result.

#### `AppF`
Resolve function, resolve return continuation from current env, resolve args, then execute function body in function closure env extended with:
- function’s return continuation parameter bound to actual continuation value,
- function’s arguments bound to actual arg values.

This is a crucial point:
the continuation itself is passed as a value in the environment.

#### `AppC`
Jump to continuation body.

#### `If`
Evaluate test primitive, choose branch continuation, then jump to a zero-argument continuation.

#### `Halt`
Extract integer and return it as process code.

### `HighCPSInterpreter`
Concrete specialization using:
- CL3 literals,
- tagged block representation,
- L3 primitive semantics.

### Important differences vs CL3 interpreter
The CPS interpreter separates:
- value primitives (`vEvaluator`)
- test primitives (`cEvaluator`)

That split directly dictates how your translator should map CL3 primitives.

### Very useful translator insight
Since `Id` exists as a value primitive in CPS, you can cheaply turn any atom into a named variable when convenient.

---

## 12.20 `Main.scala`

### Purpose
Compiler entry point.

### Most important lines
```scala
val backEnd: Tree => TerminalPhaseResult = (
  CL3ToCPSTranslator
    `andThen` treePrinter(...)
    `andThen` treeChecker
    `andThen` HighCPSInterpreter
)
```

This says the intended “real” path is:
- translate to CPS,
- optionally print,
- optionally check,
- interpret CPS.

### Main pipeline
- read/expand files
- parse
- name-analyze
- back-end
- print error or exit code

### Helper utilities
- `treeChecker`
- `treePrinter`
- `seqPrinter`
- `passThrough`

These are generic instrumentation combinators.

### Why it matters
This file shows how all compiler phases are meant to compose.

---

## 12.21 `CL3ToCPSTranslator.scala`

### Purpose
The missing pass.

### Current contents
A stub:
```scala
object CL3ToCPSTranslator extends (Any => Nothing) {
  def apply(tree: Any): Nothing =
    ???
}
```

### What it *should* become
Something like:
```scala
object CL3ToCPSTranslator
  extends (SymbolicCL3TreeModule.Tree => HighCPSTreeModule.Program)
```
or wrapped in an `Either` if the project expects error reporting.

Based on `Main.scala` and `L3Tester.scala`, it must compose with the CPS interpreter, so its output must be a `HighCPSTreeModule.Program`. The intended input is the symbolic CL3 tree.

### Your job
Encode CL3 semantics into CPS while preserving:
- scope,
- order,
- tail behavior,
- value/test primitive distinctions,
- function closure behavior,
- truthiness.

The rest of this guide now focuses heavily on that.

---

# 13. How to think about the translator

This section is the one you will probably reread the most.

## 13.1 The target shape

In CPS, every computation is one of these:
- bind a primitive result,
- introduce functions,
- introduce continuations,
- call a function,
- call a continuation,
- branch to continuations,
- halt.

So the translator must convert a CL3 expression into:
- a CPS fragment that eventually **invokes a continuation with the expression’s value**.

That suggests the canonical translation API:

```scala
nonTail(tree, k: Atom => Tail): Tail
```

or more concretely:

```scala
def nonTail(t: CL3Tree)(k: Atom => CPSTree): CPSTree
```

Meaning:
- translate CL3 term `t`,
- when its value is ready as an atom, continue with `k(atom)`.

Then also define specialized translators:
- `tail(t, kName)` — expression in tail position, result should be passed to continuation `kName`
- `cond(t, thenC, elseC)` — expression in conditional context, branch to continuations

This is the classic structured CPS translation organization.

---

## 13.2 Why you probably need multiple mutually recursive translators

A single function is usually painful. You normally want at least:

### `tail`
Translate expression in tail position.

### `nonTail`
Translate expression whose value is needed by surrounding code.

### `atomise` / `nonTailAtom`
Ensure a result becomes an atom (name or literal).

### `cond`
Translate expression used as a condition.

This decomposition mirrors classic CPS translation design from the literature and standard compiler construction practice.

---

## 13.3 Recommended translation interface

A very plausible design is:

```scala
def tail(t: S.Tree, c: Symbol): C.Tree
def nonTail(t: S.Tree)(ctx: C.Atom => C.Tree): C.Tree
def cond(t: S.Tree, tc: Symbol, fc: Symbol): C.Tree
```

Where:
- `S = SymbolicCL3TreeModule`
- `C = HighCPSTreeModule`

### Interpretation
- `tail`: produce CPS that eventually `AppC(c, Seq(v))`
- `nonTail`: produce CPS that feeds value atom to a host continuation builder
- `cond`: produce CPS that jumps to `tc` or `fc`

This is the cleanest mental model.

---

## 13.4 Fresh names you will need

Your translator must generate fresh symbols for:
- intermediate values,
- continuation names,
- function names only if needed for rewritten helpers.

Since input names are already unique symbols, your fresh names can safely be e.g.:
- `%k`
- `%v`
- `%if`
- `%prim`

using `Symbol.fresh(...)`.

Rule:
- every CPS-bound name must be globally unique, or `CPSTreeChecker` will complain.

---

# 14. Exact CL3 → CPS translation strategy

Now the concrete mechanics.

I am going to describe a robust implementation strategy, not merely theory.

---

## 14.1 Atoms

In CPS, an atom is:
- literal,
- variable name.

So:

### `Ident(x)` translates to atom `x`
### `Lit(l)` translates to atom `l`

When you need a *named* result even for a literal, use:
```scala
LetP(tmp, Id, Seq(lit), body)
```
but only if necessary.

---

## 14.2 Tail translation

Suppose `tail(t, k)` means:
translate expression `t` in tail position, delivering result to continuation `k`.

### Literals / identifiers
```scala
tail(Lit(l), k)   = AppC(k, Seq(l))
tail(Ident(x), k) = AppC(k, Seq(x))
```

### Primitive application
Need left-to-right evaluation of args into atoms.

Then:
- if primitive is a **value primitive**:
  ```scala
  LetP(tmp, prim, argsAtoms, AppC(k, Seq(tmp)))
  ```
- if primitive is a **test primitive**:
  You cannot use `LetP`; test primitives belong in `If`.
  So define local continuations:
  - then continuation sends `#t` to `k`
  - else continuation sends `#f` to `k`

Example schema:
```scala
LetC(Seq(
  Cnt(tc, Seq(), AppC(k, Seq(BooleanLit(true)))),
  Cnt(fc, Seq(), AppC(k, Seq(BooleanLit(false))))
), If(testPrim, argsAtoms, tc, fc))
```

This is one of the deepest and easiest-to-miss points.

### Function application
Evaluate callee and args left-to-right to atoms, then:
```scala
AppF(funAtom, k, argAtoms)
```

### If-expression
Translate condition in conditional mode.

Create branch continuations:
- `thenC`: tail-translate then-branch to `k`
- `elseC`: tail-translate else-branch to `k`

Then use `cond`.

### Let
Since CL3 `Let` bindings are non-recursive and their RHSs may be arbitrary expressions, the easiest correct method is:
- evaluate RHSs left-to-right,
- bind each to a name usable in CPS body.

Because CPS has only `LetP`, `LetF`, `LetC`, not general `let-value`, the standard trick is:
- evaluate each RHS non-tail,
- pass result into a continuation that continues with next binding/body.

If the CL3 binding name is already the target variable, bind through a continuation argument with that exact name.

### LetRec
Translate CL3 functions into CPS functions:
each CL3 function `f(args) = body` becomes CPS function:
```scala
Fun(f, kret, args, tail(body, kret))
```
Then the surrounding body is tail-translated in the extended function environment via `LetF`.

### Halt
Need the argument as atom, then:
```scala
Halt(atom)
```
If argument is not already atom, evaluate it non-tail first.

---

## 14.3 Non-tail translation

`nonTail(t)(ctx)` means:
compute value of `t`, then continue with `ctx(atom)`.

### Literals / identifiers
```scala
nonTail(Lit(l))(ctx) = ctx(l)
nonTail(Ident(x))(ctx) = ctx(x)
```

### Primitive applications

#### Value primitive
Evaluate args left-to-right to atoms.
Bind result to fresh tmp:
```scala
LetP(tmp, prim, atoms, ctx(tmp))
```

#### Test primitive
Since result is boolean but CPS test primitives only appear in `If`, compile by branching to continuations that feed `#t` / `#f` into `ctx`.

Schema:
```scala
LetC(Seq(
  Cnt(tc, Seq(), ctx(BooleanLit(true))),
  Cnt(fc, Seq(), ctx(BooleanLit(false)))
), If(testPrim, atoms, tc, fc))
```

### `If(cond, t, e)`
Create join continuation `kjoin(v)` whose body is `ctx(v)`.
Then:
- branch continuations tail-translate `t` and `e` into `kjoin`.

Schema:
```scala
LetC(Seq(
  Cnt(kjoin, Seq(v), ctx(v)),
  Cnt(tc, Seq(), tail(t, kjoin)),
  Cnt(fc, Seq(), tail(e, kjoin))
), cond(cond, tc, fc))
```

This is the standard “join continuation” pattern.

### `App(fun, args)`
Since application result is produced asynchronously via continuation, create a fresh continuation to receive it:
```scala
LetC(Seq(
  Cnt(kres, Seq(v), ctx(v))
), ... AppF(funAtom, kres, argAtoms))
```

### `Let`
Sequentially evaluate bindings and continue.

### `LetRec`
Introduce functions with `LetF`, then continue with non-tail translation of body.

### `Halt`
Usually only valid in tail contexts semantically as a program-ending construct, but as an expression you can translate directly to `Halt(atom)` after evaluating atom. Since `halt` does not continue, `ctx` is ignored after that point.

---

## 14.4 Conditional translation

`cond(t, tc, fc)` means:
translate CL3 expression `t` as a condition and jump to one of two zero-arg continuations.

### Literal / identifier
Need CL3 truthiness:
- exactly `#f` means false,
- everything else means true.

Since CPS `If` only uses test primitives, you can compile general condition expression by first evaluating it to an atom and then comparing to `#f`.

A very workable strategy:
```scala
nonTail(t) { a =>
  If(Eq, Seq(a, BooleanLit(false)), fc, tc)
}
```
Note the swapped branches:
- if `a == #f` → false continuation
- else → true continuation

Because `Eq` is a test primitive on arbitrary values.

This is a major conceptual simplifier:
you do **not** need special truthiness support elsewhere.

### `If(c, t, e)` in condition position
You can avoid building booleans:
- branch on `c`,
- in then branch conditionally translate `t`,
- in else branch conditionally translate `e`.

This is more elegant and often more efficient.

### `Prim(testPrim, args)`
Evaluate args to atoms, then:
```scala
If(testPrim, atoms, tc, fc)
```

### `Prim(valuePrim, args)`
Evaluate to a value atom, then compare to `#f` as above.

---

# 15. Evaluation order: the thing that will silently break your compiler

L3 has:
- mutable blocks,
- I/O primitives,
- short-circuit behavior.

Therefore the translator must preserve left-to-right evaluation order.

For any list of subexpressions:
- function position,
- then arguments in order,
- primitive arguments in order,
- let bindings in order.

A robust pattern is:

```scala
def nonTailSeq(ts: Seq[S.Tree])(ctx: Seq[C.Atom] => C.Tree): C.Tree =
  ts match
    case Seq() => ctx(Seq())
    case t +: rest =>
      nonTail(t) { a =>
        nonTailSeq(rest) { as =>
          ctx(a +: as)
        }
      }
```

This helper is one of the best tools you can build.

It will likely simplify:
- primitive application,
- function application,
- possibly let bindings.

---

# 16. Function translation in detail

Suppose CL3 has:
```scala
LetRec(Seq(Fun(f, Seq(x, y), body)), outerBody)
```

In CPS you want:
```scala
LetF(Seq(
  Fun(f, kret, Seq(x, y), tail(body, kret))
), ...)
```

### Why extra `kret`?
Because every CPS function does not return directly.
It receives the continuation that describes where to send its result.

### Important invariant
Inside a CPS function body:
- normal completion must end in `AppC(kret, Seq(result))`,
- not in `Halt`, unless the source itself used `halt`.

### Recursive references
`LetF` already supports recursive functions, just like CL3 `LetRec`.
So CL3 `LetRec` maps very naturally to CPS `LetF`.

---

# 17. Let translation in detail

This is conceptually harder than it first appears.

Suppose:
```scala
Let(Seq((x, ex), (y, ey)), body)
```

CL3 semantics:
- evaluate `ex` in current env
- evaluate `ey` in current env
- then body in env extended with `x` and `y`

Actually in the provided interpreter, all binding RHSs are evaluated in the old env, then the body gets the extended environment. So the bindings are **parallel**, not sequentially visible to each other.

That means you must preserve this.

### Easy but wrong approach
Translating as:
- evaluate `ex`, bind `x`
- evaluate `ey`, bind `y` in env containing `x`

would accidentally give `ey` access to `x`, which is wrong.

### Correct strategy
Evaluate each RHS under the original surrounding environment, but collect results before entering body.

A practical approach:
1. Evaluate each RHS to a fresh temporary symbol `tx_i`.
2. Once all are computed, bind CL3 names to those temporaries via `Id` or by passing them into a join continuation that names them exactly as the CL3 binders before translating the body.

For example:
```scala
nonTailSeq(rhses) { atoms =>
  // atoms correspond to values of RHSs, with original env semantics preserved
  bindNames(names, atoms, tail/body ...)
}
```

where `bindNames` may use `LetP(name, Id, Seq(atom), ...)` if needed to associate the exact CL3 name to the computed atom.

This is an advanced but extremely important semantic subtlety.

---

# 18. Truthiness and booleans

CL3 `if` condition semantics are:

- only `BooleanLit(false)` is false
- everything else is true

This differs from “must be boolean” languages.

Therefore:
- do **not** compile conditionals assuming condition is a test primitive or boolean only,
- general expressions in condition position must be compared to `#f`.

The safest general recipe is:

```scala
nonTail(condExpr) { a =>
  If(Eq, Seq(a, BooleanLit(false)), elseC, thenC)
}
```

---

# 19. How test primitives differ from value primitives

This is another make-or-break concept.

## Value primitives
Examples:
- `+`
- `block-alloc`
- `block-get`
- `byte-read`
- `char->int`

They produce values.

In CPS:
```scala
LetP(tmp, prim, args, body)
```

## Test primitives
Examples:
- `<`
- `<=`
- `=`
- `int?`
- `block?`

They are not used through `LetP` in CPS.
They are used only through:
```scala
If(testPrim, args, thenC, elseC)
```

If you need the *boolean value* of a test primitive in a non-conditional expression context, you synthesize it using continuations returning `#t` or `#f`.

This is standard CPS IR design and the project code makes that split explicit in both the tree definitions and the CPS interpreter.

---

# 20. The helper functions you almost certainly want to write

Here is the toolkit I would want before writing the full translator.

## 20.1 `fresh`
```scala
def fresh(prefix: String): Symbol = Symbol.fresh(prefix)
```

## 20.2 `atom`
Fast path:
```scala
def asAtom(t: S.Tree): Option[C.Atom] = t match
  case S.Ident(x) => Some(x)
  case S.Lit(l)   => Some(l)
  case _          => None
```

## 20.3 `nonTailSeq`
Evaluate a sequence left-to-right into atoms.

## 20.4 `bindAtomToName`
```scala
def bindAtomToName(name: Symbol, a: C.Atom, body: C.Tree): C.Tree =
  a match
    case n: Symbol if n == name => body
    case _ => C.LetP(name, L3ValuePrimitive.Id, Seq(a), body)
```

This is incredibly useful.

## 20.5 `mkBool`
Turn control result into boolean atom result.

## 20.6 `cond`
General condition translation.

---

# 21. A worked translation example

Take direct-style CL3:

```lisp
(if (@ < x 0)
    (@ - 0 x)
    x)
```

After name analysis, conceptually:
```scala
If(
  Prim(IntLt, Seq(Ident(x), Lit(0))),
  Prim(IntSub, Seq(Lit(0), Ident(x))),
  Ident(x)
)
```

Suppose we translate in tail position with continuation `k`.

### Step 1: translate condition into branch continuations

Create:
- `cthen = cnt () => tail(Prim(IntSub,...), k)`
- `celse = cnt () => tail(Ident(x), k)`

### Step 2: translate primitive test
Since `<` is a test primitive:
- evaluate args to atoms `x`, `0`
- emit:
```scala
If(IntLt, Seq(x, 0), cthen, celse)
```

### Step 3: translate then branch
`Prim(IntSub, Seq(0, x))` is a value primitive in tail position:
```scala
LetP(tmp, IntSub, Seq(0, x),
  AppC(k, Seq(tmp)))
```

### Full shape
```scala
LetC(Seq(
  Cnt(cthen, Seq(),
    LetP(tmp, IntSub, Seq(0, x),
      AppC(k, Seq(tmp)))),
  Cnt(celse, Seq(),
    AppC(k, Seq(x)))
),
  If(IntLt, Seq(x, 0), cthen, celse))
```

That is exactly the kind of structure you should expect everywhere.

---

# 22. Another worked example: function application in non-tail position

Source:
```lisp
(let ((y (f x)))
  (@ + y 1))
```

Conceptually in CL3:
```scala
Let(Seq((y, App(Ident(f), Seq(Ident(x))))),
    Prim(IntAdd, Seq(Ident(y), Lit(1))))
```

You cannot directly “return” from `f x` into a direct context.
So in CPS:

1. create continuation `k_y(y)` that continues with translation of `(@ + y 1)`,
2. call function `f` with continuation `k_y`.

Sketch:
```scala
LetC(Seq(
  Cnt(k_y, Seq(y),
    LetP(tmp, IntAdd, Seq(y, 1),
      AppC(k, Seq(tmp))))
),
  AppF(f, k_y, Seq(x)))
```

This example is the essence of CPS.

---

# 23. Common translator bugs you should expect

## Bug 1: using `LetP` for test primitives
Wrong because CPS `If` expects a test primitive directly.

## Bug 2: forgetting CL3 truthiness
Wrong because non-boolean values are valid conditions.

## Bug 3: making `Let` bindings sequentially visible
Wrong because CL3 `Let` RHSs are parallel with respect to scope.

## Bug 4: losing left-to-right evaluation order
Wrong because of side effects and mutation.

## Bug 5: not adding return continuation parameter to translated functions
Wrong because CPS functions do not return normally.

## Bug 6: branch continuations expecting one argument when `If` jumps to zero-arg continuations
Check `CPSInterpreter`: `If` looks up a `CntV(Seq(), ...)`.
So the continuation names used as `thenC` and `elseC` in CPS `If` must be **zero-argument** continuations.

This is a subtle project-specific detail directly visible in the interpreter implementation.

## Bug 7: duplicate fresh names
`CPSTreeChecker` will catch it.

## Bug 8: treating `halt` as if it returned to current continuation
It terminates. No continuation after that.

---

# 24. How to debug the translator systematically

## Step 1
Enable tree printing in `Main.scala` if needed.

## Step 2
Run tiny tests first:
- `stmt-halt.l3`
- `expr-let.l3`
- `expr-if.l3`
- `prim-add.l3`
- `prim-lt.l3`

## Step 3
Use `CPSTreeChecker`.

## Step 4
Compare behavior:
- `backEnd1` direct CL3 interpreter
- `backEnd2` translator + CPS interpreter

## Step 5
When a bug appears, reduce it to a smallest example.

For this project, the best debugging progression is:
1. literals/idents
2. value primitives
3. test primitives
4. `if`
5. application
6. `let`
7. `letrec`
8. nested combinations

---

# 25. A practical implementation plan for `CL3ToCPSTranslator.scala`

## Phase A: skeleton
- define proper input/output types
- import source and target tree modules with aliases

## Phase B: fresh-name utilities
- helper for fresh continuation names
- helper for fresh temp values

## Phase C: atomization helpers
- `asAtom`
- `bindAtomToName`
- `nonTailSeq`

## Phase D: control translators
- `tail`
- `nonTail`
- `cond`

## Phase E: entry point
Top-level program should likely:
- create a final continuation that halts with the result,
- tail-translate the whole input program to that final continuation,
or simply compile top-level directly to `Halt` when appropriate.

A common pattern:
```scala
val khalt = fresh("halt")
LetC(Seq(
  Cnt(khalt, Seq(r), Halt(r))
), tail(program, khalt))
```

This is probably the cleanest entry translation.

## Phase F: run checker/tests early and often

---

# 26. How to read Scala syntax in *this* project like a native

This is the mini “I open any file and do not panic anymore” section.

## Pattern 1
```scala
object X extends (A => B)
```
Means object `X` behaves like a function from `A` to `B` by implementing `apply`.

So:
```scala
CL3NameAnalyzer(tree)
```
is just calling `apply`.

## Pattern 2
```scala
case class ...
```
Immutable product type with auto-generated constructor, deconstruction, equality, etc.

## Pattern 3
```scala
enum Tree { case Let(...); ... }
```
ADT for AST nodes.

## Pattern 4
```scala
import M._
```
Bring enum cases / module members into scope.

## Pattern 5
```scala
val x = ...
def f(...) = ...
```
`val` = stored value; `def` = method recomputed on call.

## Pattern 6
```scala
private def ...
private case class ...
```
Visible only in the enclosing object/class.

## Pattern 7
```scala
case (p, vs) => ...
```
Destructure tuple in pattern match.

## Pattern 8
```scala
args map eval
```
Infix notation for:
```scala
args.map(eval)
```

## Pattern 9
```scala
funs map (_.name)
```
Map each function to its name.

## Pattern 10
```scala
Either.cond(cond, rightValue, leftValue)
```
If condition true → `Right(rightValue)` else `Left(leftValue)`.

---

# 27. “Understand any file in the project” quick reference

Here is the shortest-purpose summary of each compiler source file:

- **`package.scala`**: shared low-level types/utilities
- **`Position.scala`**: source positions
- **`Symbol.scala`**: unique names
- **`BlockTag.scala`**: symbolic tag names → numeric tags
- **`L3Ints.scala`**: 31-bit integer abstraction
- **`CL3Literal.scala`**: literals
- **`L3Primitive.scala`**: built-in primitive taxonomy
- **`CL3Tree.scala`**: CL3 AST definitions
- **`CL3TreeFormatter.scala`**: pretty-printer for CL3 AST
- **`Formatter.scala`**: pretty-printing helpers
- **`IO.scala`**: runtime byte I/O helpers
- **`L3FileReader.scala`**: module expansion + source concatenation + positions
- **`L3Parser.scala`**: parser and desugaring
- **`CL3NameAnalyzer.scala`**: scope resolution and primitive resolution
- **`CL3Interpreter.scala`**: direct semantics for CL3
- **`CPSTree.scala`**: CPS AST definitions
- **`CPSTreeFormatter.scala`**: pretty-printer for CPS
- **`CPSTreeChecker.scala`**: CPS well-formedness checker
- **`CPSInterpreter.scala`**: CPS semantics
- **`Main.scala`**: phase orchestration / executable entry point
- **`CL3ToCPSTranslator.scala`**: your missing compiler pass

---

# 28. Advanced subtleties and “compiler engineer” observations

## 28.1 Why parse-time desugaring is used
It keeps later passes small.
Rather than teaching every later phase about:
- `begin`
- `cond`
- `or`
- strings
the parser rewrites them away.

This is standard compiler engineering: reduce the core language early.

## 28.2 Why name analysis happens before CPS
Without unique names, CPS translation risks capture bugs when creating fresh continuations or temps.

## 28.3 Why CPS `If` targets named continuations instead of embedding trees
It normalizes control flow explicitly, which is easier to analyze and interpret.

## 28.4 Why continuations are separate from functions
Because they represent “control destinations” rather than ordinary callable user functions.
The IR enforces that distinction structurally.

## 28.5 Why `LetP` only handles value primitives
Because test primitives are better represented as branches than booleans in CPS.
This avoids unnecessary boxing/boolean materialization in many cases.

## 28.6 Why `Id` primitive is a gift
It gives you a uniform “bind this atom to a fresh name” mechanism without inventing extra IR syntax.

## 28.7 Why `Let` parallel semantics matter
It is easy to forget because many people mentally read `let` as sequential.
The interpreter is the authority here.

---

# 29. Scientific / theoretical reading list that matches this project

These are the most relevant external references for the ideas embodied here:

1. **Andrew W. Appel, _Compiling with Continuations_**  
   Best classic source for CPS as a compiler IR and for how control-flow translation is organized.

2. **Olivier Danvy and Andrzej Filinski, “Representing Control: A Study of the CPS Transformation”**  
   Classic paper on CPS transformation structure and theory.

3. **Scala 3 official docs on union types, opaque types, and givens/using**  
   These directly explain several language mechanisms the repository uses heavily.

---

# 30. Final checklist: what you should be able to do after studying this

You wanted concrete KPIs. Here they are translated into a self-test.

## KPI 1 — open any file and know its purpose
Can you explain, without hesitation:
- what phase/file it belongs to,
- what kind of data it consumes,
- what kind of data it produces,
- whether it is syntax, semantics, checking, formatting, or orchestration?

## KPI 2 — read/write Scala in this codebase
Can you comfortably read:
- `enum`,
- pattern matching,
- `using/given`,
- `opaque type`,
- union types,
- `Either` pipelines,
- `object X extends (A => B)`?

## KPI 3 — understand L3 → CL3 → CPS
Can you explain:
- parser desugaring,
- name analysis,
- CPS translation of functions, primitives, if, let, app?

## KPI 4 — understand compiler workflow
Can you narrate:
- file read,
- parse,
- name analysis,
- translation,
- CPS checking,
- interpretation/testing?

## KPI 5 — understand every line in `compiler/src`
You do not need to memorize every token, but you should now be able to say for each line block:
- what abstraction it introduces,
- what invariant it maintains,
- how later phases depend on it.

---

# 31. The shortest possible mental model of the whole project

If you forget everything else, remember this:

- **Parser** turns text into a small core language and removes surface sugar.
- **Name analysis** makes variable names globally unique.
- **CL3 interpreter** tells you what source semantics mean.
- **CPS tree** is a stricter IR where control flow is explicit.
- **CPS interpreter** tells you what valid CPS means.
- **Your translator** must make these two semantics coincide.

That is the whole game.

---

# 32. What I would do next if I were you

1. Read `CL3Tree.scala`, `CPSTree.scala`, `CL3Interpreter.scala`, `CPSInterpreter.scala`.
2. Re-read sections 14–19 of this guide.
3. Write helper functions first, not the whole translator at once.
4. Make literals/idents/primitives work before touching `let` and `app`.
5. Use the checker constantly.
6. Compare direct vs CPS test behavior after every milestone.

---

# 33. Mini appendix: pseudo-code blueprint for the translator

This is intentionally not full code, but it is close enough to structure your implementation.

```scala
object CL3ToCPSTranslator
  extends (S.Tree => C.Program):

  import SymbolicCL3TreeModule as S
  import HighCPSTreeModule as C
  import S.*
  import C.*
  import L3Primitive.*
  import CL3Literal.*

  def apply(tree: S.Tree): C.Program =
    let kh = fresh("halt")
    let r  = fresh("r")
    LetC(Seq(
      Cnt(kh, Seq(r), Halt(r))
    ), tail(tree, kh))

  def tail(t: S.Tree, k: Symbol): C.Tree = ...
  def nonTail(t: S.Tree)(ctx: C.Atom => C.Tree): C.Tree = ...
  def cond(t: S.Tree, tc: Symbol, fc: Symbol): C.Tree = ...

  def nonTailSeq(ts: Seq[S.Tree])(ctx: Seq[C.Atom] => C.Tree): C.Tree = ...
  def bindAtomToName(n: Symbol, a: C.Atom, body: C.Tree): C.Tree = ...
```

And the critical case ideas are:

- `tail(Lit/Ident)` → `AppC`
- `tail(Prim(valuePrim,...))` → `LetP(..., AppC(k,...))`
- `tail(Prim(testPrim,...))` → branch, then send `#t/#f` to `k`
- `tail(App(...))` → `AppF(fun, k, args)`
- `tail(If(...))` → create zero-arg branch continuations
- `tail(LetRec(...))` → `LetF`
- `nonTail(App(...))(ctx)` → create return continuation that feeds `ctx`
- `cond(anyExpr, tc, fc)` → evaluate expr and compare to `#f`, except optimized direct cases

---

# 34. One last honest note

I can reconstruct the intended translator design with high confidence from the existing ASTs, interpreters, and checker. What I cannot know with certainty from the repository alone is whether your course staff expects:
- one specific helper-function decomposition,
- one specific optimization level,
- or one exact pretty-printed CPS shape.

So when you implement, prioritize:
1. semantic correctness,
2. CPS checker validity,
3. test equivalence with direct interpretation,
4. only then prettiness/minor optimization.

That is the right compiler-construction instinct.
