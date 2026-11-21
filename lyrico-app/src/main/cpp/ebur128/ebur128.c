/* See COPYING file for copyright and license details. */

#include "ebur128.h"

#include <assert.h>
#include <float.h>
#include <limits.h>
#include <math.h> /* You may have to define _USE_MATH_DEFINES if you use MSVC */
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

/* This can be replaced by any BSD-like queue implementation. */
#include <sys/queue.h>

#include "once.h"

#ifndef M_PI
#define M_PI 3.14159265358979323846
#endif

/* This is needed to suppress a clang-tidy warning. */
#ifndef __has_builtin
#define __has_builtin(x) 0
#endif

#define CHECK_ERROR(condition, errorcode, goto_point)                          \
  if ((condition)) {                                                           \
    errcode = (errorcode);                                                     \
    goto goto_point;                                                           \
  }

#define EBUR128_MAX(a, b) (((a) > (b)) ? (a) : (b))

#if defined(_MSC_VER)
#define EBUR128_FORCE_INLINE __forceinline
#elif defined(__GNUC__) || defined(__clang__)
#define EBUR128_FORCE_INLINE __inline__ __attribute__((always_inline))
#else
#define EBUR128_FORCE_INLINE
#endif

static int safe_size_mul(size_t* out, size_t nmemb, size_t size) {
  /* Adapted from OpenBSD reallocarray. */
#define MUL_NO_OVERFLOW (((size_t) 1) << (sizeof(size_t) * 4))
  if ((nmemb >= MUL_NO_OVERFLOW ||
       size >= MUL_NO_OVERFLOW) && /* LCOV_EXCL_BR_LINE */
      nmemb > 0 &&                 /* LCOV_EXCL_BR_LINE */
      ((size_t) (-1)) / nmemb < size) {
    return EBUR128_ERROR_NOMEM;
  }
#undef MUL_NO_OVERFLOW
  *out = nmemb * size;
  return EBUR128_SUCCESS;
}

/* Computes (a * b) / 1000. Returns EBUR128_ERROR_NOMEM if the result
 * cannot be represented as unsigned long. */
static int mul_div_1000(unsigned long* out, unsigned long a, unsigned long b) {
  unsigned long a_hi, a_lo;
  unsigned long b_hi, b_lo;
  unsigned long term1, term2, term3, term4;

  /* Split a and b into (q * 1000 + r) */
  a_hi = a / 1000UL;
  a_lo = a % 1000UL;
  b_hi = b / 1000UL;
  b_lo = b % 1000UL;

  /*
   * Algebraic expansion of (a * b) / 1000:
   *
   *   (a * b) / 1000
   * = (a_hi * 1000 + a_lo) * (b_hi * 1000 + b_lo) / 1000
   * =   ( a_hi * b_hi * 1000)   // term1
   *   + ( a_hi * b_lo)          // term2
   *   + ( a_lo * b_hi)          // term3
   *   + ((a_lo * b_lo) / 1000)  // term4
   */

  /* Check term1: a_hi * b_hi * 1000 */
  if (a_hi > 0UL && b_hi > (ULONG_MAX / 1000UL) / a_hi) {
    return EBUR128_ERROR_NOMEM;
  }
  term1 = a_hi * b_hi * 1000UL;

  /* Safe from overflow */
  term2 = a_hi * b_lo;
  term3 = a_lo * b_hi;
  term4 = (a_lo * b_lo) / 1000UL;

  /* Safely sum all four terms */
#define ADD_TERM(term)                                                         \
  do {                                                                         \
    if (term1 > ULONG_MAX - (term)) {                                          \
      return EBUR128_ERROR_NOMEM;                                              \
    }                                                                          \
    term1 += (term);                                                           \
  } while (0)

  ADD_TERM(term2);
  ADD_TERM(term3); /* LCOV_EXCL_BR_LINE */
  ADD_TERM(term4); /* LCOV_EXCL_BR_LINE */

#undef ADD_TERM

  *out = term1;
  return EBUR128_SUCCESS;
}

static int calculate_interval_frames(size_t* out,
                                     unsigned long samplerate,
                                     unsigned long window,
                                     unsigned long frames_in_100ms) {
  int errcode;
  unsigned long tmp;

  errcode = mul_div_1000(&tmp, samplerate, window);
  CHECK_ERROR(errcode, errcode, exit)

  if (tmp > ((size_t) -1) - frames_in_100ms) {
    errcode = EBUR128_ERROR_NOMEM;
    goto exit;
  }

  /* round up to multiple of frames_in_100ms */
  if (tmp % frames_in_100ms) {
    tmp = (tmp + frames_in_100ms) - (tmp % frames_in_100ms);
  }

  *out = (size_t) tmp;

    exit:
  return errcode;
}

STAILQ_HEAD(ebur128_double_queue, ebur128_dq_entry);
struct ebur128_dq_entry {
    double z;
    STAILQ_ENTRY(ebur128_dq_entry) entries;
};

#define ALMOST_ZERO 0.000001
#define FILTER_STATE_SIZE 5

typedef struct {
    unsigned int count;  /* Number of coefficients in this subfilter */
    unsigned int* index; /* Delay index of corresponding filter coeff */
    double* coeff;       /* List of subfilter coefficients */
} interp_filter;

typedef struct {         /* Data structure for polyphase FIR interpolator */
    unsigned int factor;   /* Interpolation factor of the interpolator */
    unsigned int taps;     /* Taps (prefer odd to increase zero coeffs) */
    unsigned int channels; /* Number of channels */
    unsigned int delay;    /* Size of delay buffer */
    interp_filter* filter; /* List of subfilters (one for each factor) */
    float** z;             /* List of delay buffers (one for each channel) */
    unsigned int zi;       /* Current delay buffer index */
} interpolator;

typedef double (*interp_reduce_fn)(void* context,
                                   unsigned int channel,
                                   double accumulator,
                                   float sample);

/** BS.1770 filter state. */
typedef double filter_state[FILTER_STATE_SIZE];

/** Cached energy for one 100 ms block. */
struct ebur128_block_energy {
    /** Channel-weighted energy of the block. */
    double energy;
    /** Whether the cached energy needs to be recomputed. */
    unsigned char dirty;
};

typedef void (*ebur128_energy_chunk_fn)(ebur128_state* st,
                                        void* context,
                                        size_t position,
                                        size_t chunk_number,
                                        size_t position_in_chunk,
                                        size_t take);

struct ebur128_state_internal {
    /** Filtered audio data (used as ring buffer). */
    double* audio_data;
    /** Size of audio_data array in frames. */
    size_t audio_data_frames;
    /** Current frame index into audio_data. Always smaller than
     * `audio_data_frames` */
    size_t audio_data_frame_index;
    /** How many frames are needed for a gating block. Will correspond to 400ms
     *  of audio at initialization, and 100ms after the first block (75% overlap
     *  as specified in the 2011 revision of BS1770). */
    unsigned long needed_frames;
    /** The channel map. Has as many elements as there are channels. */
    int* channel_map;
    /** How many frames fit in 100ms (rounded). */
    unsigned long frames_in_100ms;
    /** BS.1770 filter coefficients (nominator). */
    double b[5];
    /** BS.1770 filter coefficients (denominator). */
    double a[5];
    /** one filter_state per channel. */
    filter_state* v;
    /** Linked list of block energies. */
    struct ebur128_double_queue block_list;
    unsigned long block_list_max;
    unsigned long block_list_size;
    /** Linked list of 3s-block energies, used to calculate LRA. */
    struct ebur128_double_queue short_term_block_list;
    unsigned long st_block_list_max;
    unsigned long st_block_list_size;
    int use_histogram;
    unsigned long* block_energy_histogram;
    unsigned long* short_term_block_energy_histogram;
    /** Keeps track of when a new short term block is needed. */
    size_t short_term_frame_counter;
    /** Maximum sample peak, one per channel */
    double* sample_peak;
    double* prev_sample_peak;
    /** Maximum true peak, one per channel */
    double* true_peak;
    double* prev_true_peak;
    interpolator* interp;
    float* resampler_buffer_input;
    size_t resampler_buffer_input_frames;
    /** The maximum window duration in ms. */
    unsigned long window;
    unsigned long history;
    /** Cached energy for each 100 ms block. */
    struct ebur128_block_energy* block_energy_cache;
    /** Number of 100 ms blocks in audio_data. */
    size_t block_energy_cache_size;
};

static const double relative_gate = -10.0;

