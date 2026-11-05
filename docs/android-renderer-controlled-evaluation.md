# Android renderer controlled evaluation

**Status: Evaluation only — no production renderer migration is approved.**

## Scope and safety boundary

This pull request establishes a controlled evaluation of Android rendering-engine candidates. It does not change the production Android renderer, browser UI, shared browser state, navigation, tabs, history, privacy policy, downloads, permissions, media handling, dApp provider, or wallet implementation.

Linux CEF is explicitly out of scope and must remain unchanged.

Android System WebView remains the production renderer and compatibility baseline. Do not remove its dependency, manager, lifecycle code, or implementation as part of this evaluation.

## Candidate decision

Cefrium 0.17.0 is not approved for production integration at this time.

The repository's candidate evaluation records vendor-published PSS results substantially above System WebView and identifies significant compatibility/toolchain costs: minSdk 29 versus the app's existing minSdk 24, JDK 25, Gradle 9.7.1, AGP 9.4, compileSdk 37, beta API stability, and large native artifacts. These figures are directional vendor data, not KaspaBrowser device measurements.

Accordingly, Cefrium must be evaluated in an isolated sample/harness. It must not be wired into the production BrowserGatewayScreen while the evaluation is in progress.

## Evaluation protocol

Run the same release build, page set, device OS, network, and test sequence for each candidate and the WebView baseline.

Devices:
- Redmi 15C
- Samsung Galaxy A52
- Additional supported Android API levels, including the current minimum supported API.

Record:
- Total app plus all renderer-process PSS: median, peak, and time series.
- Cold and warm startup; first contentful render where measurable.
- Single-tab and multi-tab memory; long-session growth; tab close/reopen.
- Crash rate, renderer termination/recovery, background/foreground and low-memory behavior.
- APK/AAB size, ABI coverage, install/upgrade/rollback behavior.
- HTTP/HTTPS navigation, redirects, history, reload, back/forward, file chooser, downloads, certificates and errors.
- Main-frame/subresource request interception, DNS/proxy policy, mixed content, storage isolation, permissions, and telemetry.
- dApp provider origin binding, main-frame restrictions, iframe and malicious-origin tests.
- Real on-device Kaspa wallet transaction review/signing, cancellation and failure cases; verify no key exposure and unchanged broadcast semantics.
- Audio/video, fullscreen, hardware decode, background playback, audio focus and Bluetooth/headset behavior.
- Cookies, local storage, IndexedDB, service workers, tab restoration and clear-data behavior.

## Acceptance gate

A candidate remains **NOT APPROVED** until all tests above have reproducible logs/results on real devices and supported Android versions. A successful Gradle build or one-page smoke test is insufficient.

A candidate must demonstrate a meaningful memory/stability/performance improvement over WebView without regressions in browsing, privacy, downloads, media, dApps, wallet signing, OS compatibility, or distribution size.

## Production migration rule

Only a separate, reviewed change may migrate the production renderer after the acceptance gate passes. WebView may be removed only after the replacement is proven on the minimum supported Android version and representative devices, and after all production acceptance tests pass.

Until then:
- Keep System WebView in production.
- Do not raise minSdk solely to accommodate an unproven candidate.
- Do not modify Linux CEF or wallet architecture.
- Do not claim candidate integration, build success, or test success without attached evidence.
