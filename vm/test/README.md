This directory contains VM tests that are meant to be run using [shelltestrunner](https://github.com/simonmichael/shelltestrunner).

When running these tests, the VM shelltest macro must be defined, and point to the executable of the VM to test. This means that the C VM can be tested as follows (after having been built with `make`):

``` shell
$ shelltest -D VM=../c/bin/vm .
```

while the Rust VM can be tested as follows (after having been built with `cargo build --release`):

``` shell
$ shelltest -D VM=../rust/target/release/l3vm .
```
