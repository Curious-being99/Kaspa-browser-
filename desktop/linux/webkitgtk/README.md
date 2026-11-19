# Kazview WebKitGTK Native Linux Desktop Host

Native desktop Linux web browser using **real WebKitGTK** (`webkit2gtk-4.1` / GTK3).

## Features
- **Genuine WebKitGTK**: Uses the official Linux WebKitGTK rendering engine.
- **Hardware Acceleration**: WebGL and accelerated 2D canvas enabled.
- **Desktop Mode Toggle**: Toggles desktop mode and custom WebKit user agents on the fly.
- **Full Navigation**: Interactive URL navigation, Back, Forward, and Reload controls.

## Prerequisites (Ubuntu / Debian)
```bash
sudo apt update
sudo apt install -y cmake g++ pkg-config libgtk-3-dev libwebkit2gtk-4.1-dev
```

## Build
```bash
cd desktop/linux/webkitgtk
cmake -B build
cmake --build build
```

## Run
```bash
./build/kazview-webkitgtk https://kaspa.org
```
