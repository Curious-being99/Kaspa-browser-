#ifndef KAZIUM_WALLET_BRIDGE_H
#define KAZIUM_WALLET_BRIDGE_H

#include <stdint.h>
#include <stddef.h>
#include <stdbool.h>

#ifdef __cplusplus
extern "C" {
#endif

/// Validates whether a Kaspa address format is syntactically correct
int kaspa_wallet_validate_address(const char* address);

/// Builds a KCC-20 covenant transfer JSON payload
int kaspa_wallet_build_kcc20_payload(
    const char* symbol,
    const char* amount,
    const char* recipient,
    char* out_buf,
    size_t out_len
);

/// Formats sompi into a decimal string (1 KAS = 100,000,000 sompi)
int kaspa_wallet_format_sompi(
    uint64_t amount_sompi,
    char* out_buf,
    size_t out_len
);

/// Returns the wallet bridge protocol version
uint32_t kaspa_wallet_version(void);

#ifdef __cplusplus
}
#endif

#endif // KAZIUM_WALLET_BRIDGE_H
