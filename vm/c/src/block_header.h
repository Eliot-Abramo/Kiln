#ifndef BLOCK_HEADER_H
#define BLOCK_HEADER_H

#include "vmtypes.h"
#include "memory.h"

// Header management

#define HEADER_SIZE 1

// Pack a block `tag` (8 bits) and `size` (24 bits) into a header word.
inline static value header_pack(tag tag, value size) {
  return ((value)tag << 24) | size;
}

// Extract the tag of `header`.
inline static tag header_unpack_tag(value header) {
  return (tag)((header >> 24) & 0xFF);
}

// Extract the size of `header`.
inline static value header_unpack_size(value header) {
  return header & 0xFFFFFF;
}

// Get the header of `block`.
inline static value block_header(const value* block) {
  return block[-HEADER_SIZE];
}

// Set the header of `block`.
inline static void block_set_header(value* block, value header) {
  block[-HEADER_SIZE] = header;
}

inline static void block_set_tag_size(value* block, tag tag, value size) {
  block[-HEADER_SIZE] = header_pack(tag, size);
}

// Get the tag of `block`.
inline static tag block_tag(const value* block) {
  return header_unpack_tag(block_header(block));
}

// Get the size of `block`.
inline static value block_size(const value* block) {
  return header_unpack_size(block_header(block));
}

#endif
