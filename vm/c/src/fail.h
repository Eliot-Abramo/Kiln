#ifndef FAIL_H
#define FAIL_H

// Fail with the given `msg` (supporting printf-like formatting).
extern void fail(char* msg, ...) __attribute__ ((noreturn));

#endif // FAIL_H
