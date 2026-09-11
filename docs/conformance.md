# JSON:API v1.1 Conformance Checklist

Conformance is reported per feature as: **supported**, **pass-through**, **delegated**, **deferred**, or **out of scope**.

Current capability: `jsonapi-java-core` owns the document model, aggregate validation, create- and update-request
shape, error `source.pointer` syntax, and reserved link names. `jsonapi-java-annotations` owns
metadata-only domain-mapping annotations. `jsonapi-java-jackson3` owns the Jackson 3 document
writer/reader, domain-to-resource mapping, compound inclusion, sparse fieldsets, flat DTO binding,
typed domain envelopes, and presence-aware PATCH binding (low-level commands and direct typed PATCH
DTOs). `jsonapi-java-jackson2` owns the Jackson 2 validated document writer with the same
validate-before-emit and provenance-composition semantics, plus the token-driven validated document
reader with the same decode-then-validate semantics, plus advanced write-side domain-to-resource
mapping through `JsonApiJackson2.resourceMapper` with the same mapping, inclusion, fieldset, and
decoration semantics as Jackson 3, plus flat resource-to-DTO binding through
`JsonApiJackson2.resourceBinder` with the same flat-binding semantics as Jackson 3, plus the
advanced heterogeneous typed domain envelope through `JsonApiJackson2.domainDocumentReader`, plus
presence-aware PATCH binding through `JsonApiJackson2.patchCommandReader` / `patchDtoReader` with
the same PATCH semantics as Jackson 3, and the configured `Jackson2JsonApi` Level-1 runtime.
`jsonapi-java-jackson-api` owns
Jackson-major-neutral policy, diagnostics, contexts, envelope values, and presence-aware update
contracts. `jsonapi-java-query` owns framework- and Jackson-major-neutral parsing of standardized
query selection while preserving opaque page/filter/unprocessed inputs. How those modules fit
together is in [`docs/architecture.md`](architecture.md). Writer output is cross-checked against pinned JSON:API 1.1 draft schemas as supplemental
evidence only. The version-neutral document corpus, closed negative corpus, and dual-success
ambiguous primary-data cases in the Jackson API test-fixtures corpus (`jsonapi/corpus/1.1/`) are
shared wire resources for every Jackson major. Capability, schema, and context selections belong
to each adapter's local specifications. Spring adapters remain deferred.

## Document structure (supported)

