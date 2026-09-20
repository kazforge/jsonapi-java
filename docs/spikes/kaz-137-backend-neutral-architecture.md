# KAZ-137 Architecture Draft — Backend-Neutral API and Mapping Domain

> **Status:** Exploration
> **Decision:** Not yet accepted
> **Linear:** KAZ-137
> **PoC branch:** poc/kaz-137-mapping-domain

This document records the current architectural hypothesis and the evidence collected during KAZ-137.
It is intentionally not an ADR and does not describe the implemented architecture on main.
docs/architecture.md remains the authority for the current system.

## Problem statement

The repository currently has a historical split centered on Jackson:

    core
      ↑
    jackson-api
      ↑
      ├─ jackson2
      └─ jackson3

That split solved useful duplication problems, but responsibility analysis shows three different
concerns mixed together:

1. application/framework-facing JSON:API operations and values;
2. application/domain-model ↔ JSON:API mapping semantics;
3. concrete JSON-library mechanics and wire codecs.

The strongest current signal is not that token-driven parsing is inherently too complex. It is that
mapping and application semantics are duplicated or named as Jackson concerns even though they are
not JSON-library-specific.

## Architectural hypothesis

Reorganize around responsibilities rather than Jackson-major lineage:

    Application / Framework
             │
             ▼
      jsonapi-java-api
     public neutral seam
             │
             ▼
    jsonapi-java-mapping
   internal mapping domain
             │
      ┌──────┼──────┐
      ▼      ▼      ▼
  jackson2 jackson3 gson?
      │      │      │
      └──────┼──────┘
             ▼
    jsonapi-java-core
   canonical protocol model

Jackson 2 and Jackson 3 are treated as separate concrete JSON-library integrations. Their common
history does not define the architecture.

## Layer responsibilities

### jsonapi-java-core

Owns protocol representation and validation:

- immutable JSON:API document/core model;
- local construction invariants;
- aggregate validation;
- protocol member/value semantics.

It remains independent of JSON libraries, mapping engines, and frameworks.

### jsonapi-java-api

Public application/framework-facing contract.

Candidate responsibilities currently living in jsonapi-java-jackson-api:

- Level-1 JsonApi operations and result/options values;
- document operation contracts such as DocumentReadContext and PrimaryDataKind;
- public mapping configuration/contracts such as identifier conversion, resource type registration,
  decorators and linkage values;
- public PATCH values/contracts;
- representation selection and policy;
- stable document-read and mapping diagnostics.

This becomes the primary seam for framework integrations such as Spring MVC. Framework modules must
not require Jackson 2/3 native types for ordinary operation.

### jsonapi-java-mapping

Internal implementation artifact.

Candidate responsibilities:

- neutral mapping definition/model;
- property roles and mapping orchestration;
- domain resource writing and binding;
- relationship/linkage mapping semantics;
- compound inclusion;
- sparse-fieldset mapping behavior and provenance;
- PATCH mapping/binding semantics;
- identifier/meta mapping helpers;
- narrow backend capability boundary.

It is consumed by concrete JSON-library modules through implementation(project(...)) and is not a
supported consumer API.

### Concrete JSON-library modules

Examples:

- jsonapi-java-jackson2
- jsonapi-java-jackson3
- experimental jsonapi-java-gson-poc

They own mechanics genuinely specific to a JSON library:

- native type model and introspection;
- configured property naming/visibility;
- custom serializer/deserializer or adapter behavior;
- configured value conversion;
- parser/generator/tree mechanics;
- wire codec;
- JSON-library-specific advanced APIs.

The concrete backend may internally use tokens, trees, reflection, adapters, or another mechanism.
The neutral architecture does not prescribe one wire implementation strategy.

## Current jsonapi-java-jackson-api classification

Repository inspection shows that its production dependency is only jsonapi-java-core; it has no
Jackson runtime dependency. That makes it effectively a neutral API artifact already, with historical
Jackson naming.

### Strong candidates for neutral public API

    com.kazforge.jsonapi.jackson.api
    com.kazforge.jsonapi.jackson.document
    com.kazforge.jsonapi.jackson.mapping
    com.kazforge.jsonapi.jackson.patch
    com.kazforge.jsonapi.jackson.representation
    com.kazforge.jsonapi.jackson.diagnostic

Likely target package families:

    com.kazforge.jsonapi.api
    com.kazforge.jsonapi.document
    com.kazforge.jsonapi.mapping
    com.kazforge.jsonapi.patch
    com.kazforge.jsonapi.representation
    com.kazforge.jsonapi.diagnostic

