# Release process

[日本語版](RELEASE.ja.md)

This project publishes `io.github.xxvw:vrc-osc-java` to Maven Central through the Central Portal. The `0.1.0` release uses a GitHub Release event to upload a signed `USER_MANAGED` bundle. A maintainer then releases the bundle in the Central Portal.

## One-time repository setup

Create the `maven-central` GitHub Environment and add these environment secrets:

| Secret | Value |
| --- | --- |
| `MAVEN_CENTRAL_USERNAME` | Central Portal user token name |
| `MAVEN_CENTRAL_PASSWORD` | Central Portal user token password |
| `SIGNING_KEY` | ASCII-armored GPG private key |
| `SIGNING_PASSWORD` | Passphrase for the signing key |

Verify the `io.github.xxvw` namespace in the Central Portal. The public key for the signing key must be available from a public keyserver so Central can verify signatures.

Enable Private vulnerability reporting under **Settings > Code security and analysis**. Keep the `maven-central` environment restricted to release maintainers.

## Prepare a release

1. Update `version` in `gradle.properties`. The tag must be `v` followed by exactly that value.
2. Move the changelog entries from `Unreleased` to the new version in [CHANGELOG.md](CHANGELOG.md).
3. Run the full local verification.

```bash
./gradlew clean check javadoc
./scripts/verify-release-artifacts.sh
./gradlew publishToMavenLocal
```

4. Confirm that `build/libs/vrc-osc-java-<version>.jar` contains `module-info.class` and only exports the three public packages. The verification script checks this.
5. Commit the version and changelog changes with a message such as `chore(release): 0.1.0`.
6. Create and push the annotated tag.

```bash
git tag -a v0.1.0 -m "vrc-osc-java 0.1.0"
git push origin main v0.1.0
```

## Publish the bundle

1. Create a GitHub Release from the tag and publish it. A draft release does not trigger publication.
2. The release workflow checks that the tag matches `version`, then runs `publishAggregationToCentralPortal` in the `maven-central` environment.
3. The upload is `USER_MANAGED`. The workflow does not release the deployment automatically.
4. Open the Central Portal, inspect the deployment, and select Release when the bundle and signatures look correct.
5. Confirm that the POM, JAR, sources JAR, Javadoc JAR, and signatures are available under the expected coordinates.

If the workflow fails before upload, fix the issue and publish the GitHub Release again after correcting it. If an uploaded deployment is wrong before release, drop it in the Central Portal and upload a corrected deployment. Published Central artifacts cannot be replaced; publish a new patch version instead.

## Local bundle check

The local Maven repository receives a non-Central copy for integration checks:

```bash
./gradlew publishToMavenLocal
```

Confirm the installed path and POM under `~/.m2/repository/io/github/xxvw/vrc-osc-java/`.

## Credentials

Never commit signing keys, tokens, or `~/.gradle/gradle.properties`. Use the GitHub Environment secrets listed above. If a key or token is exposed, revoke it, rotate the secret, and publish a security note only if users are affected.
