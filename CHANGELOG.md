# Changelog

All notable changes to this project are documented here. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the project uses [Semantic Versioning](https://semver.org/).

[日本語版](CHANGELOG.ja.md)

## [Unreleased]

## [0.1.0] - 2026-11-16

### Added

- OSC 1.0 message and bundle codec with UTF-8, four-byte padding, type tags, arrays, and bundle timetags.
- Typed arguments for int32, float32, string, blob, int64, double, timetag, char, symbol, MIDI, RGBA, bool, nil, impulse, and arrays.
- UDP sender and callback receiver with a dedicated daemon receive thread.
- VRChat helpers for avatar parameters, Chatbox, avatar changes, and typing.
- Listener registration, error handling, idempotent shutdown, and receive-port reporting.
- Java 17 build with JPMS, Gradle Wrapper, sources JAR, Javadoc JAR, and Maven Central publishing metadata.
- English and Japanese documentation, issue forms, pull request template, security policy, and release procedure.

### Known limitations

- Bundle timetags are preserved, but packets are delivered immediately in this release.
- OSCQuery and OSC address-pattern matching are not implemented.
- Chatbox automatic wrapping can exceed nine rendered lines even when the explicit line count is valid.

[Unreleased]: https://github.com/xxvw/vrc-osc-java/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/xxvw/vrc-osc-java/releases/tag/v0.1.0
