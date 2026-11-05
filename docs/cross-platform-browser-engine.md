# Cross-platform browser engine boundary

## Goal

Keep one KaspaBrowser product while allowing each operating system to host its native Chromium-based rendering engine. The platform-neutral Rust crate defines the contract; platform adapters own rendering, lifecycle, storage integration, and native UI.

## Current implementation

- Android remains on Android System WebView. No Android source or Gradle configuration is changed by this foundation.
- `kaspasearch-core` exposes typed platform/engine identifiers, capability reporting, navigation commands, browser events, and a `BrowserEngine` adapter trait.
- Navigation URL validation accepts only HTTP and HTTPS. App-specific schemes such as `kaspa:` must be handled by the native intent router, not loaded as web navigation.
- URL sanitization delegates to the existing shared privacy policy.

## Linux adapter target

The planned Linux implementation will use Chromium Embedded Framework (CEF). A Linux adapter will implement `BrowserEngine` and translate commands/events to/from CEF's browser host and client callbacks. It will not attempt to load Android WebView or reuse Android framework classes.

## Security and compatibility requirements

- Keep wallet keys and signing inside the native wallet boundary. The rendering engine receives only explicitly approved requests.
- Provider messaging must be origin-bound and main-frame-only, with explicit user approval for sensitive wallet actions.
- Do not expose a generic native JavaScript object bridge to web content.
- Engine capability flags must reflect actual runtime support; never advertise a feature just because the shared API defines it.
- Downloads, cookies, permissions, certificates, media, and profile storage remain platform adapter responsibilities until shared policy interfaces are specified.
- Android WebView behavior and its existing security controls must remain unchanged while the Linux adapter is developed.

## Verification

Run `cargo test --manifest-path kaspasearch-core/Cargo.toml` on Linux CI. A future CEF adapter must add Linux build/runtime tests and browser integration tests before being considered implemented.