/* Those will be calculated when initializing the library */
static double relative_gate_factor;
static double minus_twenty_decibels;
static double histogram_energies[1000];
static double histogram_energy_boundaries[1001];

static void ebur128_init_library(void) {
  int i;

  relative_gate_factor = pow(10.0, relative_gate / 10.0);
  minus_twenty_decibels = pow(10.0, -20.0 / 10.0);

  for (i = 0; i < 1000; ++i) {
    histogram_energies[i] =
            pow(10.0, ((double) i / 10.0 - 69.95 + 0.691) / 10.0);
  }

  histogram_energy_boundaries[0] = pow(10.0, (-70.0 + 0.691) / 10.0);
  for (i = 1; i < 1001; ++i) {
    histogram_energy_boundaries[i] =
            pow(10.0, ((double) i / 10.0 - 70.0 + 0.691) / 10.0);
  }
}

static interpolator*
interp_create(unsigned int taps, unsigned int factor, unsigned int channels) {
  int errcode; /* unused */
  interpolator* interp;
  unsigned int j;

  (void) errcode;

  interp = (interpolator*) calloc(1, sizeof(interpolator));
  CHECK_ERROR(!interp, 0, exit);

  interp->taps = taps;
  interp->factor = factor;
  interp->channels = channels;
  interp->delay = (interp->taps + interp->factor - 1) / interp->factor;

  /* Initialize the filter memory
   * One subfilter per interpolation factor. */
  interp->filter =
          (interp_filter*) calloc(interp->factor, sizeof(*interp->filter));
  CHECK_ERROR(!interp->filter, 0, free_interp);

  for (j = 0; j < interp->factor; j++) {
    interp->filter[j].index =
            (unsigned int*) calloc(interp->delay, sizeof(unsigned int));
    interp->filter[j].coeff = (double*) calloc(interp->delay, sizeof(double));
    CHECK_ERROR(!interp->filter[j].index || !interp->filter[j].coeff, 0,
                free_filter_index_coeff);
  }

  /* One delay buffer per channel. */
  interp->z = (float**) calloc(interp->channels, sizeof(float*));
  CHECK_ERROR(!interp->z, 0, free_filter_index_coeff);
  for (j = 0; j < interp->channels; j++) {
    interp->z[j] = (float*) calloc(interp->delay, sizeof(float));
    CHECK_ERROR(!interp->z[j], 0, free_filter_z);
  }

  /* Calculate the filter coefficients */
  for (j = 0; j < interp->taps; j++) {
    /* Calculate sinc */
    double m = (double) j - (double) (interp->taps - 1) / 2.0;
    double c = 1.0;
    if (fabs(m) > ALMOST_ZERO) {
      c = sin(m * M_PI / interp->factor) / (m * M_PI / interp->factor);
    }
    /* Apply Hanning window */
    c *= 0.5 * (1 - cos(2 * M_PI * j / (interp->taps - 1)));

    if (fabs(c) > ALMOST_ZERO) { /* Ignore any zero coeffs. */
      /* Put the coefficient into the correct subfilter */
      unsigned int f = j % interp->factor;
      unsigned int t = interp->filter[f].count++;
      interp->filter[f].coeff[t] = c;
      interp->filter[f].index[t] = j / interp->factor;
    }
  }
  return interp;

    free_filter_z:
  for (j = 0; j < interp->channels; j++) {
    free(interp->z[j]);
  }
  free(interp->z);
    free_filter_index_coeff:
  for (j = 0; j < interp->factor; j++) {
    free(interp->filter[j].index);
    free(interp->filter[j].coeff);
  }
  free(interp->filter);
    free_interp:
  free(interp);
    exit:
  return NULL;
}

static void interp_destroy(interpolator* interp) {
  unsigned int j = 0;
  if (!interp) {
    return;
  }
  for (j = 0; j < interp->factor; j++) {
    free(interp->filter[j].index);
    free(interp->filter[j].coeff);
  }
  free(interp->filter);
  for (j = 0; j < interp->channels; j++) {
    free(interp->z[j]);
  }
  free(interp->z);
  free(interp);
}

static EBUR128_FORCE_INLINE void interp_process(interpolator* interp,
                                                size_t frames,
                                                const float* in,
                                                double* accumulators,
                                                interp_reduce_fn reduce,
                                                void* context) {
  size_t frame = 0;
  unsigned int chan = 0;
  unsigned int f = 0;
  unsigned int t = 0;
  const unsigned int factor = interp->factor;
  const unsigned int channels = interp->channels;
  const unsigned int delay = interp->delay;
  const interp_filter* filter = interp->filter;
  int zi = 0;
  double accumulator = 0;
  double acc = 0;
  double c = 0;
  float* z = 0;
  const unsigned int* index = 0;
  const double* coeff = 0;
  unsigned int count = 0;

  for (frame = 0; frame < frames; frame++) {
    zi = (int) interp->zi;
    for (chan = 0; chan < channels; chan++) {
      /* Add sample to delay buffer */
      z = interp->z[chan];
      z[zi] = *in++;
      /* Apply coefficients */
      accumulator = accumulators[chan];
      for (f = 0; f < factor; f++) {
        acc = 0.0;
        count = filter[f].count;
        index = filter[f].index;
        coeff = filter[f].coeff;
        for (t = 0; t < count; t++) {
          int i = zi - (int) index[t];
          if (i < 0) {
            i += (int) delay;
          }
          c = coeff[t];
          acc += (double) z[i] * c;
        }
        accumulator = reduce(context, chan, accumulator, (float) acc);
      }
      accumulators[chan] = accumulator;
    }
    interp->zi++;
    if (interp->zi == delay) {
      interp->zi = 0;
    }
  }
}

/*
 * Allocates a zeroed out piece of memory for `audio_data` appropriate for `st`.
 *
 * These must be set:
 *
 * - `st->samplerate`
 * - `st->d->frames_in_100ms`
 * - `st->channels`
 */
static int ebur128_create_audio_data(double** audio_data,
                                     size_t* audio_data_frames,
                                     const ebur128_state* st,
                                     unsigned long window) {
  int errcode;

  errcode = calculate_interval_frames(audio_data_frames, st->samplerate, window,
                                      st->d->frames_in_100ms);
  CHECK_ERROR(errcode, errcode, exit)

  {
    size_t audio_data_size;

    errcode = safe_size_mul(&audio_data_size, *audio_data_frames,
                            st->channels * sizeof(double));
    CHECK_ERROR(errcode, errcode, exit)

    *audio_data = (double*) malloc(audio_data_size);
    CHECK_ERROR(!*audio_data, EBUR128_ERROR_NOMEM, exit)
  }

  {
    size_t j;

    for (j = 0; j < *audio_data_frames * st->channels; ++j) {
      (*audio_data)[j] = 0.0;
    }
  }

    exit:
  return errcode;
}

static int ebur128_create_energy_cache(struct ebur128_block_energy** cache,
                                       size_t* block_count,
                                       const ebur128_state* st,
                                       size_t audio_data_frames) {
  size_t blocks = audio_data_frames / st->d->frames_in_100ms;

  *cache = (struct ebur128_block_energy*) calloc(
          blocks, sizeof(struct ebur128_block_energy));
  if (!*cache) {
    return EBUR128_ERROR_NOMEM;
  }

  *block_count = blocks;
  return EBUR128_SUCCESS;
}

/*
 * (Re-)Initializes:
 *
 * - `st->d->window`
 * - `st->d->audio_data`
 * - `st->d->audio_data_frames`
 * - `st->d->needed_frames`
 * - `st->d->audio_data_frame_index`
 * - `st->d->short_term_frame_counter`
 *
 * ...appropriately.
 *
 * These must be set in `st`:
 *
 * - `st->samplerate`
 * - `st->d->frames_in_100ms`
 * - `st->channels`
 * - `st->d->audio_data` (either `NULL` or valid pointer)
 */
static int ebur128_reinitialize_audio_data(ebur128_state* st,
                                           unsigned long window) {
  int errcode;
  double* audio_data = NULL;
  struct ebur128_block_energy* block_energy_cache = NULL;
  size_t audio_data_frames = 0;
  size_t block_energy_cache_size = 0;

  errcode =
          ebur128_create_audio_data(&audio_data, &audio_data_frames, st, window);
  CHECK_ERROR(errcode, errcode, exit)

  errcode = ebur128_create_energy_cache(
          &block_energy_cache, &block_energy_cache_size, st, audio_data_frames);
  CHECK_ERROR(errcode, errcode, free_audio_data)

  st->d->window = window;
  free(st->d->audio_data);
  free(st->d->block_energy_cache);
  st->d->audio_data = audio_data;
  st->d->audio_data_frames = audio_data_frames;
  st->d->block_energy_cache = block_energy_cache;
  st->d->block_energy_cache_size = block_energy_cache_size;

  /* the first block needs 400ms of audio data */
  st->d->needed_frames = st->d->frames_in_100ms * 4;
  /* start at the beginning of the buffer */
  st->d->audio_data_frame_index = 0;
  /* reset short term frame counter */
  st->d->short_term_frame_counter = 0;

  return EBUR128_SUCCESS;

    free_audio_data:
  free(audio_data);
    exit:
  return errcode;
}

