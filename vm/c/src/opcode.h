#ifndef OPCODE_H
#define OPCODE_H

typedef enum {
  opcode_ADD, opcode_SUB, opcode_MUL, opcode_DIV, opcode_MOD,
  opcode_LSL, opcode_LSR, opcode_AND, opcode_OR, opcode_XOR,
  opcode_JLT, opcode_JLE, opcode_JEQ, opcode_JNE, opcode_JUMP_I, opcode_JUMP_D,
  opcode_CALL_I, opcode_CALL_D, opcode_RET, opcode_HALT,
  opcode_LDLO, opcode_LDHI, opcode_MOVE, opcode_ARGS, opcode_FRAME,
  opcode_BALO, opcode_BSIZ, opcode_BTAG, opcode_BGET, opcode_BSET, opcode_IO
} opcode;

#endif // OPCODE_H
