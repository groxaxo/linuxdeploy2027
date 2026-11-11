#!/usr/bin/env bash
set -euo pipefail
: "${ANDROID_HOME:?Set ANDROID_HOME}"
: "${LINUXOID_KEYSTORE:?Point LINUXOID_KEYSTORE at your private PKCS12 keystore outside Git}"
: "${LINUXOID_PASSWORD_FILE:?Point LINUXOID_PASSWORD_FILE at a private password file outside Git}"
root=$(cd "$(dirname "$0")/../.." && pwd)
input="$root/linuxoid/build/outputs/apk/release/linuxoid-release-unsigned.apk"
output="$root/linuxoid/build/outputs/apk/release/Linuxoid-0.1.0.apk"
"$ANDROID_HOME/build-tools/35.0.0/apksigner" sign --ks "$LINUXOID_KEYSTORE" --ks-key-alias linuxoid --ks-type PKCS12 --ks-pass "file:$LINUXOID_PASSWORD_FILE" --out "$output" "$input"
"$ANDROID_HOME/build-tools/35.0.0/apksigner" verify "$output"
printf 'Signed APK: %s\n' "$output"