static int ebur128_init_filter(ebur128_state* st) {
  int errcode = EBUR128_SUCCESS;
  int i, j;

  double f0 = 1681.974450955533;
  double G = 3.999843853973347;
  double Q = 0.7071752369554196;

  double K = tan(M_PI * f0 / (double) st->samplerate);
  double Vh = pow(10.0, G / 20.0);
  double Vb = pow(Vh, 0.4996667741545416);

  double pb[3] = {0.0, 0.0, 0.0};
  double pa[3] = {1.0, 0.0, 0.0};
  double rb[3] = {1.0, -2.0, 1.0};
  double ra[3] = {1.0, 0.0, 0.0};

  double a0 = 1.0 + K / Q + K * K;
  pb[0] = (Vh + Vb * K / Q + K * K) / a0;
  pb[1] = 2.0 * (K * K - Vh) / a0;
  pb[2] = (Vh - Vb * K / Q + K * K) / a0;
  pa[1] = 2.0 * (K * K - 1.0) / a0;
  pa[2] = (1.0 - K / Q + K * K) / a0;

  /* fprintf(stderr, "%.14f %.14f %.14f %.14f %.14f\n",
                     b1[0], b1[1], b1[2], a1[1], a1[2]); */

  f0 = 38.13547087602444;
  Q = 0.5003270373238773;
  K = tan(M_PI * f0 / (double) st->samplerate);

  ra[1] = 2.0 * (K * K - 1.0) / (1.0 + K / Q + K * K);
  ra[2] = (1.0 - K / Q + K * K) / (1.0 + K / Q + K * K);

  /* fprintf(stderr, "%.14f %.14f\n", a2[1], a2[2]); */

  st->d->b[0] = pb[0] * rb[0];
  st->d->b[1] = pb[0] * rb[1] + pb[1] * rb[0];
  st->d->b[2] = pb[0] * rb[2] + pb[1] * rb[1] + pb[2] * rb[0];
  st->d->b[3] = pb[1] * rb[2] + pb[2] * rb[1];
  st->d->b[4] = pb[2] * rb[2];

  st->d->a[0] = pa[0] * ra[0];
  st->d->a[1] = pa[0] * ra[1] + pa[1] * ra[0];
  st->d->a[2] = pa[0] * ra[2] + pa[1] * ra[1] + pa[2] * ra[0];
  st->d->a[3] = pa[1] * ra[2] + pa[2] * ra[1];
  st->d->a[4] = pa[2] * ra[2];

  st->d->v = (filter_state*) malloc(st->channels * sizeof(filter_state));
  CHECK_ERROR(!st->d->v, EBUR128_ERROR_NOMEM, exit);
  for (i = 0; i < (int) st->channels; ++i) {
    for (j = 0; j < FILTER_STATE_SIZE; ++j) {
      st->d->v[i][j] = 0.0;
    }
  }

    exit:
  return errcode;
}

static int ebur128_init_channel_map(ebur128_state* st) {
  size_t i;
  st->d->channel_map = (int*) malloc(st->channels * sizeof(int));
  if (!st->d->channel_map) {
    return EBUR128_ERROR_NOMEM;
  }
  if (st->channels == 4) {
    st->d->channel_map[0] = EBUR128_LEFT;
    st->d->channel_map[1] = EBUR128_RIGHT;
    st->d->channel_map[2] = EBUR128_LEFT_SURROUND;
    st->d->channel_map[3] = EBUR128_RIGHT_SURROUND;
  } else if (st->channels == 5) {
    st->d->channel_map[0] = EBUR128_LEFT;
    st->d->channel_map[1] = EBUR128_RIGHT;
    st->d->channel_map[2] = EBUR128_CENTER;
    st->d->channel_map[3] = EBUR128_LEFT_SURROUND;
    st->d->channel_map[4] = EBUR128_RIGHT_SURROUND;
  } else {
    for (i = 0; i < st->channels; ++i) {
      switch (i) {
        case 0: st->d->channel_map[i] = EBUR128_LEFT; break;
        case 1: st->d->channel_map[i] = EBUR128_RIGHT; break;
        case 2: st->d->channel_map[i] = EBUR128_CENTER; break;
        case 3: st->d->channel_map[i] = EBUR128_UNUSED; break;
        case 4: st->d->channel_map[i] = EBUR128_LEFT_SURROUND; break;
        case 5: st->d->channel_map[i] = EBUR128_RIGHT_SURROUND; break;
        default: st->d->channel_map[i] = EBUR128_UNUSED; break;
      }
    }
  }
  return EBUR128_SUCCESS;
}

static int ebur128_init_resampler(ebur128_state* st) {
  int errcode = EBUR128_SUCCESS;

  if (st->samplerate < 96000) {
    st->d->interp = interp_create(49, 4, st->channels);
    CHECK_ERROR(!st->d->interp, EBUR128_ERROR_NOMEM, exit)
  } else if (st->samplerate < 192000) {
    st->d->interp = interp_create(49, 2, st->channels);
    CHECK_ERROR(!st->d->interp, EBUR128_ERROR_NOMEM, exit)
  } else {
    st->d->resampler_buffer_input = NULL;
    st->d->interp = NULL;
    goto exit;
  }

  st->d->resampler_buffer_input_frames = st->d->frames_in_100ms * 4;
  st->d->resampler_buffer_input = (float*) malloc(
          st->d->resampler_buffer_input_frames * st->channels * sizeof(float));
  CHECK_ERROR(!st->d->resampler_buffer_input, EBUR128_ERROR_NOMEM, free_interp)

  return errcode;

    free_interp:
  interp_destroy(st->d->interp);
  st->d->interp = NULL;
    exit:
  return errcode;
}

static void ebur128_destroy_resampler(ebur128_state* st) {
  free(st->d->resampler_buffer_input);
  st->d->resampler_buffer_input = NULL;
  interp_destroy(st->d->interp);
  st->d->interp = NULL;
}

void ebur128_get_version(int* major, int* minor, int* patch) {
  *major = EBUR128_VERSION_MAJOR;
  *minor = EBUR128_VERSION_MINOR;
  *patch = EBUR128_VERSION_PATCH;
}

#define VALIDATE_MAX_CHANNELS (64)
#define VALIDATE_MAX_SAMPLERATE (2822400)

#define VALIDATE_CHANNELS_AND_SAMPLERATE(err)                                  \
  do {                                                                         \
    if (channels == 0 || channels > VALIDATE_MAX_CHANNELS) {                   \
      return (err);                                                            \
    }                                                                          \
                                                                               \
    if (samplerate < 16 || samplerate > VALIDATE_MAX_SAMPLERATE) {             \
      return (err);                                                            \
    }                                                                          \
  } while (0)

