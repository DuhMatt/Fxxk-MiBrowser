# HyperOS MiShare Browser Fix

An LSPosed module that prevents HyperOS Mi Share from forcing shared web links through Xiaomi Browser or Xiaomi Market.

When Xiaomi Browser is unavailable or not preferred, Mi Share may convert a received URL into a Xiaomi Market fallback such as:

```text
market://details?id=com.android.browser
```

On some devices, Xiaomi Market then launches the user's browser with only:

```text
https://
```

This module recovers the original shared URL from Mi Share's internal data and opens it in the user's default browser.

## What It Does

- Hooks Mi Share (`com.miui.mishare.connectivity`) link-opening flows.
- Detects Xiaomi Browser / Xiaomi Market redirection intents.
- Recovers the original shared URL from Mi Share's `TapRecvData` / `TapData` object fields.
- Opens the recovered URL with the user's default browser.
- Avoids changing non-web intents such as files, phone links, SMS, maps, and app-specific schemes.

## Tested Environment

- Device: Xiaomi / HyperOS device model `25128PNA1C`
- Android: 16
- Framework: LSPosed
- Browser tested: Via (`mark.via`)

Observed working recovery path:

```text
Mi Share notification click
-> tap_recv_data
-> com.miui.mishare.tap.TapData.h
-> original https URL
-> default browser
```

## Requirements

- Rooted Android device
- LSPosed
- HyperOS / MIUI device with Mi Share

Recommended LSPosed scope:

- System Framework (`android`)
- Mi Share (`com.miui.mishare.connectivity`)
- Xiaomi Market (`com.xiaomi.market`)
- Xiaomi Browser (`com.android.browser`), if installed

## Build

```bash
./gradlew assembleDebug
```

The debug APK will be generated at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Release build:

```bash
./gradlew assembleRelease
```

The default release APK is unsigned. Sign it before distributing a production release.

## Install

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Then enable the module in LSPosed, select the recommended scope, and reboot or restart the scoped apps.

## Debugging

Useful log tags:

```text
HyperOSBrowserFix_Main
HyperOSBrowserFix_Intent
HyperOSBrowserFix_Resolver
```

Helpful log patterns:

```text
Cached Mi Share URL
Recovered URL from object field
Recovered original URL from Mi Share cache
Redirecting to
```

## Notes

HyperOS and MIUI internals change often. This module uses defensive hooks and reflection, but some versions may require additional field or class handling.

## License

MIT
