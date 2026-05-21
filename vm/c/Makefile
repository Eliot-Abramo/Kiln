# Makefile for L3VM.

SHELL=/bin/bash

SRCS=src/engine.c	\
     src/fail.c		\
     src/main.c		\
     src/memory.c

# clang sanitizers (see https://clang.llvm.org/docs/)
CLANG_SAN_FLAGS=-fsanitize=address -fsanitize=undefined

# Clang warning flags
CLANG_WARNING_FLAGS=-W				\
                    -Wno-format-nonliteral	\
                    -Wno-c++98-compat		\
                    -Wno-gnu-label-as-value

CFLAGS_COMMON=-std=c17 ${CLANG_WARNING_FLAGS}

# Flags for debugging:
CFLAGS_DEBUG=${CFLAGS_COMMON} ${CLANG_SAN_FLAGS} -g

# Flags for maximum performance:
CFLAGS_RELEASE=${CFLAGS_COMMON} -O3 -DNDEBUG -fomit-frame-pointer

CFLAGS=${CFLAGS_DEBUG}

all: vm

vm: ${SRCS}
	mkdir -p bin
	clang ${CFLAGS} ${LDFLAGS} ${SRCS} -o bin/vm

test: vm
	(cd ../test; shelltest -D VM=../c/bin/vm *test)

clean:
	rm -rf bin
