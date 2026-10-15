# Changelog

Consumer-visible changes for the unified release train shared by the
`com.kazforge:jsonapi-java-*` artifacts. Version and change semantics follow
[ADR-014](docs/adr/014-unified-release-train.md); release-please maintains future
entries. Tags are named `v<version>`.

## [0.2.0](https://github.com/kazforge/jsonapi-java/releases/tag/v0.2.0) (2026-10-08)

First public release of jsonapi-java, a JSON:API 1.1 library for Java 21+.
This pre-1.0 release is intended for real use; the public API may evolve before
1.0. Minor releases may break compatibility; patch releases do not.

No migration from an earlier public coordinate is required: no earlier version
of these artifacts was publicly released. Pre-public package and artifact
renames are not consumer breaking changes.

### Highlights

* Native Jackson 2 and Jackson 3 runtimes for reading and writing JSON:API documents.
* Document validation, application DTO mapping, relationships, and compound documents.
* Presence-aware PATCH binding that distinguishes absent members, explicit null, and empty values.
* Sparse fieldsets and neutral query-parameter parsing.
* One release train under `com.kazforge`, with Java packages under `com.kazforge.jsonapi`.

Endpoints, persistence, authorization, query execution, and applying updates
remain application responsibilities. Spring integration is not included.

See the [user guide](https://jsonapi.kazforge.com/) for installation and usage.
