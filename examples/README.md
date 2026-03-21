This directory contains several example programs written in L₃. The most important ones are briefly described in the table below.

| Name    | Behavior                                           |
|---------|----------------------------------------------------|
| bignums | Compute the factorial using "big integers"         |
| life    | Conway's "Game of Life"                            |
| maze    | Inefficiently compute and draw a random maze       |
| unimaze | Like maze, but faster and using Unicode characters |
| queens  | Solve the n-queens problem                         |
| sudoku  | Solve a few Sudoku problems                        |

Once the L₃ compiler is complete, that is once it can generate `l3a` files for the L₃ virtual machine, the examples above can be compiled in different ways, as described below.

The first, but slowest technique is to execute the compiler from sbt, using the `run` command. For example, to compile the `unimaze` example, enter the following command at the sbt prompt (the `>` below represents the sbt prompt and should not be typed):

``` example
> run ../examples/unimaze.l3m
```

The second, faster technique consists in first packaging the L₃ compiler and then executing it from the shell. The packaging should be done from sbt using the `stage` command, as follows:

``` example
> stage
```

This generates a launcher script called `l3c`, which can be executed from the shell. For example, to compile the `unimaze` example as above, enter the following command in your shell, while in the `examples` directory (the `$` below represents the shell prompt and should not be typed):

``` example
$ ../compiler/target/universal/stage/bin/l3c unimaze.l3m
```

Notice that both commands above will generate an L₃ assembly file called `out.l3a`. The name of that file can be changed using the `l3.out-asm-file` Java property. For example, to compile the same example as above but put the assembly file in `unimaze.l3a`, enter the following at the shell prompt:

``` example
$ ../compiler/target/universal/stage/bin/l3c \
    -Dl3.out-asm-file=unimaze.l3a unimaze.l3m
```

To compile all the examples of this directory in parallel (to take advantage of a multi-core machine), a tool like [GNU parallel](https://savannah.gnu.org/projects/parallel/) can be used as follows:

``` example
$ ls *.l3m                                                     \
    | parallel ../compiler/target/universal/stage/bin/l3c      \
               -Dl3.out-asm-file={.}.l3a {}
```
