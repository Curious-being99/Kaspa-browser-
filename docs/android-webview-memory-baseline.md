# Android WebView memory baseline protocol

This procedure is required before changing WebView lifecycle behavior or comparing a replacement engine.

## Current implementation facts

- The browser manager holds one live WebView per open tab in a map.
- Switching tabs resumes the selected WebView; it does not currently destroy inactive tabs.
- Closing a tab removes it from the manager, detaches it, stops loading, clears history, and calls `destroy()`.
- WebView state bundles are captured for restoration.
- These behaviors are user-visible session semantics. Do not add tab eviction, global pausing, cache clearing, or renderer killing as a memory optimization without dedicated regression tests.

## Capture a baseline

Use a physical device and a release APK. Keep device, Android build, WebView provider/version, APK, network conditions, screen brightness, and page content constant. Close unrelated apps and reboot before the first baseline set where practical.

From a workstation with Android platform-tools:

```bash
chmod +x tools/android-browser-memory-baseline.sh
tools/android-browser-memory-baseline.sh -s DEVICE_SERIAL -l cold-start -n 6 -i 10
tools/android-browser-memory-baseline.sh -s DEVICE_SERIAL -l one-tab-static -n 6 -i 10
tools/android-browser-memory-baseline.sh -s DEVICE_SERIAL -l one-tab-modern -n 6 -i 10
tools/android-browser-memory-baseline.sh -s DEVICE_SERIAL -l five-tabs-mixed -n 6 -i 10
tools/android-browser-memory-baseline.sh -s DEVICE_SERIAL -l repeated-open-close -n 6 -i 10
```

The script records device/build/provider details, host-process `dumpsys meminfo`, process listings, ActivityManager's renderer connection information, and per-process meminfo for package-named processes. Some Android releases expose isolated renderer PIDs differently. Verify renderer PIDs against the ActivityManager connection output and include every app-owned renderer in the total; do not treat an empty renderer list as zero renderer memory.

## Required scenarios

1. App idle before opening a page.
2. Cold first page load, then settled page.
3. Static content with one image.
4. Modern content with JavaScript, DOM, and animation.
5. One media page playing foreground audio/video.
6. Background playback with controls active.
7. Five tabs with mixed page complexity; switch among them without closing.
8. Close four tabs, then sample after renderer cleanup settles.
9. Repeat open/navigate/close for at least 30 cycles.
10. Background/foreground transitions and low-memory pressure, recording process deaths and renderer recovery.

For each scenario, retain six or more samples after the page settles. Report median and peak host PSS, each renderer PSS, total app-owned PSS (host + all app-owned renderer/helper processes), live/attached WebView counts, startup/load time, renderer deaths, and crashes. Never add shared system WebView zygote PSS to the app total.

## Optimization gate

Only implement a behavior change when the measurements identify a specific avoidable cost. The initial source inspection already confirms explicit `destroy()` on closed tabs; this is a good lifecycle invariant and must be retained. Per-tab WebView preservation is intentional for session continuity. First investigate retained references, duplicate callbacks/listeners, view attachment, page/renderer growth, and destruction timing. Do not clear cookies, storage, history for open tabs, JavaScript sessions, or media state as an optimization.

After each change, repeat the exact same scenarios on the same device/build configuration and compare medians and peaks. Run browser, media, downloads, privacy, dApp, and wallet signing regression tests.

## Replacement comparison

A candidate must be tested with the same pages and scenarios, release optimization, device, Android version, and sampling method. Include every candidate-owned process in PSS totals. Also compare APK download size, cold start, crash/renderer-death rate, and feature coverage. Vendor benchmarks are screening evidence only; they do not substitute for KaspaBrowser measurements.

## Device evidence still required

No physical device is attached to this repository workflow. CI/emulator results cannot be reported as physical-device measurements. Attach the raw capture directory and a summary table to PR #121 before making a renderer-removal decision.
