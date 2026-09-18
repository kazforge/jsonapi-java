# Release lifecycle

One coherent release lifecycle for the unified release train. Change and
version semantics are owned by
[ADR-021](adr/021-unified-release-train.md); this page owns the mechanics.

```
Conventional Commits
        ↓
release-please
        ↓
Release PR
- gradle.properties version
- CHANGELOG.md
        ↓ merge
Git tag v<version> + GitHub Release
        ↓
GitHub Actions
        ↓
Gradle build / sign / Maven Central publish
```

## Single root release

- release-please is the only version orchestrator: one package at the
  repository root, configured in `release-please-config.json` with version
  tracking in `.release-please-manifest.json`.
- The single version source is the root `gradle.properties` `version`
  property; all six `com.kazforge:jsonapi-java-*` artifacts ship that exact
  version.
- Tags are named `v<version>`; the changelog is the root `CHANGELOG.md`,
  both maintained by release-please.

## Maintainer runbook

1. Review the release PR (`gradle.properties`, `CHANGELOG.md`, manifest) and
   merge it. Merging is the release decision.
2. release-please creates the `v<version>` tag and GitHub Release.
3. The `Publish` workflow builds, signs, and uploads the bundle to the
   Central Portal with automatic publishing.
4. Verify the deployment under the `com.kazforge` namespace. Published
   releases are immutable; fixes ship as the next train version.

## Failure and retry

- Conflicting release PR: resolve in favor of the release-please proposal and
  re-review the version.
- Missing tag or Release after merging: re-run the `Release Please` workflow;
  never create the tag by hand.
- `Publish` failure: fix the cause and re-run the failed run; the tag already
  exists, so the same version republishes.
- Portal validation failure: fix the cause and ship it as the next train
  version. Published releases are never overwritten.
