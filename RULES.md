# Repository Rules

- Seeds never come from email.
- Every signer goes through `getDecryptedSeedForSigning`.
- Every fix merges to main.
- Never delete a wallet without the recovery-phrase warning.
- Never delete live Chromium WebView cache directories on disk or race WebView initialization across threads; prepare directories before `warmUpEngineAsync` and clear cache via `WebView.clearCache(true)`.
- App lock is separate from wallet signing: unlocking the browser never counts as approving a payment, and sending must still prompt for the password or biometric every time.
- If the user forgets the browser lock password, never wipe or reset the wallet; fall back to the phone's own lock (`BiometricPrompt` with device credential).
