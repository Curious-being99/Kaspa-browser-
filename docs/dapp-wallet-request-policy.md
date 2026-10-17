# Kaspa Browser dApp wallet bridge

## Implemented in this draft

- HTTPS main-frame provider injection using AndroidX WebView messaging; no `addJavascriptInterface`.
- Per-origin connect permission and native confirmation for wallet connection.
- Read-only account, network and public-key methods.
- Native message-signing approval.
- `signPskt({ txJsonString, options: { signInputs } })`: signs only requested standard P2PK inputs controlled by the active wallet, supports Safe-JSON transactions nested under `transaction`, honors only `SIGHASH_ALL`, preserves all unselected input scripts and other JSON metadata, and recalculates mass using the declared UTXO amounts.
- `submitTransaction(txJsonString)`: requires a separate native approval and submits through the existing Kaspa wallet service.

## Transaction safety rules

- Only standard version-0 P2PK locking scripts matching the active wallet public key can be signed by this signer.
- P2SH/covenant inputs are not blindly signed. Their scriptSig is preserved exactly, including an empty scriptSig where the protocol's transaction flow requires it. The wallet signs only the explicit funding inputs listed by the dApp.
- Duplicate outpoints, duplicate input indexes, unsupported sighash types, malformed scripts, mismatched sender, invalid UTXO metadata, insufficient wallet fee floor and excessive transaction mass are rejected.
- Submission separately confirms the transaction and permits non-P2PK protocol-authorized input scripts to remain unchanged. The receiving node remains the final consensus/mempool gate.

## Compatibility boundaries

This is a generic Kaspa PSKT signing path, not a complete protocol implementation. It can participate in KRC-20 or NFT marketplace flows when their PSKT follows the supported Safe-JSON shape and uses wallet-owned P2PK funding inputs alongside pre-authorized protocol inputs. This does not independently build or validate marketplace intent, token state, P2SH redeem scripts, covenant templates, or commit/reveal sequences. Those need protocol-specific builders and transaction-review logic.

Kasplex and Igra EVM dApps require a separate EVM account derivation/signing implementation and EIP-1193 provider. This draft does not expose `window.ethereum` or claim EVM signing compatibility.

## Validation status

The draft includes unit tests for selected-input signing, preserving covenant input scripts and metadata, fee-floor enforcement, submission inspection, wallet-key ownership, and duplicate outpoints. These tests have not yet been executed with Gradle in this environment. Android build, CI, device testing, testnet submission, and end-to-end KRC-20/NFT tests remain required. Do not merge until those checks pass.