ebur128_state*
ebur128_init(unsigned int channels, unsigned long samplerate, int mode) {
  int result;
  int errcode;
  ebur128_state* st;
  unsigned int i;

  VALIDATE_CHANNELS_AND_SAMPLERATE(NULL);

  st = (ebur128_state*) malloc(sizeof(ebur128_state));
  CHECK_ERROR(!st, 0, exit)
  st->d = (struct ebur128_state_internal*) malloc(
          sizeof(struct ebur128_state_internal));
  CHECK_ERROR(!st->d, 0, free_state)
  st->channels = channels;
  errcode = ebur128_init_channel_map(st);
  CHECK_ERROR(errcode, 0, free_internal)

  st->d->sample_peak = (double*) malloc(channels * sizeof(double));
  CHECK_ERROR(!st->d->sample_peak, 0, free_channel_map)
  st->d->prev_sample_peak = (double*) malloc(channels * sizeof(double));
  CHECK_ERROR(!st->d->prev_sample_peak, 0, free_sample_peak)
  st->d->true_peak = (double*) malloc(channels * sizeof(double));
  CHECK_ERROR(!st->d->true_peak, 0, free_prev_sample_peak)
  st->d->prev_true_peak = (double*) malloc(channels * sizeof(double));
  CHECK_ERROR(!st->d->prev_true_peak, 0, free_true_peak)
  for (i = 0; i < channels; ++i) {
    st->d->sample_peak[i] = 0.0;
    st->d->prev_sample_peak[i] = 0.0;
    st->d->true_peak[i] = 0.0;
    st->d->prev_true_peak[i] = 0.0;
  }

  st->d->use_histogram = mode & EBUR128_MODE_HISTOGRAM ? 1 : 0;
  st->d->history = ULONG_MAX;
  st->samplerate = samplerate;
  st->d->frames_in_100ms = (st->samplerate + 5) / 10;
  st->mode = mode;
  if ((mode & EBUR128_MODE_S) == EBUR128_MODE_S) {
    st->d->window = 3000;
  } else if ((mode & EBUR128_MODE_M) == EBUR128_MODE_M) {
    st->d->window = 400;
  } else {
    goto free_prev_true_peak;
  }

  st->d->audio_data = NULL;
  st->d->block_energy_cache = NULL;
  st->d->block_energy_cache_size = 0;
  errcode = ebur128_reinitialize_audio_data(st, st->d->window);
  CHECK_ERROR(errcode, 0, free_prev_true_peak)

  errcode = ebur128_init_filter(st);
  CHECK_ERROR(errcode, 0, free_audio_data)

  if (st->d->use_histogram) {
    st->d->block_energy_histogram =
            (unsigned long*) malloc(1000 * sizeof(unsigned long));
    CHECK_ERROR(!st->d->block_energy_histogram, 0, free_filter)
    for (i = 0; i < 1000; ++i) {
      st->d->block_energy_histogram[i] = 0;
    }
  } else {
    st->d->block_energy_histogram = NULL;
  }
  if (st->d->use_histogram) {
    st->d->short_term_block_energy_histogram =
            (unsigned long*) malloc(1000 * sizeof(unsigned long));
    CHECK_ERROR(!st->d->short_term_block_energy_histogram, 0,
                free_block_energy_histogram)
    for (i = 0; i < 1000; ++i) {
      st->d->short_term_block_energy_histogram[i] = 0;
    }
  } else {
    st->d->short_term_block_energy_histogram = NULL;
  }
  STAILQ_INIT(&st->d->block_list);
  st->d->block_list_size = 0;
  st->d->block_list_max = st->d->history / 100;
  STAILQ_INIT(&st->d->short_term_block_list);
  st->d->st_block_list_size = 0;
  st->d->st_block_list_max = st->d->history / 3000;

  result = ebur128_init_resampler(st);
  CHECK_ERROR(result, 0, free_short_term_block_energy_histogram)

  /* initialize static constants */
  ebur128_once_call(ebur128_init_library);

  return st;

    free_short_term_block_energy_histogram:
  free(st->d->short_term_block_energy_histogram);
    free_block_energy_histogram:
  free(st->d->block_energy_histogram);
    free_filter:
  free(st->d->v);
    free_audio_data:
  free(st->d->audio_data);
  free(st->d->block_energy_cache);
    free_prev_true_peak:
  free(st->d->prev_true_peak);
    free_true_peak:
  free(st->d->true_peak);
    free_prev_sample_peak:
  free(st->d->prev_sample_peak);
    free_sample_peak:
  free(st->d->sample_peak);
    free_channel_map:
  free(st->d->channel_map);
    free_internal:
  free(st->d);
    free_state:
  free(st);
    exit:
  return NULL;
}

void ebur128_destroy(ebur128_state** st) {
  struct ebur128_dq_entry* entry;
  free((*st)->d->short_term_block_energy_histogram);
  free((*st)->d->block_energy_histogram);
  free((*st)->d->v);
  free((*st)->d->audio_data);
  free((*st)->d->block_energy_cache);
  free((*st)->d->channel_map);
  free((*st)->d->sample_peak);
  free((*st)->d->prev_sample_peak);
  free((*st)->d->true_peak);
  free((*st)->d->prev_true_peak);
  while (!STAILQ_EMPTY(&(*st)->d->block_list)) {
    entry = STAILQ_FIRST(&(*st)->d->block_list);
    STAILQ_REMOVE_HEAD(&(*st)->d->block_list, entries);
    free(entry);
  }
  while (!STAILQ_EMPTY(&(*st)->d->short_term_block_list)) {
    entry = STAILQ_FIRST(&(*st)->d->short_term_block_list);
    STAILQ_REMOVE_HEAD(&(*st)->d->short_term_block_list, entries);
    free(entry);
  }
  ebur128_destroy_resampler(*st);
  free((*st)->d);
  free(*st);
  *st = NULL;
}

static EBUR128_FORCE_INLINE double ebur128_true_peak_sample(
        void* context, unsigned int channel, double peak, float sample) {
  double absolute = fabs((double) sample);

  (void) context;
  (void) channel;

  if (absolute > peak) {
    peak = absolute;
  }
  return peak;
}

static void ebur128_check_true_peak(ebur128_state* st, size_t frames) {
  interp_process(st->d->interp, frames, st->d->resampler_buffer_input,
                 st->d->prev_true_peak, ebur128_true_peak_sample, NULL);
}

#if (defined(__aarch64__) || defined(__arm64__)) && !defined(_MSC_VER)
#define EBUR128_FPCR_FZ (1ULL << 24)
static unsigned long long ebur128_get_fpcr(void) {
  unsigned long long fpcr;
  __asm__ __volatile__("mrs %0, fpcr" : "=r"(fpcr) : : "memory");
  return fpcr;
}
static void ebur128_set_fpcr(unsigned long long fpcr) {
  __asm__ __volatile__("msr fpcr, %0" : : "r"(fpcr) : "memory");
}
#define TURN_ON_FTZ                                                            \
  unsigned long long fpcr = ebur128_get_fpcr();                                \
  ebur128_set_fpcr(fpcr | EBUR128_FPCR_FZ)
#define TURN_OFF_FTZ ebur128_set_fpcr(fpcr)
#define FLUSH_MANUALLY(v1, v2, v3, v4)
#elif defined(__SSE2_MATH__) || defined(_M_X64) || _M_IX86_FP >= 2
#include <xmmintrin.h>
#define TURN_ON_FTZ                                                            \
  unsigned int mxcsr = _mm_getcsr();                                           \
  _mm_setcsr(mxcsr | _MM_FLUSH_ZERO_ON)
#define TURN_OFF_FTZ _mm_setcsr(mxcsr)
#define FLUSH_MANUALLY(v1, v2, v3, v4)
#else
#warning                                                                       \
    "manual FTZ is being used, please enable SSE2 (-msse2 -mfpmath=sse) or use AArch64"
#define TURN_ON_FTZ
#define TURN_OFF_FTZ
#define FLUSH_MANUALLY(v1, v2, v3, v4)                                         \
  do {                                                                         \
    (v4) = fabs(v4) < DBL_MIN ? 0.0 : (v4);                                    \
    (v3) = fabs(v3) < DBL_MIN ? 0.0 : (v3);                                    \
    (v2) = fabs(v2) < DBL_MIN ? 0.0 : (v2);                                    \
    (v1) = fabs(v1) < DBL_MIN ? 0.0 : (v1);                                    \
  } while (0)
#endif

static void ebur128_invalidate_all_energy_cache(ebur128_state* st) {
  size_t i;

  for (i = 0; i < st->d->block_energy_cache_size; ++i) {
    st->d->block_energy_cache[i].dirty = 1;
  }
}

/*
 * Iterates over `audio_data_frames`, starting at frame `start_frame`, for
 * `frames` frames in total.
 *
 * Calls `callback` with chunks of maximum 100ms, where the boundaries
 * are determined by dividing the `audio_data_frames` ring buffer in 100ms
 * chunks.
 *
 * The first and last calls to `callback` may contain less than 100ms of audio
 * data. All other calls are always full 100ms chunks.
 */
static void ebur128_for_each_energy_chunk(ebur128_state* st,
                                          void* context,
                                          size_t start_frame,
                                          size_t frames,
                                          ebur128_energy_chunk_fn callback) {
  const size_t frames_per_chunk = st->d->frames_in_100ms;
  const size_t audio_data_frames = st->d->audio_data_frames;
  size_t position = start_frame;
  size_t remaining = frames;

  assert(position != audio_data_frames);
  assert(frames <= audio_data_frames);

  while (remaining > 0) {
    size_t chunk_number = position / frames_per_chunk;
    size_t position_in_chunk = position % frames_per_chunk;
    size_t take = frames_per_chunk - position_in_chunk;

    if (take > remaining) {
      take = remaining;
    }
    callback(st, context, position, chunk_number, position_in_chunk, take);
    position = (position + take) % audio_data_frames;
    remaining -= take;
  }
}

