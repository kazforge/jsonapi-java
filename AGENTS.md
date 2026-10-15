# Repository guide

## Start with the affected area

The [module map](README.md#modules) links each module's README;
[`settings.gradle.kts`](settings.gradle.kts) owns actual build membership. Start in the narrowest
affected module or root subsystem. Expand only for relevant callers, dependencies, public API
effects, or repository-wide configuration; leave unrelated cleanup out.

Read the affected module README, package documentation (`package-info.java`), focused public
Javadoc, and corresponding tests before changing a contract. Sources are under `<module>/src/main/`;
Spock specs are under `src/test/groovy/`, with Java test code under `src/test/java/`.
Shared fixture and corpus maintenance starts with the
[API module's fixture documentation](jsonapi-java-api/README.md#shared-test-fixtures) and the
affected resource README.

[`build-logic/`](build-logic/) owns shared Java/test/nullness/coverage and formatting configuration;
dependency versions live in [`gradle/libs.versions.toml`](gradle/libs.versions.toml).

## Find the canonical contract

Module READMEs own current capabilities and entry points; package documentation owns package
responsibilities and invariants; Javadoc owns API semantics; tests provide behavioral proof.
Update the affected owner and necessary navigation rather than copying the same rule across docs.

Use [architecture](docs/architecture.md) for cross-module composition and documentation ownership,
[conformance](docs/conformance.md) for current JSON:API support, and
[ADRs](docs/adr/README.md) for consequential rationale. Read only the relevant sections.
[Vision](docs/vision.md) describes direction, not current implemented capability.
The [documentation index](docs/README.md) distinguishes the user guide from repository material.

## Important boundaries

- Persistence, endpoints, authorization, query execution, and applying updates remain application
  policy, not library defaults.
- Core has no functional third-party runtime dependencies; the neutral API has no Jackson-major
  imports. Adapters own their native Jackson mechanics. See the
  [module dependency direction](docs/architecture.md#modules-and-dependency-direction).
- Absent members, explicit JSON `null`, and present-empty values are distinct wire states. Sealed
  model variants represent explicit null data/linkage; Java null represents absence there. See
  [ADR-002](docs/adr/002-document-representation.md) and
  [JSpecify nullness](docs/adr/008-jspecify-nullness.md).
- `internal` packages are unsupported consumer API, even when Java
  visibility is public. See [public API ownership](docs/architecture.md#public-api-ownership).

## Verify a change

Install JDK 21 locally and use the committed Gradle wrapper; no toolchain download resolver is
configured. [CONTRIBUTING](CONTRIBUTING.md) owns contributor commands and dependency-verification
maintenance. For Java, Groovy, Kotlin, or Gradle Kotlin DSL changes, format before building:

```bash
./gradlew spotlessApply
./gradlew spotlessCheck
./gradlew clean build
```

Run one spec while iterating:

```bash
./gradlew :<module>:test --tests '<spec FQCN>'
```

The normal build is token-free and enforces the
[repository checks](docs/architecture.md#enforcement). Markdown-only changes need link,
consistency, and section-order review, not a Gradle build. Applicable CI checks must pass;
[Java CI](.github/workflows/build.yml) owns Sonar analysis.
