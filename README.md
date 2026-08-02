# Fixed Android SDK for aarch64 Linux

Complete, working Android SDK for **aarch64 (ARM64) Linux**, with the
`dexdump`/`veridex` runtime crash fixed.

- **Build-tools 37.0.0** — native aarch64 binaries built from AOSP source
  (aapt, aapt2, aidl, zipalign, dexdump, split-select, veridex)
- **Platform-tools** — native aarch64 binaries (adb, fastboot, sqlite3,
  etc1tool, hprof-conv, mke2fs, e2fsdroid, make_f2fs, make_f2fs_casefold,
  sload_f2fs)
- **cmdline-tools** (Google official, Java-based) — sdkmanager, avdmanager, etc.
- `licenses/` already accepted

## Install

Download `android-sdk-linux-arm64-complete.tar.gz` from the
[Releases](https://github.com/sankarru/fixed/releases) page, then:

```sh
tar -xzf android-sdk-linux-arm64-complete.tar.gz -C ~
export ANDROID_SDK_ROOT=$HOME/android-sdk
export PATH="$ANDROID_SDK_ROOT/platform-tools:$ANDROID_SDK_ROOT/build-tools/37.0.0:$PATH"
```

Requires a 64-bit ARM Linux with glibc >= 2.39 (e.g. Ubuntu 24.04+ on ARM).

> The `dexdump`/`veridex` fix: AOSP's `apex/palette.cc` dlopens
> `libartpalette-system.so` at runtime and aborts when it's absent. The build
> now links the fake system palette directly. See
> [sankarru/android-sdk-arch64](https://github.com/sankarru/android-sdk-arch64).
