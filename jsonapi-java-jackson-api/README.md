# jsonapi-java-jackson-api

Public Jackson-major-neutral API surface shared by Jackson 2, Jackson 3, and future framework
integrations.

## Packages

| Package                                         | Role                                                                 |
|-------------------------------------------------|----------------------------------------------------------------------|
| `io.github.kazemek.jsonapi.jackson.document`    | Major-neutral JSON:API document read/write contract values           |
| `io.github.kazemek.jsonapi.jackson.mapping`     | Application/domain mapping contracts                                 |
| `io.github.kazemek.jsonapi.jackson.patch`       | Major-neutral PATCH state and change contracts                       |
| `io.github.kazemek.jsonapi.jackson.representation` | Representation shaping and inclusion/fieldset contracts           |
| `io.github.kazemek.jsonapi.jackson.diagnostic`  | Stable mapping/codec diagnostics and failure locations               |
| `io.github.kazemek.jsonapi.jackson.api`         | Level-1 application operation contract: `JsonApi` root plus resources, relationships, documents, and patches facets with option/result values |

Conceptual layout:

```text
io.github.kazemek.jsonapi.jackson.api
    Level-1 application operations (JsonApi root plus
    resources/relationships/documents/patches facets)

io.github.kazemek.jsonapi.jackson.document
    document contracts

io.github.kazemek.jsonapi.jackson.mapping
    domain/mapping contracts

io.github.kazemek.jsonapi.jackson.patch
    PATCH contracts

io.github.kazemek.jsonapi.jackson.representation
    representation shaping

io.github.kazemek.jsonapi.jackson.diagnostic
    diagnostics
```

## Level-1 application contract

Ordinary application code uses the neutral operation contract in
`io.github.kazemek.jsonapi.jackson.api` rather than coordinating capability phases
directly:

```java
import io.github.kazemek.jsonapi.jackson.api.JsonApi;
import io.github.kazemek.jsonapi.jackson.api.ResourceWriteOptions;

JsonApi api = /* major-specific implementation, supplied separately, e.g. Jackson 3 */;
ArticleDto article = api.resources().readOne(json, ArticleDto.class);
String created = api.resources().writeCreateDocument(article);
String represented = api.resources()
    .writeOne(article, ResourceWriteOptions.defaults());
```

Level 1 is ordinary application operations; the major-specific document
readers/writers, resource mapper/binder, `JavaType` overloads, heterogeneous envelopes,
and low-level contexts remain the advanced mechanism/control seams. The contract is
client/server-neutral and models no Jackson mechanics. See
[ADR-019](../docs/adr/019-level-one-application-api-contract.md). This module defines the
contract only; the Jackson 3 implementation is the configured `Jackson3JsonApi` runtime in
`jsonapi-java-jackson3` (via `JsonApiJackson3.jsonApi`/`builder`).

## Minimal usage

This module has no standalone entry points. Consumers use it through a Jackson adapter:

```java
// Jackson 3 (or, later, Jackson 2) consumes the same neutral contracts:
import io.github.kazemek.jsonapi.jackson.document.DocumentReadContext;
import io.github.kazemek.jsonapi.jackson.representation.IncludePath;
import io.github.kazemek.jsonapi.jackson.representation.IncludePolicy;
import io.github.kazemek.jsonapi.jackson.representation.RepresentationPolicy;
import io.github.kazemek.jsonapi.jackson.representation.RepresentationSelection;
import io.github.kazemek.jsonapi.jackson.patch.PatchCommand;
import io.github.kazemek.jsonapi.jackson.patch.PatchPresence;

DocumentReadContext context = DocumentReadContext.resourceDefaults();
RepresentationSelection selection =
    RepresentationSelection.builder().include(IncludePath.of("comments.author")).build();
RepresentationPolicy policy =
    RepresentationPolicy.defaults().withIncludePolicy(IncludePolicy.allowAll());
PatchCommand<ArticleDto> command = /* from JsonApiJackson3.patchCommandReader(...).readValue(...) */;
PatchPresence<String> title = /* from an ArticlePatchDto member after patchDtoReader binding */;
```

Types here are values only: policies (`IncludePolicy`, `FieldPolicy`, allowance keys), read/write
contexts (`DocumentReadContext`, `DocumentEnvelope`,
`MappedDocument`), diagnostics (`MappingDiagnostic`, `CodecFailureCategory`,
`JsonApiMappingException`, `JsonApiDocumentReadException`, `SourceLocation`, `MappingLocation`),
identifier conversion (`IdentifierConverter`), representation values (`RepresentationSelection`,
`RepresentationPolicy`), domain envelope values (`DomainData`,
`IncludedResources`), decoration values (`ResourceDecorator`, `ResourceDecoration`,
`RelationshipDecoration`, `ResourceDecoratorRegistry`), and presence-aware update contracts
(`PatchCommand`, `PatchChange`, `PatchPresence`, `RelationshipLinkage`, `StructuredPatch`,
`StructuredMember`, `StructuredMemberState`). `PatchChange` sealed variants cover resource-meta and
relationship-meta changes per [ADR-015](../docs/adr/015-flat-whole-object-meta-mapping.md);
identifier meta is not a variant. Applications that need `ResourceIdentifier.meta` opt into
`RelationshipLinkage<T, M>`; identifier meta rides on whole-linkage `RelationshipChange` values
per [ADR-017](../docs/adr/017-resource-identifier-meta-mapping.md). No type in this API imports
or exposes `tools.jackson.*` or `com.fasterxml.jackson.*`; Jackson-bound factories, readers,
writers, binders, and mapping introspection stay in the major-specific adapter packages.