static void ebur128_invalidate_energy_cache_chunk(ebur128_state* st,
                                                  void* context,
                                                  size_t position,
                                                  size_t chunk_number,
                                                  size_t position_in_chunk,
                                                  size_t take) {
  st->d->block_energy_cache[chunk_number].dirty = 1;
  (void) context;
  (void) position;
  (void) position_in_chunk;
  (void) take;
}

static void ebur128_invalidate_energy_cache(ebur128_state* st,
                                            size_t start_frame,
                                            size_t frames) {
  ebur128_for_each_energy_chunk(st, NULL, start_frame, frames,
                                ebur128_invalidate_energy_cache_chunk);
}

static EBUR128_FORCE_INLINE void ebur128_filter_impl(ebur128_state* st,
                                                     const void* context,
                                                     double (*src)(const void*,
                                                                   size_t),
                                                     size_t frames,
                                                     double scaling_factor) {
  const size_t channels = st->channels;
  double* audio_data =
          st->d->audio_data + st->d->audio_data_frame_index * channels;
  size_t i, c;

  ebur128_invalidate_energy_cache(st, st->d->audio_data_frame_index, frames);

  TURN_ON_FTZ;

  if ((st->mode & EBUR128_MODE_SAMPLE_PEAK) == EBUR128_MODE_SAMPLE_PEAK) {
    for (c = 0; c < channels; ++c) {
      double max = 0.0;
      for (i = 0; i < frames; ++i) {
        double absolute = fabs(src(context, i * st->channels + c));
        if (absolute > max) {
          max = absolute;
        }
      }
      max /= scaling_factor;
      if (max > st->d->prev_sample_peak[c]) {
        st->d->prev_sample_peak[c] = max;
      }
    }
  }

  if ((st->mode & EBUR128_MODE_TRUE_PEAK) == EBUR128_MODE_TRUE_PEAK &&
      st->d->interp) {
    for (i = 0; i < frames; ++i) {
      for (c = 0; c < st->channels; ++c) {
        st->d->resampler_buffer_input[i * st->channels + c] =
                (float) (src(context, i * st->channels + c) / scaling_factor);
      }
    }
    ebur128_check_true_peak(st, frames);
  }

  {
    const double b[5] = {
            st->d->b[0], st->d->b[1], st->d->b[2], st->d->b[3], st->d->b[4],
    };
    const double a[5] = {
            st->d->a[0], st->d->a[1], st->d->a[2], st->d->a[3], st->d->a[4],
    };

    for (c = 0; c < channels; ++c) {
      double v[5];

      if (st->d->channel_map[c] == EBUR128_UNUSED) {
        continue;
      }

      memcpy(v, &st->d->v[c], 5 * sizeof(double));
      for (i = 0; i < frames; ++i) {
        double input = src(context, i * channels + c) / scaling_factor;
        v[0] =            /**/
                input -       /**/
                a[1] * v[1] - /**/
                a[2] * v[2] - /**/
                a[3] * v[3] - /**/
                a[4] * v[4];
        audio_data[i * channels + c] = /**/
                b[0] * v[0] +              /**/
                b[1] * v[1] +              /**/
                b[2] * v[2] +              /**/
                b[3] * v[3] +              /**/
                b[4] * v[4];               /**/
        v[4] = v[3];
        v[3] = v[2];
        v[2] = v[1];
        v[1] = v[0];
      }
      FLUSH_MANUALLY(v[1], v[2], v[3], v[4]);
      memcpy(&st->d->v[c], v, 5 * sizeof(double));
    }
  }

  TURN_OFF_FTZ;
}

#define EBUR128_FILTER(type, min_scale, max_scale)                             \
  static double ebur128_filter_src_##type(const void* src, size_t i) {         \
    return (double) ((const type*) src)[i];                                    \
  }                                                                            \
                                                                               \
  static void ebur128_filter_##type(ebur128_state* st, const type* src,        \
                                    size_t frames) {                           \
    static const double scaling_factor =                                       \
        EBUR128_MAX(-((double) (min_scale)), (double) (max_scale));            \
    ebur128_filter_impl(st, src, ebur128_filter_src_##type, frames,            \
                        scaling_factor);                                       \
  }

EBUR128_FILTER(short, SHRT_MIN, SHRT_MAX)
EBUR128_FILTER(int, INT_MIN, INT_MAX)
EBUR128_FILTER(float, -1.0f, 1.0f)
EBUR128_FILTER(double, -1.0, 1.0)

#undef EBUR128_FILTER

static double ebur128_energy_to_loudness(double energy) {
  return 10 * (log(energy) / log(10.0)) - 0.691;
}

static size_t find_histogram_index(double energy) {
  size_t index_min = 0;
  size_t index_max = 1000;
  size_t index_mid;

  do {
    index_mid = (index_min + index_max) / 2;
    if (energy >= histogram_energy_boundaries[index_mid]) {
      index_min = index_mid;
    } else {
      index_max = index_mid;
    }
  } while (index_max - index_min != 1);

  return index_min;
}

static double ebur128_channel_weight(int channel) {
  if (channel == EBUR128_Mp110 || channel == EBUR128_Mm110 ||
      channel == EBUR128_Mp060 || channel == EBUR128_Mm060 ||
      channel == EBUR128_Mp090 || channel == EBUR128_Mm090) {
    return 1.41;
  }
  if (channel == EBUR128_DUAL_MONO) {
    return 2.0;
  }
  return 1.0;
}

static double ebur128_energy_over_frames(const ebur128_state* st,
                                         size_t start_frame,
                                         size_t frames) {
  const size_t channels = st->channels;
  const double* data = st->d->audio_data + start_frame * channels;
  double sum = 0.0;
  size_t c;
  size_t i;

  for (c = 0; c < channels; ++c) {
    if (st->d->channel_map[c] != EBUR128_UNUSED) {
      const double* channel_data = data + c;
      double channel_sum = 0.0;
      for (i = 0; i < frames; ++i) {
        double value = channel_data[i * channels];
        channel_sum += value * value;
      }
      sum += channel_sum * ebur128_channel_weight(st->d->channel_map[c]);
    }
  }

  return sum;
}

static void ebur128_update_energy_cache_chunk(ebur128_state* st,
                                              size_t chunk_number) {
  const size_t frames_per_chunk = st->d->frames_in_100ms;
  struct ebur128_block_energy* cache = st->d->block_energy_cache;

  cache[chunk_number].energy = ebur128_energy_over_frames(
          st, chunk_number * frames_per_chunk, frames_per_chunk);
  cache[chunk_number].dirty = 0;
}

static void ebur128_energy_for_interval_chunk(ebur128_state* st,
                                              void* context,
                                              size_t position,
                                              size_t chunk_number,
                                              size_t position_in_chunk,
                                              size_t take) {
  double* sum = (double*) context;

  if (position_in_chunk == 0 && take == st->d->frames_in_100ms) {
    struct ebur128_block_energy* cache = st->d->block_energy_cache;

    if (cache[chunk_number].dirty) {
      ebur128_update_energy_cache_chunk(st, chunk_number);
    }
    *sum += cache[chunk_number].energy;
  } else {
    *sum += ebur128_energy_over_frames(st, position, take);
  }
}

static double ebur128_energy_for_interval(ebur128_state* st,
                                          size_t start_frame,
                                          size_t frames) {
  double sum = 0.0;
  ebur128_for_each_energy_chunk(st, &sum, start_frame, frames,
                                ebur128_energy_for_interval_chunk);
  return sum;
}

static int ebur128_calc_gating_block(ebur128_state* st,
                                     size_t frames,
                                     double* optional_output) {
  const size_t audio_data_frames = st->d->audio_data_frames;
  size_t end_frame;
  size_t start_frame;
  double sum;

  assert(frames <= audio_data_frames);

  end_frame = st->d->audio_data_frame_index;
  if (frames > end_frame) {
    start_frame = audio_data_frames - (frames - end_frame);
  } else {
    start_frame = end_frame - frames;
  }

  sum = ebur128_energy_for_interval(st, start_frame, frames);
  sum /= (double) frames;

  if (optional_output) {
    *optional_output = sum;
    return EBUR128_SUCCESS;
  }

  if (sum >= histogram_energy_boundaries[0]) {
    if (st->d->use_histogram) {
      ++st->d->block_energy_histogram[find_histogram_index(sum)];
    } else {
      struct ebur128_dq_entry* block;
      if (st->d->block_list_size == st->d->block_list_max) {
        block = STAILQ_FIRST(&st->d->block_list);
        STAILQ_REMOVE_HEAD(&st->d->block_list, entries);
      } else {
        block =
                (struct ebur128_dq_entry*) malloc(sizeof(struct ebur128_dq_entry));
        if (!block) {
          return EBUR128_ERROR_NOMEM;
        }
        st->d->block_list_size++;
      }
      block->z = sum;
      STAILQ_INSERT_TAIL(&st->d->block_list, block, entries);
    }
  }

  return EBUR128_SUCCESS;
}

