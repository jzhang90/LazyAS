#ifndef HASHFUNC_H_
#define HASHFUNC_H_

typedef void (HashFunc)(const void *key, int len, uint32_t seed, void *out);

#endif

