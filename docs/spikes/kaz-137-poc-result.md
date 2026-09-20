# KAZ-137 / KAZ-138 PoC Result — Backend-Neutral API and Mapping Domain

> **Status:** PoC result, pending final architecture decision
> **Branch:** `poc/kaz-137-mapping-domain`
> **Draft PR:** #204
> **Parent:** KAZ-137
> **PoC subtask:** KAZ-138

## Executive summary

The PoC provides a positive architectural signal.

The current Jackson-centered module layout mixes three responsibilities that can be separated without
forcing Jackson 2 and Jackson 3 through a lowest-common-denominator compatibility layer:

1. public application/framework-facing JSON:API contracts;
2. JSON:API-specific application/domain mapping semantics;
3. concrete JSON-library mechanics and wire codecs.

A dedicated internal mapping domain can own representative write, read/bind, compound-inclusion and
sparse-fieldset semantics while concrete JSON-library integrations retain their native type model,
introspection, configured naming/conversion and construction behavior.

Jackson 2, Jackson 3 and a deliberately small Gson backend can all participate in this architecture
without a neutral abstraction that mirrors Jackson APIs.

The PoC therefore supports proceeding toward this responsibility-based architecture, subject to the
remaining production-scope work described below.

## Recommended target shape

    Application / Framework integrations
                    │
                    ▼
            jsonapi-java-api
           public neutral seam
                    │
                    ▼
          jsonapi-java-mapping
        internal JSON:API mapping domain
                    │
          ┌─────────┼─────────┐
          ▼         ▼         ▼
      jackson2   jackson3    gson?
          │         │         │
          └─────────┼─────────┘
                    ▼
           jsonapi-java-core
          canonical protocol model

### jsonapi-java-core

Own the canonical JSON:API model, invariants and aggregate protocol validation.

### jsonapi-java-api

Replace the historical role of `jsonapi-java-jackson-api` as the public neutral application seam.

Expected ownership includes:

- Level-1 `JsonApi` application operations;
- document operation contracts;
- public mapping configuration such as identifier conversion, decorators and registries;
- PATCH value/contracts;
- representation selection/policy;
- stable mapping/document diagnostics.

Framework integrations such as Spring should depend on this neutral seam rather than a concrete JSON
library.

### jsonapi-java-mapping

Internal implementation artifact, consumed through `implementation(project(...))`.

Expected ownership includes:

- mapping/binding definitions and roles;
- application/domain → JSON:API core orchestration;
- JSON:API core → application/domain binding orchestration;
- relationship/linkage semantics that are genuinely JSON:API-level;
- compound inclusion;
- sparse fieldsets and linkage exemptions;
- PATCH mapping semantics where backend-independent;
- mapping diagnostics/orchestration helpers.

The module is not intended as a public extension API. Backend capability interfaces should remain
internal unless a concrete external-extension requirement emerges.

### Concrete JSON-library modules

Jackson 2, Jackson 3 and any future Gson backend are separate JSON-library integrations.

They own:

- native type tokens and property handles;
- serialization/deserialization introspection;
- effective external names and visibility;
- native configured value conversion;
- object construction;
- parser/generator/tree mechanics;
- backend-specific advanced API;
- wire codec strategy.

The architecture does not require each backend to use the same wire strategy.

## Important design findings

### Jackson 2 and Jackson 3 are best modeled as separate JSON-library backends

The architecture remains useful if Jackson 2 disappears. Shared code exists because it models
jsonapi-java semantics, not because two Jackson majors need normalization.

This passes the future-Jackson-3-only fitness test.

### Write-side and read-side metadata should remain distinct

The existing Jackson implementation already has different authorities for serialization and
deserialization.

The PoC therefore uses separate neutral concepts for write mapping and read/binding mapping rather
than pretending one property model can always serve both directions.

This preserves real backend capabilities such as creator-only, setter-only and write-only
deserialization properties.

### A mapped property needs three names

The PoC exposed an important distinction:

1. logical/application property identity;
2. JSON-library external name used for native construction/conversion;
3. JSON:API member name.

These can differ. For example, a JSON-library annotation may rename an identifier property while the
JSON:API member remains `id`.

The PoC now makes the identity invariant explicit: `MappingRole.ID` must use JSON:API member
`id`, and `MappingRole.LOCAL_ID` must use `lid`. A backend-resolved name such as
`@JsonProperty("blog_id")` or `@SerializedName("blog_id")` remains separate backend metadata.

This distinction belongs in the neutral mapping model even though the external/backend name is
produced by the concrete backend.

### Compound inclusion and sparse fieldsets are mapping-domain semantics

