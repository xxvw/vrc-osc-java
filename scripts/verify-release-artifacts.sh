#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

java_version="${JAVA_VERSION:-17}"
version="$(sed -n 's/^version=//p' gradle.properties | head -n 1 | tr -d '[:space:]')"
artifact="build/libs/vrc-osc-java-${version}.jar"
sources="build/libs/vrc-osc-java-${version}-sources.jar"
javadoc="build/libs/vrc-osc-java-${version}-javadoc.jar"
pom="build/publications/mavenJava/pom-default.xml"

./gradlew jar sourcesJar javadocJar generatePomFileForMavenJavaPublication \
    -PjavaVersion="${java_version}" --no-configuration-cache

for file in "${artifact}" "${sources}" "${javadoc}" "${pom}"; do
    test -f "${file}" || { echo "Missing artifact: ${file}" >&2; exit 1; }
done

module_listing="$(jar --describe-module --file "${artifact}")"
jar_listing="$(jar tf "${artifact}")"
grep -q '^exports io.github.xxvw.vrcosc$' <<<"${module_listing}"
grep -q '^exports io.github.xxvw.vrcosc.osc$' <<<"${module_listing}"
grep -q '^exports io.github.xxvw.vrcosc.transport$' <<<"${module_listing}"
grep -q '^module-info.class$' <<<"${jar_listing}"
grep -q '^io/github/xxvw/vrcosc/VrcOscClient.class$' <<<"${jar_listing}"
if grep -q 'Test' <<<"${jar_listing}"; then
    echo "Test classes must not be packaged in the release JAR" >&2
    exit 1
fi

sources_listing="$(unzip -l "${sources}")"
javadoc_listing="$(unzip -l "${javadoc}")"
grep -q 'io/github/xxvw/vrcosc/VrcOscClient.java' <<<"${sources_listing}"
grep -q 'module-info.java' <<<"${sources_listing}"
grep -q 'index.html' <<<"${javadoc_listing}"
grep -q 'io/github/xxvw/vrcosc/VrcOscClient.html' <<<"${javadoc_listing}"

grep -q '<groupId>io.github.xxvw</groupId>' "${pom}"
grep -q '<artifactId>vrc-osc-java</artifactId>' "${pom}"
grep -q "<version>${version}</version>" "${pom}"
grep -q '<name>MIT License</name>' "${pom}"
grep -q 'https://github.com/xxvw/vrc-osc-java' "${pom}"
grep -q 'https://github.com/xxvw/vrc-osc-java/issues' "${pom}"
if grep -q '<dependencies>' "${pom}"; then
    echo "The published POM must not declare runtime dependencies" >&2
    exit 1
fi

echo "Verified release artifacts for vrc-osc-java ${version} on Java ${java_version}."
