# Ubuntu overrides

Gradle copies these files into the vendored `app/src/main/assets/env` environment
before building. No manual copy or submodule initialization is needed.

- `include/bootstrap/ubuntu/deploy.sh`: default noble release, architecture-aware
  mirror selection, security/updates APT sources, Android chroot APT settings.
- `include/bootstrap/debian/debootstrap/scripts`: suite scripts for the vendored
  debootstrap implementation, including Ubuntu 26.04 LTS (`resolute`).
  Resolute uses the merged-/usr extraction path and receives security/updates
  APT sources. Full Android deployment remains unvalidated; noble stays the default.

The full environment and the four optional custom server/desktop components are
tracked directly under `app/src/main/assets/env`. Their `EXTENDS` list explicitly
loads both Debian's installer functions and Ubuntu's repository configuration.
Component scripts are sourced, so they must not infer their location from `$0`.

When changing overrides, build once and commit the synchronized assets as well.
Run `python3 -m unittest discover -s tests -v` and the Android integration tests.
