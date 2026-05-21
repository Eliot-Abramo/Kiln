#ifndef MEMORY_H
#define MEMORY_H

#include <stdlib.h>
#include "vmtypes.h"

typedef enum {
  tag_FreeBlock = 0x00,
  tag_Function = 0x01,
  tag_String = 0x02,
  tag_RegisterFrame = 0xFF
} tag;

typedef struct memory memory;

// Returns a string identifying the memory module.
char* memory_get_identity(void);

// Create a memory module with `total_byte_size` memory.
memory* memory_create(size_t total_byte_size);

// Release the memory module.
void memory_destroy(memory* self);

// Get the address of the beginning of the memory area.
value* memory_get_start(memory* self);

// Get the address just after the end of the memory area.
value* memory_get_end(memory* self);

// Set the heap start, following the code area.
void memory_set_heap_start(memory* self, value* heap_start);

// Allocate a block with the given `tag` and `size`, using `root` (the
// frame pointer) in case garbage collection has to be performed.
value* memory_allocate(memory* self, tag tag, value size, value* root);

// Copy `block` in a newly-allocated block, using `root` (the frame
// pointer) in case garbage collection has to be performed.
value* memory_copy_of_block(memory* self, value* block, value* root);

// Free `block` (withouht having to wait for next garbage collection).
void memory_free_block(memory* self, value* block);

#endif
