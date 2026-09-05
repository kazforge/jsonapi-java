# jsonapi-java

> Read and write JSON:API v1.1 documents in Java without surrendering control of persistence, endpoints, or application architecture.

A lightweight [JSON:API v1.1](https://jsonapi.org/) document model and validation library for
**Java 21+**. Opt-in bidirectional flat DTO mapping, typed envelopes, and Jackson 3 presence-aware
PATCH binding (low-level commands and direct typed PATCH DTOs) are available; query parsing and
Spring adapters are planned. Compliance is tracked by feature and layer; the library does not claim
that an application's endpoint behavior is automatically JSON:API compliant.

## Status

**Pre-alpha.** The Gradle build, CI pipeline, architecture decisions, `jsonapi-java-core` document model and validation, `jsonapi-java-annotations`, Jackson 3 document codec and domain mapping (compound inclusion, sparse fieldsets, flat DTO binding, typed envelopes, presence-aware PATCH binding and direct typed PATCH DTO binding), the Jackson 2 validated document writer, and Jackson-major-neutral contracts in `jsonapi-java-jackson-api` are in place. Remaining Jackson 2 parity capabilities, query parsing, and Spring adapters are not started.

Maven group: `io.github.kazemek`. Java packages: `io.github.kazemek.jsonapi.*`.

## Requirements

- JDK 21 (enforced via Gradle toolchain)

## Build

```bash
./gradlew clean build
```

`check` (and therefore `build`) enforces a fixed 80% JaCoCo line and branch coverage floor for
library modules. `jsonapi-java-annotations` is exempt because it is annotation-only and has no
executable coverage.

## Project structure

| Path                           | Purpose                                                                                          |
|--------------------------------|--------------------------------------------------------------------------------------------------|
| `jsonapi-java-core/`           | Zero-dependency JSON:API document model and validation                                           |
| `jsonapi-java-annotations/`    | Dependency-free domain-mapping annotations                                                       |
| `jsonapi-java-jackson3/`       | Jackson 3 document codec, domain-to-resource mapping, flat DTO reads, typed domain envelopes, and presence-aware PATCH |
| `jsonapi-java-jackson2/`       | Jackson 2 validated document writer (parity artifact; further capabilities follow) |
| `jsonapi-java-jackson-api/`    | Public Jackson-major-neutral API surface: document, mapping, PATCH, representation, and diagnostic contracts; shared passive carriers and JSON/schema test fixtures via `testFixtures` |
| `build-logic/`                 | Shared Gradle convention plugins                                                                 |
| `docs/`                        | Vision, architecture overview, conformance, and architecture decision records |

## Module registry

| Module                                                           | Status                                                                                                                                                                                  | Purpose                                                                    |
|------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------|
| [`jsonapi-java-core`](jsonapi-java-core/README.md)               | Available | Dependency-free document model and validation                              |
| [`jsonapi-java-annotations`](jsonapi-java-annotations/README.md) | Available | Dependency-free domain-mapping role annotations                            |
| [`jsonapi-java-jackson3`](jsonapi-java-jackson3/README.md)       | Available | Jackson 3 document codec, annotated domain mapping, and presence-aware PATCH binding (commands and direct typed PATCH DTOs) |
| [`jsonapi-java-jackson-api`](jsonapi-java-jackson-api/README.md)       | Available | Public Jackson-major-neutral API surface shared by Jackson 2, Jackson 3, and future framework integrations |
| [`jsonapi-java-jackson2`](jsonapi-java-jackson2/README.md)       | Available | Jackson 2 validated document writer (first Jackson 2 parity artifact; further capabilities follow) |
| `jsonapi-java-query`                                             | Planned   | Optional query-parameter parsing                                           |
| `jsonapi-java-spring-webmvc`                                     | Planned   | Jackson 3-based Spring WebMVC transport and DTO binding                    |
| `jsonapi-java-spring-webflux`                                    | Future evaluation | Separately scoped reactive adapter candidate                               |

Planned and future-evaluation modules have no usable entry point yet. Use each available module
README for its package map, minimal usage, non-goals, and contributor/agent notes; the registry does
not duplicate those module-specific contracts.

## Documentation

- [Core module](jsonapi-java-core/README.md)
- [Annotations module](jsonapi-java-annotations/README.md)
- [Jackson 3 module](jsonapi-java-jackson3/README.md)
- [Jackson 2 module](jsonapi-java-jackson2/README.md)
- [Jackson API module](jsonapi-java-jackson-api/README.md)
- [Vision](docs/vision.md) — stable product direction and principles
- [Architecture](docs/architecture.md) — current cross-module mental model and flows
- [Conformance checklist](docs/conformance.md) — current JSON:API 1.1 feature status
- [Architecture decision records](docs/adr/README.md)
- [Agent workflow](AGENTS.md) — knowledge ownership, routing, and completion gates

## License

Apache License 2.0 — see [LICENSE](LICENSE).
