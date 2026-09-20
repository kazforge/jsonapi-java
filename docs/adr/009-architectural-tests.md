# ADR-009: Architectural Tests for Module Boundaries

**Status:** Accepted
**Date:** 2026-07-29

## Context

Gradle controls artifact dependencies, but it cannot prevent production code from referring to an
unintended type already present on a compile or test classpath. Compilation also cannot express the
package responsibility DAGs inside core and the Jackson adapters.

## Decision

Use ArchUnit as a `testImplementation`-only dependency where package or type boundaries need
executable enforcement. It is the repository-wide tool for those checks; do not replace it with
source-import or classpath scanners, and never publish it as a runtime dependency.

Architecture specifications enforce allowlists for the neutral Jackson API, the mapping
implementation namespace, each Jackson adapter,
shared fixtures, and query parsing. In particular:

- neutral production code stays free of either Jackson major and major-specific adapter packages;
- neutral production code does not depend on the mapping implementation namespace;
- mapping implementation production code depends only on neutral contracts, core, and platform
  types, never on a backend or backend-native library;
- each adapter uses only its own Jackson major and supported lower-layer contracts;
- sibling modules do not depend on `core.internal`;
- supported neutral contracts are not redeclared by adapters, and shared internal helpers do not
  leak through supported public signatures; the mapping implementation namespace is treated as
  shared internal implementation for that signature check;
- shared fixtures remain passive application-shaped data and resources, apart from the neutral
  resource loader; behavioral orchestration and assertions remain adapter-local;
- shared characterization contract specs under `com.kazforge.jsonapi.fixtures.contract` are the
  sanctioned shared-assertion exception: abstract Spock specs asserting neutral Level-1 observable
  semantics, executed through adapter-supplied concrete subclasses, depending only on Groovy, Spock,
  and the neutral packages the passive fixtures may use.
Core preserves its downward responsibility DAG: aggregate validation may depend on model, internal,
and validation responsibilities; model may depend on internal and validation; internal may depend on
validation. The reverse edges are forbidden. The declared Gradle project edges themselves are the
source of truth for physical module dependencies; ArchUnit enforces production-code boundaries,
not build declarations.

Each Jackson adapter independently preserves its local DAG: the public composition root may depend
on mapping and internal implementation packages, and the ordinary internal package may depend on
mapping; reverse and sibling-internal edges are forbidden. Same-package recursive models are allowed,
so there is no blanket cycle ban.

Every responsibility selector must match production classes so rules cannot pass vacuously. A
legitimate allowlist or protected-DAG change requires this ADR to change with the enforcing
specification.

## Consequences

- `./gradlew clean build` fails near the module that violates a protected boundary.
- Contributors treat failures as architecture violations, not tests to weaken without a decision
  change.
- Exact package inventories stay in the module-owned architecture specifications rather than this
  rationale record.