| Rule                                                                        | Status       | Notes                                                                                                                                                                                                                                                                                        |
|-----------------------------------------------------------------------------|--------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Top-level members: `data`, `errors`, `meta`, `jsonapi`, `links`, `included` | supported    | `JsonApiDocument`                                                                                                                                                                                                                                                                            |
| At least one of `data`, `errors`, `meta`, or extension member               | supported    | Local construction; `@` members alone are insufficient                                                                                                                                                                                                                                       |
| `data` and `errors` must not coexist                                        | supported    | Local construction                                                                                                                                                                                                                                                                           |
| `included` absent when `data` absent                                        | supported    | Local construction                                                                                                                                                                                                                                                                           |
| Explicit `"data": null` vs absent `data`                                    | supported    | `DocumentData.NullData` vs Java `null`                                                                                                                                                                                                                                                       |
| Primary data: resource, collection, identifier, identifier collection       | supported    | Sealed `DocumentData`                                                                                                                                                                                                                                                                        |
| Resource `type` required                                                    | supported    | `ResourceObject`, `ResourceIdentifier`                                                                                                                                                                                                                                                       |
| Resource `id` required except create context                                | supported    | Aggregate validator; empty/whitespace strings are present                                                                                                                                                                                                                                    |
| Local identifier (`lid`) for new resources                                  | supported    | `ResourceIdentifier`; structured `ResourceIdentity` keys                                                                                                                                                                                                                                     |
| Attributes / relationships flat wrappers                                    | supported    | Semantic maps reject `type`/`id`, `@`, and extension names; local resource construction rejects one field namespace across semantic and non-`@` pass-through members of both wrappers (`MEMBER_NAME_COLLISION` at `/data/relationships/<escaped-name>`); `@` members stay outside that namespace |
| Absent vs present-empty attributes/relationships                            | supported    | `null` wrapper vs `empty()`                                                                                                                                                                                                                                                                  |
| `@` and extension members in containers                                     | pass-through | Only via `additionalMembers`; namespace policy in aggregate                                                                                                                                                                                                                                  |
| Relationship linkage: absent, null, single, collection                      | supported    | `RelationshipData` sealed variants                                                                                                                                                                                                                                                           |
| Link-only and meta-only relationships                                       | supported    | `Relationship`                                                                                                                                                                                                                                                                               |
| Links: string and object forms                                              | supported    | Sealed `Link`; object form preserves additional members                                                                                                                                                                                                                                      |
| Link-object `describedby` value forms                                       | supported    | Nested value is the existing sealed `Link`: string link or link object (with its own `href`, `type`, `meta`, `hreflang`, and additional members); omitted `describedby` stays absent; both adapters read and emit recursively |
| `hreflang` canonical list representation                                    | supported    | `Link.ObjectLink`; writer always emits JSON array form                                                                                                                                                                                                                                       |
| Nullable pagination links                                                   | supported    | `Links` null-preserving map                                                                                                                                                                                                                                                                  |
| Meta flat object (no synthetic `members` key)                               | supported    | `Meta`                                                                                                                                                                                                                                                                                       |
| Error object requires ≥1 standard member                                    | supported    | `ErrorObject`                                                                                                                                                                                                                                                                                |
| Framework-neutral error construction convenience                             | supported    | `ErrorObject.builder()`, `ErrorSource.builder()`, and `JsonApiDocument.withError(...)` construct the existing core values; no new wire or validation semantics                                                                                                                                |
| Error `source.pointer` RFC 6901 syntax                                      | supported    | `ErrorSource.pointer`; syntax only via `SyntaxValidators.isValidJsonPointer`; empty string allowed; no document resolution; URI-fragment form rejected                                                                                                                                       |
| Error source additional members                                             | pass-through | `ErrorSource.additionalMembers`                                                                                                                                                                                                                                                              |
| Additional member name grammar                                              | supported    | `MemberNames` (alphanumeric namespaces, may start with digit)                                                                                                                                                                                                                                |
| Reserved dedicated members in additional maps                               | supported    | Document, resource, identifier, relationship, error, jsonapi, link, source, Links                                                                                                                                                                                                            |
| Non-empty relationships                                                     | supported    | At least one of `data`, `links`, `meta`, or extension                                                                                                                                                                                                                                        |
| Links-only relationship minimum                                             | supported    | Non-pagination link locally; aggregate requires self/related/allowed ext/profile                                                                                                                                                                                                             |
| Parameterized link media types                                              | supported    | RFC 7231 OWS (SP/HTAB) only immediately before `;`; leading/terminal bare-type and final-parameter trailing whitespace rejected; tokens; obs-text `%x80-FF`; HTAB qdtext; quoted-pair restricts CTLs                                                                                         |
| Link relation syntax                                                        | supported    | LOALPHA registered type or absolute URI; semantic keys use relation grammar                                                                                                                                                                                                                  |
| Absolute extension/profile URIs                                             | supported    | ASCII RFC 3986 absolute URI; structured authority (userinfo/host/port, IP-literal)                                                                                                                                                                                                           |
| Unknown unnamespaced members rejected                                       | supported    | Aggregate validator; profile members allowed via context                                                                                                                                                                                                                                     |
| Extension namespace policy                                                  | supported    | `ValidationContext.allowedExtensionNamespaces`; includes link-object `meta`                                                                                                                                                                                                                  |
| Profile member policy                                                       | supported    | Additional members and policy-permitted link relations                                                                                                                                                                                                                                       |
| Profile URI policy                                                          | supported    | Enforced when `allowedProfileUris` is non-empty                                                                                                                                                                                                                                              |
| Duplicate resource identities                                               | supported    | `DUPLICATE_RESOURCE_IDENTITY`; structured identity; provisional lid aliases; per-array uniqueness for primary/relationship identifier collections; alias-aware uniqueness after id↔lid binding; same identity with unequal `ResourceObject` representations rejected (representation-strict) |
| Full linkage                                                                | supported    | Canonical alias store/lookup; sparse-fieldset linkage exemptions scoped to the fieldset-orphaned included resources                                                                                                                                                                            |
| Consistent local identifiers                                                | supported    | Order-independent one-to-one `id`↔`lid` partners                                                                                                                                                                                                                                             |
| Context-specific link members                                               | supported    | `LinksContext`; `@` keys only via additional members                                                                                                                                                                                                                                         |
| Relationship pagination cardinality                                         | supported    | Rejected only on positive to-one evidence: explicit null or single linkage, or an occurrence-keyed `TO_ONE` hint; collection linkage and `TO_MANY` hints are allowed; absent linkage with no hint is allowed because cardinality is unknown rather than proven to-one                                      |
| Top-level pagination requires collection                                    | supported    | Proven from `ResourceCollection` and `IdentifierCollection`                                                                                                                                                                                                                                  |
| Open JSON value shapes                                                      | supported    | Immutable numbers; cycles rejected with stable diagnostics                                                                                                                                                                                                                                   |
| Null collection payloads and elements                                       | supported    | Stable `NULL_COLLECTION_*` codes; empty lists remain valid                                                                                                                                                                                                                                   |
| Null required single payloads                                               | supported    | `NULL_REQUIRED_VALUE` for single resource/identifier/linkage                                                                                                                                                                                                                                 |
| Validation context null diagnostics                                         | supported    | `NULL_REQUIRED_VALUE` for usage, links context, policy sets/elements, hints                                                                                                                                                                                                                  |
| Resource type member-name grammar                                           | supported    | Only `null` is missing; Unicode-legal types accepted via `MemberNames`                                                                                                                                                                                                                       |
| Defensive collection copies                                                 | supported    | Model types and `ValidationContext`                                                                                                                                                                                                                                                          |
| URI-reference syntax                                                        | supported    | ASCII RFC 3986; structured authority; empty string allowed; raw non-ASCII rejected                                                                                                                                                                                                           |

