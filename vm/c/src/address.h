#ifndef ADDRESS_H
#define ADDRESS_H

#include "vmtypes.h"

// Address conversion (virtual <-> physical)

// Convert a "virtual" (L3VM) address to a "physical" (host computer) one.
inline static value* addr_v_to_p(value* memory_start, value v_addr) {
  return &memory_start[v_addr >> 2];
}

// Convert a "physical" (host computer) address to a "virtuel" (L3VM) one.
inline static value addr_p_to_v(value* memory_start, value* p_addr) {
  return (value)(p_addr - memory_start) << 2;
}

#endif
