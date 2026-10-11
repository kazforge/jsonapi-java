# Release lifecycle

One coherent release lifecycle for the unified release train. Change and
version semantics are owned by
[ADR-014](adr/014-unified-release-train.md); this page owns the mechanics.

```
Conventional Commits
        ↓
release-please
        ↓
Release PR
- gradle.properties version
- public guide dependency version
- CHANGELOG.md
        ↓ merge
Git tag v<version> + GitHub Release
        ↓
Publish job (same workflow run)
- checkout tag
- Gradle build, sign, and stage Maven publications
- generate aggregate SBOM and attest primary release JARs
- assemble and upload the Central bundle
```

## Single root release

- release-please is the only version orchestrator: one package at the
  repository root, configured in `release-please-config.json` with version
  tracking in `.release-please-manifest.json`.
- The single version source is the root `gradle.properties` `version`
  property; all seven `com.kazforge:jsonapi-java-*` artifacts ship that exact
  version.
- Tags are named `v<version>`; the changelog is the root `CHANGELOG.md`,
  both maintained by release-please.
- The dependency examples in `docs/site/getting-started.md` are a release-managed copy, not
  another version source. Generic `extra-files` markers update the Gradle and Maven values
  together with `gradle.properties`; do not maintain them manually. Keep the version markers
  around the dependency examples so release PRs continue to update both build-tool examples.
- One `Release` workflow runs both jobs. release-please uses a repository-scoped,
  short-lived GitHub App token so its release PRs receive the normal required
  checks; the publish job checks out the finalized tag and only receives the
  Central/signing secrets. The publish job also has only the GitHub OIDC and
  attestation permissions required for signed release evidence.

## Release evidence

- Each published module's `mavenJava` publication includes a Direct CycloneDX
  JSON SBOM as the `cyclonedx` classifier. It covers the compile and runtime
  classpaths (including compile-only inputs), is PGP-signed with the rest of the
  publication, and is staged with the module's other Maven artifacts.
- The publish job generates one Aggregate CycloneDX JSON SBOM for the release
  train after staging by combining the generated Direct SBOMs. It then creates
  GitHub Artifact Attestations for SLSA build provenance and the aggregate SBOM,
  both over only the primary binary release JARs directly in the staging tree
  (not sources, Javadoc, or test-fixtures JARs). Attestations use the pinned
  `actions/attest` action and GitHub's short-lived OIDC/Sigstore signing.
- The Central bundle is assembled and uploaded only after all evidence steps
  succeed. The aggregate SBOM is an attestation predicate, not an additional
  Maven artifact.

## Maintainer runbook

1. Keep the rolling release PR open as an accumulator. Merge it only when it contains meaningful
   consumer-visible changes, not merely tooling updates or routine documentation. Review
   `gradle.properties`, public guide dependency examples, `CHANGELOG.md`, and the manifest.
   Check [supported API and behavior changes](architecture.md#public-api-ownership),
   including support-floor or support-line changes, are classified in Conventional Commits and
   release notes according to ADR-014. Review the documented
   [Jackson 2](../jsonapi-java-jackson2/README.md#jackson-support) and
   [Jackson 3](../jsonapi-java-jackson3/README.md#jackson-support) support baselines and their
   [verification commands](#dependency-baseline-acceptance). Merging is the release decision.
2. release-please creates the `v<version>` tag and GitHub Release; the publish
   job checks out that tag, then builds, signs, and uploads the bundle to the
   Central Portal with automatic publishing.
3. Verify the deployment under the `com.kazforge` namespace. Published
   releases are immutable; fixes ship as the next train version.
    Verify Central availability before telling users the artifacts are public.

### Public notes hygiene

The first public release is 0.2.0. Its curated changelog and GitHub release text describe shipped
capabilities, not pre-public commit history or migrations from coordinates that were never released.
Correcting release text does not change its tag, version, or published artifacts.

Future notes show consumer features, fixes, performance improvements, dependency changes, and
reverts. Routine `docs` and internal-only commit types are hidden. Scope-specific exclusions precede
the general feature/fix sections; do not hide `deps` wholesale because runtime and security fixes
must remain visible. Genuine breaking-change notes remain visible even on hidden commit types.

Classify internal work as `chore`, `build`, `ci`, `test`, or another non-releasing type, not `feat`
or `fix`. Hiding a changelog entry does not remove its effect on version selection when combined
with releasable commits. Commits changing only files under `.agents/` are excluded from release
processing altogether; mixed consumer/internal commits remain eligible. The path exclusion matches
directories, not individual files: root `AGENTS.md` changes rely on internal commit classification.
Renovate classifies known tooling/test dependencies and the Jackson current-version test selectors
as `chore`; published Jackson minimums remain explicit maintainer decisions. Review dependency
updates by their consumer effect rather than by bot authorship.

For an already squash-merged PR with a misclassified commit, preserve its description and append a
`BEGIN_COMMIT_OVERRIDE` / `END_COMMIT_OVERRIDE` block containing the corrected Conventional Commit
message. Release-please uses that message on its next run; do not rewrite Git history. See
[release-please's override instructions](https://github.com/googleapis/release-please#how-can-i-fix-release-notes).

When no visible changes remain, release-please does not generate a new proposal or update the
existing accumulator. Leave it open and unmerged; stale generated text is not evidence that a
release is warranted. Once consumer-visible work arrives, review the regenerated notes before
merging. Curate noteworthy consumer documentation in that release's notes when appropriate.

## Dependency-baseline acceptance

When changing a Jackson minimum or test-version selection, verify both minimum and current versions:

```bash
./gradlew clean build
./gradlew :jsonapi-java-jackson2:test :jsonapi-java-jackson3:test -PjacksonTestVersion=current
```

The default build compiles and tests against each adapter's published minimum. The current selector
uses separate Renovate-maintained [version-catalog references](../gradle/libs.versions.toml) on test
classpaths only; production compilation and published dependencies retain the minimums.
Generate consumer metadata through the existing publication convention:

```bash
./gradlew :jsonapi-java-jackson2:generatePomFileForMavenJavaPublication \
  :jsonapi-java-jackson2:generateMetadataFileForMavenJavaPublication \
  :jsonapi-java-jackson3:generatePomFileForMavenJavaPublication \
  :jsonapi-java-jackson3:generateMetadataFileForMavenJavaPublication
```

Repeat with `-PjacksonTestVersion=current`. Compare each adapter's
`build/publications/mavenJava/pom-default.xml` and `module.json` from both selections using `xmllint`
and `jq`: published Jackson dependency versions must retain the documented minimums, with ordinary
version declarations and no strict constraints, forced versions, platforms, or version ranges. This
metadata inspection is an acceptance check for baseline changes; the existing publication convention
remains unchanged.

## Failure and retry

- Conflicting release PR: resolve in favor of the release-please proposal and
  re-review the version.
- Missing tag or Release after merging: re-run the `Release` workflow; never
  create the tag by hand.
- Publish job failure: fix the cause and re-run the failed publish job only.
  Re-running the whole workflow would find the release already created and
  skip publication.
- Portal validation failure: fix the cause and ship it as the next train
  version. Published releases are never overwritten.