Exact package placement remains a design decision; this document records ownership rather than
freezing names.

### Strong candidates for neutral internal mapping implementation

Current Jackson-neutral internal helpers:

    IdentifierMetaSupport
    PropertyRole
    ResourceTypeMatch
    PresenceMarker
    CompoundInclusionState
    EffectiveRepresentation
    IncludedResourcesResult

These fit naturally beside the PoC's:

    GenericCompoundInclusionEngine
    InclusionMappingBackend

inside the internal mapping artifact.

### Do not move blindly

The current neutral wire helpers:

    JsonPointerAccumulator
    MemberClassifier
    PointerEscapes
    ReadLocationIndex
    ValidationPointers

are not mapping-domain responsibilities. Their future ownership depends on the wire-codec decision.
They should not be moved into jsonapi-java-mapping merely because they are Jackson-free.

## Mapping vs wire are independent axes

The spike intentionally separates:

    Mapping axis
    Application/Domain Model ↔ JSON:API Core

    Wire axis
    JSON ↔ JSON:API Core

A clean mapping architecture does not require tree-based wire decoding.

### Current wire hypothesis

Token-driven Jackson codecs may be justified backend-local complexity if they continue to provide
meaningful observable capabilities:

- duplicate-member detection;
- precise malformed-input categories;
- semantic JSON Pointer plus physical source location;
- caller-owned parser/stream behavior;
- sequential root-value reading;
- numeric/open-value fidelity;
- explicit unknown-member handling.

The correct evaluation point is after mapping/application semantics have been removed from the
Jackson adapters. The relevant question then becomes whether the remaining codec complexity pays for
those capabilities.

## Backend abstraction rule

Do not build a UniversalJsonMapper or a lowest-common-denominator copy of Jackson/Gson APIs.

The mapping layer should request only capabilities it actually needs. Candidate capability categories
include:

- resolve effective domain type;
- discover effective JSON-facing properties;
- obtain logical and external names;
- obtain property type/cardinality metadata;
- read/write property values;
- convert a value using the configured JSON-library runtime;
- construct/bind values where required.

Concrete interface shapes must be derived from real extraction call sites, not designed upfront.

## Architecture fitness tests

Every proposed abstraction must pass two tests.

### Future Jackson-3-only test

If Jackson 2 were deleted tomorrow, would the API/mapping split still be simpler and conceptually
valuable?

If not, the abstraction is probably only version-normalization machinery and should be rejected.

### Alternative-backend test

Can a deliberately small non-Jackson backend implement a representative slice without exposing
Jackson concepts under neutral names?

Gson is useful here because it has materially different metadata/conversion mechanics.

This is an architectural test, not a support commitment.

## Test strategy

The current repository already has strong regression protection:

- 80% minimum line and branch coverage;
- module/package ArchUnit rules;
- shared JSON:API v1.1 corpus;
- negative wire corpus;
- PATCH corpus;
- compound inclusion and sparse fieldset tests;
- mapping/binding/decoration/meta tests;
- Java 21 and Java 25 CI;
- draft schema cross-checks.

On main, Jackson 2 currently has 42 Spock specs and Jackson 3 has 36; 26 spec names are shared
between both modules. This is a strong behavioral safety net, but most mapping behavior is still
proven through concrete Jackson implementations rather than through an explicit reusable backend
contract.

The spike therefore adds a small black-box mapping contract before attempting the larger extraction.
That contract deliberately freezes observable behavior rather than any proposed SPI shape.

### Mapping contract suite

Representative behavior should be testable independent of a concrete backend:

    Domain/Application Model ↔ JSON:API Core

Initial vertical slice:

- resource type;
- id/lid identity;
- scalar attribute;
- external/renamed member;
- to-one relationship;
- to-many relationship;
- compound inclusion;
- configured conversion boundary;
- one presence-aware PATCH case if practical.

Jackson 2 and Jackson 3 now run the same representative contract on the PoC branch. A test-only
Gson module runs the same contract as an alternative-backend fitness test. The contract currently
covers resource type, identity, scalar attributes, to-one linkage, to-many linkage, and compound
inclusion.

Backend-specific naming remains an integration concern: the Gson PoC separately proves
SerializedName handling, while the existing Jackson test suite continues to prove JsonProperty and
configured Jackson naming behavior.

### Wire contract suite

The existing shared corpus already provides much of:

    JSON ↔ JSON:API Core

Future refactoring should make this contract more explicit without forcing every backend to expose
identical advanced/native capabilities.

## PoC evidence so far

The first experiment extracted compound inclusion.

Before:

