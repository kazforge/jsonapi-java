# ADR-021: Unified Release Train and Version Semantics

**Status:** Accepted
**Date:** 2026-09-18

## Context

The repository ships a completed, non-Spring artifact set from a single root version. Before the
first public 0.x release, the project needs one recorded policy for how artifact versions and
public change semantics work, so release automation can implement it mechanically instead of
re-deciding the model. Maven coordinates and the Java package base are already permanent
([ADR-020](020-kazforge-namespace.md)); this record consumes that decision and does not change any
module boundary, coordinate, or API.

## Decision

### One unified release train

All publishable artifacts in this repository share one project version and are released together,
starting with the first public 0.x release and continuing through stable 1.0 unless a later ADR
changes it.

| Artifact | Versioning path |
|----------|-----------------|
| `com.kazforge:jsonapi-java-core` | Release-train version |
| `com.kazforge:jsonapi-java-annotations` | Release-train version |
| `com.kazforge:jsonapi-java-jackson-api` | Release-train version |
| `com.kazforge:jsonapi-java-query` | Release-train version |
| `com.kazforge:jsonapi-java-jackson2` | Release-train version |
| `com.kazforge:jsonapi-java-jackson3` | Release-train version |

The single version source is the root `version` Gradle property (`gradle.properties`); every module
inherits it, and a release moves all six artifacts to the same version in one train. There are no
independent or hybrid artifact trains: no artifact gets its own version line, and no artifact skips
a train release because it did not change. Jackson 2 and Jackson 3 remain separate dependency
adapters, but participate in the same train; supported dependency lines are an API-compatibility
concern, not a versioning mechanism.

Rationale:

- The repository already has one root version and one build; independent trains would add
  coordination cost before there is a demonstrated need for them.
- The artifact set is small and cohesive, so per-artifact versions would mostly communicate noise,
  not signal.
- A single train gives consumers one number to reason about for the whole library.

Spring is **not** part of this repository's release train or artifact inventory. The separate
Spring integration project is outside this decision. If Spring artifacts later join a shared
train, that requires an explicit follow-up product decision.

### No BOM

No BOM artifact exists and none is created for the first public 0.x release or the planned 1.0
launch. With one version for all artifacts, a BOM adds publishing surface without adding
information. A BOM may be reconsidered later if the artifact set or release trains become complex
enough to justify it; if introduced under the unified model, it follows the same train version
unless a later ADR changes that rule.

### Conventional Commits → version bumps

Releases use [Conventional Commits](https://www.conventionalcommits.org/) with these project rules:

| Commit change | Release effect |
|---------------|----------------|
| `feat` | Minor release |
| `fix` or `perf` | Patch release |
| `refactor`, `docs`, `test`, `build`, `ci`, `chore`, and similar internal-only changes | No release by default |
| `!` in the commit header or a `BREAKING CHANGE:` footer | Breaking change (see below) |

Internal-only types do not trigger a release unless they carry an explicit breaking-change marker
or are intentionally reclassified as user-visible `fix`/`feat` work. A `revert` commit is
classified by the user-visible change it produces, not by the type of the commit it reverts: the
breaking-change rules below take precedence; a revert that is not breaking releases like the
equivalent `feat`/`fix`/`perf` change, or triggers no release when it is internal-only.

### Breaking-change semantics

- **Before 1.0:** a breaking change increments the **minor** version. Breaking changes must not be
  shipped in a patch release.
- **From 1.0 onward:** a breaking change increments the **major** version. Supported public API
  removals should normally be preceded by deprecation in at least one minor release before the next
  major, except where an urgent correctness or security issue makes that impractical; such an
  exception must be called out in the release notes.

### Release notes and changelog semantics

Release notes and the changelog must surface breaking changes explicitly and group user-visible
features and fixes. Internal-only commits may be omitted from user-facing notes.

### What this record does not own

Snapshot, release-candidate, tag, release-PR, signing, and publication mechanics; compatibility
tooling and supported JDK/Jackson dependency matrices; the full OSS contribution guide. Those are
separate concerns and remain open. Release automation must implement the model above rather than
reopen it.

## Consequences

- Consumers track one version for all `com.kazforge:jsonapi-java-*` artifacts and can upgrade the
  whole set together.
- Pre-1.0 minor releases may contain breaking changes; a 0.x patch release never does. This is the
  documented, deliberate pre-1.0 posture and is surfaced in release notes.
- Release automation derives versions mechanically from the bump and breaking-change rules above;
  per-artifact version variance is never introduced.
- Any move to independent trains, a BOM now, or Spring in this train requires a new ADR, not a
  silent drift in automation configuration.
