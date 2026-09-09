/**
 * Time Obtained on My Machine:
 * Bignums: 1.37
 * Maze: 1.15
 * Queens: 1.22
 * Unimaze: 0.02
 * 
 */
#include <stdio.h>
#include <stdlib.h>
#include <stdint.h>
#include <string.h>
#include <assert.h>
#include <stdbool.h>

#include "memory.h"
#include "fail.h"
#include "block_header.h"
#include "address.h"

// segregated free lists. exact sizes up to 30, then one list for larger blocks
#define FREE_LIST_COUNT 32

// in a register frame, slot 0 is the saved PC and slot 1 the caller frame
#define REGISTER_FRAME_CONTEXT_SIZE 2

// the two top frames are just before the heap and have a normal block layout
#define TOP_FRAME_TOTAL_WORDS (HEADER_SIZE + REGISTER_FRAME_CONTEXT_SIZE + 256)

struct memory {
  // whole VM memory area: code, top frames, heap, then bitmap
  value* start;
  value* end;

  // heap interval, with heap_end excluded
  value* heap_start;
  value* heap_end;
  value* bitmap;
  size_t bitmap_words;

  // used to trigger a collection even before the heap is fully exhausted
  size_t allocated_words_since_gc;

  // free blocks are chained through their first payload word
  value* free_lists[FREE_LIST_COUNT];
};

char* memory_get_identity() {
  return "mark-and-sweep GC";
}

