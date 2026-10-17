# dApp wallet request-policy foundation

## Scope of this draft

This change adds an isolated, dependency-free policy evaluator and unit tests. It does not alter WebView creation, navigation, PWA behavior, wallet key storage, signing, network routing, or transaction submission.

It is not yet a working dApp provider. The policy must be connected to a trusted native permission/confirmation UI before it can authorize real wallet operations.

## Policy guarantees

- Rejects non-HTTPS URLs and URLs containing user information.
- Rejects requests from a cross-origin frame.
- Requires a connection prompt before granting a site access.
- Treats account reads as read-only only after a connection grant.
- Never approves signing or transaction submission automatically.
- Requires a transaction payload for signing/submission.
- Rejects a transaction request that names a different active network.
- Requires explicit approval for a network change.
- Denies unknown methods.

## Required next integration steps

1. Bind requests to the actual top-level and requesting-frame origins supplied by the browser engine; never accept origins claimed by page JavaScript.
2. Add a trusted native permission UI and persistent per-origin grants with revoke support.
3. Integrate native Kaspa signing with the existing wallet engine only after user approval.
4. Add a separately reviewed EVM provider/signing path for Kasplex and Igra; do not route EVM payloads into the native Kaspa transaction signer.
5. Add WalletConnect pairing/session validation for external dApps if required.
6. Verify KRC-20, KRC-721 commit/reveal, Kasplex and Igra purchase flows against live test environments.
7. Run unit tests, Android debug build, regression tests and device tests covering proxy/privacy routing, tabs, redirects and standalone PWAs.

Do not advertise wallet-connect support until those integration and validation steps pass.
