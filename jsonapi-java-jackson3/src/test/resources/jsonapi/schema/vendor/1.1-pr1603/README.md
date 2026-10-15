# JSON:API 1.1 draft schemas (PR #1603 pin)

Vendored JSON Schema Draft 2020-12 schemas from the JSON:API project's contributor PR
[json-api/json-api#1603](https://github.com/json-api/json-api/pull/1603) ("Add JSON Schema that is
v1.1 compliant", fork `VGirol/json-api`, branch `schema-1.1`).

**These are unreleased draft-PR schemas, not official JSON:API schemas.** The upstream PR remains
open and the schema publication issue
[json-api/json-api#1672](https://github.com/json-api/json-api/issues/1672) is unresolved; a schema
result is supplemental evidence only and never changes a conformance status in
[`docs/conformance.md`](../../../../../../../../docs/conformance.md).

## Provenance

| Field            | Value                                                                                                                                                              |
|------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Pull request     | https://github.com/json-api/json-api/pull/1603                                                                                                                     |
| Fork             | `VGirol/json-api` (head branch `schema-1.1`)                                                                                                                       |
| Pinned commit    | `4ee1c644fcc273044ecec39a6b8c0f0485abdc0e` ("Allow for parallel relationships in JSON schema v1.1")                                                                |
| Source paths     | `_schemas/1.1/schema.json`, `_schemas/1.1/schema_create_resource.json`, `_schemas/1.1/schema_update_resource.json`, `_schemas/1.1/schema_update_relationship.json` |
| Retrieval date   | 2026-08-02                                                                                                                                                         |
| Upstream license | CC0 1.0 Universal (see `LICENSE` in the fork at the pinned commit)                                                                                                 |
| Draft            | JSON Schema Draft 2020-12 (`$schema` declared in every file)                                                                                                       |

## Files

| File                              | Usage                        | SHA-256                                                            |
|-----------------------------------|------------------------------|--------------------------------------------------------------------|
| `schema.json`                     | Response documents           | `64f85af7e3d1351023db50aa81f3d70663900e89b82f82bc769281e564623f15` |
| `schema_create_resource.json`     | Create-resource requests     | `9c6c22a18642d53656f5c0b0dcfb712f7cb74702ea0024cea39dd17671833ba6` |
| `schema_update_resource.json`     | Update-resource requests     | `6311c6bd199247dd806b5f213909c860051638d88052d637879077afe084be1b` |
| `schema_update_relationship.json` | Update-relationship requests | `293fc495b2ce325f726100ef147311dde72577afbf1dd2444edcef17ef73fd05` |

`sha256.sum` repeats the hashes in machine-readable form. Adapter suites consume the same classpath
resources for direct writer-output and corpus-versus-schema checks and should keep the recorded
hashes synchronized when the pin changes.

## Offline `$ref` resolution

`schema.json` declares `$id: https://jsonapi.org/schemas/spec/v1.1/draft` and is self-contained.
The three request schemas reference that URI remotely (`"$ref":
"https://jsonapi.org/schemas/spec/v1.1/draft#/definitions/..."`). The harness registers the
vendored `schema.json` under that URI so no network access happens during tests.

## Invalid controls

`invalid-controls/` holds repo-authored documents that must fail their schema kind. They are not
upstream files; they prove the harness rejects malformed documents instead of silently passing.

## Updating the pin

Updating requires an intentional diff of all four schemas, their hashes, provenance metadata, and
the explicit expected-gap results in adapter `JsonApiDraftSchemaSpec` suites. Follow the upstream issue
[json-api/json-api#1672](https://github.com/json-api/json-api/issues/1672) before repinning; a
released official schema would supersede this directory.
