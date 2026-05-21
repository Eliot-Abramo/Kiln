#include <stdlib.h>
#include <stdio.h>
#include <stdarg.h>

#include "fail.h"

/* A method to indicate vm failure */
void fail(char* msg, ...) {
  va_list arg_list;
  va_start(arg_list, msg);
  fprintf(stderr, "Error: ");
  vfprintf(stderr, msg, arg_list);
  fprintf(stderr, "\n");
  fflush(stderr);
  va_end(arg_list);
  exit(1);
}
