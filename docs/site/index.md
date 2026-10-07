# jsonapi-java

Read and write [JSON:API 1.1](https://jsonapi.org/) documents in Java 21 without handing over your
endpoints, persistence, or application architecture.

The permanent coordinates are `com.kazforge:jsonapi-java-jackson3` or
`com.kazforge:jsonapi-java-jackson2`. Choose the adapter for your application's Jackson major;
both implement the same `JsonApi` operations. [Install and run an example](getting-started.md).

## Release status

The first public 0.x release is not published yet. Use the
[source-build path](getting-started.md#before-the-first-release) for now, not a guessed Maven version.
The 0.x line is intended for real library use, but public API refinement remains possible before
1.0: minor releases may break compatibility; patch releases do not.

## Choose a task

| I need to… | Start here |
|------------|------------|
| Add a dependency and configure Jackson | [Getting started](getting-started.md) |
| Read or write application DTOs; author create/update documents | [Resources](resources.md) |
| Work with linkage, errors, or exact document state | [Relationships and documents](relationships-and-documents.md) |
| Distinguish an omitted update from an explicit null | [PATCH](patches.md) |
| Parse query parameters and select fields/included resources | [Query and representation](query-and-representation.md) |

Ordinary mapping follows your configured Jackson property model. Relationships carry identifiers,
not automatically loaded graphs. The document APIs preserve wire states that a flat DTO cannot.

## Application boundaries

You own HTTP/media-type handling, persistence, authorization, query execution, relationship lookup,
and applying updates. An annotation does not fetch a relationship; parsing `filter` does not run a
query; reading a PATCH does not mutate an entity.

Spring integration is a separate project and is not shipped here. The core 0.x release does not
wait for it. No controller, ORM, or repository integration is required to use this library.
