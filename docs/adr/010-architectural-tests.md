# ADR-010: Architectural Tests for Module Boundaries

**Status:** Accepted  
**Date:** 2026-07-29  
**Amended:** 2026-07-30 (jackson3 allowlist and `core.internal` ban); 2026-08-10 (jackson-common allowlist and the jackson3 common-contract dependency); 2026-08-11 (test-fixtures allowlist for the shared domain-write fixtures); 2026-08-12 (replaces Groovy codec fixtures with Java and JSON-P); 2026-08-31 (renames `jsonapi-java-jackson-common` to `jsonapi-java-jackson-api` and reorganizes API contracts into concept packages); 2026-09-02 (moves passive shared fixtures to the Jackson API test-fixtures source set and adds the neutral loader exception); 2026-09-10 (registers the neutral query-parser allowlist)

## Context

ADR-007 and the vision require dependency-free foundation modules and controlled coupling for optional integrations. Gradle dependency declarations guard the published classpath, but they do not stop production sources from referring to types that appear on the compile or test classpath by mistake, or from sibling modules reaching into non-public packages such as `core.internal`.

JSpecify (`org.jspecify.annotations`) is an intentional compile-only exception (ADR-009) and must remain allowed in production sources that use `@NullMarked` / `@Nullable`.

## Decision

- Enforce package and type dependency rules with [ArchUnit](https://www.archunit.org/) as a **`testImplementation`** dependency on library modules whose boundaries cannot be expressed by the compiler or Gradle. ArchUnit must never appear on the published runtime classpath.
- ArchUnit is the project-wide architectural test tool—not core-only. New modules add ArchUnit rules alongside their production packages only when they have a package or type boundary that cannot be enforced by the compiler or Gradle; dependency-free modules without such a boundary do not require ArchUnit. Do not reinvent coupling checks with classpath or source-import scanners.
- Current allowlists:
  - `com.kazforge.jsonapi.jackson..` (jackson-api) → `java..`, `org.jspecify.annotations..`,
    `com.kazforge.jsonapi.core.model..`, `com.kazforge.jsonapi.core.validation..`, and
    other `com.kazforge.jsonapi.jackson..` types. Production sources must not depend on
    `core.internal`, on either Jackson major (`tools.jackson..`, `com.fasterxml.jackson..`), or on
    a major-specific adapter package (`jackson2..`, `jackson3..`).
  - `com.kazforge.jsonapi.jackson3..` → `java..`, `org.jspecify.annotations..`,
    `com.kazforge.jsonapi.core.model..`, `com.kazforge.jsonapi.core.validation..`,
    `com.kazforge.jsonapi.annotation..`, `com.kazforge.jsonapi.jackson..`,
    `com.kazforge.jsonapi.jackson3..`, and
    `tools.jackson..`. Production sources must not depend on
    `com.kazforge.jsonapi.core.internal..` or `com.fasterxml.jackson..`.
  - `com.kazforge.jsonapi.fixtures..` (passive carriers and the one neutral resource loader in
    the Jackson API test-fixtures source set) → `java..`,
    `org.jspecify.annotations..`, `com.kazforge.jsonapi.annotation..`,
    `com.kazforge.jsonapi.core.model..`, `com.kazforge.jsonapi.jackson..`, other
    `com.kazforge.jsonapi.fixtures..` types, and `com.fasterxml.jackson.annotation..`. This
    stricter sub-allowlist is the structural boundary for shared fixtures. `TestFixtureResources`
    is the sole explicitly authorized executable exception: it may provide neutral classpath access
    to the corpus and schemas using only the JDK and JSpecify. Scenario catalogs, descriptors,
    invariant services, and other executable support remain outside this package. The Jackson 3
    architecture suite imports the test-fixtures variant and enforces this allowlist.
- Major-specific Jackson 2 allowlist (when registered):
  - `com.kazforge.jsonapi.jackson2..` → JDK, JSpecify, core public packages, annotations,
    module-owned types, and `com.fasterxml.jackson..`; never Jackson 3 or another module's
    internals. Must not depend on `core.internal`.
- Query allowlist:
  - `com.kazforge.jsonapi.query..` → JDK, JSpecify, other query types,
    `com.kazforge.jsonapi.core.validation.MemberNames`, and
    `com.kazforge.jsonapi.jackson.representation..`. It has no Jackson-major or framework
    dependency and does not depend on core internals.
- Spring modules record their exact framework package allowlists when those modules are registered.
  Spring may use public core, annotation, Jackson 3, and query contracts; no lower layer may
  acquire Spring types.
- Gradle continues to own artifact selection and publication; ArchUnit owns package/type coupling that Gradle cannot express.
- Changing an allowlist requires updating this ADR.
- Sibling modules must not depend on `com.kazforge.jsonapi.core.internal..`. That ban is
  enforced for `jsonapi-java-jackson3` and `jsonapi-java-jackson-api` and must be added when
  further sibling modules register.
- Major-specific adapters must not re-declare public top-level contracts that live in
  `jsonapi-java-jackson-api`; each adapter's architecture test derives the forbidden simple names
  from the compiled API package boundary rather than a hand-maintained moved-type list. This
  automatically protects later neutral contracts and is the model for Jackson 2 when registered.

## Consequences

- `./gradlew clean build` fails when a guarded module's production code gains an illegal type dependency.
- Agents and contributors treat ArchUnit failures as boundary violations, not tests to delete or weaken without an ADR change.
- Additional architectural rules (cross-module `internal` bans, layer DAGs) land alongside the
  modules they protect.
