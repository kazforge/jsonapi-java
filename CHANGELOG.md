# Changelog

Consumer-visible changes for the unified release train shared by the
`com.kazforge:jsonapi-java-*` artifacts. Version and change semantics follow
[ADR-014](docs/adr/014-unified-release-train.md); release-please maintains future
entries. Tags are named `v<version>`.

## [0.4.1](https://github.com/kazforge/jsonapi-java/compare/v0.4.0...v0.4.1) (2026-10-31)


### Bug Fixes

* **ci:** verify Gradle plugin and constrain vulnerable Guava ([1e4029e](https://github.com/kazforge/jsonapi-java/commit/1e4029e1dd7f9c0c920285d6e758043cda8d6827))

## [0.4.0](https://github.com/kazforge/jsonapi-java/compare/v0.3.0...v0.4.0) (2026-10-16)


### Features

* **api:** add create-aware resource DTO reads ([08bdd83](https://github.com/kazforge/jsonapi-java/commit/08bdd83e49a383b2c6cac45372dcbf13edbc3870))

## [0.3.0](https://github.com/kazforge/jsonapi-java/compare/v0.2.0...v0.3.0) (2026-10-15)


### ⚠ BREAKING CHANGES

* **jackson:** consolidate internal mapping into jackson 3
* **jackson:** the Jackson 2 adapter is no longer published; use jsonapi-java-jackson3.

### Features

* **core:** add focused record construction helpers ([9bd42cd](https://github.com/kazforge/jsonapi-java/commit/9bd42cdaede3823adf19b35c56df19668d486ddd))
* **jackson:** drop jackson 2 runtime support ([c7047f3](https://github.com/kazforge/jsonapi-java/commit/c7047f34cc9c6eb2ca9705dd2fa066d7995bcb6d))


### Bug Fixes

* **jackson3:** align typed patch creator-failure classification across majors ([fb300d1](https://github.com/kazforge/jsonapi-java/commit/fb300d1e70f6184c19a85e22baed72760ed2e9bc))


### Code Refactoring

* **jackson:** consolidate internal mapping into jackson 3 ([d1a14a7](https://github.com/kazforge/jsonapi-java/commit/d1a14a7d8e3efd32cfc1e0e0475e0ba177b54eaa))

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
