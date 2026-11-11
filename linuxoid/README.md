# Linuxoid application

A new native Android application for Ubuntu Base chroots on rooted phones.
Android 8.0+ is required. Compile/target SDK 35; application ID
`io.groxaxo.linuxoid`. It installs alongside the legacy Linux Deploy app.

| Starting point | Contents |
| --- | --- |
| Minimal | Official Ubuntu Base, CA certificates, regular `linuxoid` user, sudo and the in-app terminal; no SSH or desktop server |
| Terminal + SSH | Minimal plus OpenSSH; optional access from the phone's network |
| Desktop | SSH plus LXDE, LXTerminal, TigerVNC and a D-Bus session |

Ubuntu 26.04, 24.04 and 22.04 are the recommended series. The release picker also
reads Canonical's Ubuntu Base catalogue and selects the newest point image
available for the phone's native architecture. A release can only install if
Canonical supplies that architecture and the Android kernel can execute its
userspace. Older entries may be unsupported upstream or require archived mirrors;
listing an entry does not certify it on every phone.

Desktop mode provides a VNC server. Connect a VNC viewer on the phone to the
address shown in the app. A viewer is not bundled. From a computer, use an SSH
tunnel to the desktop port. SSH listens only on localhost unless network access
was explicitly enabled at creation. VNC always stays on localhost. VNC uses the
first eight characters of the workspace password, as required by VNC auth.

## Implementation

- Native Java activities and a foreground operation service; independent JSON
  profiles. Passwords are used during provisioning and are not saved in profiles.
- Canonical HTTPS manifests and SHA-256 checking, with architecture filtering and
  automatic point-release selection. Downloads have timeouts and a size limit.
- Fresh, UUID-named directories at `/data/local/linuxoid/<id>/rootfs`. There is no
  user-supplied installation path or recursive deletion feature. An existing
  destination is never reinstalled over; incomplete files are retained.
- An original shell engine and small C mount helper: private bind mounts with
  setuid support inside Ubuntu, private proc/dev/devpts/run mounts, scoped process
  shutdown and checked unmounting. Android's `/data` flags stay unchanged.
- UID/GID 60000 for the Linux user avoids assigning it Android's system UID 1000.
- A native PTY with xterm.js rendering gives the built-in terminal a real TTY,
  resizing, ANSI output, arrow keys, Tab, Escape and Control input.
- Third-party tools remain identified and licensed. See [NOTICE.md](NOTICE.md).

This is a chroot using Android's kernel, not a VM or a separately booted systemd
system. Physical phones require a working root manager and compatible mount,
execution and SELinux policy. Existing Linux Deploy installations are untouched.

## Build

JDK 21, Android SDK 35/build-tools 35.0.0, NDK 28.2.13676358, CMake 3.22.1:

```sh
export ANDROID_HOME=/path/to/android-sdk
./gradlew :linuxoid:assembleDebug :linuxoid:assembleRelease \
  :linuxoid:testDebugUnitTest :linuxoid:lintDebug
```

Installable development APK:
`linuxoid/build/outputs/apk/debug/linuxoid-debug.apk`.
Unsigned release APK:
`linuxoid/build/outputs/apk/release/linuxoid-release-unsigned.apk`.

The build includes only Linuxoid's app sources and explicitly vendored tools; it
does not depend on the legacy app's files. Native PTY libraries build from source
for ARM64, ARMv7, x86_64 and x86. To rebuild the small root mount executables:

```sh
export ANDROID_NDK_HOME=/path/to/android-sdk/ndk/28.2.13676358
bash linuxoid/contrib/build-mount-helper.sh
```

Use the SDK's `apksigner` with a private key kept outside Git for distribution.
The debug key is for development. Keep the same signing key for future updates.

## Live tests

Run only on a disposable rooted emulator; these tests download Ubuntu images,
install packages and create isolated workspaces. App-level `su` is required;
`adb root` by itself does not supply that to the app.

```sh
./gradlew :linuxoid:assembleDebug :linuxoid:assembleDebugAndroidTest
adb -s emulator-5580 install -r linuxoid/build/outputs/apk/debug/linuxoid-debug.apk
adb -s emulator-5580 install -r linuxoid/build/outputs/apk/androidTest/debug/linuxoid-debug-androidTest.apk
adb -s emulator-5580 shell am instrument -w -e linuxoid.disposable yes \
  io.groxaxo.linuxoid.test/androidx.test.runner.AndroidJUnitRunner
```

Without the explicit disposable-device argument, live installation tests skip.
See [validation results](../docs/LINUXOID_VALIDATION.md) for the measured scope.