## Resource update request validation (supported)

| Rule                                                                                                            | Status       | Notes                                                                                        |
|-----------------------------------------------------------------------------------------------------------------|--------------|----------------------------------------------------------------------------------------------|
| Update primary data must be one resource object (absent, null, collection, or identifier primary data rejected) | supported    | `UPDATE_REQUIRES_SINGLE_RESOURCE` at `/data`                                                 |
| Update resource `id` required; lid-only rejected                                                                | supported    | Reuses `RESOURCE_ID_REQUIRED` at `/data/id`; inherited non-create identity rule              |
| Every supplied relationship must contain replacement `data`                                                     | supported    | `RELATIONSHIP_DATA_REQUIRED` at `/data/relationships/<name>/data`; primary resource only     |
| Relationship linkage preserved: null, single, empty and non-empty collection                                    | supported    | All `RelationshipData` variants valid replacements                                           |
| Omitted/present-empty attribute and relationship wrappers; explicit-null attribute values preserved             | supported    | Absent vs `Attributes.empty()` vs explicit null values; no normalization                     |
| Optional expected endpoint identity comparison                                                                  | supported    | `ENDPOINT_IDENTITY_MISMATCH` at `/data/type` or `/data/id`; supplied via `ValidationContext` |
| Update rules scoped to the primary resource                                                                     | supported    | `included` resources keep response semantics; full linkage still enforced                    |
| Command application                                                                                             | out of scope | Applications apply authorized update commands                                             |
| HTTP/route identity derivation and mutation                                                                     | out of scope | Application-owned; core compares only a supplied expected identity                           |

## Resource create request validation (supported)

