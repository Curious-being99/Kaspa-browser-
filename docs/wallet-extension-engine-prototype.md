# Kaspa wallet-extension engine prototype

## Scope

This branch is an isolated feasibility prototype for supporting multiple Kaspa wallet WebExtensions. It does not replace the current WebView browser, PWA activity, or privacy relay.

## Why GeckoView is being evaluated

Android System WebView does not implement the Chrome extension runtime. GeckoView exposes a real WebExtensions controller, but it does not guarantee compatibility with Chrome Web Store packages. GeckoView's remote extension installation validates Mozilla signatures, so a Chrome Web Store URL or CRX cannot be treated as an installable XPI.

## Non-negotiable safety gates

- Do not inject a fake `window.kaspa` provider or expose native wallet secrets to page JavaScript.
- Do not claim support for all Kaspa wallets. Maintain a tested compatibility matrix per extension ID/version.
- Do not wire this prototype into production navigation until the extension engine has its own tested session, permission prompts, popup/action handling, and lifecycle.
- Do not silently bypass KRP/proxy routing. GeckoView networking must be evaluated separately; existing WebView ProxyController behavior does not automatically apply.
- Keep PWA standalone behavior and current WebView path unchanged during the prototype.
- Wallet transaction approvals must be shown in trusted browser UI and must never be auto-approved by extension messages.
- Reject unsupported extension packages with a clear error; never disable signature validation to make installation work.

## Required validation before integration

1. Confirm the pinned GeckoView artifact resolves and builds against this repository's Android Gradle Plugin/Kotlin versions.
2. Add an isolated GeckoView session screen behind a developer-only entry point; do not replace MainActivity's WebView.
3. Test a signed GeckoView-compatible wallet extension and at least two other independently maintained Kaspa wallet extensions where a compatible signed package is available.
4. Test dApp connect/disconnect, account/network changes, rejected and approved transaction prompts, extension restart persistence, and malicious-origin attempts.
5. Verify direct requests, redirects, POSTs, DNS, WebRTC, downloads, and PWA requests do not bypass the intended privacy-routing policy.
6. Run debug and release builds, unit/instrumentation tests, install/upgrade tests, and device tests before marking this PR ready.

## Known limitation

This is not yet an installable-wallet feature. It is a design and validation gate; it must not be represented to users as working extension support.
