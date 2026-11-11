# Linuxoid 0.1.0 verification — 2026-11-12

Linuxoid is a new application and engine in `linuxoid/`. These results are
separate from [the legacy application's validation](VALIDATION.md).

## Build and artifact

- Host: local `dsanke`, Linux x86_64, JDK 21, SDK/platform 35, build tools 35.0.0,
  NDK 28.2.13676358 and CMake 3.22.1.
- Debug, release and test APK builds: pass. Eight JVM tests: pass. Android lint:
  pass, with nonblocking English-string/deprecation/tooling warnings.
- App: `io.groxaxo.linuxoid`, version 0.1.0 / code 1, min SDK 26, target SDK 35.
- PTY libraries build for ARM64, ARMv7, x86 and x86_64. GPL/MIT texts and
  third-party notices are included in the APK.
- Signed release APK installs fresh and updates with the same dedicated Linuxoid
  key. `apksigner verify`: pass. The key and its password are stored outside Git.
- Certificate SHA-256:
  `9021695cddd5a1c955e3c7a4e5c74b930eb59bb7a030f226f9ab69afaf44bbe2`.

The exact APK hash is recorded in [the verification transcript](evidence/linuxoid-verification.txt).
No new APK has been published as a GitHub release; previous releases are the
legacy app. CI artifacts include a development APK and an unsigned release APK.

## Local emulator

KVM-backed Google APIs Android 15 / API 35 x86_64 emulator, kernel
`6.6.50-android15-8-g8adecb593e9b-ab12525588`. SELinux was permissive and an
external test-only `su` daemon supplied app-level root. No root bypass or test
root helper is packaged in Linuxoid. A physical phone still needs its own root
manager and compatible kernel/policy.

## Runtime results

| Scenario | Observed result |
| --- | --- |
| Minimal Ubuntu 26.04 | Official latest-point Ubuntu Base image downloads and verifies, extracts and configures; regular user and sudo work; no SSH server installed |
| Terminal + SSH Ubuntu 22.04 | Fresh image and package install complete; OpenSSH starts; a real host password login reports Ubuntu 22.04.5, UID/GID 60000, internet group 3003, and working sudo |
| Minimal Ubuntu 24.04 | Fresh image and package install complete; package audit is empty; no SSH server installed |
| Desktop Ubuntu 26.04 | LXDE, LXTerminal and TigerVNC install and run; authenticated RFB 3.8 yields a 1280×800 framebuffer; VNC keyboard input runs Ubuntu version, identity and sudo commands in LXTerminal |
| Overwrite refusal | An install attempt targeting an already installed workspace is refused; a sentinel file survives |
| Mount lifecycle | Private rootfs bind mount permits sudo without changing Android `/data` flags; stopping removes every workspace mount; restart works |
| Repeated desktop start | Running Start twice succeeds without launching a conflicting X server; stop/restart restores an RFB endpoint on the workspace's own port |
| In-app PTY | Native PTY, login shell, terminal input and resize path work; signed-release instrumentation verifies the regular user, bash job control, overwrite refusal and stop/restart |
| Signed release UI | Creation dialog provisions Minimal Ubuntu 26.04.1 through the actual foreground service; workspace becomes Running; typed in-app terminal commands show Ubuntu, UID/GID, sudo and `job_control:on` |

Fresh-install cases were run in the development APK. Later corrections were
retested against retained, disposable workspaces rather than repeatedly erasing
them. The final signed release received its own UI and targeted PTY/lifecycle
checks. Tests that install images require explicit opt-in:
`-e linuxoid.disposable yes`. Normal live-test runs skip without that argument.

## Corrections verified during implementation

- A direct `/data` directory inherited Android's `nosuid` setting. A private,
  rootfs-only bind mount with the required flags fixes sudo.
- Shared mount propagation produced hidden duplicate mounts. A small original C
  helper sets the workspace tree private; stop checks for remaining mounts.
- Explicit umask and `/run/sshd` ownership/mode fix SSH startup on Ubuntu 22.04.
- Current TigerVNC uses `~/.config/tigervnc`; startup and password paths now use
  that location. Desktop output is redirected to a workspace log.
- TigerVNC list output may use either numeric or colon-prefixed display numbers;
  the repeated-start check accepts both.
- LXSession's default PolicyKit agent expects a logind session, which a chroot
  does not provide. Per-user LXSession/XDG configuration suppresses that agent,
  with sudo available for administration. The visible error dialog is gone.
- A user-switch command created a new session and lost terminal job control.
  `su --session-command` preserves it. Passing the trusted chroot command via
  Android `su -c` prevents startup plumbing from appearing as terminal input.
- The emulator fixture needed Android-compatible libc, descriptor cleanup and
  controlling-TTY handling. Those changes are outside the product.

## Inspectable evidence

- [Runtime and test transcript](evidence/linuxoid-verification.txt)
- [Original dashboard](evidence/linuxoid-home.png)
- [Creation dialog](evidence/linuxoid-create.png)
- [Signed release terminal](evidence/linuxoid-terminal.png)
- [Actual authenticated VNC desktop](evidence/linuxoid-desktop.png)

## Limits

Runtime validation is on amd64 in this emulator. ARM/ARM64/x86 native assets were
built or packaged, not tested on a physical phone. Enforcing SELinux, specific
root-manager policies and old Android kernels still need hardware validation.
Only the three recommended Ubuntu series were provisioned. Other catalogue
entries are not certified and older archives may need different mirrors.

Linuxoid uses Android's kernel and native directory storage; it does not boot a
separate kernel or systemd as PID 1. VNC requires a viewer, which is not bundled.
This is a 0.1 preview, not a claim that every desktop application or hardware
configuration has been tested. Existing Linux Deploy profiles were not migrated.
