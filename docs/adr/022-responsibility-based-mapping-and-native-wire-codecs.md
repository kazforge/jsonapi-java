# ADR-022: Share Mapping Semantics, Keep Native Wire Codecs

**Status:** Accepted
**Date:** 2026-09-24

## Context

Jackson 2 and Jackson 3 implement the same JSON:API operations, but duplicating inclusion,
resource-write/read, and PATCH orchestration invites semantic drift. Sharing those rules does not
require sharing Jackson's property model or the mechanics of reading and writing JSON tokens. The
post-extraction wire review found that the adapter-local token codecs preserve observable behavior
that a tree-first or generic neutral JSON representation would have to reproduce: duplicate-member
classification, source locations and pointers, caller-owned parser sequencing, unknown-member
handling, and native scalar fidelity.

## Decision

- `jsonapi-java-core` remains the canonical document model and validation authority.
  `jsonapi-java-api` provides supported backend-neutral application contracts and values without
  Jackson-major production imports; it is not a standalone codec or mapping runtime.
- The published `jsonapi-java-mapping` artifact owns backend-neutral inclusion, resource-write and
  resource-read, decoration, low-level and typed PATCH, and structured-value orchestration, plus
  semantic mapping metadata. Its `com.kazforge.jsonapi.mapping.internal` package is Java-public for
  cross-artifact cooperation only, not supported consumer API or an application extension SPI.
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

## Relation to earlier decisions

This decision partially supersedes [ADR-019](019-jackson-neutral-implementation-helpers.md): its
no-new-artifact, unchanged-dependency-direction, and adapter-owned inclusion, read, write, and PATCH
orchestration conclusions no longer describe production. Its unsupported internal-helper boundary
and adapter ownership of native Jackson mechanics still apply. [ADR-004](004-jackson-integration.md)
retains configured-Jackson property authority and explicit document codecs;
[ADR-018](018-level-one-application-api-contract.md) retains the Level-1 neutral contract; and
[ADR-005](005-domain-mapping-and-inclusion.md) retains the separation of linkage from inclusion.
[ADR-007](007-module-boundaries.md) identifies the physical modules; this record explains their
current responsibility split and the retained wire-codec choice.
