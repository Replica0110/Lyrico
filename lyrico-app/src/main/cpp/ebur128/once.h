#ifndef ONCE_H_
#define ONCE_H_

#ifdef __cplusplus
extern "C" {
#endif

/*
 * Calls `fn` exactly once per process, even if multiple threads are present.
 * `fn` must always be the same function.
 */
void ebur128_once_call(void (*fn)(void));

#ifdef __cplusplus
}
#endif

#endif
