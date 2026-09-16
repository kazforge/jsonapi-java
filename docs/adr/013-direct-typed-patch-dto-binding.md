# ADR-013: Direct Typed PATCH DTO Binding

**Status:** Accepted
**Date:** 2026-08-17

## Context

The low-level `PatchCommand` from [ADR-012](012-resource-patch-binding.md) preserves supplied-member
state, but applications that prefer an annotated DTO otherwise need to project that command and
restate omission semantics. A normal read/write DTO cannot reliably distinguish an omitted member
from explicit JSON `null`, especially for immutable construction.

## Decision

Provide an opt-in typed PATCH DTO path in both Jackson adapters. It validates a resource-update
document and binds directly to an application-owned annotated shape whose patchable attributes,
relationships, and supported meta properties are exactly `PatchPresence<T>`.

`PatchPresence` is a neutral tri-state: `Omitted`, `Present(value)`, and `Present(null)`. An inner
`Optional<T>` remains a value-conversion concern, so `PatchPresence<Optional<T>>` does not collapse
omission into explicit null.

The PATCH DTO is the binding schema; it is not inferred from a normal DTO. Identity uses the normal
explicit or conventional `id` mapping, comes from resource `id` without `lid` fallback, and is not
wrapped in `PatchPresence`. Unannotated ordinary properties do not participate.

The typed path rejects unknown supplied members. This intentionally differs from the low-level path,
which skips unmapped members while retaining the supplied changes it can represent.

Configured Jackson remains authoritative for DTO construction, naming, creators, and inner-value
conversion. Wrapper-level Jackson customization that would replace the `PatchPresence` machinery is
an invalid declaration; inner-type customization remains supported. Adapter-internal marker and
deserializer mechanics are not part of the neutral contract.

The low-level `PatchCommand` path remains available. Applications authorize and apply either result;
the library only validates, converts, and binds.

## Consequences

- Immutable application PATCH shapes can preserve omitted, null, and supplied values without a
  command-to-DTO projector.
- PATCH DTOs are distinct from normal read/write DTOs; one shape is not supported for both roles.
- Strict unknown-member handling is the safer default when there is no lossless intermediate.
- Both Jackson majors implement the same neutral presence contract with native-major mechanisms.
