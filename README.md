# jsonapi-java

Read and write [JSON:API 1.1](https://jsonapi.org/) documents in **Java 21+**. Map application DTOs,
validate documents, and preserve PATCH presence with a native Jackson 3 runtime.
Endpoints, persistence, authorization, query execution, and applying updates stay with your application.

Available from Maven Central. Public API refinement is possible before 1.0: minor releases may
break compatibility; patch releases do not.

## Get started

Use `com.kazforge:jsonapi-java-jackson3`. Packages use `com.kazforge.jsonapi.*`.

<!-- x-release-please-start-version -->
**[Install and run the first example](https://jsonapi.kazforge.com/core/0.4.0/getting-started/)**. The
[user guide](https://jsonapi.kazforge.com/) owns user workflows; its
[API-reference section](https://jsonapi.kazforge.com/core/0.4.0/getting-started/#api-reference-and-next-steps)
links Javadoc.
<!-- x-release-please-end -->

## Modules

Five consumer artifacts are published together as one release train.

| Module | Purpose |
|--------|---------|
| [`jsonapi-java-core`](jsonapi-java-core/README.md) | Dependency-free document model and validation |
| [`jsonapi-java-annotations`](jsonapi-java-annotations/README.md) | Domain-mapping role annotations |
| [`jsonapi-java-api`](jsonapi-java-api/README.md) | Neutral application and capability contracts |
| [`jsonapi-java-query`](jsonapi-java-query/README.md) | Neutral query-parameter parsing |
| [`jsonapi-java-jackson3`](jsonapi-java-jackson3/README.md) | Native Jackson 3 runtime |

Spring integration lives in the separate `kazforge/jsonapi-java-spring` companion repository,
which has its own version and release train and consumes released public `jsonapi-java`
contracts. It is not shipped here and is not part of the consumer artifact inventory.

## Project and development

[Contributing](CONTRIBUTING.md) · [Security reporting](SECURITY.md) ·
[Maintainer documentation](docs/README.md)

With a local JDK 21 and the committed wrapper:

```bash
./gradlew clean build
```

## License

Apache License 2.0 — see [LICENSE](LICENSE).
