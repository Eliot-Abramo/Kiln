# L₃ examples

Build both components with `make` from the repository root. The commands below also run from the root.

| Module | Behavior |
| --- | --- |
| `hello.l3` | Writes Hello, world using byte primitives; no library required. |
| `bignums.l3m` | Computes factorials using big integers. |
| `life.l3m` | Runs Conway's Game of Life. |
| `maze.l3m` | Generates and draws a random maze. |
| `unimaze.l3m` | Generates a maze with disjoint sets and Unicode rendering. |
| `queens.l3m` | Solves the N-queens problem. |
| `sudoku.l3m` | Solves a collection of Sudoku problems. |
| `printint.l3m` | Demonstrates integer output. |

## Compile and run

```sh
compiler/target/universal/stage/bin/l3c examples/queens.l3m
printf '8\n0\n' | vm/c/bin/vm out.l3a
```

A `.l3m` manifest includes both the source program and its library dependencies. Programs prompt for input when executed by the VM.

Choose a separate output file to keep several examples compiled:

```sh
compiler/target/universal/stage/bin/l3c \
  -Dl3.out-asm-file=examples/unimaze.l3a examples/unimaze.l3m
vm/c/bin/vm -m 2000000 examples/unimaze.l3a
```

For an initial experiment without packaging, run from `compiler/`:

```sh
sbt 'run ../examples/hello.l3'
../vm/c/bin/vm out.l3a
```

The output filename defaults to `out.l3a` in the compiler's working directory. Generated files are ignored by Git.
