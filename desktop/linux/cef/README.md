# KaspaBrowser Linux CEF host

Native Linux Chromium rendering via CEF. This does not load Android WebView.
The Rust `LinuxCefEngine` process adapter talks to this host through a private
newline-delimited stdin command pipe and stdout JSON event stream.

## Build prerequisites
- Linux x86_64 or arm64
- CMake 3.21+, C++20 compiler, make or Ninja
- Extracted CEF binary distribution for the target architecture
- System dependencies listed in the CEF distribution README

## Build
```bash
cmake -S desktop/linux/cef -B build/linux-cef -DCEF_ROOT=/absolute/path/to/cef_binary_distribution
cmake --build build/linux-cef --target kaspabrowser-cef
```
The output is `build/linux-cef/bin/kaspabrowser-cef`. CEF runtime libraries and
resource files must remain beside the executable.

## Run
```bash
./build/linux-cef/bin/kaspabrowser-cef --url=https://kaspabrowser.xyz
```

## Rust usage
```rust
use kaspasearch_core::linux_cef::LinuxCefEngine;
let engine = LinuxCefEngine::launch("/path/to/kaspabrowser-cef", "https://kaspabrowser.xyz")?;
```

The initial host supports a single CEF window, HTTP(S) navigation, back/forward,
reload/stop, main-frame navigation/load events, progress and title changes.
Downloads, tabs, profile/cookie policy, permission prompts, web-message wallet
provider, crash recovery and release packaging are not implemented. Web-message
capability is explicitly false; never expose signing keys or generic native
bridges to page JavaScript.