int ebur128_set_channel(ebur128_state* st,
                        unsigned int channel_number,
                        int value) {
  if (channel_number >= st->channels) {
    return EBUR128_ERROR_INVALID_CHANNEL_INDEX;
  }
  if (value == EBUR128_DUAL_MONO && st->channels != 1) {
    return EBUR128_ERROR_INVALID_CHANNEL_INDEX;
  }
  if (st->d->channel_map[channel_number] == value) {
    return EBUR128_SUCCESS;
  }
  st->d->channel_map[channel_number] = value;
  ebur128_invalidate_all_energy_cache(st);
  return EBUR128_SUCCESS;
}

int ebur128_change_parameters(ebur128_state* st,
                              unsigned int channels,
                              unsigned long samplerate) {
  int errcode = EBUR128_SUCCESS;

#if __has_builtin(__builtin_unreachable)
  if (st->channels == 0) {   /* LCOV_EXCL_BR_LINE */
    __builtin_unreachable(); /* LCOV_EXCL_LINE */
  } /*                          LCOV_EXCL_LINE */
#endif

  VALIDATE_CHANNELS_AND_SAMPLERATE(EBUR128_ERROR_NOMEM);

  if (channels == st->channels && samplerate == st->samplerate) {
    return EBUR128_ERROR_NO_CHANGE;
  }

  free(st->d->audio_data);
  st->d->audio_data = NULL;
  free(st->d->block_energy_cache);
  st->d->block_energy_cache = NULL;
  st->d->block_energy_cache_size = 0;

  if (channels != st->channels) {
    unsigned int i;

    free(st->d->channel_map);
    st->d->channel_map = NULL;
    free(st->d->sample_peak);
    st->d->sample_peak = NULL;
    free(st->d->prev_sample_peak);
    st->d->prev_sample_peak = NULL;
    free(st->d->true_peak);
    st->d->true_peak = NULL;
    free(st->d->prev_true_peak);
    st->d->prev_true_peak = NULL;
    st->channels = channels;

    errcode = ebur128_init_channel_map(st);
    CHECK_ERROR(errcode, EBUR128_ERROR_NOMEM, exit)

    st->d->sample_peak = (double*) malloc(channels * sizeof(double));
    CHECK_ERROR(!st->d->sample_peak, EBUR128_ERROR_NOMEM, exit)
    st->d->prev_sample_peak = (double*) malloc(channels * sizeof(double));
    CHECK_ERROR(!st->d->prev_sample_peak, EBUR128_ERROR_NOMEM, exit)
    st->d->true_peak = (double*) malloc(channels * sizeof(double));
    CHECK_ERROR(!st->d->true_peak, EBUR128_ERROR_NOMEM, exit)
    st->d->prev_true_peak = (double*) malloc(channels * sizeof(double));
    CHECK_ERROR(!st->d->prev_true_peak, EBUR128_ERROR_NOMEM, exit)
    for (i = 0; i < channels; ++i) {
      st->d->sample_peak[i] = 0.0;
      st->d->prev_sample_peak[i] = 0.0;
      st->d->true_peak[i] = 0.0;
      st->d->prev_true_peak[i] = 0.0;
    }
  }
  if (samplerate != st->samplerate) {
    st->samplerate = samplerate;
    st->d->frames_in_100ms = (st->samplerate + 5) / 10;
  }

  /* If we're here, either samplerate or channels
   * have changed. Re-init filter. */
  free(st->d->v);
  st->d->v = NULL;

  errcode = ebur128_init_filter(st);
  CHECK_ERROR(errcode, errcode, exit)

  errcode = ebur128_reinitialize_audio_data(st, st->d->window);
  CHECK_ERROR(errcode, errcode, exit)

  ebur128_destroy_resampler(st);
  errcode = ebur128_init_resampler(st);
  CHECK_ERROR(errcode, errcode, exit)

    exit:
  return errcode;
}

int ebur128_set_max_window(ebur128_state* st, unsigned long window) {
  int errcode = EBUR128_SUCCESS;

  if ((st->mode & EBUR128_MODE_S) == EBUR128_MODE_S && window < 3000) {
    window = 3000;
  } else if (
    /* LCOV_EXCL_BR_START */
          (st->mode & EBUR128_MODE_M) == EBUR128_MODE_M &&
          /* LCOV_EXCL_BR_STOP */
          window < 400) {
    window = 400;
  }

  if (window == st->d->window) {
    return EBUR128_ERROR_NO_CHANGE;
  }

  errcode = ebur128_reinitialize_audio_data(st, window);
  CHECK_ERROR(errcode, errcode, exit)

    exit:
  return errcode;
}

int ebur128_set_max_history(ebur128_state* st, unsigned long history) {
  if ((st->mode & EBUR128_MODE_LRA) == EBUR128_MODE_LRA && history < 3000) {
    history = 3000;
  } else if (
    /* LCOV_EXCL_BR_START */
          (st->mode & EBUR128_MODE_M) == EBUR128_MODE_M &&
          /* LCOV_EXCL_BR_STOP */
          history < 400) {
    history = 400;
  }
  if (history == st->d->history) {
    return EBUR128_ERROR_NO_CHANGE;
  }
  st->d->history = history;
  st->d->block_list_max = st->d->history / 100;
  st->d->st_block_list_max = st->d->history / 3000;
  while (st->d->block_list_size > st->d->block_list_max) {
    struct ebur128_dq_entry* block = STAILQ_FIRST(&st->d->block_list);
    STAILQ_REMOVE_HEAD(&st->d->block_list, entries);
    free(block);
    st->d->block_list_size--;
  }
  while (st->d->st_block_list_size > st->d->st_block_list_max) {
    struct ebur128_dq_entry* block =
            STAILQ_FIRST(&st->d->short_term_block_list);
    STAILQ_REMOVE_HEAD(&st->d->short_term_block_list, entries);
    free(block);
    st->d->st_block_list_size--;
  }
  return EBUR128_SUCCESS;
}

static int ebur128_energy_shortterm(ebur128_state* st, double* out);