| Rule                                                                                                            | Status       | Notes                                                                                                                     |
|-----------------------------------------------------------------------------------------------------------------|--------------|---------------------------------------------------------------------------------------------------------------------------|
| Create primary data must be one resource object (absent, null, collection, or identifier primary data rejected) | supported    | `CREATE_REQUIRES_SINGLE_RESOURCE` at `/data`                                                                              |
| Create resource `id` optional; `id` and `lid` remain independent                                                 | supported    | Aggregate validator; neither substitutes for the other; empty/whitespace strings are present                               |
| Every relationship supplied on the primary create resource must contain `data`                                  | supported    | Reuses `RELATIONSHIP_DATA_REQUIRED` at `/data/relationships/<name>/data`; primary resource only                           |
| Relationship linkage preserved: null, single, empty and non-empty collection                                    | supported    | All `RelationshipData` variants valid, including `lid`-based linkage                                                     |
| Omitted/present-empty relationship wrappers; links/meta coexist with present linkage                             | supported    | Absent vs `Relationships.empty()`; no normalization                                                                       |
| Create rules scoped to the primary resource                                                                     | supported    | `included` resources are exempt from the primary relationship-data rule and gain no nested-create interpretation; full linkage still enforced; pre-existing id-optional identity leniency is unchanged document-wide |
| Links-only/meta-only relationships outside create-specific restrictions                                         | supported    | Valid general core/document representations per ADR-018; rejected only on the primary create resource                      |
| HTTP/method handling and mutation                                                                               | out of scope | Application-owned; a future Spring layer selects `CREATE_REQUEST` from its own operation context; core stays method-neutral |

## Annotation metadata (supported)

| Rule                                                         | Status    | Notes                                                                 |
|--------------------------------------------------------------|-----------|-----------------------------------------------------------------------|
| `@JsonApiResource(type)` on domain types                     | supported | Runtime-retained, `@Documented`; not `@Inherited`                     |
| `@JsonApiId` marker on logical properties                    | supported | Fields, methods, parameters, and record components                    |
| `@JsonApiLocalId` marker on logical properties               | supported | Independent `lid` role: fields, methods, parameters, and record components; never falls back to or from the `id` role |
| `@JsonApiAttribute` attribute role                           | supported | Role-only; configured Jackson owns the external member name           |
| `@JsonApiRelationship` linkage role                          | supported | Role-only; no inclusion, fetch, cascade, or persistence elements      |
| `@JsonApiMeta` whole resource-side meta                      | supported | One property per resource; complete `ResourceObject.meta` object; read/write/PATCH (ADR-015) |
| `@JsonApiRelationshipMeta(relationship)` relationship meta   | supported | Required Jackson property identity of a mapped relationship; meta follows that relationship's Jackson external name; one per relationship; read/write/PATCH (ADR-015) |
| `RelationshipLinkage<T, M>` identifier meta                  | supported | Opt-in wrapper; `target` maps as the ordinary relationship target; `meta` maps to `ResourceIdentifier.meta`; read/write; PATCH only via whole-linkage replacement (ADR-017) |

## Codec / wire format (supported)

| Rule                                             | Status    | Notes                                                                                                 |
|--------------------------------------------------|-----------|-------------------------------------------------------------------------------------------------------|
| JSON serialization                               | supported | `jsonapi-java-jackson3` and `jsonapi-java-jackson2` validate-then-write                                                                                               |
| Canonical member ordering                        | supported | Standard members in model accessor order; additional members insertion order; `hreflang` always array |
| Golden fixture write comparisons                 | supported | Adapter-owned writer checks cover direct core models, provenance composition, sink parity, and exact UTF-8 (Jackson 3 also canonical corpus round trips); paths and resources remain in Jackson API test fixtures |
| JSON deserialization                             | supported | Token-driven decode via public core constructors; explicit `PrimaryDataKind`                          |
| Malformed input diagnostics with source location | supported | `JsonApiDocumentReadException` with category, pointer, and safe location; a Jackson failure without a usable exception location falls back to the parser cursor |
| Caller-owned read/write sinks                    | supported | Supplied `InputStream`/`OutputStream`/`Writer`/parser/generator stay open; only convenience-created parsers/generators close; the caller owns its buffering and flushing lifecycle |
| Shared read-only negative corpus                 | supported | Closed read-only `negative/` corpus; each adapter names its fixture files directly with version-neutral expectations |
| Ambiguous primary data requires explicit kind    | supported | Shared dual-success object/empty-array cases decode under both `PrimaryDataKind` values               |

## Draft-schema cross-check (supplemental)

