#ifndef KAZVIEW_NATIVE_CORE_H
#define KAZVIEW_NATIVE_CORE_H

#include <stdbool.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

/**
 * Evaluates whether an HTTPS URL is eligible for speculative preconnect/prefetch
 * without blocking or allocating garbage objects on hot navigation/typing paths.
 *
 * Returns true if valid HTTPS, non-local, and not an auth, payment, CAPTCHA, or tracker host.
 */
bool kazview_is_speculative_eligible(const char* url, bool privacy_blocked, bool low_ram_device);

/**
 * Checks & records origin warmup in a fixed-capacity lock-guarded ring buffer.
 * Returns true if warmup should proceed (not within cooldown_ms), false if suppressed.
 */
bool kazview_record_origin_warmup(const char* origin, int64_t now_ms, int64_t cooldown_ms);

/**
 * Clears all tracked origin warmup entries (invoked on memory pressure or privacy reset).
 */
void kazview_reset_warmup_cache(void);

#ifdef __cplusplus
}
#endif

#endif // KAZVIEW_NATIVE_CORE_H
