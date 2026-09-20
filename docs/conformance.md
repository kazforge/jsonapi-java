# JSON:API v1.1 Conformance Checklist

This page owns current support status by feature and layer. **Supported** means the library implements
and verifies the behavior; **pass-through** means it preserves a valid form without interpreting it;
**delegated** means application policy is required; **deferred** is not implemented; **out of scope**
is intentionally outside the product boundary. Module composition is described in
[`docs/architecture.md`](architecture.md).

## Core document structure and validation

| Feature | Status | Boundary |
|---------|--------|----------|
| Top-level `data`, `errors`, `meta`, `jsonapi`, `links`, and `included` structure | supported | Requires a content member; `data` and `errors` do not coexist; `included` requires `data` |
| Absent, explicit-null, single, and collection primary data | supported | Sealed document variants preserve each state; resource and identifier collections may be empty |
| Resource identity | supported | `type` is required; `id` is required except for the primary create-resource allowance; `lid` is independent |
| Attributes and relationships | supported | Absent and present-empty wrappers are distinct; semantic names reject reserved members and collide with non-`@` pass-through fields in the same resource namespace |
| Relationship linkage | supported | Absent `data`, explicit null, single, empty collection, and non-empty collection remain distinct |
| Links-only and meta-only relationships | supported | General document forms; operation-specific validation may impose stronger rules |
| Additional extension and `@` members | pass-through | Preserved in additional-member maps under extension/profile policy; extension semantics are not interpreted |
| Unknown unnamespaced structural members | supported | Readers may discard them under read policy; recognized standard, extension, profile, and `@` members remain validated; direct construction and writers stay strict |
| String and object links | supported | Object links preserve standard and additional members; nested `describedby` supports link, explicit null, or omission |
| `hreflang` | supported | Canonical model is a list and writers emit the array form |
| Nullable pagination links | supported | Explicit null values are preserved |
| Link syntax | supported | URI references, registered/absolute relation names, parameterized media types, and context-specific members are validated |
| Extension/profile identifiers | supported | Absolute URI and namespace/member policy are enforced through validation context |
| Meta | supported | Flat JSON object; no synthetic `members` key |
| Error objects and sources | supported | Errors require a standard member; builders construct the same core values; source additional members pass through |
| Error `source.pointer` | supported | RFC 6901 syntax, including empty string; no document resolution and no URI-fragment form |
| Member names and reserved members | supported | JSON:API member grammar and dedicated-member exclusions apply across model containers |
| Resource identity uniqueness | supported | Detects duplicates, id/lid aliases, per-array duplicates, inconsistent alias pairing, and unequal representations sharing an identity |
| Full linkage | supported | Alias-aware reachability with sparse-fieldset exemptions scoped to affected included resources |
| Endpoint role and primary-data context | supported | Resource and relationship endpoints have distinct shape/link rules; top-level `related` is relationship-endpoint-only when primary data is present |
| Pagination cardinality | supported | Top-level pagination requires collection data; relationship pagination rejects only positive to-one evidence and allows unknown cardinality |
| Open JSON values | supported | Legal JSON shapes are immutable; object/collection cycles are rejected |
| Null payload diagnostics | supported | Null collections/elements, required single values, and null validation-context inputs fail with stable rule codes |
| Defensive copies | supported | Model and validation-context collections are copied |
| URI-reference and resource-type syntax | supported | ASCII RFC 3986 URI references and JSON:API member-name grammar; raw non-ASCII URI text is rejected |

`PrimaryDataKind` (resource versus identifier decoding) and `PrimaryDataContext` (resource versus
relationship endpoint role) are independent choices.

## Resource update requests

| Feature | Status | Boundary |
|---------|--------|----------|
| Primary shape | supported | Exactly one resource object on an ordinary resource endpoint |
| Identity | supported | Resource `id` is required; lid-only updates are rejected |
| Supplied relationships | supported | Each supplied primary relationship contains replacement `data` |
| Replacement linkage | supported | Null, single, empty collection, and non-empty collection are valid |
| Presence preservation | supported | Omitted/present-empty wrappers and explicit-null attributes are not normalized |
| Expected endpoint identity | supported | Optional comparison through validation context |
| Scope | supported | Update-specific rules apply to the primary resource; included resources and linkage retain general response rules |
| Route identity, mutation, and command application | out of scope | Applications derive route context, authorize, and apply changes |