They do not require Jackson concepts.

The shared inclusion engine owns include traversal, identity/visit handling, included ordering,
policy/depth/count checks and sparse-fieldset linkage exemptions.

Concrete backends only supply related type/value access, identity and resource rendering.

### Object construction remains backend-specific

The shared Core → domain binder owns JSON:API role/presence semantics and builds backend-facing
property input.

Jackson remains authoritative for Jackson construction and configured deserialization behavior.
Gson can use Gson construction instead.

This avoids building a generic bean construction framework inside jsonapi-java.

## Representative shared contract

The PoC introduces black-box contract tests rather than coupling regression protection to the
prototype SPI.

The write contract covers representative behavior including:

- resource type;
- id and local-id identity;
- scalar attributes;
- to-one relationship linkage;
- to-many relationship linkage;
- compound inclusion;
- sparse-fieldset relationship omission with included-resource linkage exemption.

The read/binding contract covers representative behavior including:

- id and local-id binding;
- attribute binding;
- to-one and to-many linkage;
- explicit null relationship linkage;
- backend-controlled final object construction.

Both the existing Jackson public implementations and the new shared mapping-domain slices are tested
against the same observable contract where applicable.

The existing full Jackson suites remain the broader behavioral safety net.

## Gson result

The Gson experiment should no longer be viewed only as disposable proof code.

It provides useful evidence for two separate claims:

1. the mapping architecture is not secretly Jackson-shaped;
2. a backend may use a different wire implementation strategy.

The PoC uses:

- `java.lang.reflect.Type` / reflection-based metadata rather than Jackson `JavaType`;
- Gson-specific `@SerializedName` handling at the backend edge;
- the same shared domain→core writer;
- the same shared core→domain binder;
- a deliberately small Gson tree-based JSON↔core codec.

The shared mapping contract also covers structured/open values containing nested JSON nulls, for
example array values such as `["one", null, "two"]` and object members such as
`{"street": null}`. The Gson prototype preserves those nulls rather than using immutable-copy
helpers that reject null elements or values.

The Gson PoC does **not** establish production support parity.

An independent review identified a concrete null-open-value bug in the first Gson prototype:
`List.copyOf` / `Map.copyOf` rejected valid nested JSON nulls. The PoC now preserves nested null
values and the shared mapping contract explicitly covers a nullable array/list element and nullable
object member across backends. This is useful evidence that the backend contract can catch real
cross-library semantic drift.

A separate product-scope evaluation should decide whether a small supported Gson module is worthwhile.
Likely questions include `FieldNamingStrategy`, `ExclusionStrategy`, `@Expose`,
`@SerializedName.alternate`, TypeAdapter/custom conversion behavior, generic typing, polymorphism,
diagnostic expectations, JSON:API wire coverage and maintenance cost.

The architecture does not require a Gson backend to match Jackson-only advanced parser capabilities.

## Jackson wire-codec result

The PoC does not provide evidence that token-driven parsing is a problem.

That concern should be reassessed only after production mapping extraction reduces the Jackson
modules to genuinely backend-specific code.

The current token-driven codecs provide observable capabilities that a simple tree rewrite could
lose or weaken:

- duplicate-member detection;
- precise malformed-input categories;
- semantic JSON Pointer plus physical source location;
- caller-owned parser/stream behavior;
- sequential root parsing;
- explicit numeric/open-value handling;
- precise unknown-member policy.

**Provisional recommendation:** retain the current Jackson token codecs unless the later
post-extraction reassessment finds a concrete maintenance or correctness problem.

Gson demonstrates that a different backend can legitimately choose a tree codec instead.

## Public API artifact finding

`jsonapi-java-jackson-api` is already mechanically Jackson-free in production dependencies.

The PoC therefore supports treating it as the historical precursor to a real neutral
`jsonapi-java-api` artifact rather than keeping both artifacts long-term.

Strong move/rename candidates are the current public package families for:

- application API;
- document contracts;
- public mapping contracts;
- PATCH contracts;
- representation contracts;
- diagnostics.

Internal mapping helpers should move to `jsonapi-java-mapping`, while wire-specific helpers should
remain with the concrete codec until their ownership is separately decided.

The PoC intentionally does not perform the large mechanical package/artifact rename because that does
not add architectural evidence and would make the exploratory branch noisier.

## Current implementation signal

The shared mapping module is now a substantial semantic slice rather than one extracted helper.

It contains shared implementations for representative:

- domain→core resource writing;
- core→domain binding;
- compound inclusion;
- sparse-fieldset behavior;
- supporting neutral definitions/capabilities;
- an exploratory low-level PATCH mapping API sketch.

