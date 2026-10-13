# Wallet send-history and balance consistency fix plan

## Reproduced from the supplied video

The wallet screen shows a successful outgoing transaction in the Sent filter, but the balance shown after broadcast is still the pre-send balance. The history also shows incoming transactions and an outgoing transaction with a fee. This report focuses on the send path and refresh merge; no claim is made that the video proves an on-chain balance bug.

## Source findings

- `DecentralViewModel.sendKaspaTransaction` constructs a local sent item and immediately subtracts amount plus fee from the displayed balance.
- It then calls `refreshKaspaWallet`, which fetches the live API balance and overwrites the displayed balance with the remote response.
- `refreshKaspaWallet` merges local Room history with remote history by transaction ID. Local records absent from the remote response are retained, but the remote map currently wins for matching IDs.
- `KaspaWalletService.fetchWalletState` derives history type by examining inputs and outputs. When an input's `utxo_entry` is absent, it must resolve the previous outpoint; if that resolution fails, an outgoing transaction may be misclassified or omitted.
- The video alone cannot establish whether the displayed balance is wrong on-chain or only stale in the UI. Do not permanently subtract locally from the authoritative balance without a transaction-state model; that can double-subtract after the API catches up.

## Safe implementation requirements

1. Persist the broadcast transaction to Room and await persistence before triggering refresh; use a unique transaction ID and the selected network/account as the record scope.
2. Preserve sent/received type for a locally broadcast transaction when a remote response with the same transaction ID is incomplete or misclassified. Update acceptance/confirmation metadata only from trustworthy remote data.
3. Resolve input ownership from the previous outpoint when verbose input UTXO data is missing. If ownership cannot be determined, mark the transaction as unresolved instead of guessing that it is received.
4. Keep chain balance and pending outgoing amount distinct. Display an explicitly computed pending balance only while a broadcast transaction is pending; reconcile it against the fetched UTXO/on-chain balance when the transaction is accepted/rejected or the API balance catches up. Never subtract a transaction twice.
5. Scope local history queries to the active account/address as well as network; network-only history can mix wallet accounts.
6. Add unit tests for input ownership resolution, self-change outputs, sent transactions missing from the API response, duplicate refreshes, API balance lag, broadcast failure, and account/network switching.
7. Verify with a physical-device test and known transaction ID before release.

## Status

This document records source-level findings and required acceptance tests. It does not by itself fix runtime behavior. The actual code change must be made and tested before this draft can be marked ready.
