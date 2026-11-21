#include <pthread.h>

#include "once.h"

static pthread_once_t g_once = PTHREAD_ONCE_INIT;

void ebur128_once_call(void (*fn)(void)) {
  (void) pthread_once(&g_once, fn);
}