static EBUR128_FORCE_INLINE int
ebur128_add_frames_impl(ebur128_state* st,
                        const void* src,
                        const void* (*filter)(ebur128_state* st,
                                              const void* src,
                                              unsigned long frames_to_feed),
                        size_t frames) {
  unsigned int c = 0;
  for (c = 0; c < st->channels; c++) {
    st->d->prev_sample_peak[c] = 0.0;
    st->d->prev_true_peak[c] = 0.0;
  }
  while (frames > 0) {
    if (frames >= st->d->needed_frames) {
      unsigned long frames_to_feed = st->d->needed_frames;

      /* 100ms are needed for all blocks besides the first one */
      st->d->needed_frames = st->d->frames_in_100ms;

      src = filter(st, src, frames_to_feed);
      frames -= frames_to_feed;

      st->d->audio_data_frame_index += frames_to_feed;
      /* reset audio_data_frame_index when buffer full */
      if (st->d->audio_data_frame_index == st->d->audio_data_frames) {
        st->d->audio_data_frame_index = 0;
      }

      /* calculate the new gating block */
      if ((st->mode & EBUR128_MODE_I) == EBUR128_MODE_I) {
        if (ebur128_calc_gating_block(st, st->d->frames_in_100ms * 4, NULL)) {
          return EBUR128_ERROR_NOMEM;
        }
      }
      if ((st->mode & EBUR128_MODE_LRA) == EBUR128_MODE_LRA) {
        st->d->short_term_frame_counter += frames_to_feed;
        if (st->d->short_term_frame_counter == st->d->frames_in_100ms * 30) {
          int error;
          struct ebur128_dq_entry* block;
          double st_energy;

          st->d->short_term_frame_counter = st->d->frames_in_100ms * 20;

          error = ebur128_energy_shortterm(st, &st_energy);
          if (error) {    /* LCOV_EXCL_BR_LINE */
            return error; /* LCOV_EXCL_LINE */
          } /*               LCOV_EXCL_LINE */

          if (st_energy >= histogram_energy_boundaries[0]) {
            if (st->d->use_histogram) {
              ++st->d->short_term_block_energy_histogram[find_histogram_index(
                      st_energy)];
            } else {
              if (st->d->st_block_list_size == st->d->st_block_list_max) {
                block = STAILQ_FIRST(&st->d->short_term_block_list);
                STAILQ_REMOVE_HEAD(&st->d->short_term_block_list, entries);
              } else {
                block = (struct ebur128_dq_entry*) malloc(
                        sizeof(struct ebur128_dq_entry));
                if (!block) {
                  return EBUR128_ERROR_NOMEM;
                }
                st->d->st_block_list_size++;
              }
              block->z = st_energy;
              STAILQ_INSERT_TAIL(&st->d->short_term_block_list, block, entries);
            }
          }
        }
      }
    } else {
      unsigned long frames_to_feed = (unsigned long) frames;
      st->d->needed_frames -= frames_to_feed;
      (void) filter(st, src, frames_to_feed);
      frames = 0;
      st->d->audio_data_frame_index += frames_to_feed;
      assert(st->d->audio_data_frame_index != st->d->audio_data_frames);
      if ((st->mode & EBUR128_MODE_LRA) == EBUR128_MODE_LRA) {
        st->d->short_term_frame_counter += frames_to_feed;
      }
    }
  }
  for (c = 0; c < st->channels; c++) {
    if (st->d->prev_sample_peak[c] > st->d->sample_peak[c]) {
      st->d->sample_peak[c] = st->d->prev_sample_peak[c];
    }
    if (st->d->prev_true_peak[c] > st->d->true_peak[c]) {
      st->d->true_peak[c] = st->d->prev_true_peak[c];
    }
  }
  return EBUR128_SUCCESS;
}

#define EBUR128_ADD_FRAMES(type)                                               \
  static const void* ebur128_add_frames_filter_##type(                         \
      ebur128_state* st, const void* src_arg, unsigned long frames_to_feed) {  \
    const type* src = (const type*) src_arg;                                   \
    ebur128_filter_##type(st, src, frames_to_feed);                            \
    return src + frames_to_feed * st->channels;                                \
  }                                                                            \
                                                                               \
  int ebur128_add_frames_##type(ebur128_state* st, const type* src,            \
                                size_t frames) {                               \
    return ebur128_add_frames_impl(st, src, ebur128_add_frames_filter_##type,  \
                                   frames);                                    \
  }

EBUR128_ADD_FRAMES(short)
EBUR128_ADD_FRAMES(int)
EBUR128_ADD_FRAMES(float)
EBUR128_ADD_FRAMES(double)

#undef EBUR128_ADD_FRAMES

static int ebur128_calc_relative_threshold(ebur128_state* st,
                                           size_t* above_thresh_counter,
                                           double* relative_threshold) {
  struct ebur128_dq_entry* it;
  size_t i;

  if (st->d->use_histogram) {
    for (i = 0; i < 1000; ++i) {
      *relative_threshold +=
              (double) st->d->block_energy_histogram[i] * histogram_energies[i];
      *above_thresh_counter += st->d->block_energy_histogram[i];
    }
  } else {
    STAILQ_FOREACH(it, &st->d->block_list, entries) {
      ++*above_thresh_counter;
      *relative_threshold += it->z;
    }
  }

  return EBUR128_SUCCESS;
}

static int
ebur128_gated_loudness(ebur128_state** sts, size_t size, double* out) {
  struct ebur128_dq_entry* it;
  double gated_loudness = 0.0;
  double relative_threshold = 0.0;
  size_t above_thresh_counter = 0;
  size_t i, j, start_index;

  for (i = 0; i < size; i++) {
    if (sts[i] && (sts[i]->mode & EBUR128_MODE_I) != EBUR128_MODE_I) {
      return EBUR128_ERROR_INVALID_MODE;
    }
  }

  for (i = 0; i < size; i++) {
    if (!sts[i]) {
      continue;
    }
    ebur128_calc_relative_threshold(sts[i], &above_thresh_counter,
                                    &relative_threshold);
  }
  if (!above_thresh_counter) {
    *out = -HUGE_VAL;
    return EBUR128_SUCCESS;
  }

  relative_threshold /= (double) above_thresh_counter;
  relative_threshold *= relative_gate_factor;

  above_thresh_counter = 0;
  if (relative_threshold < histogram_energy_boundaries[0]) {
    start_index = 0;
  } else {
    start_index = find_histogram_index(relative_threshold);
    if (relative_threshold > histogram_energies[start_index]) {
      ++start_index;
    }
  }
  for (i = 0; i < size; i++) {
    if (!sts[i]) {
      continue;
    }
    if (sts[i]->d->use_histogram) {
      for (j = start_index; j < 1000; ++j) {
        gated_loudness += (double) sts[i]->d->block_energy_histogram[j] *
                          histogram_energies[j];
        above_thresh_counter += sts[i]->d->block_energy_histogram[j];
      }
    } else {
      STAILQ_FOREACH(it, &sts[i]->d->block_list, entries) {
        if (it->z >= relative_threshold) {
          ++above_thresh_counter;
          gated_loudness += it->z;
        }
      }
    }
  }
  /* I think is unreachable, but still leave it in as a defensive check. */
  /* LCOV_EXCL_START */
  if (!above_thresh_counter) {
    *out = -HUGE_VAL;
    return EBUR128_SUCCESS;
  }
  /* LCOV_EXCL_STOP */
  gated_loudness /= (double) above_thresh_counter;
  *out = ebur128_energy_to_loudness(gated_loudness);
  return EBUR128_SUCCESS;
}

int ebur128_relative_threshold(ebur128_state* st, double* out) {
  double relative_threshold = 0.0;
  size_t above_thresh_counter = 0;

  if ((st->mode & EBUR128_MODE_I) != EBUR128_MODE_I) {
    return EBUR128_ERROR_INVALID_MODE;
  }

  ebur128_calc_relative_threshold(st, &above_thresh_counter,
                                  &relative_threshold);

  if (!above_thresh_counter) {
    *out = -70.0;
    return EBUR128_SUCCESS;
  }

  relative_threshold /= (double) above_thresh_counter;
  relative_threshold *= relative_gate_factor;

  *out = ebur128_energy_to_loudness(relative_threshold);
  return EBUR128_SUCCESS;
}

int ebur128_loudness_global(ebur128_state* st, double* out) {
  return ebur128_gated_loudness(&st, 1, out);
}

int ebur128_loudness_global_multiple(ebur128_state** sts,
                                     size_t size,
                                     double* out) {
  return ebur128_gated_loudness(sts, size, out);
}

static int ebur128_energy_in_interval(ebur128_state* st,
                                      size_t interval_frames,
                                      double* out) {
  if (interval_frames > st->d->audio_data_frames) {
    return EBUR128_ERROR_INVALID_MODE;
  }
  ebur128_calc_gating_block(st, interval_frames, out);
  return EBUR128_SUCCESS;
}

static int ebur128_energy_shortterm(ebur128_state* st, double* out) {
  return ebur128_energy_in_interval(st, st->d->frames_in_100ms * 30, out);
}

int ebur128_loudness_momentary(ebur128_state* st, double* out) {
  double energy;
  int error;

  error = ebur128_energy_in_interval(st, st->d->frames_in_100ms * 4, &energy);
  if (error) {    /* LCOV_EXCL_BR_LINE */
    return error; /* LCOV_EXCL_LINE */
  } /*               LCOV_EXCL_LINE */

  if (energy <= 0.0) {
    *out = -HUGE_VAL;
    return EBUR128_SUCCESS;
  }

  *out = ebur128_energy_to_loudness(energy);
  return EBUR128_SUCCESS;
}

int ebur128_loudness_shortterm(ebur128_state* st, double* out) {
  double energy;
  int error;

  error = ebur128_energy_shortterm(st, &energy);
  if (error) {
    return error;
  }

  if (energy <= 0.0) {
    *out = -HUGE_VAL;
    return EBUR128_SUCCESS;
  }

  *out = ebur128_energy_to_loudness(energy);
  return EBUR128_SUCCESS;
}

