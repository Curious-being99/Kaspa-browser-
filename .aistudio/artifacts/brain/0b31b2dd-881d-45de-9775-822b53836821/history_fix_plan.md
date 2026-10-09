# Implementation Plan: Fix Transaction History & Balance Sync

## Problem
When sending Kaspa, the transaction is not being properly recorded in the wallet's history page, and the balance update isn't syncing correctly on the chain.

## Proposed Solution
1.  **Fix History Recording**: 
    -   Ensure that when a transaction is successful (`result.onSuccess`), the `KaspaTransactionItem` is not only added to the `recentTransactions` list in the `kaspaWalletState` but also persisted into a database table (Room) if one is available for transactions, or that the current history mechanism is correctly updated.
2.  **Fix Balance Sync**:
    -   Verify that `refreshTestnetWallet()` is correctly re-fetching the updated balance from the Kaspa network after a successful broadcast.
    -   Ensure `KaspaTransactionEngine` and `KaspaWalletService` provide accurate confirmation status.
3.  **UI Update**:
    -   Ensure the `TransactionCard` is correctly handling the "SENT" type with red color as requested.

## Steps
1.  **Modify `DecentralViewModel.kt`**:
    -   Update `sendKaspaTransaction` to ensure the new transaction is properly persisted.
    -   Check if a dedicated DAO for transaction history needs to be updated.
2.  **Verify UI**:
    -   Review `KaspaTestnetWalletScreen.kt` to ensure `TransactionCard` renders sent transactions in red.
3.  **Verification**:
    -   Broadcast a transaction and verify:
        -   It appears in the history list.
        -   The balance updates correctly.
        -   It is colored red.
