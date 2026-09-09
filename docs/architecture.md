# Architecture

L₃ separates source semantics, control flow, value layout, and machine instructions into successive representations. [Main.scala](../compiler/src/l3/Main.scala) composes these passes with `andThen`.

## From source to assembly

| Stage | Responsibility | Entry point |
| --- | --- | --- |
| Module expansion | Resolve `.l3m` manifests and deduplicate sources. | [L3FileReader](../compiler/src/l3/L3FileReader.scala) |
| Parsing | Parse surface syntax and desugar it to the CL₃ core tree. | [L3Parser](../compiler/src/l3/L3Parser.scala) |
| Name analysis | Resolve identifiers to unique symbols and report unbound names. | [CL3NameAnalyzer](../compiler/src/l3/CL3NameAnalyzer.scala) |
| CPS translation | Express evaluation order, branches, and returns with continuations. | [CL3ToCPSTranslator](../compiler/src/l3/CL3ToCPSTranslator.scala) |
| High CPS optimization | Simplify while values retain source-level meaning. | [HighCPSOptimizer](../compiler/src/l3/CPSOptimizer.scala) |
| Value representation | Encode tagged values and convert functions to workers, wrappers, and environments. | [CPSValueRepresenter](../compiler/src/l3/CPSValueRepresenter.scala) |
| Hoisting | Move nested function definitions to the top level. | [CPSHoister](../compiler/src/l3/CPSHoister.scala) |
| Flat CPS optimization | Optimize the lowered representation. | [FlatCPSOptimizer](../compiler/src/l3/CPSOptimizer.scala) |
| Constant naming | Introduce names for literals where the machine requires registers. | [CPSConstantNamer](../compiler/src/l3/CPSConstantNamer.scala) |
| Register allocation | Assign registers using liveness information and parallel moves. | [CPSRegisterAllocator](../compiler/src/l3/CPSRegisterAllocator.scala) |
| Assembly generation | Select instructions, resolve labels, and encode the program. | [CPSToASMTranslator](../compiler/src/l3/CPSToASMTranslator.scala), [ASMLabelResolver](../compiler/src/l3/ASMLabelResolver.scala), [ASMFileWriter](../compiler/src/l3/ASMFileWriter.scala) |

The textual `.l3a` format contains one encoded 32-bit instruction per line, followed by a readable instruction annotation. The C loader reads the hexadecimal encoding.

## Why CPS?

A continuation names what happens next. Instead of returning an expression into an implicit call stack, a CPS function receives a return continuation and transfers control to it with the result.

The CPS tree has explicit forms for function and continuation definitions (`LetF`, `LetC`), primitive bindings (`LetP`), calls (`AppF`, `AppC`), branches (`If`), and termination (`Halt`). This gives optimization passes direct access to control flow while retaining a structured tree.

## Closures and tagged values

Value representation lowers integers to `(n << 1) | 1`, characters to `(c << 3) | 6`, booleans to distinct immediate constants, and unit to `2`. Compound values use heap blocks with a tag and size header.

Closure conversion separates a function's worker from its wrapper. A known call can pass captured values directly to the worker. A function used as a value receives a closure containing its wrapper address and environment; indirect calls enter through the wrapper. Hoisting then collects function definitions into a flat program.

## Optimization

The optimizer repeatedly applies shrinking rewrites until a fixed point:

- Propagate copies and evaluate constant primitives and conditions.
- Apply neutral/absorbing-element rules and simplify repeated arguments.
- Eliminate unused bindings whose primitives have no side effects.
- Reuse common expressions only for pure, stable primitives.
- Inline functions and continuations used once in the appropriate call position.
- Track known block tags, lengths, and eligible initialized fields.

A separate inlining loop is bounded to eight rounds. The accepted tree is capped at 1.5 times the initially shrunk tree's size. Each round is followed by shrinking, balancing opportunities for simplification against code growth.

## Memory management

The VM stores 32-bit words in a contiguous memory region:

```text
┌──────────────┬─────────────────┬──────────────────┬──────────────┐
│ instructions │ two top frames  │ heap blocks      │ mark bitmap  │
└──────────────┴─────────────────┴──────────────────┴──────────────┘
```

Heap blocks contain a header followed by payload words. Free blocks use the first payload word as a link to the next block in their size class. Allocation searches 32 segregated free lists and splits a block when space remains.

The collector is non-moving and stops VM execution during collection:

1. Start from the active register frame and follow the caller-frame chain.
2. Validate candidate references using alignment, heap bounds, and the allocation bitmap.
3. Traverse reachable blocks using an explicit host-allocated mark stack. Skip raw string data and code-address fields; scan closure captures and value slots according to block tags.
4. Sweep the heap, reclaim unreachable blocks, merge adjacent free regions, and rebuild the free lists.

Collection runs after sufficient allocation or when allocation cannot find a suitable block. If collection cannot reclaim enough space, allocation reports out of memory. The total VM memory option includes code, frames, heap, and bitmap; the host-side mark stack is separate.

## Validation strategy and scope

The Scala tests reuse fixtures across six backends: CL₃ interpretation, high CPS interpretation, lowered CPS interpretation, optimized CPS interpretation, assembly interpretation, and optimized assembly interpretation. Example suites omit the unoptimized assembly backend because the larger examples exceed its practical register constraints. Sudoku is excluded from the Scala example suite for runtime cost and included in the standalone C VM fixtures.

The VM suite checks precompiled assembly independently, while CI also compiles source examples and executes the generated assembly on the C VM. Debug builds enable address and undefined-behavior sanitizers.

This is a teaching compiler with a custom VM target. It does not emit native host machine code. The collector does not compact the heap, so free-list coalescing mitigates fragmentation without moving live objects.
