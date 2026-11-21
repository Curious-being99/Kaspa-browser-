#!/usr/bin/env bash
set -euo pipefail

if ! command -v cargo >/dev/null 2>&1; then
  echo "cargo is not installed in this container. Run this script on a host with Rust (rustup/cargo) installed." >&2
  exit 1
fi

cargo generate-lockfile --manifest-path Cargo.toml
cargo test --workspace --locked
echo "Generated Cargo.lock and verified all Rust workspace unit tests."
