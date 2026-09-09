# A short guide to L₃

L₃ is a small Lisp-like language with lexical scope, first-class functions, recursion, tagged heap blocks, and byte-oriented input/output. The standard library builds lists, vectors, strings, and other useful structures on top of those primitives.

## Expressions and functions

Function calls put the function first; primitive operations use `@`:

```scheme
(defrec factorial
  (fun (n)
    (if (@ <= n 1)
        1
        (@ * n (factorial (@ - n 1))))))

(int-print (factorial 6))
(newline-print)
```

Compile this with `library/lib.l3m` before the source filename to make `int-print` and `newline-print` available. The program prints `720`.

`def` binds a value and `defrec` binds a recursive function. `let` introduces local bindings; `let*` introduces them sequentially. `fun` creates a function that can capture values from its surrounding scope. `begin` sequences expressions.

Only `#f` is false. `if`, `cond`, `and`, `or`, and `not` provide conditional and short-circuit evaluation. `(halt 0)` terminates the program with an exit code.

## Surface syntax, CL₃, and CPS

The parser reduces surface forms to the CL₃ core:

| Surface form | Core idea |
| --- | --- |
| Anonymous `fun` | Bind a fresh local function and return its name. |
| `let*` | Nest ordinary local bindings. |
| `begin` | Bind intermediate results to fresh, unused names. |
| `cond` | Nest conditional expressions. |
| `not` | Select a boolean through a conditional. |
| String literal | Allocate a string block and initialize its characters. |

The core tree contains bindings, recursive functions, conditionals, applications, primitive operations, identifiers, literals, and termination. CPS translation then gives intermediate computations names and makes their continuations explicit. See the [architecture guide](architecture.md).

## Modules and library conventions

A `.l3m` file is a manifest of source files and other manifests. Paths are relative to the manifest; repeated source files are included only once. For example:

```text
../library/lib.l3m
queens.l3
```

Library names generally start with their module prefix, such as `string-` or `vector-`. Predicates end in `?`, functions with side effects end in `!`, and conversions use `->`. Names beginning with `%` denote private helpers by convention.

The [library reference](../library/README.md) lists modules, prefixes, and block tags. The [examples](../examples/README.md) show complete programs.
