<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# cursing_less Changelog

## [Unreleased] 


## [0.2.0]

### Added
- Command to copy a mark to the clipboard.

### Changed
- Redesigned to use a CustomHighlighter in place of inlays.
- Use flows to debounce and decrease the time by half (250 ms -> 125 ms)
- Don't ignore "." in default regex or PSI.

### Fixed
- Fixes around inlays at a location with a token.
- Fixes to marks window display initial size.


## [0.1.0]

### Added
- Many new commands added.
- More options in preferences
- New shapes (disabled by default).
- A new marks tool window (multi copy/paste)

### Fixed
- Bug fixes around small editor windows, preferences, and hanging tokens.
- Fixes for saving preferences.


## [0.0.7]

### Added
- Settings configuration for colors, shapes, regex, and size of tokens
- new commands (mark commands are experimental)

### Fixed
- Fixes for inlay navigation and drawing


## [0.0.6]

### Added
- New commands selecting, Copying, Cutting, Clearing, and Cursors

### Fixed
- Improvements to the way that tokens are prioritized. 