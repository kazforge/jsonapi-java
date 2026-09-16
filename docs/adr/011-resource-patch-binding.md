# ADR-011: Resource PATCH Produces Presence-Aware Commands

**Status:** Accepted  
**Date:** 2026-07-30

## Context

JSON:API resource updates use HTTP `PATCH`, but they are not JSON Merge Patch (RFC 7386). The
request primary data must be one resource object with `type` and `id`. Omitted attributes and
relationships retain their current values, while a supplied relationship must contain `data` and
replaces the relationship linkage.

A normal DTO instance cannot reliably represent this contract. An omitted property and an
explicit JSON `null` often both become Java `null`, and immutable records may require constructor
values for properties absent from the update. Applying changes directly would also make the
library responsible for authorization, mutation, persistence, and application invariants.

## Decision

Add a core update-request validation usage and an optional Jackson domain-binding layer that
produces an immutable presence-aware patch command parameterized by the annotated DTO type.

The core update contract:

- requires `data` to be a single resource object with non-null `type` and `id`;
- rejects absent data, `data: null`, resource collections, and resource-identifier primary data;
- treats omitted `attributes` and `relationships` as no requested changes;
- preserves missing attribute keys versus present keys whose value is JSON `null`;
- requires every supplied relationship object to contain `data`;
- preserves explicit null, single, empty collection, and non-empty collection relationship
  linkage; and
- can compare the document resource identity with an expected endpoint identity supplied by the
  caller.

Jackson binding uses ADR-010 mapping definitions to convert only supplied attributes and
relationship linkage into typed property changes. The command exposes presence explicitly and
does not construct a complete DTO, resolve `included`, or mutate an existing object.

Resource links, metadata, extension/profile members, and included resources remain available
through the validated/domain envelope but are not patchable DTO properties in the initial
contract. Applications own authorization, business validation, relationship mutation, and
application of the command.

## Consequences

- Omitted values cannot accidentally clear existing state.
- Explicit attribute null and null/empty relationship replacement remain observable.
- Record and immutable DTO mappings do not require fabricated constructor defaults.
- Endpoint adapters can reject body/URL identity mismatches before application logic.
- Applications must deliberately translate an accepted command into domain mutations.
- Supporting direct object mutation or additional patchable member classes would require a later
  decision and implementation plan.
