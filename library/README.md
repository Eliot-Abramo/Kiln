# Introduction

This directory contains a basic library for L₃. It defines two kinds of modules:

1.  Modules providing functions on values of the built-in types (unit, booleans, characters and integers).

2.  Modules providing new types and functions to operate on these types. All these types are represented as tagged blocks.

# Modules

The table below lists the modules belonging to the standard library, their prefix and the block tag(s) they use, if any. Both the prefix and the tag(s) must be globally unique.

Modules for predefined, tagged types:

| Module       | Prefix    | Note                                |
|--------------|-----------|-------------------------------------|
| `booleans`   | `boolean` |                                     |
| `characters` | `char`    |                                     |
| `integers`   | `int`     | Operators (+, <, …) aren't prefixed |
| `unit`       | `unit`    |                                     |

Modules for additional types, predefined or not but represented as tagged blocks with the given tag(s):

| Module          | Prefix     | Tag(s)      |
|-----------------|------------|-------------|
| `pairs`         | `pair`     | `pair`      |
| `vectors`       | `vector`   | `vector`    |
| `lists`         | `list`     | `list`      |
| `disjoint-sets` | `diset`    | `diset`     |
| `random`        | `rng`      | `rng`       |
| `strings`       | `string`   | `_string`   |
| `functions`     | `function` | `_function` |

A meta-module called `lib` requires all the above modules.

# Naming conventions

With a few exceptions, all entities defined by the various modules obey the following naming conventions:

- Entities defined in a module have a name starting with the (globally unique) prefix assigned to that module, given in the tables above.

- Private entities that are not meant to be used outside of the module they are defined in have a name starting with a `%`, followed by the module prefix.

- Functions with side-effect have a name ending with a `!`.

- Predicates (functions returning a boolean) have a name ending with a `?`. Type-testing predicates are simply named by concatenating the prefix of that type with a `?`.

- Conversion functions from a type *T* to a type *U* have a name formed by concatenating the prefix of *T*, the string `->` and the prefix of *U*.
