#!/usr/bin/env bash
set -euo pipefail
: "${ANDROID_NDK_HOME:?Set ANDROID_NDK_HOME to NDK 28.2.13676358}"
root=$(cd "$(dirname "$0")/.." && pwd)
compiler="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin"
for item in arm64:aarch64-linux-android armhf:armv7a-linux-androideabi amd64:x86_64-linux-android i386:i686-linux-android; do
    arch=${item%%:*}; target=${item#*:}
    mkdir -p "$root/vendor/rootfs-mount/$arch"
    "$compiler/${target}26-clang" -Os -Wall -Wextra -Werror -fPIE -pie "$root/contrib/rootfs-mount.c" -o "$root/vendor/rootfs-mount/$arch/rootfs-mount"
    "$compiler/llvm-strip" "$root/vendor/rootfs-mount/$arch/rootfs-mount"
done
