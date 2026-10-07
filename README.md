# jsonapi-java

Read and write [JSON:API 1.1](https://jsonapi.org/) documents in **Java 21+**. Map application DTOs,
validate documents, and preserve PATCH presence with native Jackson 2 or Jackson 3 runtimes.
Endpoints, persistence, authorization, query execution, and applying updates stay with your application.

The first public 0.x release is not published yet. Public API refinement is possible before 1.0:
minor releases may break compatibility; patch releases do not.

## Get started

Choose `com.kazforge:jsonapi-java-jackson3` or `com.kazforge:jsonapi-java-jackson2` to match your
Jackson major. Packages use `com.kazforge.jsonapi.*`.

**[Install and run the first example](https://jsonapi.kazforge.com/getting-started/)** — including
the local source-build path while artifacts remain unpublished. The
[user guide](https://jsonapi.kazforge.com/) owns user workflows; its
[API-reference section](https://jsonapi.kazforge.com/getting-started/#api-reference-and-next-steps)
links Javadoc.

## Modules

These seven implemented artifacts form the release train; none is publicly published yet.

| Module | Purpose |
|--------|---------|
| [`jsonapi-java-core`](jsonapi-java-core/README.md) | Dependency-free document model and validation |
| [`jsonapi-java-annotations`](jsonapi-java-annotations/README.md) | Domain-mapping role annotations |
| [`jsonapi-java-api`](jsonapi-java-api/README.md) | Neutral application and capability contracts |
| [`jsonapi-java-mapping`](jsonapi-java-mapping/README.md) | Adapter implementation dependency; **unsupported consumer API** |
| [`jsonapi-java-query`](jsonapi-java-query/README.md) | Neutral query-parameter parsing |
| [`jsonapi-java-jackson3`](jsonapi-java-jackson3/README.md) | Native Jackson 3 runtime |
| [`jsonapi-java-jackson2`](jsonapi-java-jackson2/README.md) | Native Jackson 2 runtime |

Spring integration is separate and not shipped here. Future direction lives in the
[vision](docs/vision.md), not the consumer artifact inventory.

## Project and development

[Contributing](CONTRIBUTING.md) · [Security reporting](SECURITY.md) ·
[Maintainer documentation](docs/README.md)

With a local JDK 21 and the committed wrapper:

```bash
./gradlew clean build
```

## License

Apache License 2.0 — see [LICENSE](LICENSE).
