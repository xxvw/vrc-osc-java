# Contributing to vrc-osc-java

[日本語版](CONTRIBUTING.ja.md)

Issues and pull requests are welcome in English or Japanese. Clear, focused contributions are easier to review than broad ones.

## Before opening a pull request

Open an issue before implementing a new feature or a public API change. Describe the use case, the proposed API, and the alternatives you considered. Small bug fixes and documentation corrections can go directly to a pull request.

Check existing issues first. If your change affects VRChat behavior, include the VRChat version and the exact OSC address and argument types you observed.

## Development

Use Java 17 or newer. The Gradle Wrapper pins the build version.

```bash
./gradlew clean check javadoc
./scripts/verify-release-artifacts.sh
./gradlew publishToMavenLocal
```

Run the full `check` and Javadoc tasks before opening a pull request. Add or update tests for behavior changes. Update the English README and the Japanese README when user-facing behavior changes. Public Javadoc is written in English.

## Pull requests

- Keep one pull request to one purpose. Split unrelated refactors, documentation changes, and behavior changes.
- Use a Conventional Commits style title, such as `feat: add avatar parameter helpers` or `fix: release the receiver port on close`.
- Reference the related issue in the description.
- Explain what changed, why it changed, how it was tested, and any remaining limitation.
- Add tests and documentation for user-visible behavior.
- Do not include credentials, tokens, private logs, personal data, or other non-public information.
- Maintainers squash merge pull requests. Keep the final commit message meaningful.

## Issues

- Write in English or Japanese.
- Use the bug report or feature request form.
- Include library version, Java version, operating system, and relevant VRChat version.
- Remove credentials, personal data, and private logs. Public issue trackers are not a place for secrets.
- Report security vulnerabilities through GitHub Private vulnerability reporting, not through a public issue. See [SECURITY.md](SECURITY.md).

## Commits and versioning

The project follows Conventional Commits and Semantic Versioning. A release uses a `v` prefix, for example `v0.1.0`. Breaking public API changes require a major version after `1.0.0`, or a minor version while the project remains in `0.x`.

## Code style

Match the surrounding Java style: four-space indentation, UTF-8, LF line endings, and no wildcard imports. Keep public types immutable where practical and document thread-safety constraints. Follow [.editorconfig](.editorconfig) for whitespace settings.

## License

By contributing, you agree that your contribution is licensed under the MIT License in [LICENSE](LICENSE).
