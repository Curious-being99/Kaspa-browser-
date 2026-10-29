# BraidSwap integration status

## Scope and safety boundary

BraidSwap is implemented as a separate, read-only API client in `BraidSwapClient.kt`. It has separate mainnet and TN10 endpoints, expected current router fingerprints, and a local network-specific token allowlist sourced from the supplied Braid wallet kit. Before requesting quotes, it checks the router status/pin, validates amount strings, enforces network-specific slippage limits, and can pass an optional integrator fee address/rate (1–200 bps). When a fee is requested, it checks that the returned quote reports the same fee address/rate. These API checks are not a substitute for independently hashing the router program and verifying a built transaction.

This is intentionally not wired to the existing swap execution button yet. The client currently supports read-only quotes only; it does not call `/deposit-build`, `/deposit-submit`, `/status`, or `/details`. Braid's deposit protocol is different from KaspaRocket's `planTrade` / signing instructions; passing a Braid deposit through `KaspaRocketNativeSigner` would be unsafe. The full Braid pre-sign verifier, independent BLAKE2b-256 router pin calculation, independent chain-time checks, transaction sighash calculation, and post-submit status verification must be ported and tested before any Braid deposit is built, signed, or submitted.

KaspaRocket execution and quote code is unchanged. The wallet send flow and SI Agent are not changed.

## Supported tokens from the supplied kit

- Mainnet: THEN, KRON, PEPE, IFWEN, ANSEM, GUZA, KASCOV.
- TN10: ICE, ROCKET, MKRON, MKPEPE, FIRE, KAHA20, KRKT20, KZEN20.

The registry is network-specific. A token listed on one network is not implicitly supported on the other.

## $KB compatibility investigation

$KB covenant/token ID: `b75a6e90f569965bdeec23c460ebd59e0f0445d8aa8f5d4330fe15c66a47b4d8`.

The supplied Braid wallet kit's pinned token registry and pinned-pool table did not include this ID in the material reviewed. The kit requires the wallet to pin both a known token contract lineage/template and a pool covenant/code identity. Being a KCC-20 token, or being listed on KaspaRocket, does not by itself prove that Braid's route contracts support this exact token or that a compatible Braid-routed pool exists. The public Braid API could not be independently queried from this session, so live pool compatibility remains unverified.

**Decision: $KB is not marked Braid-compatible and is rejected by this client.** Do not add it to the allowlist based on symbol or token ID alone. Reconsider only after confirming from chain data:
1. the actual $KB covenant template/lineage matches one of Braid's pinned templates and expected prefix/suffix lengths;
2. a live pool is reachable through Braid and its covenant ID and code hash can be independently pinned;
3. Braid's quote/build API supports the exact $KB covenant ID;
4. the complete deposit verifier passes against a fresh quote/build and tampering fixtures, including outputs, fees, token notes, recovery payload, deadline, minimum output, sighash, and exact wallet debit.

## Validation status

The added unit tests cover the local token allowlist and network router pin constants. Android compilation, CI, Braid live API behavior, quote fee behavior, transaction verification, signing, and a low-value live trade have not been verified in this session. Do not treat Braid swaps as production-ready yet.
