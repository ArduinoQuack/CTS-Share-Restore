# Contributing

Thanks for helping improve CTS Share Restore.

## Before opening an issue

1. Install the latest release.
2. Scope the module to Google only.
3. Force-stop Google and reproduce the problem.
4. Check the framework log for entries beginning with `CTSShare:`.

Include your device model, Android version, Google app version, Xposed-compatible framework/version, exact reproduction steps, and a redacted log excerpt. Never upload your Google APK, private signing keys, account data, or unredacted selected content.

## Pull requests

- Keep changes focused and explain why they are needed.
- Preserve runtime discovery; do not hard-code obfuscated Google class names without a validated fallback.
- Fail closed when a class or view shape is ambiguous.
- Add or update tests for behavior changes.
- Run `python3 build.py` and verify installation on a test device when possible.
- Do not commit APKs, downloaded tools, build output, keystores, or decompiled proprietary code.

By contributing, you agree that your contribution is licensed under the MIT License.