memory* memory_create(size_t total_byte_size) {
  // calloc gives zeroed frame slots, heap words and bitmap bits
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

static size_t ceil_div(size_t numerator, size_t denominator) {
  return (numerator + denominator - 1) / denominator;
}

static size_t bitmap_words_for_available_words(size_t available_words) {
  // split the remaining memory between heap words and one bitmap bit per word
  return ceil_div(available_words, VALUE_BITS + 1);
}

static size_t free_list_index(value size) {
  // small blocks have exact bins; larger ones share the last bin
  return size < FREE_LIST_COUNT - 1 ? size : FREE_LIST_COUNT - 1;
}

static value null_block_addr(void) {
  // heap blocks never live at virtual address 0, so it is a good list end
  return 0;
}

static value* free_block_next(memory* self, value* block) {
  // store free-list links as VM addresses, not host pointers
  assert(block_size(block) > 0);
  value next = block[0];
  return next == null_block_addr() ? NULL : addr_v_to_p(self->start, next);
}

static void free_block_set_next(memory* self, value* block, value* next) {
  // a linked free block needs at least one payload slot for the next pointer
  assert(block_size(block) > 0);
  block[0] = next == NULL ? null_block_addr() : addr_p_to_v(self->start, next);
}

static void clear_free_lists(memory* self) {
  for (size_t i = 0; i < FREE_LIST_COUNT; ++i)
    self->free_lists[i] = NULL;
}

static void insert_free_block(memory* self, value* block) {
  value size = block_size(block);
  if (size == 0)
    return;

  // normal frees use cheap head insertion
  size_t index = free_list_index(size);
  free_block_set_next(self, block, self->free_lists[index]);
  self->free_lists[index] = block;
}

static void append_free_block(memory* self, value* block, value** tails) {
  value size = block_size(block);
  // size-0 blocks have no payload slot for the next pointer; skip them
  // they are header-only fragments that the next sweep will coalesce
  if (size == 0)
    return;

  size_t index = free_list_index(size);
  free_block_set_next(self, block, NULL);

  // sweep appends so free blocks stay in heap order (better cache locality)
  if (tails[index] == NULL)
    self->free_lists[index] = block;
  else
    free_block_set_next(self, tails[index], block);
  tails[index] = block;
}

static size_t bitmap_index(memory* self, value* header) {
  // bitmap bits are indexed by heap header positions
  assert(header >= self->heap_start);
  assert(header < self->heap_end);
  return (size_t)(header - self->heap_start);
}

static bool bitmap_get(memory* self, value* header) {
  size_t index = bitmap_index(self, header);
  return (self->bitmap[index / VALUE_BITS] & ((value)1 << (index % VALUE_BITS))) != 0;
}

static void bitmap_set(memory* self, value* header) {
  size_t index = bitmap_index(self, header);
  self->bitmap[index / VALUE_BITS] |= (value)1 << (index % VALUE_BITS);
}

static void bitmap_clear(memory* self, value* header) {
  size_t index = bitmap_index(self, header);
  self->bitmap[index / VALUE_BITS] &= ~((value)1 << (index % VALUE_BITS));
}

void memory_set_heap_start(memory* self, value* heap_start) {
  assert(heap_start >= self->start);
  assert(heap_start <= self->end);

  self->heap_start = heap_start;

  size_t available_words = (size_t)(self->end - heap_start);
  self->bitmap_words = bitmap_words_for_available_words(available_words);
  size_t heap_words = available_words - self->bitmap_words;

  self->heap_end = self->heap_start + heap_words;
  self->bitmap = self->heap_end;
  self->allocated_words_since_gc = 0;

  clear_free_lists(self);
  memset(self->bitmap, 0, self->bitmap_words * sizeof(value));

  if (heap_words > 0) {
    value* block = self->heap_start + HEADER_SIZE;
    block_set_tag_size(block, tag_FreeBlock, (value)(heap_words - HEADER_SIZE));
    insert_free_block(self, block);
  }
}

typedef struct {
  value** elements;
  size_t size;
  size_t capacity;
} mark_stack;

static void mark_stack_destroy(mark_stack* stack) {
  free(stack->elements);
}

static void mark_stack_push(mark_stack* stack, value* block) {
  if (stack->size == stack->capacity) {
    size_t new_capacity = stack->capacity == 0 ? 256 : stack->capacity * 2;
    value** new_elements =
      realloc(stack->elements, new_capacity * sizeof(value*));
    if (!new_elements)
      fail("cannot allocate mark stack");

    stack->elements = new_elements;
    stack->capacity = new_capacity;
  }

  stack->elements[stack->size++] = block;
}

static value* mark_stack_pop(mark_stack* stack) {
  assert(stack->size > 0);
  return stack->elements[--stack->size];
}

static value* unmarked_heap_block_at(memory* self,
                                     value maybe_pointer,
                                     bool allow_register_frame) {

  // tagged immediates cannot be block pointers
  if ((maybe_pointer & (VALUE_BYTES - 1)) != 0)
    return NULL;

  // VM pointers are virtual byte addresses, so convert back to a word index
  size_t block_index = (size_t)(maybe_pointer >> LOG2_VALUE_BYTES);
  size_t heap_start_index = (size_t)(self->heap_start - self->start);
  size_t heap_end_index = (size_t)(self->heap_end - self->start);

  // a block pointer points to the payload, just after its header
  if (block_index <= heap_start_index || block_index > heap_end_index)
    return NULL;

  value* block = self->start + block_index;
  value* header = block - HEADER_SIZE;

  // reject interior pointers, free blocks, and blocks already marked
  if (!bitmap_get(self, header))
    return NULL;

  tag tag = block_tag(block);
  assert(tag != tag_FreeBlock);
  assert(header + HEADER_SIZE + block_size(block) <= self->heap_end);

  // register frames are only followed through the caller-frame chain
  if (tag == tag_RegisterFrame && !allow_register_frame)
    return NULL;

  return block;
}

static void mark_value(memory* self,
                       mark_stack* stack,
                       value maybe_pointer,
                       bool allow_register_frame) {
  // mark by clearing the bit, then scan the block later from the stack
  value* block = unmarked_heap_block_at(self, maybe_pointer, allow_register_frame);
  if (block == NULL)
    return;

  bitmap_clear(self, block - HEADER_SIZE);
  mark_stack_push(stack, block);
}

static void mark_block_contents(memory* self, mark_stack* stack, value* block) {
  tag tag = block_tag(block);
  value size = block_size(block);

  // scan according to the block layout, so raw code addresses are ignored
  switch (tag) {
  case tag_String:
    // strings contain raw data, not heap references
    return;

  case tag_Function:
    // slot 0 is the wrapper code address; captures start at slot 1
    for (value i = 1; i < size; ++i)
      mark_value(self, stack, block[i], false);
    return;

  case tag_RegisterFrame:
    // skip saved PC. The caller frame is allowed only from slot 1
    if (size > 1)
      mark_value(self, stack, block[1], true);
    for (value i = REGISTER_FRAME_CONTEXT_SIZE; i < size; ++i)
      mark_value(self, stack, block[i], false);
    return;

  default:
    // ordinary blocks may contain L3 values in any field
    for (value i = 0; i < size; ++i)
      mark_value(self, stack, block[i], false);
    return;
  }
}

static value* other_top_frame(memory* self, value* root) {
  // top frames are outside the heap, so find the sibling frame by position
  if (root == NULL || self->heap_start == NULL)
    return NULL;

  // if there is not enough room before the heap, root is not a top frame
  if ((size_t)(self->heap_start - self->start) < 2 * TOP_FRAME_TOTAL_WORDS)
    return NULL;

  value* first_frame = self->heap_start - 2 * TOP_FRAME_TOTAL_WORDS + HEADER_SIZE;
  value* second_frame = self->heap_start - TOP_FRAME_TOTAL_WORDS + HEADER_SIZE;

  if (root == first_frame)
    return second_frame;
  if (root == second_frame)
    return first_frame;
  return NULL;
}

static void memory_mark(memory* self, value* root) {
  // start from the current VM frame
  mark_stack stack = { 0 };

  if (root != NULL) {
    mark_block_contents(self, &stack, root);

    // the other top frame is live only if the caller link points to it
    value* other_frame = other_top_frame(self, root);
    if (other_frame != NULL && root[1] == addr_p_to_v(self->start, other_frame))
      mark_block_contents(self, &stack, other_frame);
  }

  // continue marking through all blocks reached from the roots
  while (stack.size > 0) {
    value* block = mark_stack_pop(&stack);
    mark_block_contents(self, &stack, block);
  }

  mark_stack_destroy(&stack);
}

static void flush_free_run(memory* self,
                           value* header,
                           size_t words,
                           value** tails) {
  // turn one run of adjacent free/dead words into a free block
  if (words == 0)
    return;

  assert(header >= self->heap_start);
  assert(header + words <= self->heap_end);

  value* block = header + HEADER_SIZE;
  block_set_tag_size(block, tag_FreeBlock, (value)(words - HEADER_SIZE));
  bitmap_clear(self, header);
  append_free_block(self, block, tails);
}

static void memory_sweep(memory* self) {
  // rebuild the free lists, coalescing neighbouring free/dead blocks
  clear_free_lists(self);
  value* tails[FREE_LIST_COUNT] = { 0 };

  value* free_run_header = NULL;
  size_t free_run_words = 0;

  for (value* header = self->heap_start; header < self->heap_end; ) {
    value* block = header + HEADER_SIZE;
    value size = block_size(block);
    size_t words = (size_t)size + HEADER_SIZE;
    assert(words > 0);
    assert(header + words <= self->heap_end);

    bool is_free = block_tag(block) == tag_FreeBlock;
    if (!is_free) {
      if (bitmap_get(self, header)) {
        // still set after marking means the block was not reached
        bitmap_clear(self, header);
        is_free = true;
      } else {
        // reachable blocks get their normal allocated bit back
        bitmap_set(self, header);
      }
    } else {
      // free blocks should not look allocated
      bitmap_clear(self, header);
    }

    if (is_free) {
      if (free_run_header == NULL)
        free_run_header = header;
      free_run_words += words;
    } else {
      // a live block ends the current free run
      flush_free_run(self, free_run_header, free_run_words, tails);
      free_run_header = NULL;
      free_run_words = 0;
    }

    header += words;
  }

  flush_free_run(self, free_run_header, free_run_words, tails);
}

static void memory_collect(memory* self, value* root) {
  // stop-the-world collection: mark from roots, then sweep the heap
  memory_mark(self, root);
  memory_sweep(self);
  self->allocated_words_since_gc = 0;
}

static value* find_free_block(memory* self, value size) {
  // first-fit search, starting at the smallest possible free-list bin
  size_t index = free_list_index(size);

  for (size_t i = index; i < FREE_LIST_COUNT; ++i) {
    value* previous = NULL;
    value* current = self->free_lists[i];

    while (current != NULL) {
      value* next = free_block_next(self, current);
      if (block_size(current) >= size) {
        // unlink the chosen free block before reusing it
        if (previous == NULL)
          self->free_lists[i] = next;
        else
          free_block_set_next(self, previous, next);

        return current;
      }

      previous = current;
      current = next;
    }
  }

  return NULL;
}

static value* allocate_from_free_block(memory* self,
                                       value* block,
                                       tag tag,
                                       value size) {
  // reuse the front of this free block and keep the remainder, if any
  value free_size = block_size(block);
  assert(free_size >= size);

  // remaining_words includes the header of the leftover block
  size_t remaining_words = (size_t)(free_size - size);
  block_set_tag_size(block, tag, size);
  bitmap_set(self, block - HEADER_SIZE);

  if (remaining_words > 0) {
    // a one-word remainder can hold only a header (zero payload), so
    // insert_free_block drops it. sweep will coalesce it later
    value* remainder_header = block + size;
    value* remainder_block = remainder_header + HEADER_SIZE;
    block_set_tag_size(remainder_block,
                       tag_FreeBlock,
                       (value)(remaining_words - HEADER_SIZE));
    insert_free_block(self, remainder_block);
  }

  return block;
}

value* memory_allocate(memory* self,
                         tag tag,
                         value size,
                         value* root) {
  // the size field is 24 bits in the block header
  if (size > 0xFFFFFF)
    fail("invalid block size %u", size);

  // also collect after enough allocation, even if a free block still exists
  size_t heap_words = (size_t)(self->heap_end - self->heap_start);
  if (self->allocated_words_since_gc > heap_words / 2)
    memory_collect(self, root);

  // if allocation fails, collect once and try again
  value* block = find_free_block(self, size);
  if (block == NULL) {
    memory_collect(self, root);
    block = find_free_block(self, size);
  }

  if (block == NULL)
    fail("no memory left (block of size %u requested)", size);

  value* allocated = allocate_from_free_block(self, block, tag, size);
  self->allocated_words_since_gc += (size_t)size + HEADER_SIZE;
  return allocated;
}

value* memory_copy_of_block(memory* self, value* block, value* root) {
  // used when the engine evicts a top frame to the heap
  tag tag = block_tag(block);
  value size = block_size(block);
  value* copy = memory_allocate(self, tag, size, root);
  memcpy(copy, block, size * sizeof(value));
  return copy;
}

void memory_free_block(memory* self, value* block) {
  // immediate free used when an evicted frame is copied back to a top frame
  assert(block != NULL);
  assert(block - HEADER_SIZE >= self->heap_start);
  assert(block - HEADER_SIZE < self->heap_end);
  assert(bitmap_get(self, block - HEADER_SIZE));

  bitmap_clear(self, block - HEADER_SIZE);
  block_set_tag_size(block, tag_FreeBlock, block_size(block));
  insert_free_block(self, block);
}
