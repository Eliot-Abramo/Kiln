#include <string.h>
#include <stdio.h>
#include <stdlib.h>
#include <stdalign.h>
#include <assert.h>

#include "memory.h"
#include "engine.h"
#include "fail.h"

typedef struct {
  size_t memory_size;
  char* file_name;
} options;

static options default_options = { 1000000, NULL };

// Argument parsing

static void display_usage(char* prog_name) {
  printf("Usage: %s [<options>] <asm_file>\n", prog_name);
  printf("\noptions:\n");
  printf("  -h         display this help message and exit\n");
  printf("  -m <size>  set memory size in bytes (default %zd)\n",
         default_options.memory_size);
  printf("  -v         display version and exit\n");
}

static void parse_args(int argc, char* argv[], options* opts) {
  int i = 1;
  while (i < argc) {
    char* arg = argv[i++];
    const size_t arg_len = strlen(arg);

    if (arg_len == 2 && arg[0] == '-') {
      switch (arg[1]) {
      case 'm': {
        if (i >= argc) {
          display_usage(argv[0]);
          fail("missing argument to -m");
        }
        opts->memory_size = strtoul(argv[i++], NULL, 10);
      } break;

      case 'h': {
        display_usage(argv[0]);
        exit(0);
      }

      case 'v': {
        printf("vm v1.0\n");
        printf("  memory module: %s\n", memory_get_identity());
        exit(0);
      }

      default:
        display_usage(argv[0]);
        fail("invalid option %s", arg);
      }
    } else
      opts->file_name = arg;
  }
}

// Memory/size alignment

static size_t align_down(size_t value, size_t align) {
  assert(align > 0 && (align & (align - 1)) == 0); /* check power of 2 */
  return value & ~(align - 1);
}

// ASM file loading

static void load_file(char* file_name, engine* engine) {
  FILE* file = fopen(file_name, "r");
  if (file == NULL)
    fail("cannot open file %s", file_name);

  char line[1000];
  while (fgets(line, sizeof(line), file) != NULL) {
    value instr;
    int read_count = sscanf(line, "%8x", &instr);
    if (read_count != 1)
      fail("error while reading file %s", file_name);
    engine_emit_instruction(engine, instr);
  }
  fclose(file);
}

int main(int argc, char* argv[]) {
  options options = default_options;
  parse_args(argc, argv, &options);
  if (options.file_name == NULL) {
    display_usage(argv[0]);
    fail("missing input file name");
  }
  if (options.memory_size == 0)
    fail("invalid memory size %zd", options.memory_size);

  const int value_align = alignof(value);

  memory* memory = memory_create(align_down(options.memory_size, value_align));
  if (!memory) fail("cannot create memory");
  engine* engine = engine_create(memory);
  if (!engine) fail("cannot create engine");

  load_file(options.file_name, engine);
  value halt_code = engine_run(engine);

  engine_destroy(engine);
  memory_destroy(memory);

  return (int)halt_code;
}
