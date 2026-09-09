SBT ?= sbt

.PHONY: all compiler vm test test-compiler test-vm test-debug demo clean
all: compiler vm

compiler:
	cd compiler && $(SBT) stage

vm:
	$(MAKE) -C vm/c vm

test: test-compiler test-vm

test-compiler:
	cd compiler && $(SBT) test

test-vm:
	$(MAKE) -C vm/c test

test-debug:
	$(MAKE) -C vm/c test-debug

demo: all
	compiler/target/universal/stage/bin/l3c examples/hello.l3
	vm/c/bin/vm out.l3a

clean:
	cd compiler && $(SBT) clean
	$(MAKE) -C vm/c clean
	rm -f out.l3a