Adapter-owned writer output is cross-checked against the JSON:API 1.1 **draft-PR schemas** for
direct core-model cases, while canonical corpus resource bytes are checked against the same schemas.
Each adapter additionally validates writer-produced positive documents for all four schema kinds:
RESPONSE and CREATE through the Level-1 runtime, and UPDATE and UPDATE_RELATIONSHIP through the
resource and relationship update operations.
The schemas are pinned under the Jackson API test-fixtures source set (`jsonapi/schema/vendor/1.1-pr1603/`, PR
[json-api/json-api#1603](https://github.com/json-api/json-api/pull/1603), fork `VGirol/json-api`
commit `4ee1c644fcc273044ecec39a6b8c0f0485abdc0e`). These are unreleased draft schemas, not an
official conformance oracle; the cross-check is **supplemental evidence only**. A schema result
never changes a feature status on this page: disagreements are resolved in favor of the textual
specification, and `JsonApiDraftSchemaSpec` keeps explicit expected-gap rows failing so a schema fix
forces an intentional re-review.

| Fixture                    | Draft-schema gap                                                                                         | Governing rule                                                                                                                                                                  |
|----------------------------|----------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `member-order`             | Draft forbids `lid` in response resources and models only `@` members, so `ext:` members are unevaluated | [v1.1 local identifiers](https://jsonapi.org/format/1.1/#document-resource-object-local-identifiers) and [extension members](https://jsonapi.org/format/1.1/#extension-members) |
| `extension-and-at-members` | Draft models only `@` members; `ext:` members at top level and in the resource are unevaluated           | [v1.1 extension members](https://jsonapi.org/format/1.1/#extension-members); PR #1603 description states @/extension rules are incomplete                                       |
| `string-and-object-links`  | Draft `linkObject.hreflang` accepts only a string                                                        | [v1.1 links](https://jsonapi.org/format/1.1/#document-links): `hreflang` is a canonical list representation; the writer always emits the array form                             |

`JsonApiDraftSchemaSpec` runs fully offline: the draft URI referenced by the request schemas is
mapped to the vendored response schema, all four schema files are SHA-256-pinned, an explicit local
table assigns the applicable corpus paths to their response, create-resource, update-resource, or
update-relationship schema kinds and validates their resource bytes, and one malformed control per
schema kind proves the harness rejects invalid documents. The same spec keeps explicit expected
failures for the three documented draft-schema gaps above, so a schema change forces an intentional
review.

## Domain mapping (supported)

| Rule                                                    | Status       | Notes                                                                                                                                                                                                                          |
|---------------------------------------------------------|--------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Jackson-visible domain-to-resource mapping (write-side) | supported    | Produce ResourceObject from annotated types; Jackson 3 via `JsonApiJackson3.resourceMapper`, Jackson 2 via `JsonApiJackson2.resourceMapper`                                                                                                                                                    |
| Independent id/lid identity roles in domain mapping      | supported    | `@JsonApiId` maps only `ResourceObject.id`; `@JsonApiLocalId` maps only `ResourceObject.lid`; neither role falls back to the other on write, flat read, linkage, or included resources; document validation owns usage legality (create-request lid-only states) |
| Relationship `data`-presence boundary (ADR-018)          | supported    | Mapped relationships are linkage-oriented: every selected mapped relationship emits `data` (explicit null, single, or collection; empty to-many is `[]`, never absent); a wire relationship without `data` binds no linkage on flat reads while its `meta` still binds; links-only/meta-only relationships remain core/document-level, preserved by the codec in both directions and never produced by ordinary mapping or decoration |
| Compound inclusion (explicit context / IncludePolicy)   | supported    | Opt-in paths and policy only — no automatic graph traversal                                                                                                                                                                    |
| Sparse fieldsets on write                               | supported    | `RepresentationSelection` fieldsets + `RepresentationPolicy` / `FieldPolicy`; `MappedDocument` linkage-exemption provenance composed into validation by the document writer; HTTP `fields[TYPE]` parsing and caller authorization remain application/adapter responsibilities            |
| Resource-link decoration on write                       | supported    | `ResourceDecorator`/`ResourceDecoration`/`RelationshipDecoration` (major-neutral; decoration adds only `ResourceObject.links` and mapped `Relationship.links`, keyed by logical property name, never resurrects fieldset-omitted relationships; Jackson 3 via `ResourceDecoratorRegistry` on `JsonApiJackson3.resourceMapper`; Jackson 2 via `ResourceDecoratorRegistry` on `JsonApiJackson2.resourceMapper`) |
| Flat resource-to-DTO binding                            | supported    | Validated document first; linkage only — never reads `included`; Jackson 3 via `JsonApiJackson3.resourceBinder`, Jackson 2 via `JsonApiJackson2.resourceBinder`                                                                |
| Typed domain document envelopes                         | supported    | `JsonApiDomainDocument` via `JsonApiJackson3.domainDocumentReader` and `JsonApiJackson2.domainDocumentReader`; explicit `ResourceTypeRegistry` dispatch remains advanced and Level-1 reads remain homogeneous/registry-free |
| Independent typed binding of `included` resources       | supported    | Wire-ordered `IncludedResources` with dual id/lid lookup in both Jackson adapters; no relationship injection                                                                                                                   |
| Presence-aware resource-update commands                 | supported    | Jackson 3 via `JsonApiJackson3.patchCommandReader`, Jackson 2 via `JsonApiJackson2.patchCommandReader` |
| Direct typed PATCH DTO binding                          | supported    | `PatchPresence` tri-state; Jackson 3 via `JsonApiJackson3.patchDtoReader`, Jackson 2 via `JsonApiJackson2.patchDtoReader` |
| Recursive structured value PATCH semantics              | supported    | `StructuredPatch` payload for structured attributes on both PATCH paths (ADR-014); Jackson 3 and Jackson 2 binding supported |
| Automatic domain graph hydration                        | out of scope | Linkage resolution remains application policy                                                                                                                                                                                  |
| Automatic mutation of domain or persistence objects     | out of scope | Applications apply authorized update commands                                                                                                                                                                                  |

## Level-1 application runtime (supported)

| Rule | Status | Notes |
|------|--------|-------|
| Major-neutral `JsonApi` root and four coordinated facets | supported | `Jackson2JsonApi` and `Jackson3JsonApi` are constructed from configured mapper instances |
| Strict homogeneous resource reads | supported | `readOne` requires one resource; `readMany` requires a resource collection; incompatible shapes are not coerced |
| Resource, create, and update writes | supported | Resource writes validate as response usage; create/update methods select their core request usage and optional endpoint identity |
| To-one, explicit-null, and to-many linkage operations | supported | Linkage-only documents remain separate from domain mapping and do not inherit resource-write defaults |
| Raw document operations with explicit read context | supported | `documents()` preserves explicit context and validates before writing |
| Presence-aware PATCH projections | supported | Typed `PatchPresence` DTO and low-level `PatchCommand` paths are available through `patches()` |
| Configured representation, decoration, linkage, and identifier collaborators | supported | Application-lifetime builder settings are applied without mutating the caller mapper; request-scoped selection and envelope remain per operation |
| Jackson 2 stream I/O at the Level-1 boundary | supported | Unavoidable checked I/O is exposed as `UncheckedIOException`; existing read, validation, and mapping families remain distinct |

## Query parameters (supported)

| Rule                                                | Status    | Notes |
|-----------------------------------------------------|-----------|-------|
| `filter`, `page`, and unknown parameter preservation | supported | Ordered opaque maps retain parameter and repeated-value order; filter/page semantics remain application-owned |
| `include` parsing                                   | supported | Relationship paths, explicit empty requests, member-name syntax, and optional exact allow-lists |
| `fields[TYPE]` parsing                              | supported | Sparse fieldset syntax, explicit empty fieldsets, member-name validation, and optional exact allow-lists |
| `sort` parsing                                      | supported | Ordered ascending/descending fields, exact token preservation, and optional exact allow-lists |
| Raw query decoding                                  | supported | Optional leading `?`, literal `&`/first `=`, UTF-8 form decoding, and stable malformed-encoding diagnostics |

## HTTP / endpoints (out of scope)

| Rule                                            | Status       | Notes                   |
|-------------------------------------------------|--------------|-------------------------|
| Spring-annotated DTO and typed-envelope binding | deferred     | Spring WebMVC DTO plan  |
| Spring presence-aware PATCH command binding     | deferred     | Spring WebMVC PATCH plan |
| Endpoint availability and operation semantics   | out of scope | Application-owned       |
| HTTP status selection                           | out of scope | Except adapter behavior |
| Content negotiation beyond adapter              | out of scope | Application-owned       |

## Verification evidence index

This index is documentation, not executable infrastructure. Every entry is a Spock specification
under `<module>/src/test/groovy/`, in a package mirroring the production package. Both Jackson
adapters own independent copies of a same-named spec unless a row names one adapter only.
`./gradlew clean build` compiles and runs all of them plus ArchUnit, Spotless, and the fixed JaCoCo
line/branch floor; CI additionally runs `spotlessCheck`, Sonar analysis, and the new-code issue gate.

| Capability group | Proving specs and gates |
|---|---|
| Core document model and aggregate validation | `jsonapi-java-core`: `model/JsonApiDocumentSpec`, `model/DocumentDataSpec`, `model/ResourceObjectSpec`, `model/AttributesRelationshipsSpec`, `model/RelationshipSpec`, `model/LinkSpec`, `model/ErrorObjectSpec`, `model/ErrorSourceSpec`, `model/JsonApiObjectSpec`, `validation/JsonApiDocumentValidatorSpec`, `validation/MemberNamesSpec`, `internal/SyntaxValidatorsSpec`, `internal/OpenJsonValuesSpec`, `internal/JsonPointersSpec` |
| Create/update request validation | `jsonapi-java-core`: `validation/CreateRequestValidationSpec`, `validation/UpdateRequestValidationSpec`, `validation/JsonApiDocumentValidatorSpec` |
| Links and errors | `jsonapi-java-core`: `model/LinkSpec`, `model/ErrorObjectSpec`, `model/ErrorSourceSpec`, `validation/JsonApiDocumentValidatorSpec`; both adapters: `DocumentReaderSpec`, `DocumentWriterContractSpec`, `internal/JsonApiWireWriterSpec` |
| Codecs (validate-before-write, decode-then-validate, diagnostics, sink ownership) | both adapters: `DocumentReaderSpec`, `DocumentWriterContractSpec`, `DocumentWriterValidationSpec`, `DocumentWriterSinkSpec`, `DocumentReaderIsolationSpec`, `DocumentWriterIsolationSpec`, `internal/JsonApiWireWriterSpec` |
| Draft-schema gates | both adapters: `JsonApiDraftSchemaSpec` (pinned SHA-256, all four schema kinds, writer-produced RESPONSE/CREATE/UPDATE/UPDATE_RELATIONSHIP, explicit expected-gap table) |
| Mapping, metadata, configured-Jackson authority | both adapters: `ResourceMapperSpec`, `ResourceMappingJacksonFeaturesSpec`, `PropertyScopedAuthoritySpec`, `ConfiguredResourceMetadataAuthoritySpec`, `LocalIdentifierMappingSpec`, `IdentifierConversionSpec`, `IdentifierMetaMappingSpec`, `ResourceDecorationSpec`, `DomainResourceWriterDiagnosticsSpec`, `ResourceMapperIsolationSpec`; Jackson 3 also `FlatMetaMappingSpec`; Jackson 2 also `PolymorphicMetaSpec`; `jsonapi-java-jackson-api`: `mapping/ResourceTypeRegistrySpec`, `mapping/ResourceDecorationSpec`, `MappingLocationSpec`; `jsonapi-java-annotations`: `AnnotationMetaContractSpec` |
| Sparse fieldsets and compound inclusion | both adapters: `SparseFieldsetSpec`, `CompoundSerializationSpec`, `ResourceDecorationSpec`, `GenericDomainWriteSpec`; Jackson 2 also `ConfiguredRelationshipIncludeSpec`, `ResourceMapperWriterSinkSpec`; `jsonapi-java-jackson-api`: `JacksonCommonContractsSpec` |
| Flat binding | both adapters: `ResourceBinderSpec`, `ResourceMappingJacksonFeaturesSpec`, `PropertyScopedAuthoritySpec`, `LocalIdentifierMappingSpec`, `DomainDocumentReaderSpec` |
| Typed envelopes | both adapters: `DomainDocumentReaderSpec`; `jsonapi-java-jackson-api`: `mapping/ResourceTypeRegistrySpec`, `JacksonCommonContractsSpec` |
| PATCH | both adapters: `PatchBindingSpec` (Jackson 3) / `PatchCommandBindingSpec` (Jackson 2), `PatchDtoBindingSpec`, `PatchStructuredBindingSpec`, `internal/PatchPresenceMarkerSpec`, `IdentifierMetaMappingSpec`, `Jackson2JsonApiPatchesSpec`/`Jackson3JsonApiPatchesSpec`; Jackson 2 also `PatchExtendedBindingSpec`, `PatchDtoExtendedBindingSpec`, `JsonApiJackson2PatchConstructionSpec`; Jackson 3 also `FlatMetaMappingSpec`; `jsonapi-java-core`: `validation/UpdateRequestValidationSpec` |
| Level-1 runtime | both adapters: `JsonApiJackson2ConstructionSpec`/`JsonApiJackson3ConstructionSpec`, `Jackson2JsonApiResourcesSpec`/`Jackson3JsonApiResourcesSpec`, `Jackson2JsonApiDocumentsSpec`/`Jackson3JsonApiDocumentsSpec`, `Jackson2JsonApiRelationshipsSpec`/`Jackson3JsonApiRelationshipsSpec`, `Jackson2JsonApiPatchesSpec`/`Jackson3JsonApiPatchesSpec`; `jsonapi-java-jackson-api`: `api/LevelOneApiContractSpec` |
| Query parsing | `jsonapi-java-query`: `JsonApiQueryParserSpec`, `QueryArchitectureSpec` |
| Architecture boundaries | `jsonapi-java-jackson-api`: `architecture/JacksonApiDependencyRulesSpec`; `jsonapi-java-jackson2`: `architecture/Jackson2DependencyRulesSpec`; `jsonapi-java-jackson3`: `architecture/Jackson3DependencyRulesSpec`; `jsonapi-java-query`: `QueryArchitectureSpec`; `jsonapi-java-annotations`: `AnnotationMetaContractSpec`; the core dependency boundary is compiler-enforced (no functional third-party runtime dependency) |

### Correction evidence

| Correction | Proving evidence |
|---|---|
| Field namespace includes non-`@` pass-through members | `jsonapi-java-core`: `model/ResourceObjectSpec` ("non-at pass-through fields share the resource field namespace", "at pass-through fields are outside the resource field namespace"); writer-facing pre-emission guard in `jsonapi-java-jackson2`: `DocumentWriterValidationSpec` ("field-namespace collisions fail before any writer output"). The guard is core-owned and the colliding state is unrepresentable, so a single adapter writer regression proves the boundary. |
| Link-object `describedby` supports string and object `Link` forms | `jsonapi-java-core`: `model/LinkSpec` (recursive construction, omitted value); both adapters: `DocumentReaderSpec` (object-form decode), `DocumentWriterContractSpec` (round trip), `internal/JsonApiWireWriterSpec` (emission, omission) |
| Unknown relationship cardinality is permissive for pagination | `jsonapi-java-core`: `validation/JsonApiDocumentValidatorSpec` (absent linkage with no hint allowed; `TO_MANY` allowed; null/single linkage and `TO_ONE` hint rejected) |
| Jackson 3 non-closing `OutputStream` bulk delegation | `jsonapi-java-jackson3`: `DocumentWriterSinkSpec` ("non-closing OutputStream delegates bulk writes to the caller sink") |
| Caller-owned sink/Javadoc contract | both adapters: `DocumentWriterSinkSpec` (output visible without an explicit call-level flush; caller-owned sinks stay open after emission failure; caller-created generator stays open after a successful write), `DocumentReaderSpec`/`DomainDocumentReaderSpec` (caller stream/parser open on success and failure), Level-1 resource specs (Jackson 2 `UncheckedIOException` boundary) |
| Stream-constraint diagnostic location fallback | both adapters: `DocumentReaderSpec` ("locationless malformed parser failure ... falls back to the parser cursor"); `DomainDocumentReaderSpec` keeps document-prefix composition |
