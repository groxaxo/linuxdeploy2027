# Validation — 2026-11-11

Source baseline: `2bcedf2` on `groxaxo/linuxdeploy2027`.
Build host: Linux x86_64, OpenJDK 21, Android SDK 35, NDK 28.2.13676358.

## Automated checks

- `./gradlew assembleDebug assembleDebugAndroidTest assembleRelease lint`: pass.
- `python3 -m unittest discover -s tests -v`: pass; component loading for four
  Ubuntu jammy/noble server/desktop profiles, and noble merged-/usr extraction.
- APK instrumentation: five tests pass on Android 15 / API 35 x86_64.
  Actual asset extraction/CLI help, nonzero CLI exit propagation, bundled zstd
  round trip without Termux, config values containing `=`, Turkish-locale keys.
- `apksigner verify --verbose --print-certs`: v1/v2/v3 signatures verify.

The CLI failure, profile inheritance, config round-trip, locale and merged-/usr
regressions were observed failing before their respective fixes.

## Runtime checks

Disposable Android emulator, Google APIs userdebug API 35, kernel 6.6.50,
x86_64. App-level root was supplied by a test-only root daemon in the emulator;
SELinux was permissive. No test root helper is included in the APK. The app still
requires a real root manager and compatible kernel/policy on a physical device.

- Deploy Ubuntu noble/amd64 from the official Ubuntu archive using the bundled
  debootstrap and native tools: pass (both bootstrap stages and core configuration).
- Install and start OpenSSH on port 2222: pass.
- Connect from the host over an ADB-forwarded SSH port as the deployed `android`
  account: pass; correct UID/groups, x86_64, Ubuntu 24.04 LTS, passwordless sudo.
- `dpkg --audit`: clean. `apt-get update`: pass for noble, security and updates.
- Stop SSH, VNC and desktop components, then unmount every container mount: pass.
- Bundled `dd`, `mke2fs`, kernel loop/ext4 mount and `e2fsck`: pass on a 32 MiB
  disposable image (create, format, mount, write, unmount, clean filesystem check).

- Install and start TightVNC + LXDE through the bundled graphics/desktop components:
  pass. Authenticated RFB 3.8 connection and 1024×768 framebuffer capture: pass.
  A live LXTerminal shows Ubuntu version and deployed-user identity.
- Signed, minified release APK fresh install on a second clean API 35 emulator:
  pass. Main screen, environment extraction, CLI help and Properties all work.
  Default profile is Ubuntu / noble / amd64 with the correct Ubuntu mirror.
- Updating that installation with the final APK signed by the same new key: pass.

Screenshots: [LXDE over VNC](evidence/lxde-vnc.png),
[release main screen](evidence/release-main.png).

## Signing and installation compatibility

The 2.6.1 release uses a **new** RSA-4096 private key, stored outside Git. Its
certificate SHA-256 is:

`8778b4756ce18677a2896092d98b0add81e16cf806df7a84c050a87c210c1494`

The prior `ubunapk` v1.0.2 APK uses a different certificate (`3eeabd11…`). Android
will reject an in-place update from that APK. Back up/export profiles before
uninstalling the older app. Container files outside app-private storage should be
preserved separately. If the original key is recovered, the same unsigned build
can be re-signed to support in-place upgrades.

## Limits

Runtime checks cover Ubuntu 24.04 amd64 on an emulator. ARM/ARM64/x86 zstd binaries
were cross-compiled and packaged; physical ARM devices and their root-manager /
SELinux policies were not tested. Jammy and optional custom meta-package flavors
have loader regression coverage, not complete deployment coverage. Other inherited
distributions, boot automation and every possible GUI are not certified by these
checks. The app's release notes must preserve these distinctions.

The target remains API 28 to permit the existing extracted-binary execution model;
this is a sideloaded utility. Lint suppresses the Google Play target-level rule for
that reason. Existing upstream deprecation/accessibility warnings remain visible.

Toolchain reference: [Android Gradle Plugin 8.13](https://developer.android.com/build/releases/agp-8-13-0-release-notes).

## Ubuntu 26.04 selector update — 2026-11-12

- Added `resolute` to the Ubuntu suite selector and CLI help, with a dedicated
  debootstrap script and security/updates APT sources. Noble remains the default.
- `./gradlew --no-daemon syncUbuntuBootstrap syncUbuntuDebootstrapScripts
  assembleDebug assembleRelease lint`: pass with SDK 35 and the existing toolchain.
- `python3 -m unittest discover -s tests -v`: four tests pass, covering existing
  jammy/noble component loading, noble/resolute merged-/usr extraction and
  resolute component loading with main/security/updates APT sources.
- Override files match their packaged asset copies. Resource/banner XML parses;
  README local file links resolve.

This adds a selectable recipe, not a certification of Ubuntu 26.04 on Android.
No full resolute deployment or new device instrumentation was run for this update.
The runtime evidence above remains specific to Ubuntu 24.04 on the emulator.
The newly built APKs have not been signed or uploaded as a new release.
