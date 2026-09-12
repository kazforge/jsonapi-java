# ADR-009: JSpecify Nullness

**Status:** Accepted  
**Date:** 2026-07-29

## Context

The document model uses Java `null` for member absence while sealed types represent explicit JSON `null` (ADR-002). That contract lived mainly in Javadoc and runtime checks (`LocalValidation.requireNonNull`). Public APIs need a standard, tool-checkable way to declare which reference types may be null without adding a functional runtime dependency to `jsonapi-java-core`.

Competing annotation sets (JSR-305, JetBrains, Checker Framework) fragment tooling. JSpecify is the cross-tool nullness vocabulary.

## Decision

- Use only `org.jspecify.annotations` (`@NullMarked`, `@Nullable`). Do not add `@NonNull` under `@NullMarked` scopes.
- Mark every production package with `@NullMarked` on `package-info.java`.
- Annotate Java type usages that may be null with `@Nullable`. This covers member absence on containing objects and intentionally null map/list values.
- Keep sealed wire-null variants (`DocumentData.NullData`, relationship linkage null variants, and similar). Do not represent explicit JSON `null` as a bare `@Nullable` reference where a sealed type already exists.
- Keep runtime construction checks; annotations document contracts, they do not replace them.
- Depend on `org.jspecify:jspecify` as **`compileOnly`** in the shared library convention plugin so published core artifacts remain free of third-party runtime dependencies. CLASS-retention metadata stays in bytecode.
- Enforce nullness on Java `main` sources with Error Prone and NullAway (JSpecify mode) for packages under `com.kazforge.jsonapi`. Do not require Groovy/Spock test sources to be annotated.
- Teach agents via ADR, module README agent notes, `AGENTS.md`, and existing skill checklists. Do not add Cursor rules or a dedicated jspecify skill.

## Consequences

- Callers and IDEs can distinguish absence-nullable members from non-null payloads.
- NullAway fails `./gradlew clean build` when annotated contracts are violated in main sources.
- Vision “no third-party runtime dependencies” for core still holds: JSpecify is compile-only and not required on the consumer runtime classpath.
- New production packages must ship `@NullMarked` package-info and follow this policy.
- Future modules inherit the same convention plugin wiring when they apply `jsonapi-java-library`.
