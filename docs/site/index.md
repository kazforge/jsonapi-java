# jsonapi-java

Read and write [JSON:API 1.1](https://jsonapi.org/) documents in Java without giving a library control
of your endpoints, persistence, authorization, or query execution.

`jsonapi-java` requires Java 21. Its dependency-free core models and validates JSON:API documents;
optional modules add domain-mapping roles, query parsing, and native Jackson 2 or Jackson 3 adapters.

## Current modules

| Module | Responsibility |
|--------|----------------|
| `jsonapi-java-core` | Immutable document model and aggregate validation |
| `jsonapi-java-annotations` | Domain-mapping role annotations |
| `jsonapi-java-jackson-api` | Jackson-major-neutral application and capability contracts |
| `jsonapi-java-query` | Query-selection parsing and opaque parameter preservation |
| `jsonapi-java-jackson2` | Native Jackson 2 codec, mapping, and PATCH binding |
| `jsonapi-java-jackson3` | Native Jackson 3 codec, mapping, and PATCH binding |

!!! note
    The user guide is intentionally growing from this compact foundation. It will lead with
    dependency setup and concrete document, mapping, relationship, and PATCH workflows rather than
    duplicate Javadoc or repository-maintenance material.

## What the library does not own

Applications remain responsible for persistence, transactions, endpoints, authorization, query
execution, relationship resolution, and applying update projections. The library represents and
validates documents; it does not become an API server or ORM layer.
