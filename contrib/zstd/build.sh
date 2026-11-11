#!/usr/bin/env bash
# Rebuild the self-contained Android zstd CLI from upstream BSD-licensed source.
set -euo pipefail
: "${ANDROID_NDK_HOME:?Set ANDROID_NDK_HOME to an Android NDK (tested with 28.2.13676358)}"
repo_dir=$(cd "$(dirname "$0")/../.." && pwd)
work_dir=$(mktemp -d)
trap 'rm -rf "$work_dir"' EXIT
curl -fL --retry 3 https://github.com/facebook/zstd/releases/download/v1.5.7/zstd-1.5.7.tar.gz -o "$work_dir/source.tar.gz"
echo 'eb33e51f49a15e023950cd7825ca74a4a2b43db8354825ac24fc1b7ee09e6fa3  '"$work_dir/source.tar.gz" | sha256sum -c -
tar -xzf "$work_dir/source.tar.gz" -C "$work_dir"
toolchain="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin"
for target in arm:armv7a-linux-androideabi arm_64:aarch64-linux-android x86:i686-linux-android x86_64:x86_64-linux-android; do
    arch=${target%%:*}
    triple=${target#*:}
    make -C "$work_dir/zstd-1.5.7" clean >/dev/null
    make -C "$work_dir/zstd-1.5.7/programs" -j4 zstd \
        CC="$toolchain/${triple}21-clang" \
        CFLAGS='-Os -fPIE -DLIBC_NO_FSEEKO' LDFLAGS='-static -Wl,-z,max-page-size=16384' \
        ZSTD_MULTITHREAD=0 ZSTD_LEGACY_SUPPORT=0 HAVE_ZLIB=0 HAVE_LZMA=0 HAVE_LZ4=0
    install -m755 "$work_dir/zstd-1.5.7/programs/zstd" "$repo_dir/app/src/main/assets/bin/$arch/zstd"
    "$toolchain/llvm-strip" "$repo_dir/app/src/main/assets/bin/$arch/zstd"
done
cp "$work_dir/zstd-1.5.7/LICENSE" "$repo_dir/contrib/zstd/LICENSE"
