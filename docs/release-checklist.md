# Release checklist

Use this checklist before publishing a new APK.

## Scope

- [ ] Link the release to the fixed Issue(s) or describe the supported feature.
- [ ] State the affected HyperOS/MIUI and app versions.
- [ ] Keep unresolved compatibility limits separate from fixed behavior.

## Regression checks

- [ ] Settings search opens without a crash.
- [ ] Xiaomi Market internal navigation still works.
- [ ] Copy Direct keeps non-browser notification icons unchanged.
- [ ] Xiaomi Browser starts without a crash.
- [ ] The changed scenario opens the system default browser.

## Build and release

- [ ] Confirm `versionCode` and `versionName` in `app/build.gradle.kts`.
- [ ] Build the release APK and verify its signing certificate.
- [ ] Install over the prior release on a test device.
- [ ] Update the Chinese and English README when version, support, or known limitations change.
- [ ] Publish concise release notes with a validation environment and the release link.

## Issue follow-up

- [ ] Reply with the fixed version and release link.
- [ ] Keep an Issue open when the result is only a workaround or a known compatibility limitation.
