# jsonapi-java

> Read and write JSON:API v1.1 documents in Java without surrendering control of persistence, endpoints, or application architecture.

A lightweight, pre-alpha [JSON:API v1.1](https://jsonapi.org/) document, mapping, and validation
library for **Java 21+**. It provides neutral application contracts with Jackson 2 and Jackson 3
implementations while leaving transport, persistence, authorization, and query execution to the
application.

Maven group: `com.kazforge`. Java packages: `com.kazforge.jsonapi.*`.

## Requirements

- JDK 21 (enforced via Gradle toolchain)

## Build

```bash
./gradlew clean build
```

`check` (and therefore `build`) enforces a fixed 80% JaCoCo line and branch coverage floor for
library modules. `jsonapi-java-annotations` is exempt because it is annotation-only and has no
executable coverage.

## Modules

| Module | Status | Purpose |
|--------|--------|---------|
| [`jsonapi-java-core`](jsonapi-java-core/README.md) | Available | Dependency-free document model and validation |
| [`jsonapi-java-annotations`](jsonapi-java-annotations/README.md) | Available | Dependency-free domain-mapping role annotations |
| [`jsonapi-java-jackson-api`](jsonapi-java-jackson-api/README.md) | Available | Jackson-major-neutral application and capability contracts |
| [`jsonapi-java-query`](jsonapi-java-query/README.md) | Available | Neutral query-parameter parsing |
| [`jsonapi-java-jackson3`](jsonapi-java-jackson3/README.md) | Available | Jackson 3 runtime, codec, mapping, and PATCH binding |
| [`jsonapi-java-jackson2`](jsonapi-java-jackson2/README.md) | Available | Jackson 2 runtime, codec, mapping, and PATCH binding |
| `jsonapi-java-spring-webmvc` | Planned | Spring WebMVC transport and DTO binding |
| `jsonapi-java-spring-webflux` | Future evaluation | Separately scoped reactive adapter candidate |

`settings.gradle.kts` is the build-membership authority. Planned and future modules have no usable
entry points. Shared Gradle conventions live in `build-logic/`; maintainer documentation lives in
`docs/`.

## Documentation

- [Vision](docs/vision.md) — stable product direction and principles
- [Architecture](docs/architecture.md) — current cross-module mental model and flows
- [Conformance checklist](docs/conformance.md) — current JSON:API 1.1 feature status
- [Architecture decision records](docs/adr/README.md)
- [Agent workflow](AGENTS.md) — knowledge ownership, routing, and completion gates

The module registry links directly to each module's capability, entry-point, and local-maintenance
documentation.

## License

Apache License 2.0 — see [LICENSE](LICENSE).
