# Linux Deploy — Ubuntu fork

Install and run GNU/Linux in a chroot on a **rooted Android device**. Based on
[Linux Deploy](https://github.com/meefik/linuxdeploy), copyright © 2012–2019
Anton Skshidlevsky, distributed under [GPLv3](LICENSE).

## Install

Download the signed APK from [Releases](https://github.com/groxaxo/linuxdeploy2025/releases).
Allow installation from that source, open Linux Deploy, and grant your root manager's
superuser request. Android 5.0 or later is required. Termux is **not required**.

Fresh profiles default to **Ubuntu 24.04 LTS (noble)** and choose the native
architecture and official Ubuntu mirror. Existing profiles keep their settings.
Ubuntu 22.04 LTS (jammy) is also available. Older distribution choices are inherited
from upstream; their presence does not mean their mirrors or releases are still supported.

1. Open Properties. Check the distribution, release, architecture and installation path.
2. Use a disk image for shared storage. A directory installation needs a Linux filesystem
   with Unix permissions (for example, a dedicated directory under `/data/local`).
3. Enable SSH or a graphical desktop if needed; choose the ports and credentials.
4. Choose **Menu → Install** and wait for installation to finish.
5. Tap **START**, then connect over SSH/VNC. Tap **STOP** before removing an image.

A chroot shares Android's kernel; it does not boot its own kernel or systemd as PID 1.
Root access, suitable kernel mount/chroot support and root-manager policy are required.
The emulator validation and hardware limits are recorded in [docs/VALIDATION.md](docs/VALIDATION.md).

## Build

Use JDK 17 or 21 and an Android SDK with platform 35 and build tools 35.0.0:

```sh
git clone https://github.com/groxaxo/linuxdeploy2025.git
cd linuxdeploy2025
export ANDROID_HOME=/path/to/android-sdk
./gradlew assembleDebug assembleRelease lint
python3 -m unittest discover -s tests -v
```

The pinned Gradle wrapper is 8.14.3 and Android Gradle Plugin is 8.13.2.
Debug output: `app/build/outputs/apk/debug/app-debug.apk`.
Release output: `app/build/outputs/apk/release/app-release-unsigned.apk`.
Sign release APKs with a private keystore using the SDK's `apksigner`; keys and
passwords must stay outside the repository. An installed APK can only be updated
with an APK signed by the same key.

The compile SDK is modern, while target SDK stays at **28** because the app executes
its extracted tools. Raising the target requires redesigning that execution model.
This is a sideloaded root utility, not a Google Play publication build.

The CLI environment is fully vendored under `app/src/main/assets/env`; there are no
required submodules. Gradle synchronizes the maintained Ubuntu/debootstrap overrides
from `custom_env` automatically before packaging.

## Native tools and tests

The APK bundles static zstd 1.5.7 executables for ARM, ARM64, x86 and x86_64 so modern
Ubuntu `.deb` files can be extracted without another Android app. Rebuild them with:

```sh
export ANDROID_NDK_HOME=/path/to/android-ndk
bash contrib/zstd/build.sh
```

That script verifies the source archive SHA-256 and retains the upstream BSD license.
Other native tools are inherited from Linux Deploy.

On a disposable rooted emulator with working app-level `su`, run:

```sh
ANDROID_SERIAL=emulator-5554 ./gradlew connectedDebugAndroidTest
```

`adb root` alone does not give applications access to `su`. The integration tests
exercise real APK asset extraction, the CLI success/failure path, bundled zstd, and
configuration round trips. Host tests exercise the actual component loader for all
four custom Ubuntu server/desktop profiles. CI builds debug/release APKs and runs
lint and the host tests; emulator results are recorded separately.
