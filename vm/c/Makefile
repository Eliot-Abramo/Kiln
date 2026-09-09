# L3 virtual machine: release and sanitizer builds use separate executables.
CC = clang
PYTHON ?= python3
CPPFLAGS += -D_POSIX_C_SOURCE=200809L
SRCS = src/engine.c src/fail.c src/main.c src/memory.c
HEADERS = $(wildcard src/*.h)
CFLAGS ?= -std=c17 -Wall -Wextra -O3 -DNDEBUG -fomit-frame-pointer
DEBUG_FLAGS = -std=c17 -Wall -Wextra -g -O1 \
              -fsanitize=address,undefined -fno-sanitize-recover=all \
              -fno-omit-frame-pointer

.PHONY: all vm debug test test-debug clean
all: vm
vm: bin/vm
debug: bin/vm-debug

bin/vm: $(SRCS) $(HEADERS) Makefile
	mkdir -p bin
	$(CC) $(CPPFLAGS) $(CFLAGS) $(SRCS) $(LDFLAGS) -o $@

bin/vm-debug: $(SRCS) $(HEADERS) Makefile
	mkdir -p bin
	$(CC) $(CPPFLAGS) $(DEBUG_FLAGS) $(SRCS) $(LDFLAGS) -o $@

test: vm
	$(PYTHON) ../test/run.py --vm bin/vm

test-debug: debug
	$(PYTHON) ../test/run.py --vm bin/vm-debug

clean:
	rm -rf bin
