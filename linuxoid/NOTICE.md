# Linuxoid source and third-party notices

Linuxoid's Android application, profile format, image downloader, PTY bridge and
chroot engine were written for this project in 2026. Copyright © 2026 groxaxo.
These application sources use the repository's [GPLv3 license](../LICENSE).
They are a new implementation, not renamed or paraphrased Linux Deploy classes
or CLI scripts. This is not a claim that Ubuntu or bundled dependencies were
created by this project.

The legacy `:app` module retains its existing upstream notices and history.
`:linuxoid` does not compile or execute that module's Java code, CLI or component
framework. Existing Linux Deploy containers and profiles are not imported or
modified. Linuxoid has its own application ID and storage tree.

## BusyBox

The unmodified `1.31.1-meefik` binaries in `vendor/busybox` are GPLv2 work by the
BusyBox contributors and Anton Skshidlevsky. The four files match the assets in
[meefik/busybox at d0f5067b2449ca55ccde426d3e606bd7877c155b](https://github.com/meefik/busybox/tree/d0f5067b2449ca55ccde426d3e606bd7877c155b)
byte for byte. Their hashes are in `vendor/busybox/SHA256SUMS`.

The matching BusyBox 1.31.1 upstream source archive, Android configuration,
patches and build scripts are included in `vendor/busybox-source`. The build
script retains its original copyright and GPLv3 notice; the BusyBox executable
it builds retains GPLv2. The GPLv2 text is in `licenses/BusyBox-GPL-2.txt` and is
packaged in the APK. Source archive origin:
https://busybox.net/downloads/busybox-1.31.1.tar.bz2

The tiny `rootfs-mount` helper is Linuxoid code. Its complete source and NDK
build script are in `contrib`; its per-architecture outputs are in
`vendor/rootfs-mount`. It does not contain Linux Deploy or BusyBox code.

## Terminal renderer

xterm.js 5.5.0 is MIT-licensed work by the xterm.js contributors. The packaged
JavaScript/CSS came from `@xterm/xterm@5.5.0` on npm. The complete MIT notice is in
`licenses/xterm-MIT.txt` and is packaged in the APK. Upstream source:
https://github.com/xtermjs/xterm.js/tree/5.5.0

## Ubuntu

Ubuntu Base is downloaded from Canonical's HTTPS image archive at install time,
not bundled into the APK. Each image is checked against SHA256SUMS obtained over
HTTPS from that archive. Linuxoid does not independently verify SHA256SUMS.gpg.
APT uses the Ubuntu image's archive keyring for repository signature checks.
Ubuntu packages retain their own licenses and `/usr/share/doc/*/copyright` files.
Ubuntu and Canonical are trademarks of Canonical Ltd.; this is an independent
project, not an official Canonical Android application.
