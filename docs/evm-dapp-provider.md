# Kaspa Browser EVM dApp provider draft

## Supported networks

- Kasplex Mainnet: chain ID 202555, RPC `https://evmrpc.kasplex.org`.
- Igra Mainnet: chain ID 38833, RPC `https://rpc.igralabs.com:8545`.

These are built-in networks. Custom RPC endpoints and arbitrary chain IDs are not accepted by the provider. Native Kaspa Mainnet remains a separate network and does not use EVM addresses or EVM transaction signing.

## Real provider and network operations

The WebView installs an EIP-1193-style `window.ethereum` provider using AndroidX WebMessageListener and document-start script injection. The native message listener accepts HTTPS main-frame messages only when the message origin matches the current page origin. It does not use `addJavascriptInterface`.

The provider obtains chain data and transaction responses through real JSON-RPC requests to the selected network's configured RPC endpoint. Signing is performed locally with the derived EVM key.

Implemented request groups:
- Account connection and reads: `eth_requestAccounts`, `eth_accounts`, `eth_chainId`, `net_version`.
- Read-only RPC allowlist for balances, calls, blocks, code, receipts, logs, gas estimation, and fee/nonce reads.
- User-approved network switching between the two built-in EVM networks.
- `personal_sign` with a native approval prompt and local signing.
- `eth_signTypedData_v4` with a native approval prompt, local signing, and a domain chain-ID check when supplied.
- `eth_sendTransaction`: reads pending nonce and gas data from the selected RPC where needed, requests native user approval, signs locally, broadcasts the signed transaction to the selected RPC, and returns the RPC transaction hash.

A broadcast hash is not represented as transaction confirmation. Confirmation must be determined from the selected chain's real transaction receipt/status. The current request flow does not fabricate a receipt or a confirmed status.

Unsupported signing methods are rejected instead of being accepted. The current supported signing surface is intentionally limited to the methods listed above; it does not claim compatibility with every wallet-specific or EVM signing method. Custom networks, contract creation (empty `to`), typed-data variants other than v4, raw transaction submission from the dApp, and unrecognized RPC methods are not supported in this draft.

## Transaction approval

The approval dialog is the authorization step before local signing and RPC broadcast. It shows the requesting website, selected network, account, destination, native value, and gas limit, and warns that contract calls may transfer tokens or NFTs and may be irreversible. It displays transaction fields and does not claim to decode or predict arbitrary contract effects. Users should only approve calls they understand.

## Key derivation and compatibility

The EVM account uses the standard BIP-39 seed and BIP-32 path `m/44'/60'/0'/0/0`, independently of Kaspa's native key path. New mnemonic generation now uses the standard BIP-39 wordlist and checksum.

Legacy wallet phrases created by the older non-standard phrase generator may fail BIP-39 validation. This draft refuses EVM derivation for invalid phrases rather than deriving an undocumented key or presenting a fabricated address. It does not yet provide a verified migration path for those legacy phrases. Wallets created with a custom BIP-39 passphrase also require that passphrase to derive the same EVM account; this draft currently derives with an empty passphrase and must not claim support for passphrase-protected recovery phrases.

## Validation status

Unit tests were added for the known BIP-39 test account, chain IDs, legacy transaction signing, EIP-712 signing, sender mismatch, and chain mismatch. They have not been executed locally in this environment. Android build, Gradle tests, CI, device tests, and end-to-end dApp tests remain required. This is an experimental draft, not production-certified wallet support.
