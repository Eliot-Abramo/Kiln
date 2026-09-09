#!/usr/bin/env python3
"""Run the repository's shelltest fixtures using only the Python standard library.

Supports the fixture subset used here: optional stdin, a VM command, exact
stdout, optional trailing `echo`, and an empty `>=` stderr expectation.
Commands are parsed as arguments and never executed through a shell.
"""

import argparse
import difflib
from pathlib import Path
import shlex
import subprocess
import sys


def read_fixture(path):
    lines = path.read_text(encoding="utf-8").splitlines(keepends=True)
    command_index = next(i for i, line in enumerate(lines) if line.startswith("$ "))
    if command_index and lines[0] != "<\n":
        raise ValueError("expected '<' before stdin")
    stdin = "".join(lines[1:command_index]) if command_index else ""
    command = lines[command_index][2:].strip()
    echo = command.endswith("; echo")
    if echo:
        command = command[:-len("; echo")]
    args = shlex.split(command)
    if not args or args[0] != "VM" or any(c in command for c in ";|&<>`$"):
        raise ValueError("expected a VM command")
    if lines[command_index + 1] != ">\n":
        raise ValueError("expected exact stdout marker '>'")
    output = lines[command_index + 2:]
    if ">=\n" in output:
        marker = output.index(">=\n")
        if any(line.strip() for line in output[marker + 1:]):
            raise ValueError("only empty stderr expectations are supported")
        output = output[:marker]
    return args[1:], stdin.encode(), "".join(output).encode(), echo


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--vm", type=Path, required=True)
    parser.add_argument("--timeout", type=float, default=120,
                        help="timeout per fixture in seconds (default: 120)")
    options = parser.parse_args()
    vm = options.vm.resolve()
    if not vm.is_file():
        parser.error(f"VM executable not found: {vm}")
    fixtures = sorted(Path(__file__).parent.glob("*.test"))
    if not fixtures:
        parser.error("no test fixtures found")
    failures = 0
    for fixture in fixtures:
        try:
            args, stdin, expected, echo = read_fixture(fixture)
            result = subprocess.run(
                [str(vm), *args], cwd=fixture.parent, input=stdin,
                capture_output=True, timeout=options.timeout,
            )
            actual = result.stdout + (b"\n" if echo else b"")
            if result.returncode or result.stderr or actual != expected:
                print(f"FAIL {fixture.stem} (exit {result.returncode})")
                if result.stderr:
                    print(result.stderr.decode("utf-8", errors="replace"), end="")
                print("".join(difflib.unified_diff(
                    expected.decode("utf-8", errors="replace").splitlines(True),
                    actual.decode("utf-8", errors="replace").splitlines(True),
                    fromfile="expected", tofile="actual",
                )), end="")
                failures += 1
            else:
                print(f"PASS {fixture.stem}")
        except (OSError, ValueError, IndexError, StopIteration,
                subprocess.TimeoutExpired) as error:
            print(f"FAIL {fixture.stem}: {error}")
            failures += 1
    print(f"\n{len(fixtures) - failures}/{len(fixtures)} VM tests passed")
    return bool(failures)


if __name__ == "__main__":
    sys.exit(main())
