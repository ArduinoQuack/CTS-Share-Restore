# Changelog

All notable changes to CTS Share Restore are documented here.


## [1.0.0] - 2026-10-21

### Fixed

- Fixed image sharing to Gemini.
- Improved image compatibility with standard JPEG attachments and URI grants.
- Added automatic cleanup for temporary shared images.

## [0.7.0] - 2026-10-14

### Added

- Native action-row integration when Google's selection actions are visible.
- Standalone Share pill anchored to the exact region drawn by Google.
- Runtime discovery and structural validation of obfuscated selection classes.
- Image sharing, text sharing, and a chooser when both are available.
- Automatic hiding in Translate and Live Translate.

### Fixed

- Share now follows moving, animated, zoomed, and minimum-size-expanded regions.
- Standalone Share no longer overlaps the selection handles.
- Correct edge clamping and below-selection fallback when there is no room above.
- Removed the launcher entry while retaining the module settings activity.


[1.0.0]: https://github.com/ArduinoQuack/CTS-Share-Restore/releases/tag/v1.0.0
[0.7.0]: https://github.com/ArduinoQuack/CTS-Share-Restore/releases/tag/v0.7.0