`RepresentationSelection` is per operation: it requests JSON:API wire-name include paths and sparse
fieldsets only. `RepresentationPolicy` is application/configuration scoped: it determines which
requested relationships and fields are permitted and bounds include traversal. Policy is not a
complete authorization system. Applications may reuse a selection as an input to persistence
projection planning, but jsonapi-java neither defines nor executes persistence projections.

`ResourceDecorator`/`ResourceDecoration`/`RelationshipDecoration` are application/runtime
decoration for domain writes: they add only `ResourceObject.links` and `Relationship.links` for
existing mapped relationships. Decoration is keyed by the mapped property identity (Jackson logical
name, e.g. `comments`), not the final wire name; the mapper follows configured-Jackson renaming
(e.g. `@JsonProperty("article-comments")`) automatically. Decoration never replaces type, id, lid,
attributes, linkage, meta, identifier meta, inclusion membership, or sparse-fieldset provenance, and
it never resurrects a fieldset-omitted relationship or creates a synthetic relationship. Resource
level links stay distinct from document-level `DocumentEnvelope.links` and from relationship links.
Register decorators through the mapper's immutable `ResourceDecoratorRegistry`; no annotation carries
decorator metadata.

## Test Fixtures

The `java-test-fixtures` variant contains passive, Jackson-major-neutral DTO carriers and the
canonical JSON:API corpus and pinned draft-schema resources used by adapter tests. The small
`io.github.kazemek.jsonapi.fixtures.TestFixtureResources` type only loads those classpath resources.
Behavioral cases, policy tables, diagnostics, and assertions remain owned by each adapter's local
specifications; this module does not provide shared test orchestration or scenario catalogs.

## Non-goals

This module does not share Jackson-bound readers, writers, mapping introspection, serializers,
binders, module registration, or mapper factories; there is no runtime major detection and no
lowest-common-denominator Jackson abstraction. Jackson 2 and Jackson 3 remain separately compiled
artifacts; see [ADR-007](../docs/adr/007-module-boundaries.md).

## Further reading

- [Architecture overview](../docs/architecture.md)
- [Conformance checklist](../docs/conformance.md)
- [ADR-004 — Jackson integration](../docs/adr/004-jackson-integration.md)
- [ADR-007 — Module boundaries](../docs/adr/007-module-boundaries.md)
- [ADR-009 — JSpecify nullness](../docs/adr/009-jspecify-nullness.md)
- [ADR-010 — Architectural tests](../docs/adr/010-architectural-tests.md)
- [ADR-015 — Flat whole-object mapping for resource-side meta](../docs/adr/015-flat-whole-object-meta-mapping.md)
- [ADR-016 — Mapper-instance construction for Jackson adapters](../docs/adr/016-jackson-adapter-construction.md)
- [ADR-017 — Opt-in RelationshipLinkage for resource identifier meta](../docs/adr/017-resource-identifier-meta-mapping.md)
- [ADR-019 — Major-neutral Level-1 application API contract](../docs/adr/019-level-one-application-api-contract.md)
- [Root agent workflow](../AGENTS.md)

## For contributors / agents

- **Jackson-free boundary:** Production code must not import `tools.jackson.*`,
  `com.fasterxml.jackson.*`, or any major-specific adapter package (`jackson2..`, `jackson3..`).
  ArchUnit enforces this via `JacksonApiDependencyRulesSpec` (ADR-010). Moved-type Javadocs
  must not `{@link}` Jackson-major-specific types; keep wording neutral.
- **IncludedResources invariant:** Assemble with `IncludedResources.of(resources,
  identitiesByPosition)` where each position declares the identities of its bound DTO. The index is
  derived from those declarations, so `find` can only return the DTO at the position that declared
  the identity: inconsistent states are unrepresentable. Duplicate identities across positions and
  length mismatches are rejected. Do not re-introduce a raw two-collection constructor.
- **Move policy:** Neutral contracts live here, not in the adapters. When a type can be expressed
  without Jackson imports, move it here rather than duplicating it per major; when it exposes
  Jackson APIs it must stay in the adapter. Adapter architecture tests derive their duplicate-name
  guard from this public package boundary, so newly moved public contracts are protected without a
  second type-name inventory.
- **Nullness:** Each concept package is `@NullMarked` (JSpecify only). Document/envelope/codec
  contracts: Java `null` means member absence; explicit JSON `null` stays a sealed variant
  (`DomainData.NullData`, etc.). Presence-aware PATCH: `PatchChange` entries in `changes()` are
  present; explicit attribute JSON `null` / relationship NullLinkage use `@Nullable value == null`
  (no sealed attribute-null variant). Direct PATCH DTO members declare presence through
  `PatchPresence`: `Present` with a `null` value is explicit null, never omission; nullable
  `Optional` stays a separate inner concern (`PatchPresence<Optional<T>>` is meaningful). Recursive
  structured attributes use `StructuredPatch` / `StructuredMember(wireName, logicalName)` /
  `StructuredMemberState` (`Atomic` / `Structured`) as the neutral requested-change payload
  (ADR-014); an empty `StructuredPatch` is a supplied empty structured object, never a clear-all.
  NullAway enforces this on Java `main` sources (ADR-009).
- **Diagnostics:** Three families stay distinct: core validation (`JsonApiValidationException`),
  codec/read (`JsonApiDocumentReadException`), and mapping (`JsonApiMappingException`). See
  [architecture](../docs/architecture.md) for the taxonomy and `JsonApiMappingException` for the
  mapping-location contract. Do not introduce new failure types without an implementation plan.
- **Tests:** Spock specs under `src/test/groovy/` mirror the main package layout; unit/contract
  tests of moved types live here, while Jackson-bound integration suites stay in the adapters.
  The repository enforces a fixed 80% JaCoCo line and branch coverage floor; coverage failures
  require meaningful tests rather than threshold tuning.