- Jackson 2 CompoundInclusionEngine: ~18.2 KB
- Jackson 3 CompoundInclusionEngine: ~18.2 KB
- ~36.4 KB combined duplicated concern

PoC:

- one shared inclusion engine;
- one shared narrow backend contract;
- Jackson 2/3 bridge implementations;
- thin compatibility facades;
- dedicated jsonapi-java-mapping module;
- architecture rule preventing Jackson 2/3 dependencies from entering the mapping module;
- fake non-Jackson type-token test.

The immediate source reduction is modest. The more important result is that JSON:API inclusion
traversal now has one semantic owner and Jackson-specific mechanics are concentrated at the edge.

See docs/spikes/kaz-137-mapping-domain-poc.md for the focused experiment.

The branch also contains a test-only jsonapi-java-gson-poc module. It has no production sources,
no publish plugin, and no release commitment. It depends on Gson only for the spike and is guarded
against concrete Jackson 2/3 library and adapter dependencies. It demonstrates that the shared
compound-inclusion engine accepts a java.lang.reflect.Type based backend and that Gson-specific
SerializedName handling can stay at the backend edge.

One transitional smell remains visible by design: the neutral representation/diagnostic contracts
still live under the historically named jsonapi-java-jackson-api artifact/packages. The Gson proof
can use them because they are mechanically Jackson-free, but the target architecture should rename
or relocate that public surface before considering a non-Jackson backend production-ready.

## Proposed Gson fitness-test scope

Create an unpublished spike-only module such as:

    jsonapi-java-gson-poc

Target only a representative vertical slice:

    Article
      id
      title
      author
      comments relationship
            │
            ▼
    Gson backend mechanics
            │
            ▼
    shared mapping domain
            │
            ▼
    ResourceObject

And, if cheap enough:

    ResourceObject ↔ Gson JSON

Required proof:

- no Jackson dependency;
- no Jackson-shaped neutral contracts;
- one renamed/external property mechanism;
- scalar attribute;
- to-one and to-many relationship;
- compound include;
- same shared mapping-contract fixture where applicable.

Explicitly out of scope:

- full Gson product API;
- full TypeAdapter compatibility matrix;
- complete PATCH surface;
- heterogeneous envelopes;
- complete diagnostics parity;
- publication/support commitment.

## Migration hypothesis if accepted

A production migration would likely be incremental:

1. establish shared contract tests;
2. rename/extract neutral API artifact and packages;
3. move neutral internal helpers into mapping;
4. extract neutral mapping definitions;
5. move one mapping vertical slice at a time behind the backend capability boundary;
6. reduce Jackson 2/3 modules to concrete JSON-library mechanics + advanced APIs;
7. reassess token/tree wire implementation with the smaller adapter surface;
8. record the accepted design in a new ADR;
9. update docs/architecture.md only when the implementation matches it.

No big-bang rewrite is required or desirable.

## ADR implications

If accepted, a new ADR should supersede affected parts of:

- ADR-004 — Jackson Introspection Is Authoritative
- ADR-007 — Optional Adapter Modules
- ADR-018 — Major-Neutral Level-1 Application API Contract
- ADR-019 — Jackson-Neutral Implementation Helpers

Older ADRs should remain as history.

## Open questions

- Should jsonapi-java-api be the actual artifact name, or should the public neutral surface live in
  another existing/new coordinate?
- Which current mapping contracts are genuinely public versus historical exposure?
- Should backend capability interfaces remain entirely internal even if future JSON-library modules
  are added?
- How should backend-specific external property naming interact with JSON:API annotations in a
  library-neutral description?
- Can a neutral MappingDefinition cover structured values and polymorphism without becoming a
  generic serialization framework?
- Which mapping diagnostics belong in the public API and which should become implementation detail?
- Can the query module depend on jsonapi-java-api instead of today's Jackson-named representation
  contracts without changing behavior?
- What is the smallest useful Gson proof?
- After mapping extraction, does the token-driven Jackson codec still represent material maintenance
  cost?
- Are semantic-error source locations a product differentiator worth preserving?


## PoC result

The representative implementation result is documented in
`docs/spikes/kaz-137-poc-result.md`.

The current PoC is positive: shared write and read/binding slices, inclusion and sparse-fieldset
semantics work behind backend capability boundaries for Jackson 2, Jackson 3, and a small Gson
backend. The PoC also identified that write-side and read-side metadata should remain distinct and
that neutral property metadata needs separate logical, JSON-library-external, and JSON:API names.

The final architecture decision is intentionally deferred to KAZ-137/KAZ-143; the PoC branch is
evidence and is not intended to be merged wholesale.
