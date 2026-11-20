# ADR-017: Exact Successful-Binding Source Attribution

**Status:** Accepted
**Date:** 2026-11-16

## Context

After binding a create or typed PATCH DTO, applications may need to attribute a logical Java
property path to the request. DTO values cannot establish wire presence: defaults, null handling,
custom conversions, and later mutation may change them. Construction-failure diagnostics are
best-effort explanations and can name absent creator inputs; they are not proof that a value was supplied.

Configured Jackson already owns the effective names, targets, conversion, and structured shapes.
A second mapping service would duplicate that authority and would not establish request-local presence.

## Decision

Offer opt-in operations on the existing resource and PATCH facets, preserving DTO-only operations.
Return `BoundResource<T>` with two methods: the bound `resource()` and `source(List<String>)`.
No public lookup service, path grammar, mapping registry, callback factory, or native metadata is needed.
`BindingSource` carries a four-state presence and a `MappingLocation` only for present states.

Paths contain raw logical property names and, where proven, canonical decimal array/list indices.
An empty path denotes `/data`. Presence/optional wrappers are transparent; adapting a validator path
must use node/type information rather than dropping arbitrary properties named `value`.

Attribution is exact-only:

- Present null and present non-null describe wire state, independently of conversion.
- Known omission means a bindable mapped member is absent from its supplied containing object.
- Unknown, ambiguous, opaque, and unsupported correspondence is unresolved. Descendants of an
  omitted/null parent are unresolved; the parent itself remains queryable.
- Locations are document-relative, escaped, and address actually supplied values. A failed lookup
  never silently falls back to an ancestor.

Capture a compact immutable snapshot from the operation's existing resolved definition and effective
deserialization shapes after successful binding. Traverse supplied mapped structures, not DTO graphs
or arbitrary omitted type graphs. Retain names, presence and pointers only. Binding and conversion
occur once; binding an already-validated PATCH document does not parse or validate again.

Ordinary beans and proven stock ordered containers can be traversed for attribution even when PATCH
treats their value as an atomic replacement. Custom conversion and unsupported container correspondence
remain opaque. Relationships address linkage only; direct identifier and wrapper occurrence/meta
locations follow the existing relationship binder, never included-resource hydration.

New facet methods have explicit unsupported defaults for external implementors. The Jackson runtime
overrides them; a default must not bind a DTO and pretend that empty/unresolved metadata is supported
attribution.

## Consequences

- Framework integrations can consume neutral request-local attribution without Jackson internals,
  Spring, HTTP, or Jakarta Validation dependencies in the library.
- DTO-only operations do not collect sources. Source lookup does not perform conversion and is
  independent of later DTO mutation.
- Exactness intentionally takes priority over coverage of aliases, custom transformations, maps,
  and unproven positional correspondence. Applications may explicitly query a meaningful ancestor.
- Input omission is not update policy: a missing nested property of an atomic replacement may still
  need full-object validation. Recursive presence-aware semantics remain those of
  [ADR-010](010-resource-patch-binding.md).
- Existing document-read, validation, and mapping failures keep their families, phase precedence,
  and diagnostic coordinates. Reusing `MappingLocation` does not make diagnostics exact provenance.
- Public guide examples ship with the software release that adds the API, not as a documentation
  revision of an older release.