The current backend bridges are much smaller than the existing duplicated Jackson
`DomainResourceWriter` implementations, but source reduction alone is not the decision criterion.
The stronger result is that jsonapi-java mapping semantics now have one owner and backend mechanics
remain native.

## What the PoC has not proven

The following must not be inferred from the successful representative slices:

- complete production extraction of `DomainResourceWriter` / `DomainResourceBinder`;
- full resource/relationship/identifier meta support in the neutral engine;
- all `RelationshipLinkage` custom-mapper semantics;
- all Optional/array/set/map/container combinations;
- full structured-attribute behavior;
- complete polymorphic/generic model behavior;
- low-level PATCH architecture beyond a preliminary API/seam sketch;
- complete PATCH / structured PATCH extraction;
- identical diagnostics for every construction edge case;
- production-ready Gson support;
- a decision to replace Jackson token parsing.

The PATCH prototype is intentionally weaker evidence than writer/binder/inclusion: it has no shared
cross-backend characterization contract and currently reuses write-side `MappingDefinition`.
Production PATCH work should therefore be treated as a later, separate extraction and should evaluate
read/inbound or dedicated patch metadata before stabilizing any SPI.

PATCH deserves particular caution: production PATCH includes presence semantics, recursively
structured values, property-scoped conversion, whole/resource/relationship meta, custom linkage and
diagnostic behavior. Its eventual metadata model should be derived from inbound/read requirements or
a dedicated patch view rather than assuming the write-side `MappingDefinition` is authoritative.

These are follow-up implementation/contract risks, not reasons to reject the architecture.

## Test-surface conclusion

The existing repository test surface is strong enough to perform the production refactor
incrementally:

- 80% line and branch coverage gates;
- extensive Jackson 2/3 mapping/binding suites;
- shared JSON:API conformance/negative wire corpus;
- PATCH fixtures;
- inclusion/sparse-fieldset coverage;
- architecture rules;
- Java 21 and Java 25 CI.

The new black-box backend contracts should be expanded during production extraction around each
moved semantic slice rather than trying to recreate the entire existing suite in advance.

The independent review reached the same overall conclusion: accept the architecture direction, but
do not freeze the PoC capability interfaces as final SPIs. The hardest remaining proof areas are
RelationshipLinkage/custom linkage, resource/relationship/identifier meta, construction diagnostics,
structured values, and PATCH/structured PATCH.


## Independent review follow-up

An independent review of `main`, the PoC branch, and draft PR #204 reached the same architectural
conclusion with important constraints:

- accept the responsibility-based architecture direction, not the current PoC interfaces as a final SPI;
- keep write-side and read-side metadata distinct while allowing a shared semantic property identity;
- preserve the explicit logical/backend/JSON:API naming distinction;
- treat PATCH as the largest remaining architecture proof and extract it later behind its own contracts;
- keep Jackson token codecs unchanged during the mapping refactor;
- treat Gson as a plausible separately scoped product backend, not as a prerequisite for the core refactor;
- expand characterization/backend contracts per extracted responsibility during production migration.

These findings have been folded into the PoC rather than merely recorded: identity-member naming is
now enforced, null-containing structured open values are covered by the shared mapping contract, and
the unvalidated executable PATCH sketch has been removed.

## Proposed production work if KAZ-137 is accepted

The PoC branch should not be merged wholesale.

Production work should be split into dedicated changes, approximately:

1. establish/expand shared mapping and binding contract fixtures;
2. create/rename the neutral `jsonapi-java-api` artifact and packages;
3. create the internal `jsonapi-java-mapping` artifact with final package ownership;
4. extract write-side mapping definitions and resource writing incrementally;
5. extract read-side binding definitions and binding orchestration incrementally;
6. extract inclusion/sparse-fieldset/PATCH/meta/linkage semantics by responsibility;
7. reduce Jackson 2/3 modules to native JSON-library integrations;
8. reassess Jackson wire codec only after the mapping extraction;
9. decide Gson product scope and, if positive, implement it as separate dedicated work;
10. record the accepted design in a new ADR and update `docs/architecture.md` when implementation
    matches it.

## Provisional verdict

**Positive.**

The responsibility-based API / mapping-domain / JSON-library-backend architecture is sufficiently
validated by the PoC to justify production implementation planning.

The strongest evidence is not code deduplication. It is that three materially different backends can
share JSON:API mapping semantics without the shared layer exposing Jackson-native concepts, while
Jackson-specific introspection/conversion and backend-specific wire strategies remain intact.

Final acceptance belongs to KAZ-137/KAZ-143 after reviewing the PoC result and defining the production
ticket breakdown.
