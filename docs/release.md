# Release lifecycle

This page owns release mechanics. Version and change semantics are defined in
[ADR-014](adr/014-unified-release-train.md).

## Release flow

The [Release workflow](../.github/workflows/release.yml) runs on `main`:

1. release-please maintains a rolling release PR with the root version, changelog,
   manifest, and public guide dependency examples.
2. Merging that PR creates the `v<version>` tag and GitHub Release.
3. The publish job checks out the tag, builds and signs Maven publications, generates
   SBOMs and provenance attestations, then uploads the bundle to Central for automatic publishing.

`gradle.properties` is the version source. Keep the release-managed markers in
`docs/site/getting-started.md`; do not update those dependency versions independently.

## Release PR review

Release notes must reflect meaningful consumer-visible changes. Keep the PR open
as an accumulator; do not merge it for internal-only changes. Merging is the release decision.

Before merging:

- Check that the version, manifest, changelog, and guide dependency examples agree.
- Review [supported API and behavior changes](architecture.md#public-api-ownership),
  including dependency support changes, against ADR-014 and the release notes.
- For Jackson baseline changes, check the documented
  [Jackson 2](../jsonapi-java-jackson2/README.md#jackson-support) and
  [Jackson 3](../jsonapi-java-jackson3/README.md#jackson-support) support and run the checks below.
- Confirm required PR checks pass.

## Publication verification

Check that the publish job succeeded and all release-train artifacts are available
on Maven Central under `com.kazforge` before announcing availability. Verify signed
publications, per-module `cyclonedx` SBOMs, and GitHub provenance/SBOM attestations for
the primary JARs. Published artifacts are immutable; corrections require a new train version.

## Dependency-baseline acceptance

When changing a Jackson minimum or test-version selection, test both selections:

```bash
./gradlew clean build
./gradlew :jsonapi-java-jackson2:test :jsonapi-java-jackson3:test -PjacksonTestVersion=current
```

Generate consumer metadata for both adapters:

```bash
./gradlew :jsonapi-java-jackson2:generatePomFileForMavenJavaPublication \
  :jsonapi-java-jackson2:generateMetadataFileForMavenJavaPublication \
  :jsonapi-java-jackson3:generatePomFileForMavenJavaPublication \
  :jsonapi-java-jackson3:generateMetadataFileForMavenJavaPublication
```

Repeat with `-PjacksonTestVersion=current`. Compare each adapter's
`build/publications/mavenJava/pom-default.xml` and `module.json` using `xmllint` and `jq`.
Both selections must publish the documented Jackson minimums with ordinary version declarations:
no strict constraints, forced versions, platforms, or version ranges.

## Failure and retry

- Conflicting release PR: resolve in favor of the release-please proposal and re-review the version.
- Missing tag or GitHub Release after merge: re-run the `Release` workflow; do not create tags manually.
- Failed publish job: fix the cause and re-run only that job. Re-running the whole workflow finds
  the existing release and skips publication.
- Central validation failure: fix the cause and ship the next train version; never overwrite
  published artifacts.