int ebur128_loudness_window(ebur128_state* st,
                            unsigned long window,
                            double* out) {
  double energy;
  size_t interval_frames;
  int error;

  if (window > st->d->window) {
    return EBUR128_ERROR_INVALID_MODE;
  }

  /*
   * At this point, the operations (`mul_div_1000` and the cast to `size_t`)
   * cannot overflow.
   */
  {
    unsigned long tmp;
    error = mul_div_1000(&tmp, st->samplerate, window);
    assert(error == EBUR128_SUCCESS);
    interval_frames = (size_t) tmp;
  }

  error = ebur128_energy_in_interval(st, interval_frames, &energy);
  if (error) {    /* LCOV_EXCL_BR_LINE */
    return error; /* LCOV_EXCL_LINE */
  } /*               LCOV_EXCL_LINE */

  if (energy <= 0.0) {
    *out = -HUGE_VAL;
    return EBUR128_SUCCESS;
  }

  *out = ebur128_energy_to_loudness(energy);
  return EBUR128_SUCCESS;
}

static int ebur128_double_cmp(const void* p1, const void* p2) {
  const double* d1 = (const double*) p1;
  const double* d2 = (const double*) p2;
  return (*d1 > *d2) - (*d1 < *d2);
}

/* EBU - TECH 3342 */
int ebur128_loudness_range_multiple(ebur128_state** sts,
                                    size_t size,
                                    double* out) {
  size_t i, j;
  struct ebur128_dq_entry* it;
  double* stl_vector;
  size_t stl_size;
  double* stl_relgated;
  size_t stl_relgated_size;
  double stl_power, stl_integrated;
  /* High and low percentile energy */
  double h_en, l_en;
  int use_histogram = 0;

  for (i = 0; i < size; ++i) {
    if (sts[i]) {
      if ((sts[i]->mode & EBUR128_MODE_LRA) != EBUR128_MODE_LRA) {
        return EBUR128_ERROR_INVALID_MODE;
      }
      if (i == 0 && sts[i]->mode & EBUR128_MODE_HISTOGRAM) {
        use_histogram = 1;
      } else if (use_histogram != !!(sts[i]->mode & EBUR128_MODE_HISTOGRAM)) {
        return EBUR128_ERROR_INVALID_MODE;
      }
    }
  }

  if (use_histogram) {
    unsigned long hist[1000] = {0};
    size_t percentile_low, percentile_high;
    size_t index;

    stl_size = 0;
    stl_power = 0.0;
    for (i = 0; i < size; ++i) {
      if (!sts[i]) {
        continue;
      }
      for (j = 0; j < 1000; ++j) {
        hist[j] += sts[i]->d->short_term_block_energy_histogram[j];
        stl_size += sts[i]->d->short_term_block_energy_histogram[j];
        stl_power += (double) sts[i]->d->short_term_block_energy_histogram[j] *
                     histogram_energies[j];
      }
    }
    if (!stl_size) {
      *out = 0.0;
      return EBUR128_SUCCESS;
    }

    stl_power /= (double) stl_size;
    stl_integrated = minus_twenty_decibels * stl_power;

    if (stl_integrated < histogram_energy_boundaries[0]) {
      index = 0;
    } else {
      index = find_histogram_index(stl_integrated);
      if (stl_integrated > histogram_energies[index]) {
        ++index;
      }
    }
    stl_size = 0;
    for (j = index; j < 1000; ++j) {
      stl_size += hist[j];
    }
    /* defensive check */
    /* LCOV_EXCL_START */
    if (!stl_size) {
      *out = 0.0;
      return EBUR128_SUCCESS;
    }
    /* LCOV_EXCL_STOP */

    percentile_low = (size_t) ((double) (stl_size - 1) * 0.1 + 0.5);
    percentile_high = (size_t) ((double) (stl_size - 1) * 0.95 + 0.5);

    stl_size = 0;
    j = index;
    while (stl_size <= percentile_low) {
      stl_size += hist[j++];
    }
    l_en = histogram_energies[j - 1];
    while (stl_size <= percentile_high) {
      stl_size += hist[j++];
    }
    h_en = histogram_energies[j - 1];

    *out = ebur128_energy_to_loudness(h_en) - ebur128_energy_to_loudness(l_en);
    return EBUR128_SUCCESS;
  }

  stl_size = 0;
  for (i = 0; i < size; ++i) {
    if (!sts[i]) {
      continue;
    }
    STAILQ_FOREACH(it, &sts[i]->d->short_term_block_list, entries) {
      ++stl_size;
    }
  }
  if (!stl_size) {
    *out = 0.0;
    return EBUR128_SUCCESS;
  }
  stl_vector = (double*) malloc(stl_size * sizeof(double));
  if (!stl_vector) {
    return EBUR128_ERROR_NOMEM;
  }

  j = 0;
  for (i = 0; i < size; ++i) {
    if (!sts[i]) {
      continue;
    }
    STAILQ_FOREACH(it, &sts[i]->d->short_term_block_list, entries) {
      stl_vector[j] = it->z;
      ++j;
    }
  }
  qsort(stl_vector, stl_size, sizeof(double), ebur128_double_cmp);
  stl_power = 0.0;
  for (i = 0; i < stl_size; ++i) {
    stl_power += stl_vector[i];
  }
  stl_power /= (double) stl_size;
  stl_integrated = minus_twenty_decibels * stl_power;

  stl_relgated = stl_vector;
  stl_relgated_size = stl_size;
  while (stl_relgated_size > 0 && /* LCOV_EXCL_BR_LINE */
         *stl_relgated < stl_integrated) {
    ++stl_relgated;
    --stl_relgated_size;
  }

  /* defensive check */
  if (stl_relgated_size > 0) { /* LCOV_EXCL_BR_LINE */
    h_en =
            stl_relgated[(size_t) ((double) (stl_relgated_size - 1) * 0.95 + 0.5)];
    l_en =
            stl_relgated[(size_t) ((double) (stl_relgated_size - 1) * 0.1 + 0.5)];
    free(stl_vector);
    *out = ebur128_energy_to_loudness(h_en) - ebur128_energy_to_loudness(l_en);
  } else {
    free(stl_vector); /* LCOV_EXCL_LINE */
    *out = 0.0;       /* LCOV_EXCL_LINE */
  } /*                   LCOV_EXCL_LINE */

  return EBUR128_SUCCESS;
}

int ebur128_loudness_range(ebur128_state* st, double* out) {
  return ebur128_loudness_range_multiple(&st, 1, out);
}

int ebur128_sample_peak(ebur128_state* st,
                        unsigned int channel_number,
                        double* out) {
  if ((st->mode & EBUR128_MODE_SAMPLE_PEAK) != EBUR128_MODE_SAMPLE_PEAK) {
    return EBUR128_ERROR_INVALID_MODE;
  }

  if (channel_number >= st->channels) {
    return EBUR128_ERROR_INVALID_CHANNEL_INDEX;
  }

  *out = st->d->sample_peak[channel_number];
  return EBUR128_SUCCESS;
}

int ebur128_prev_sample_peak(ebur128_state* st,
                             unsigned int channel_number,
                             double* out) {
  if ((st->mode & EBUR128_MODE_SAMPLE_PEAK) != EBUR128_MODE_SAMPLE_PEAK) {
    return EBUR128_ERROR_INVALID_MODE;
  }

  if (channel_number >= st->channels) {
    return EBUR128_ERROR_INVALID_CHANNEL_INDEX;
  }

  *out = st->d->prev_sample_peak[channel_number];
  return EBUR128_SUCCESS;
}

int ebur128_true_peak(ebur128_state* st,
                      unsigned int channel_number,
                      double* out) {
  if ((st->mode & EBUR128_MODE_TRUE_PEAK) != EBUR128_MODE_TRUE_PEAK) {
    return EBUR128_ERROR_INVALID_MODE;
  }

  if (channel_number >= st->channels) {
    return EBUR128_ERROR_INVALID_CHANNEL_INDEX;
  }

  *out = EBUR128_MAX(st->d->true_peak[channel_number],
                     st->d->sample_peak[channel_number]);
  return EBUR128_SUCCESS;
}

int ebur128_prev_true_peak(ebur128_state* st,
                           unsigned int channel_number,
                           double* out) {
  if ((st->mode & EBUR128_MODE_TRUE_PEAK) != EBUR128_MODE_TRUE_PEAK) {
    return EBUR128_ERROR_INVALID_MODE;
  }

  if (channel_number >= st->channels) {
    return EBUR128_ERROR_INVALID_CHANNEL_INDEX;
  }

  *out = EBUR128_MAX(st->d->prev_true_peak[channel_number],
                     st->d->prev_sample_peak[channel_number]);
  return EBUR128_SUCCESS;
}
