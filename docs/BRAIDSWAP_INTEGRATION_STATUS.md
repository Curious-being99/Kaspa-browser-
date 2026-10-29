# BraidSwap integration status

## Scope and safety boundary

BraidSwap is implemented as a separate API client in `BraidSwapClient.kt`. It has its own mainnet and TN10 endpoints, hard-pinned current router fingerprints, and a local token allowlist sourced from the supplied Braid wallet kit. It validates the current router pin before asking for a quote and rejects assets that are not in the allowlist for the selected network.

This is intentionally not wired to the existing swap execution button yet. Braid's deposit protocol is different from KaspaRocket's `planTrade` / signing instructions; passing a Braid deposit through `KaspaRocketNativeSigner` would be unsafe. The full Braid pre-sign verifier must be ported and tested before any Braid deposit is built, signed, or submitted. Until that exists, this client is read-only and does not broadcast transactions.

KaspaRocket execution and quote code is unchanged. The wallet send flow and SI Agent are not changed.

## Supported tokens from the supplied kit

- Mainnet: THEN, KRON, PEPE, IFWEN, ANSEM, GUZA, KASCOV.
- TN10: ICE, ROCKET, MKRON, MKPEPE, FIRE, KAHA20, KRKT20, KZEN20.

The registry is network-specific. A token listed on one network is not implicitly supported on the other.

## $KB compatibility investigation

$KB covenant/token ID: `b75a6e90f569965bdeec23c460ebd59e0f0445d8aa8f5d4330fe15c66a47b4d8`.

The supplied Braid wallet kit does not include this ID in its pinned token registry or pinned-pool table. The kit requires the wallet to pin both a known token contract lineage/template and a pool covenant/code identity. Being a KCC-20 token, or being listed on KaspaRocket, does not by itself prove that Braid's route contracts support this exact token or that a compatible Braid-routed pool exists.

**Decision: $KB is not marked Braid-compatible and is rejected by this client.** Do not add it to the allowlist based on symbol or token ID alone. Reconsider only after confirming from chain data:
1. the actual $KB covenant template/lineage matches one of Braid's pinned templates and expected prefix/suffix lengths;
2. a live pool is reachable through Braid and its covenant ID and code hash can be independently pinned;
3. Braid's quote/build API supports the exact $KB covenant ID;
4. the complete deposit verifier passes against a fresh quote/build and tampering fixtures, including outputs, fees, token notes, recovery payload, deadline, minimum output, sighash, and exact wallet debit.

## Validation status

The added unit tests cover the local token allowlist and network router pin constants. Android compilation, CI, Braid live API behavior, transaction verification, signing, and a low-value live trade have not been verified in this session. Do not treat Braid swaps as production-ready yet.