## Resource create requests

| Feature | Status | Boundary |
|---------|--------|----------|
| Primary shape | supported | Exactly one resource object on an ordinary resource endpoint |
| Primary identity | supported | `id` is optional; `id` and `lid` remain independent |
| Lid-only linkage | supported | Allowed only for a matching `type` + `lid` self-reference to the primary create resource; unrelated and included resources require `id` |
| Supplied relationships | supported | Each relationship on the primary create resource contains `data` |
| Linkage and wrapper presence | supported | Null, single, empty/non-empty collection, omitted wrapper, and present-empty wrapper remain distinct |
| Scope | supported | Create-specific allowances apply only to the primary resource and do not imply nested-create semantics |
| General data-less relationships | supported | Links-only/meta-only forms remain valid outside the primary-create restriction |
| HTTP method handling and mutation | out of scope | The caller selects create usage and applies application behavior |

## Mapping metadata

| Feature | Status | Boundary |
|---------|--------|----------|
| `@JsonApiResource(type)` | supported | Declares JSON:API resource type |
| `@JsonApiId` and `@JsonApiLocalId` | supported | Independent property roles; neither falls back to the other |
| `@JsonApiAttribute` and `@JsonApiRelationship` | supported | Role-only; configured Jackson owns property names and behavior |
| `@JsonApiMeta` | supported | One whole `ResourceObject.meta` property per mapped resource |
| `@JsonApiRelationshipMeta` | supported | One whole relationship-meta property associated by Jackson property identity |
| `RelationshipLinkage<T, M>` | supported | Opt-in `ResourceIdentifier.meta`; PATCH changes it only through whole-linkage replacement |

## Codec and wire format

| Feature | Status | Boundary |
|---------|--------|----------|
| JSON serialization | supported | Jackson 2 and Jackson 3 validate before emission |
| JSON deserialization | supported | Token-driven decode through core construction, followed by aggregate validation |
| Ambiguous primary data | supported | Caller supplies explicit `PrimaryDataKind`; shared object/empty-array cases are valid under both applicable kinds |
| Deterministic emission | supported | Standard member order, insertion-ordered additional members, canonical `hreflang`, and exact UTF-8 behavior |
| Malformed-input diagnostics | supported | Category, pointer, and safe source location; parser cursor is the fallback when an exception lacks location |
| Caller-owned sources and sinks | supported | Supplied streams, writers, parsers, and generators remain open; callers own buffering and flushing |
| Shared negative wire corpus | supported | Read-only neutral inputs; behavioral expectations remain adapter-local |
| Jackson 2 advanced stream I/O | supported | Advanced APIs retain checked `IOException`; the Level-1 runtime adapts unavoidable stream failures to `UncheckedIOException` |

## Draft-schema cross-check (supplemental)

Writer output and selected corpus resources are cross-checked offline against vendored, SHA-256-pinned
JSON:API 1.1 draft-PR schemas for response, create-resource, update-resource, and
update-relationship documents. These schemas are unreleased and are **not** an official conformance
oracle. A schema result never changes a feature status: the textual specification wins, and expected
failures remain explicit so schema changes force review.

| Fixture | Recorded draft-schema gap | Governing JSON:API behavior |
|---------|---------------------------|-----------------------------|
| `member-order` | Draft rejects response-resource `lid` and does not evaluate extension members | v1.1 permits local identifiers and namespaced extension members |
| `extension-and-at-members` | Draft models `@` members but leaves extension members unevaluated | v1.1 extension members remain valid |
| `string-and-object-links` | Draft accepts only string `hreflang` | v1.1 permits the canonical list representation emitted by the writers |

