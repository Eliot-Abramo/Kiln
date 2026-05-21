# Introduction

This directory contains the source code of the L₃ virtual machine, written in C.

# Compiling

To compile the virtual machine, use the provided `Makefile` with the `vm` target:

``` example
$ make vm
```

# Running

Once compiled, the virtual machine can be found in the `bin` directory. It takes an assembly file produced by the compiler as argument and runs it, e.g.:

``` example
$ ./bin/vm ../compiler/out.l3a
```

It also accepts the `-m` option to set the total memory size (code and heap), in bytes.
