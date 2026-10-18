# CTS Share Restore

[![Release](https://img.shields.io/github/v/release/ArduinoQuack/CTS-Share-Restore)](https://github.com/ArduinoQuack/CTS-Share-Restore/releases/latest)
[![License: MIT](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)
[![Android](https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white)](#requirements)

An Xposed-compatible module that restores **Share** for image and text selections in Google Circle to Search.

The control follows Google's current selection UI: it joins the native action row when one is visible, otherwise it appears as a standalone pill above the selected region. It stays hidden in Translate and Live Translate.

## Features

- Shares the selected image crop through Android's normal share sheet.
- Shares recognized text, or offers text/image choices when both are available.
- Uses Google's native action-row styling and selection geometry.
- Tracks animated, resized, zoomed, and edge-adjacent selections.
- Hides in Translate and Live Translate modes.
- Has no launcher icon and requests no network, storage, overlay, or accessibility permissions.
- Rediscovers obfuscated Google classes after app updates instead of pinning class names.

## Requirements

- Android 8.0 or newer.
- Root with an Xposed-compatible framework. The tested setup uses Vector.
- The Google app with Circle to Search support.

## Installation

1. Download `CTS-Share-Restore-0.7.apk` from the [latest release](https://github.com/ArduinoQuack/CTS-Share-Restore/releases/latest).
2. Install the APK normally.
3. In your module manager, enable **CTS Share Restore** and scope it to **Google** (`com.google.android.googlequicksearchbox`) only.
4. Force-stop the Google app, then reopen it. Follow any restart prompt from your framework.
5. Open Circle to Search, select text or an image region, and tap **Share**.

The module has no home-screen or app-drawer entry. Open its information screen through your module manager.

## Compatibility

Version 0.7.0 was verified on a Samsung Galaxy S24+ (`SM-S926U`) with Google `17.66.11.ve.arm64` and Vector.

Google's selection classes are located from semantic DEX strings and structurally validated. The mapping is cached per installed Google APK and refreshed after an update. This makes ordinary obfuscation changes survivable, but a major internal redesign can still require a module update.

When the native action row cannot be used, the module reads the exact rectangle drawn by Google's `RegionView` and transforms it into window coordinates. If neither compatible placement path exists, Share remains hidden rather than appearing in the wrong place.

## Privacy and safety

CTS Share Restore does not request internet or broad file access. Selected content is not logged.

- Images come from Google's already-captured screenshot and are cropped into Google's private cache.
- Text comes from Google's recognized selection.
- Temporary shared images older than 24 hours are removed on later shares.
- The Google APK, feature flags, and system partitions are not modified.

To roll back, disable the module and force-stop Google.

## Troubleshooting

- Confirm the module is enabled for **Google only**.
- Force-stop Google after enabling or updating the module.
- Reboot if your framework requests it.
- Look for entries beginning with `CTSShare:` in the framework log.
- When reporting a problem, include your device, Android version, Google app version, module framework/version, and the relevant log excerpt. Remove selected text or other personal content first.

## Building

The project uses a small Python build script instead of Gradle. It requires Python 3, Java 17 (including `keytool`), and internet access for the first build.

```bash
python3 build.py
```

The script downloads pinned tool versions into `tools/`, compiles the Java sources, builds and aligns the APK, generates a local development keystore when needed, signs the APK, and verifies the result. Local keystores and APK outputs are ignored by Git.

Official release APKs are signed separately. A locally built APK will not install over the official release unless it is signed with the same private key.

## Development notes

No Google code, APK, or decompiled source is included. Runtime discovery rejects ambiguous class shapes instead of guessing. Placement logic covers small expanded selections, display edges, landscape, and top-edge fallback.

See [CONTRIBUTING.md](CONTRIBUTING.md) for contribution guidance and [SECURITY.md](SECURITY.md) for private vulnerability reports.

## License

CTS Share Restore is released under the [MIT License](LICENSE). Google, Android, Circle to Search, Samsung, Vector, and Xposed are trademarks of their respective owners. This project is not affiliated with or endorsed by Google.
