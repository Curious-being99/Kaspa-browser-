# Native dApp Wallet Provider — Implementation Contract

## Goal

Allow a website opened in Kaspa Browser to discover and connect to the browser's own wallet, request read-only wallet data, and request reviewed signatures for Kaspa L1 and Kasplex L2 operations. This document is the implementation contract; it does not claim that provider support is implemented yet.

## Existing constraints

- The browser currently uses Android System WebView. It does not provide a Chrome extension runtime.
- The existing native wallet has account/network state and a native send flow in `DecentralViewModel`.
- Do not expose seeds, private keys, decrypted secrets, or signing primitives to page JavaScript.
- Do not add a broadly callable `addJavascriptInterface` wallet object. The existing WebAuthn bridge is a separate feature and must not be reused as a wallet bridge.

## Provider surfaces

### Kaspa L1

Implement the KIP-12 / Kaspa Wallet Standard discovery handshake where practical (`kaspa:requestProvider` and `kaspa:provider`), plus compatibility aliases required by dApps that use `window.kasware` or `window.kastle`. Advertise only methods actually implemented.

Minimum read/connect operations:
- request account access after explicit user approval
- get authorized accounts, public key, and active network
- get balance and UTXOs only after origin authorization, with capability checks

Transaction operations must be native-approved and use the existing wallet transaction engine:
- native KAS sends
- KRC-20 operations using the dApp's supported signing/build flow
- KRC-721/NFT transfers and purchase flows where the protocol and transaction format are supported
- PSKT/PSKT-like signing only when the wallet can validate the transaction and sign only explicitly selected wallet-owned inputs

Do not report a generic NFT or KRC-20 purchase as supported just because arbitrary bytes can be signed. Each protocol operation needs a supported transaction format and test fixtures.

### Kasplex L2 / KCC20

Provide a separate EVM-compatible `window.ethereum` provider only when the active wallet has an actual Kasplex L2 account, signing implementation, and RPC/network configuration. Follow EIP-1193 request/error semantics and report the correct chain ID. Support `eth_requestAccounts`, account/chain reads, message signing, and `eth_sendTransaction` only after each operation is implemented and shown in a native confirmation screen.

KCC20 can refer to different implementations and may involve covenant-native rather than EVM contract transactions. Detect the dApp's actual protocol; do not route a KCC20 covenant transaction through the EVM signer or assume every KCC20 purchase is an ERC-20 transfer.

## Security requirements

- Bind every request to the current top-level HTTPS origin and a per-request ID.
- Reject stale responses after navigation, tab changes, origin changes, or WebView destruction.
- Keep per-origin permissions revocable; disclose only the approved public address.
- Require an explicit native approval screen for account connection and every signature, purchase, transfer, contract call, or broadcast. Show network, destination/contract, amount/assets, fee, and decoded operation when available.
- User rejection must reject the page Promise; it must never broadcast.
- Validate network prefixes/chain IDs, addresses, transaction structure, fee, selected inputs, and signing scope in native code before signing.
- Never let page JavaScript choose a wallet secret, bypass transaction review, silently change networks, or auto-approve a request.
- Use an origin-scoped AndroidX WebKit message channel / listener with a strict origin allowlist where supported. If secure origin binding cannot be established on a supported Android System WebView, disable wallet injection for that environment rather than falling back to an unrestricted native bridge.
- Inject only into the top-level eligible HTTPS page, not untrusted frames, file URLs, or arbitrary origins. Preserve the browser's existing privacy/proxy routing and PWA lifecycle.

## Required validation before enabling

1. Unit tests for provider discovery, origin matching, request IDs, navigation invalidation, permission revocation, rejection paths, malformed inputs, and network mismatch.
2. Tests proving no key/seed/private key is reachable from JavaScript.
3. Test dApps that use KIP-12, `window.kasware`, and EIP-1193 on the exact target networks.
4. Fixtures for KRC-20 mint/transfer/buy, KRC-721/NFT transfer/purchase, and supported KCC20 transaction types. Verify that signing preserves all unselected inputs and signs only approved wallet-owned inputs.
5. Confirm the native confirmation UI accurately displays what is being signed; cancellation must leave the chain unchanged.
6. Android debug build, unit tests, physical-device tests, mainnet/testnet isolation tests, and proxy/privacy regression tests.

## Status

Planning/contract only. This file does not add a provider, signing flow, KCC20 support, or working dApp connection. Keep any implementation PR in draft until the checks above pass; advertise capabilities incrementally rather than claiming all token/NFT purchases work at once.
