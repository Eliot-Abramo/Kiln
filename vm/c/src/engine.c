#define _DEFAULT_SOURCE

#include <assert.h>
#include <stdio.h>
#include <stdbool.h>
#include <string.h>

#include "vmtypes.h"
#include "address.h"
#include "engine.h"
#include "opcode.h"
#include "memory.h"
#include "fail.h"

#include "block_header.h"

#define CONTEXT_SIZE 2

struct engine {
  memory* memory;
  value* free_boundary;
};

engine* engine_create(memory* memory) {
  engine* self = calloc(1, sizeof(engine));
  if (!self) fail("cannot allocate engine");

  self->memory = memory;
  self->free_boundary = memory_get_start(memory);

  return self;
}

void engine_destroy(engine* self) {
  free(self);
}

void engine_emit_instruction(engine* self, value instruction) {
  *(self->free_boundary++) = instruction;
}

// Instruction decoding

static inline unsigned int instr_extract_u(value instr, int start, int len) {
  return (instr >> start) & ((1 << len) - 1);
}

static inline int instr_extract_s(value instr, int start, int len) {
  int bits = (int)instr_extract_u(instr, start, len);
  int m = 1 << (len - 1);
  return (bits ^ m) - m;
}

static inline opcode instr_opcode(value instr) {
  return (opcode)instr_extract_u(instr, 27, 5);
}

static inline int instr_d11(value instr) {
  return instr_extract_s(instr, 16, 11);
}

static inline int instr_d19(value instr) {
  return instr_extract_s(instr, 8, 19);
}

static inline int instr_d27(value instr) {
  return instr_extract_s(instr, 0, 27);
}

// Register access macros (unsigned and signed)

#define Ra curr_frame[CONTEXT_SIZE + instr_extract_u(*pc,  0, 8)]
#define Rb curr_frame[CONTEXT_SIZE + instr_extract_u(*pc,  8, 8)]
#define Rc curr_frame[CONTEXT_SIZE + instr_extract_u(*pc, 16, 8)]

#define sRa (svalue)Ra
#define sRb (svalue)Rb
#define sRc (svalue)Rc

// Instruction definition macros

// A "bare" instruction (i.e. a labeled block)
#define INSTR(L, B) L: { B; }

// An instruction prefetching the next instruction, to improve performance
// (to see the impact, exchange the "B;" line with its predecessor).
#define INSTR_P(L, P, B)                                        \
  INSTR(L, {                                                    \
      value* next_pc = P;                                     \
      void* next_label = labels[instr_opcode(*next_pc)];        \
      B;                                                        \
      pc = next_pc;                                             \
      goto *next_label;                                         \
    })

// A "linear" instruction, whose successor is the one that directly follows it
#define INSTR_L(L, B) INSTR_P(L, pc + 1, B)

// A jump instruction, doing nothing else than computing the next PC (no body)
#define INSTR_J(L, P) INSTR_P(L, P, {})

// A call instruction, which saves the caller's context in the frame
// of the callee, before switching to it.
#define INSTR_C(L, P) INSTR_P(L, P, {                           \
      value* callee_frame = other_frame;                      \
      callee_frame[0] = addr_p_to_v(memory_start, pc + 1);      \
      callee_frame[1] = addr_p_to_v(memory_start, curr_frame);  \
      other_frame = curr_frame;                                 \
      curr_frame = callee_frame;                                \
    })

