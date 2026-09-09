# L₃ compiler

The Scala compiler translates `.l3` source files into assembly for the [C virtual machine](../vm/c/README.md). The main pipeline is defined in [Main.scala](src/l3/Main.scala); its passes are described in the [architecture guide](../docs/architecture.md).

## Build and test

Use JDK 21 and sbt. Run these commands from this directory:

```sh
sbt compile
sbt test
sbt stage
```

The build pins Scala 3.8.1 and sbt 1.12.2. `stage` creates a standalone launcher in `target/universal/stage/bin/l3c`. The tests execute sequentially because interpreter output capture uses process-wide input/output streams.

## Compile a program

```sh
sbt 'run ../examples/hello.l3'
```

This writes `out.l3a` in the current directory. Run it separately:

```sh
make -C ../vm/c
../vm/c/bin/vm out.l3a
```

For repeated compilation, use the staged launcher:

```sh
target/universal/stage/bin/l3c ../examples/queens.l3m
../vm/c/bin/vm out.l3a
```

Choose the output filename with a JVM property:

```sh
target/universal/stage/bin/l3c \
  -Dl3.out-asm-file=queens.l3a ../examples/queens.l3m
```

Compilation does not run the program or consume its input. The output directory must already exist.

## Source files and modules

The compiler accepts one or more filenames:

- `.l3`: source code.
- `.l3m`: a list of source files or other modules, resolved relative to the module file.

Modules expand recursively. Duplicate source paths are removed, keeping their first occurrence, and the resulting sources are parsed together. For example, `../examples/queens.l3m` includes the standard library and the solver.

## Inspecting intermediate representations

`Main.scala` retains commented tree/sequence printers next to the relevant passes for local debugging. Reference interpreters are available for CL₃, high CPS, low CPS, flat CPS, and assembly. [L3Tester.scala](test/l3/L3Tester.scala) assembles the six backends used by the tests.
