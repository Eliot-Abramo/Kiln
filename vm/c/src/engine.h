#ifndef ENGINE__H
#define ENGINE__H

#include "vmtypes.h"
#include "memory.h"

typedef struct engine engine;

// Create an engine module attached to the `memory` module.
engine* engine_create(memory* memory);

// Release the engine module.
void engine_destroy(engine* self);

// Emit `instruction` after the last one emitted (starting at 0).
void engine_emit_instruction(engine* self, value instruction);

// Run the program, starting with the first emitted instruction.
value engine_run(engine* self);

#endif // ENGINE__H
