# Kaspa Browser EVM dApp provider draft

## Mainnet separation

- Kasplex Mainnet: chain ID 202555, RPC `https://evmrpc.kasplex.org`.
- Igra Mainnet: chain ID 38833, RPC `https://rpc.igralabs.com:8545`.
- Native Kaspa Mainnet remains separate and uses its native address and transaction engine.

The selected EVM network controls RPC reads, dApp chain ID, transaction signing, broadcasting, and the activity view. Kasplex and Igra activity is never merged with native Kaspa history or with the other EVM chain.

## Real provider operations

The WebView installs an EIP-1193-style `window.ethereum` provider using AndroidX WebMessageListener and document-start script injection. The native message listener accepts HTTPS main-frame messages only when the message origin matches the current page origin. It does not use `addJavascriptInterface`.

The provider obtains chain data and transaction responses through real JSON-RPC requests to the selected network endpoint. Private-key operations happen locally. No sample balances or fabricated transaction hashes/statuses are used.

Supported methods include:
- `eth_requestAccounts`, `eth_accounts`, `eth_chainId`, and `net_version`.
- Read-only RPC calls for balances, calls, blocks, code, receipts, logs, gas estimation, fees, and nonce reads.
- User-approved `wallet_switchEthereumChain` for the two built-in EVM networks.
- `personal_sign` using the exact decoded message bytes and the Ethereum personal-message prefix.
- `eth_sign` using Keccak-256 of the supplied bytes without the personal-message prefix; it has a separate warning and approval.
- `eth_signTypedData`, `eth_signTypedData_v3`, and `eth_signTypedData_v4` with native approval and a chain-ID check when the typed-data domain includes one.
- `eth_sendTransaction`: fetches missing nonce/gas information from the selected RPC, asks for approval, signs locally, broadcasts the signed transaction, and returns the actual RPC transaction hash.
- `eth_signTransaction`: performs the same preparation and approval, signs locally, and returns the signed raw transaction without broadcasting it.

A transaction hash means the RPC accepted the broadcast request; it is not proof of confirmation. Confirmation must be read from a real transaction receipt. Unsupported methods are rejected; arbitrary raw transaction submission from a page is disabled. Only standard destination-address transactions with legacy or EIP-1559 fee fields are supported by the current transaction encoder; contract creation and custom networks are rejected.

## Wallet activity screen

The activity view queries the currently selected network directly over JSON-RPC. It scans the latest 100 blocks for native transactions involving the active EVM address and requests real ERC-20/ERC-721-style `Transfer` logs for that same block range. Transaction receipts are queried to display actual status where available. Transfer log amounts are shown as raw units because token decimals and metadata are not assumed.

This is deliberately a bounded recent-activity view, not a complete historical indexer. It reports the scanned block range and any RPC scan errors. Older activity is not claimed to be present. No mock transaction records or sample balances are inserted.

## BIP-39 passphrases and legacy phrases

The EVM key path is `m/44'/60'/0'/0/0`. The optional BIP-39 passphrase is encrypted using an AES-GCM key held by Android Keystore and stored separately per wallet account. If encrypted passphrase data cannot be decrypted, account access and signing fail closed until the passphrase is re-entered and saved.

New recovery phrases are generated using the standard BIP-39 generator. Legacy phrases that fail BIP-39 validation can be used in explicit compatibility mode only when they contain 12–24 alphabetic words. The exact phrase is passed to the deterministic BIP-39 seed derivation function without changing its words; this does not repair the phrase's checksum, establish that it is a standard BIP-39 phrase, or guarantee interoperability with other wallets.

For a non-standard legacy phrase, the wallet displays the derived EVM address and requires the user to confirm that exact address before dApp connection or signing. Confirmation is tied to the address and wallet account, so changing the passphrase produces a different address that must be confirmed again. This is an explicit compatibility path, not a claim that a pre-existing EVM address can be recovered when the legacy derivation scheme is unknown.

## Approval and limitations

Transaction approval shows the requesting origin, selected network, account, destination, value, and gas limit. It does not decode or predict arbitrary smart-contract effects. Token and NFT calls can have irreversible consequences; review calldata and spender/recipient details before approving.

This draft does not claim to support every EVM wallet extension method, every transaction type, or complete historical indexing. Android build, Gradle tests, CI, device tests, and end-to-end tests against both real mainnets remain required. Keep the PR in draft until those checks pass.

## Native Kaspa dApp provider boundary

The native Kaspa provider is separate from the EVM `window.ethereum` provider. It is injected only into HTTPS main-frame pages through the secure WebView message bridge.

The current native bridge supports address connection, account reads, network reads, public-key reads, message signing, PSKT input signing, signed-transaction submission, and disconnect. Signing and submission require native user approval. PSKT signing validates the selected inputs against the active wallet's public-key locking script and validates the declared UTXO data and fee before signing.

The bridge does not yet implement the full, independently documented APIs of Kastle and Kasware, nor the full request/method contract of Kaspire or Kasperia. It must not advertise itself as those wallets or install their names as aliases unless their exact documented API, return types, event semantics, network semantics, and token methods are implemented and tested. A generic provider alias is not wallet compatibility.

KRC-20 and KRC-721/NFT actions require the dApp's actual transaction/PSKT shape to be supported by the signer and the corresponding marketplace/covenant effects to be reviewed. KCC-20 is an EVM-side contract flow and must use the EVM provider, selected chain, ABI encoding, transaction simulation/review, and chain-specific signing path. None of these asset flows should be described as supported solely because address connection or generic PSKT signing exists.

Compatibility completion requires provider-contract tests for each supported wallet API, network/account-change event tests, native approval/rejection tests, malformed and hostile transaction tests, and real test transactions on the relevant networks. Keep unsupported methods explicit and fail closed.
