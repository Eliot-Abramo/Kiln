#include <stdio.h>
#include <stdlib.h>
#include <stdint.h>
#include <string.h>
#include <assert.h>

#include "memory.h"
#include "fail.h"
#include "block_header.h"

struct memory {
  value* start;
  value* end;
  value* free;
};

char* memory_get_identity() {
  return "no GC (memory is never freed)";
}

memory* memory_create(size_t total_byte_size) {
  value* memory_start = calloc(1, total_byte_size);
  if (!memory_start)
    fail("cannot allocate %zd bytes of memory", total_byte_size);
  value* memory_end = memory_start + (total_byte_size / sizeof(value));

  memory* self = calloc(1, sizeof(memory));
  if (!self) fail("cannot allocate memory");
  self->start = memory_start;
  self->end = memory_end;
  return self;
}

void memory_destroy(memory* self) {
  free(self->start);
  free(self);
}

value* memory_get_start(memory* self) {
  return self->start;
}

value* memory_get_end(memory* self) {
  return self->end;
}

void memory_set_heap_start(memory* self, value* heap_start) {
  self->free = heap_start;
}

value* memory_allocate(memory* self,
                         tag tag,
                         value size,
                         value* root) {
  const value total_size = size + HEADER_SIZE;
  if (self->free + total_size > self->end)
    fail("no memory left (block of size %u requested)", size);

  value* block = &self->free[HEADER_SIZE];
  block_set_tag_size(block, tag, size);
  self->free += total_size;

  return block;
}

value* memory_copy_of_block(memory* self, value* block, value* root) {
  tag tag = block_tag(block);
  value size = block_size(block);
  value* copy = memory_allocate(self, tag, size, root);
  memcpy(copy, block, size * sizeof(value));
  return copy;
}

void memory_free_block(memory* self, value* block) {
  // do nothing
}
