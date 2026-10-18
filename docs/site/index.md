---
title: "JSON:API 1.1 for Java 21+"
description: "Read and write JSON:API 1.1 in Java 21+ with Jackson 3. Map DTOs, validate documents, preserve PATCH presence, and parse queries."
---

# JSON:API 1.1 for Java 21+ {#jsonapi-java}

Read and write [JSON:API 1.1](https://jsonapi.org/) documents in Java 21+ without handing over your
endpoints, persistence, or application architecture. jsonapi-java uses **Jackson 3** for DTO
mapping, document validation, presence-aware PATCH, and relationship linkage, with a separate query
parser for includes, sparse fieldsets, sort, and opaque parameters.

The runtime coordinate is `com.kazforge:jsonapi-java-jackson3`. [Install and run an example](getting-started.md).

## Release status

<!-- x-release-please-start-version -->

Current release: **[0.4.1][current-release]**.

[current-release]: https://github.com/kazforge/jsonapi-java/releases/tag/v0.4.1

<!-- x-release-please-end -->

Find published artifacts on [Maven Central](https://central.sonatype.com/namespace/com.kazforge).
The 0.x line is intended for real library use, but public API refinement remains possible before
1.0: minor releases may break compatibility; patch releases do not.

## Choose a task

| I need to… | Start here |
|------------|------------|
| Add a dependency and configure Jackson | [Getting started](getting-started.md) |
| Look up exact API contracts for a release | [API Reference](api-reference.md) |
| Change runtime defaults or distinguish per-operation settings | [Configuration](configuration.md) |
| Read or write application DTOs; author create/update documents | [Resources](resources.md) |
| Work with linkage, errors, or exact document state | [Relationships and documents](relationships-and-documents.md) |
| Distinguish an omitted update from an explicit null | [PATCH](patches.md) |
| Parse query parameters and select fields/included resources | [Query and representation](query-and-representation.md) |
| Diagnose validation, mapping, or query failures | [Diagnostics](diagnostics.md) |

DTO mapping follows your configured Jackson property model. Relationships carry identifiers,
not automatically loaded objects. The document APIs distinguish absent members, explicit null,
and empty collections or objects, including states a flat DTO cannot represent.

## Application boundaries

You own HTTP/media-type handling, persistence, authorization, query execution, relationship lookup,
and applying updates. An annotation does not fetch a relationship; parsing `filter` does not run a
query; reading a PATCH does not mutate an entity.

Spring integration lives in the separate `kazforge/jsonapi-java-spring` companion repository with
its own release train and is not shipped here; it is not required to use the core
library. No controller, ORM, or repository integration is required to use this library.

## Project

[Releases and release notes](https://github.com/kazforge/jsonapi-java/releases) ·
[Issues](https://github.com/kazforge/jsonapi-java/issues) ·
[Apache-2.0 license](https://github.com/kazforge/jsonapi-java/blob/main/LICENSE) ·
[Security reporting](https://github.com/kazforge/jsonapi-java/blob/main/SECURITY.md) ·
[Contributing](https://github.com/kazforge/jsonapi-java/blob/main/CONTRIBUTING.md)

Maven Central publications are signed and include per-module CycloneDX SBOMs.
