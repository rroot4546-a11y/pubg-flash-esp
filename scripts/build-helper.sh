#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SRC="$ROOT/native/esphelper.c"
OUT="$ROOT/app/src/main/assets/esphelper64"

NDK="${ANDROID_NDK_HOME:-}"
if [[ -z "$NDK" ]]; then
  SDK="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-/opt/sdk}}"
  NDK="$(find "$SDK/ndk" -maxdepth 1 -mindepth 1 -type d 2>/dev/null | sort -V | tail -1 || true)"
fi
[[ -n "$NDK" && -d "$NDK" ]] || { echo "NDK not found" >&2; exit 1; }

TOOLCHAIN="$NDK/toolchains/llvm/prebuilt/linux-x86_64"
if [[ -x "$TOOLCHAIN/bin/aarch64-linux-android24-clang" ]]; then
  "$TOOLCHAIN/bin/aarch64-linux-android24-clang" \
    -O2 -Wall -Wextra -Werror -fPIE -pie -o "$OUT" "$SRC"
else
  # Host arch does not match the shipped NDK toolchain (e.g. aarch64 runner).
  RES="$TOOLCHAIN/lib64/clang/14.0.7"
  clang --target=aarch64-linux-android24 \
    --sysroot="$TOOLCHAIN/sysroot" -resource-dir="$RES" \
    -L"$RES/lib/linux/aarch64" \
    -O2 -Wall -Wextra -Werror -fPIE -pie -fno-lto -o "$OUT" "$SRC"
fi

file "$OUT"