value engine_run(engine* self) {
  // Emit top frames

  value empty_frame[] = {
    header_pack(tag_RegisterFrame, CONTEXT_SIZE),    // header
    0, 0,                                            // context
    0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,  // registers
    0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
    0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
    0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
    0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
    0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
    0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
    0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
    0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
    0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
    0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
    0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
    0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
    0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
    0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07,  // constant registers
    0x08, 0x09, 0x0a, 0x0b, 0x0c, 0x0d, 0x0e, 0x0f,
    0x10, 0x11, 0x12, 0x13, 0x14, 0x15, 0x16, 0x17,
    0x18, 0x19, 0x1a, 0x1b, 0x1c, 0x1d, 0x1e, 0x1f,
  };

  memcpy(self->free_boundary, empty_frame, sizeof(empty_frame));
  value* curr_frame = &self->free_boundary[HEADER_SIZE];
  self->free_boundary += sizeof(empty_frame) / sizeof(value);

  memcpy(self->free_boundary, empty_frame, sizeof(empty_frame));
  value* other_frame = &self->free_boundary[HEADER_SIZE];
  self->free_boundary += sizeof(empty_frame) / sizeof(value);

  value* memory_start = memory_get_start(self->memory);
  memory_set_heap_start(self->memory, self->free_boundary);

  // Interpret program

  void* labels[] = {
    [opcode_ADD]    = &&l_ADD,
    [opcode_SUB]    = &&l_SUB,
    [opcode_MUL]    = &&l_MUL,
    [opcode_DIV]    = &&l_DIV,
    [opcode_MOD]    = &&l_MOD,
    [opcode_LSL]    = &&l_LSL,
    [opcode_LSR]    = &&l_LSR,
    [opcode_AND]    = &&l_AND,
    [opcode_OR]     = &&l_OR,
    [opcode_XOR]    = &&l_XOR,
    [opcode_JLT]    = &&l_JLT,
    [opcode_JLE]    = &&l_JLE,
    [opcode_JEQ]    = &&l_JEQ,
    [opcode_JNE]    = &&l_JNE,
    [opcode_JUMP_I] = &&l_JUMP_I,
    [opcode_JUMP_D] = &&l_JUMP_D,
    [opcode_CALL_I] = &&l_CALL_I,
    [opcode_CALL_D] = &&l_CALL_D,
    [opcode_RET]    = &&l_RET,
    [opcode_HALT]   = &&l_HALT,
    [opcode_LDLO]   = &&l_LDLO,
    [opcode_LDHI]   = &&l_LDHI,
    [opcode_MOVE]   = &&l_MOVE,
    [opcode_ARGS]   = &&l_ARGS,
    [opcode_FRAME]  = &&l_FRAME,
    [opcode_BALO]   = &&l_BALO,
    [opcode_BSIZ]   = &&l_BSIZ,
    [opcode_BTAG]   = &&l_BTAG,
    [opcode_BGET]   = &&l_BGET,
    [opcode_BSET]   = &&l_BSET,
    [opcode_IO]     = &&l_IO,
  };

  value* pc = memory_start;
  goto *labels[instr_opcode(*pc)];

  INSTR_L(l_ADD, Ra = Rb + Rc);
  INSTR_L(l_SUB, Ra = Rb - Rc);
  INSTR_L(l_MUL, Ra = Rb * Rc);
  INSTR_L(l_DIV, Ra = (value)(sRb / sRc));
  INSTR_L(l_MOD, Ra = (value)(sRb % sRc));
  INSTR_L(l_LSL, Ra = Rb << (Rc & 0x1F));
  INSTR_L(l_LSR, Ra = (value)(sRb >> (Rc & 0x1F)));
  INSTR_L(l_AND, Ra = Rb & Rc);
  INSTR_L(l_OR,  Ra = Rb | Rc);
  INSTR_L(l_XOR, Ra = Rb ^ Rc);

  INSTR_J(l_JLT, pc + (sRa < sRb ? instr_d11(*pc) : 1));
  INSTR_J(l_JLE, pc + (sRa <= sRb ? instr_d11(*pc) : 1));
  INSTR_J(l_JEQ, pc + (Ra == Rb ? instr_d11(*pc) : 1));
  INSTR_J(l_JNE, pc + (Ra != Rb ? instr_d11(*pc) : 1));
  INSTR_J(l_JUMP_I, addr_v_to_p(memory_start, Ra));
  INSTR_J(l_JUMP_D, pc + instr_d27(*pc));

  INSTR_C(l_CALL_I, addr_v_to_p(memory_start, Rb));
  INSTR_C(l_CALL_D, pc + instr_d19(*pc));

  INSTR_P(l_RET, addr_v_to_p(memory_start, curr_frame[0]), {
      value call_instr = addr_v_to_p(memory_start, curr_frame[0])[-1];
      unsigned int ret_reg = instr_extract_u(call_instr,  0, 8);
      value ret_value = Ra;

      value* caller_frame = addr_v_to_p(memory_start, curr_frame[1]);
      if (caller_frame == other_frame) {
        // Caller frame already loaded, switch to it
        other_frame = curr_frame;
        curr_frame = caller_frame;
      } else {
        // Caller frame in heap, copy it to current
        int caller_size = block_size(caller_frame) + HEADER_SIZE;
        memcpy(&curr_frame[-HEADER_SIZE],
               &caller_frame[-HEADER_SIZE],
               caller_size * sizeof(value));
        memory_free_block(self->memory, caller_frame);
      }
      curr_frame[CONTEXT_SIZE + ret_reg] = ret_value;
    });

  INSTR(l_HALT, return Ra);

  INSTR_L(l_LDLO, Ra = (value)instr_extract_s(*pc, 8, 19));
  INSTR_L(l_LDHI, {
      value ms16b = (value)instr_extract_u(*pc, 8, 16) << 16;
      Ra = ms16b | (Ra & 0xFFFF);
    });

  INSTR_L(l_MOVE, Ra = Rb);

  // ARGS instructions are "fused", in that a succession of them is
  // interpreted as one instruction by the code below. Therefore, it
  // is defined as a "bare" instruction, without prefetching.
  INSTR(l_ARGS, {
      if (curr_frame[1] == addr_p_to_v(memory_start, other_frame)) {
        // Other top frame already occupied, evict it to the heap
        value* other_copy =
          memory_copy_of_block(self->memory, other_frame, curr_frame);
        curr_frame[1] = addr_p_to_v(memory_start, other_copy);
      }

      int i = CONTEXT_SIZE;
      do {
        other_frame[i++] = Ra; other_frame[i++] = Rb; other_frame[i++] = Rc;
        pc += 1;
      } while (instr_opcode(*pc) == opcode_ARGS);
      block_set_tag_size(other_frame, tag_RegisterFrame, i);
      goto *labels[instr_opcode(*pc)];
    });

  INSTR_L(l_FRAME, {
      int new_size = CONTEXT_SIZE + instr_extract_u(*pc, 0, 8);
      int curr_size = block_size(curr_frame);
      int size_to_clear = (new_size - curr_size) * sizeof(value);
      if (size_to_clear > 0)
        memset(&curr_frame[curr_size], 0, size_to_clear);
      block_set_tag_size(curr_frame, tag_RegisterFrame, new_size);
    });

  INSTR_L(l_BALO, {
      value* block = memory_allocate(self->memory, Rb, Rc, curr_frame);
      Ra = addr_p_to_v(memory_start, block);
    });
  INSTR_L(l_BSIZ, Ra = block_size(addr_v_to_p(memory_start, Rb)));
  INSTR_L(l_BTAG, Ra = block_tag(addr_v_to_p(memory_start, Rb)));
  INSTR_L(l_BGET, {
      assert(Rc < block_size(addr_v_to_p(memory_start, Rb)));
      Ra = addr_v_to_p(memory_start, Rb)[Rc];
    });
  INSTR_L(l_BSET, {
      assert(Rc < block_size(addr_v_to_p(memory_start, Rb)));
      addr_v_to_p(memory_start, Rb)[Rc] = Ra;
    });

  INSTR_L(l_IO, {
      switch (instr_extract_u(*pc, 8, 8)) {
      case 0: {
        int maybe_byte = getchar_unlocked();
        Ra = (value)(maybe_byte != EOF ? maybe_byte : -1);
      } break;

      case 1: {
        putchar_unlocked((uint8_t)Ra);
      } break;

      default:
        fail("invalid I/O command");
      }
    });
}
