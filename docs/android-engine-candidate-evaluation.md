# Android browser-engine replacement evaluation

**Evaluation date:** 2026-11-05  
**Repository baseline:** `feat/shared-browser-engine-contract` / PR #121  
**Decision:** No evaluated candidate currently qualifies to replace Android System WebView.

## Product constraints

The current Android app has:

- `minSdk = 24` (Android 7.0+)
- `compileSdk = 36`
- Java source/target compatibility 17
- Kotlin JVM target 17
- Gradle wrapper 9.3.1
- Android Gradle Plugin 9.1.1
- A native Compose shell and a per-tab WebView manager

The engine must preserve the existing native wallet boundary, origin-bound dApp provider behavior, request/privacy policy, tab/session state, downloads, media and background playback. A renderer swap must not expose wallet keys or introduce a generic JavaScript-to-native bridge.

## Candidate results

### 1. Cefrium 0.17.0 — Chromium/CEF for Android

**Status: Not eligible for integration as the default renderer.**

Positive:
- Embeddable Android Chromium/CEF engine with multi-process rendering.
- ARM64 and x86_64 artifacts.
- Request interception and an embeddable view.
- Current published release tracks Chromium 156.0.8078.73.

Blocking issues:
- Its published benchmark reports total PSS of 392 MB vs 156 MB for System WebView on the plain-page W90 test, and 387 MB vs 157 MB on the Redmi Note 13 Pro test. On the modern-web test it reports 475 MB vs 279 MB and 438 MB vs 205 MB respectively. These are vendor-published matched-harness measurements, not KaspaBrowser measurements or an independent audit; they are nevertheless a strong reason not to assume a memory win.
- Published requirements are minSdk 29, JDK 25, Gradle 9.7.1, AGP 9.4 and compileSdk 37. This repository currently targets minSdk 24, Java 17, Gradle 9.3.1, AGP 9.1.1 and compileSdk 36. Integrating it directly would require a toolchain upgrade and would exclude Android 7–9 unless a second renderer path were retained.
- The published SDK is pre-1.0/public beta and its API may change.
- The ARM64 native library is reported as about 203 MB uncompressed; the published download-size estimate is about 89 MB per architecture.
- No KaspaBrowser integration evidence exists for wallet signing, provider messaging, privacy interception, downloads, media, background playback, or tab/session restoration.

Conclusion: do not add the dependency or migrate the app. It currently conflicts with the memory-reduction objective and the app's supported OS/toolchain floor.

### 2. Mozilla GeckoView 159 — non-Chromium alternative

**Status: Not selected; possible research fallback only.**

Positive:
- Mozilla documents GeckoView as an embeddable engine intended for Android apps and browsers.
- It provides a browser-oriented embedding API and a self-contained engine.

Blocking issues:
- The same Cefrium-published comparison reports GeckoView PSS at 492/634 MB on the W90 and 528/562 MB on the Redmi Note 13 Pro, compared with WebView at 156/279 MB and 157/205 MB. This is not an independent benchmark, and a KaspaBrowser build would need its own controlled measurement.
- It is a different engine family, so Chromium-specific behavior and dApp compatibility cannot be presumed.
- No KaspaBrowser-specific wallet, privacy, media, download, or session integration has been implemented or tested.

Conclusion: it does not presently satisfy the memory objective. Only reconsider if a reproducible KaspaBrowser-specific benchmark contradicts the published directional result and the team accepts a non-Chromium engine.

### 3. Tauri on Android

**Status: Not a replacement.**

Tauri's Android implementation uses the system WebView. It may add a Rust application layer, but it does not provide an independently bundled Android rendering engine. It cannot meet the requirement to replace WebView.

### 4. Chrome Custom Tabs

**Status: Not suitable for the primary browser surface.**

It renders in an external Chrome-owned surface and is not an embeddable engine for the existing Compose browser UI, tab manager, or native wallet integration.

### 5. Ultralight and WPE Android

**Status: Not viable for production evaluation at this time.**

The reviewed material describes Ultralight as lacking an official Android embedding target. WPE Android is described as experimental. Neither currently provides a sufficiently established Android integration path for this browser's complete feature and security requirements.

## Recommendation

1. Keep Android System WebView as the production renderer.
2. Do not integrate Cefrium or GeckoView into the production app now.
3. Continue the existing physical-device WebView baseline. Measure total app + renderer PSS with the same release build, device, OS, page set, warm/cold state, and sampling procedure. Record median and peak PSS, startup time, crashes, and renderer deaths.
4. Use those measurements to make narrowly scoped WebView lifecycle/memory changes. Do not evict inactive tabs or pause all WebViews until page state, dApp sessions, audio/video playback, and wallet flows have regression coverage.
5. Re-open engine replacement only when a candidate demonstrates a real memory/stability advantage on the supported device matrix and can preserve minSdk/device coverage, security update cadence, licensing, and the required web APIs.

## Mandatory replacement acceptance matrix

A candidate remains **NOT APPROVED** until every row has reproducible evidence from a real Android device. A successful Gradle build or a single page-render smoke test is insufficient.

| Area | Required evidence |
|---|---|
| Build and distribution | Release build; supported ABIs; APK/AAB size; install/upgrade/rollback; supported Android versions |
| Memory and performance | App + every engine process PSS; cold/warm startup; single/multi-tab; long-session growth; close/reopen; low-memory pressure; no regression against WebView baseline |
| Stability | Repeated launch/navigation; renderer termination/recovery; background/foreground; process death; OEM battery/memory pressure |
| Browsing | HTTP/HTTPS navigation, redirects, history, reload, back/forward, popups policy, downloads, file chooser, certificates, errors |
| Privacy and security | Main-frame and subresource interception, DoH/proxy policy, DNS/IPv6 routing, mixed-content policy, permissions, storage isolation, no unexpected telemetry, no generic JS native bridge |
| dApp provider | Origin binding, main-frame-only provider access, account requests, chain/network handling, event lifecycle, malicious-origin and iframe tests |
| Native wallet | Real on-device Kaspa transaction review and signing; correct origin/approval boundary; no private-key exposure; cancellation and failure paths; broadcast behavior unchanged |
| Media | Audio/video playback, fullscreen, hardware decode, background playback controls, audio focus, Bluetooth/headset, pause/resume |
| State and data | Cookies, local storage, IndexedDB, service workers, downloads, tab restore, profile migration, clear-data behavior |
| Regression | Existing Android unit tests, instrumentation tests, wallet/dApp integration tests, and manual device sign-off all pass |

**Removal rule:** Android WebView may only be removed after the full matrix passes on the minimum supported Android version and representative current devices, with the results attached to the PR. Until then, WebView remains the production renderer and fallback.
