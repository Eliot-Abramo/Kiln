# VM regression fixtures

These checked-in `.l3a` programs let the VM be tested without building the Scala compiler. Each matching `.test` file defines input and expected output in the original shelltestrunner format.

From the repository root:

```sh
make test-vm
make test-debug
```

Or run a previously built VM directly through the runner:

```sh
python3 vm/test/run.py --vm vm/c/bin/vm --timeout 120
```

The runner uses only Python's standard library. It supports the subset used by these fixtures: optional `<` stdin, `$ VM ...` commands, exact `>` stdout, optional trailing `; echo`, and empty `>=` stderr expectations. It executes the VM directly without a shell and requires a zero exit status and empty stderr for every test. This also makes sanitizer failures visible. Unsupported fixture syntax fails rather than being silently ignored.

Assembly files here are intentional test data; generated assembly elsewhere is ignored by Git. These tests validate the runtime against existing machine programs. The compiler suites and CI's source-to-VM examples separately validate newly generated code.

If shelltestrunner is installed, the original workflow remains available from this directory:

```sh
shelltest -D VM=../c/bin/vm .
```
