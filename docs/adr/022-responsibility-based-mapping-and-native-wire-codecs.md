# ADR-022: Share Mapping Semantics, Keep Native Wire Codecs

**Status:** Accepted
**Date:** 2026-09-24

## Context

Jackson 2 and Jackson 3 implement the same JSON:API operations, but duplicating mapping decisions
invites semantic drift. Sharing those rules does not require sharing Jackson's property model or the
mechanics of reading and writing JSON tokens. Adapter-local token codecs preserve observable
behavior that a tree-first or generic neutral JSON representation would have to reproduce:
duplicate-member classification, source locations and pointers, caller-owned parser sequencing,
unknown-member handling, and native scalar fidelity.

## Decision

- `jsonapi-java-core` remains the canonical document model and validation authority.
  `jsonapi-java-api` provides supported backend-neutral application contracts and values without
  Jackson-major production imports; it is not a standalone codec or mapping runtime.
- The published `jsonapi-java-mapping` artifact owns backend-neutral mapping policy and orchestration,
  including inclusion, resource read/write and decoration, typed-envelope binding, PATCH projections,
  recursive structured changes, Level-1 primary-data shapes, and mapping-definition invariants. Its
  `com.kazforge.jsonapi.mapping.internal` package is Java-public for cross-artifact cooperation only,
  not supported consumer API or an application extension SPI.
  Narrow internal backend capabilities supply native facts and operations without creating a
  general-purpose mapper, parser, or JSON intermediate representation.
- Each native-major adapter owns configured property/type authority, introspection, native
  conversion, construction, parser/generator integration, and wire decoding and emission. Jackson 2
  and Jackson 3 keep separately compiled token-driven codecs. Decode constructs and validates the
  core document before optional application binding; write mapping produces a core document that is
  validated before emission. Shared neutral rules do not normalize Jackson mechanics.
- There is no generic neutral JSON tree/IR codec, runtime-major detection, or supported Gson backend.
  A future backend would require its own explicit implementation and behavioral proof rather than
  being implied by the neutral API or the mapping artifact.

## Consequences

- Common JSON:API mapping decisions have one implementation while native configuration and wire
  behavior remain adapter-local. Unsupported mapping capabilities are implementation seams, not
  stable public signatures for consumers.
- The additional published artifact and the backend → mapping → API → core dependency edge are
  intentional. Adapters retain direct dependencies on API, annotations, and core.
- Some native codec code remains intentionally separate across majors; a shared JSON IR is not a
  prerequisite for semantic parity.
