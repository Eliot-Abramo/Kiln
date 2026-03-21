# Introduction

This directory contains the source code of the L₃ compiler, written in Scala. All interactions with the compiler should be done through [sbt](https://www.scala-sbt.org/), a Scala build tool.

`Sbt` can either be run in interactive mode, by simply typing `sbt` and then entering commands at the prompt, or in batch mode. The following sections use batch mode for illustration, but in practice interactive mode is often to be preferred as it avoids repeated startup of `sbt` itself.

# Compiling

To compile the compiler, use the `compile` command:

``` example
$ sbt compile
```

(the dollar sign `$` represents the shell prompt and should not be typed).

# Testing

To test the compiler (and compile it beforehand, if necessary), use the `test` command:

``` example
$ sbt test
```

# Running

To run the compiler (and compile it beforehand, if necessary), use the `run` command, followed by arguments for the compiler, e.g.:

``` example
$ sbt 'run ../examples/queens.l3m'
```

The compiler accepts a list of files to compile as arguments. These files can have one of the following extensions:

- `.l3` A normal source file, containing L₃ code.
- `.l3m` A module file, containing a list of other files, which must also be either source files (with a `.l3` extension) or other module files (with a `.l3m` extension).

Modules are expanded recursively, until only `.l3` files remain. Then, duplicate file names are removed, with only the first occurrence kept. Finally, this list of files is fed to the compiler.

As an example, assume that the file `lib.l3m` references `characters.l3m` and `integers.l3m`, and that `characters.l3m` references `characters.l3` while `integers.l3m` references both `characters.l3m` and `integers.l3`. Then, a command line consisting of `lib.l3m` and `helloworld.l3` is expanded as follows:

1. `lib.l3m` `helloworld.l3` (original command line),

2. `characters.l3m` `integers.l3m` `helloworld.l3` (expansion of `lib.l3m`),

3. `characters.l3` `characters.l3m` `integers.l3` `helloworld.l3` (expansion of `characters.l3m` and `integers.l3m`),

4. `characters.l3` `characters.l3` `integers.l3` `helloworld.l3` (expansion of the second `characters.l3m`),

5. `characters.l3` `integers.l3` `helloworld.l3` (removal of duplicates).
