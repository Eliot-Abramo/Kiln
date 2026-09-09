# L₃ virtual machine

A C17 implementation of the L₃ 32-bit register machine with a mark-and-sweep garbage collector. [engine.c](src/engine.c) implements instruction dispatch; [memory.c](src/memory.c) manages the heap.

## Build and run

Use Clang and Make in a Unix-like environment. From this directory:

```sh
make
./bin/vm ../../compiler/out.l3a
```

The example path assumes the compiler ran from `compiler/`. Assembly filenames are resolved from the VM's current working directory.

```sh
./bin/vm -h                 # command-line help
./bin/vm -v                 # version and memory-manager identity
./bin/vm -m 2000000 program.l3a
```

`-m` sets the total VM memory budget in bytes, including code, register frames, heap, and bitmap. The default is 1,000,000 bytes. The host-side GC mark stack is allocated separately.

## Test

Python 3 is the only additional test dependency:

```sh
make test
make test-debug
```

Release and sanitizer builds are stored separately as `bin/vm` and `bin/vm-debug`. The debug build enables assertions, AddressSanitizer, and UndefinedBehaviorSanitizer, with failures treated as test failures. See the [fixture guide](../test/README.md).

The Makefile tracks both C sources and headers. Override toolchain settings with, for example, `make CC=gcc`. `make clean` removes both executables.

## Memory management

Allocation uses 32 segregated free lists and splits larger free blocks. Collection marks reachable blocks from VM register frames using a bitmap and an explicit stack, then sweeps unreachable blocks and coalesces adjacent free space. Block tags determine which fields can contain heap references.

See the [architecture guide](../../docs/architecture.md#memory-management) for the layout and collection sequence.