Schema provenance, pins, and fixture-specific invariants live in the
[vendored schema README](../jsonapi-java-api/src/testFixtures/resources/jsonapi/schema/vendor/1.1-pr1603/README.md).

## Domain mapping

| Feature | Status | Boundary |
|---------|--------|----------|
| Annotated domain-to-resource mapping | supported | Both Jackson adapters; configured Jackson owns visible property behavior |
| Independent id/lid roles | supported | Separate write, read, relationship, and inclusion identities; usage legality remains core validation |
| Relationship data presence | supported | Every selected mapped relationship emits `data`; a data-less wire relationship binds no linkage while relationship meta can still bind |
| Compound inclusion | supported | Explicit include selection plus application policy; no automatic graph traversal |
| Sparse fieldsets | supported | Write-side selection with policy; `MappedDocument` provenance supplies scoped linkage exemptions to validation |
| Link decoration | supported | Adds resource and mapped-relationship links only; never creates or resurrects relationships |
| Flat resource-to-DTO binding | supported | Validated document first, linkage-oriented, never reads `included` |
| Typed domain envelopes | supported | Advanced explicit type registry; included resources bind independently in wire order and are not injected into relationships |
| Presence-aware PATCH commands | supported | Low-level supplied-change projection in both adapters |
| Direct typed PATCH DTOs | supported | `PatchPresence` preserves omitted, explicit-null, and supplied states in both adapters |
| Recursive structured PATCH | supported | Neutral `StructuredPatch`; typed and low-level paths preserve their distinct declaration rules |
| Domain graph hydration | out of scope | Linkage resolution remains application policy |
| Domain/persistence mutation | out of scope | Applications authorize and apply projected changes |

## Level-1 application runtime

| Feature | Status | Boundary |
|---------|--------|----------|
| Neutral `JsonApi` root and four facets | supported | Jackson 2 and Jackson 3 provide configured native-major runtimes |
| Homogeneous resource reads | supported | One-resource and collection operations are strict; incompatible primary shapes are not coerced |
| Resource, create, and update writes | supported | Select the corresponding core validation usage; expected update identity is explicit |
| Relationship operations | supported | Separate strict to-one, explicit-null, and to-many linkage documents |
| Raw document operations | supported | Preserve explicit semantic read context and validate before writing |
| PATCH projections | supported | Typed DTO and low-level command paths |
| Runtime versus operation configuration | supported | Policy, decoration, linkage, identifier conversion, and optional defaults are application-lifetime; selection and envelopes are per operation |
| Caller mapper ownership | supported | Runtimes do not mutate configured mapper instances |

## Query parameters

| Feature | Status | Boundary |
|---------|--------|----------|
| `include` | supported | Relationship paths, explicit empty requests, exact tokens, member syntax, optional exact allow-list |
| `fields[TYPE]` | supported | Explicit empty fieldsets, exact field names, member syntax, optional exact allow-list |
| `sort` | supported | Ordered direction and exact tokens, optional exact allow-list |
| `filter`, `page`, and unknown parameters | pass-through | Ordered opaque values preserve repeated-value order |
| Raw query decoding | supported | Optional leading `?`, empty segments, first `=`, UTF-8 form decoding, stable malformed-encoding diagnostics |
| Query interpretation and execution | delegated | Applications define filter/page semantics, authorization, limits, and persistence behavior |

## Delegated and transport boundaries

| Concern | Status | Boundary |
|---------|--------|----------|
| Include/field/profile/extension authorization | delegated | Application policy |
| Persistence, relationship resolution, and update application | delegated | Application behavior |
| Spring DTO/envelope and PATCH binding | deferred | No Spring module is a current build member |
| Endpoint availability and operation semantics | out of scope | Application/framework responsibility |
| HTTP status selection | out of scope | Except behavior explicitly guaranteed by a future adapter |
| Content negotiation beyond adapter guarantees | out of scope | Application/framework responsibility |

Behavioral proof lives in each module's `src/test` tree. `./gradlew clean build` executes those tests,
architecture rules, formatting checks, and the repository coverage policy; test filenames are not a
second conformance inventory.
