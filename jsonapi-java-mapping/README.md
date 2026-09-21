# jsonapi-java-mapping

Internal cross-artifact mapping implementation namespace for backend runtimes. This module is
published on the unified release train so backend runtime implementations can consume it, but it
is not supported consumer API.

The [`com.kazforge.jsonapi.mapping.internal`](src/main/java/com/kazforge/jsonapi/mapping/internal/package-info.java)
package owns backend-neutral semantics for mapped resources:

- **Compound inclusion.** Include-path validation, breadth-first traversal of intermediate and
  nested resources, identity aliasing, first-encounter order, included deduplication and conflict
  checks, traversal limits, and sparse-fieldset linkage exemptions, plus the neutral
  per-invocation inclusion state and effective-representation fieldset lookup used by selective
  rendering and traversal alike.
- **Basic resource write orchestration.** Fieldset validation and filtering, strict versus
  create-request identity rules, empty-member omission, attribute and member naming, ordinary
  domain-object to-one/to-many identifier construction, relationship `data` construction, and base
  `ResourceObject` assembly. Resource and relationship meta, decoration, and the advanced direct
  and wrapper relationship forms stay with each backend and are applied around this writer.
- **Mapping roles and naming metadata.** The backend-neutral mapping roles and the per-property
  semantic metadata that adapters compose into their own write and read mapping records: role,
  logical backend property identity, configured backend external name, and JSON:API member name.
  The adapter-independent role/name invariants are enforced when that value is constructed, so
  identifier roles always name `id`/`lid`, resource meta always names `meta`, and attributes and
  relationships use their backend external name. Relationship-meta metadata is valid only in
  matched form, carrying the target relationship's JSON:API name; an unresolved annotation target
  stays resolver-local.

Native type and property models, introspection, mapping lookup, property access, container
handling, configured conversion, identifier conversion, whole-meta conversion, relationship
normalization, decoration, and selective rendering stay in each backend. The compound-inclusion
engine reaches native mechanics through the `InclusionBackend` capability boundary; the basic
resource writer reaches them through a thin `WriteResourceBackend` boundary limited to mapping
lookup, property access, identifier and attribute conversion, and native type specialization, plus
an adapter-supplied `BasicRelationshipWriter` phase that keeps the advanced direct/wrapper forms,
their identifier meta, and per-relationship meta in the existing adapter path. Adapter write and
read property records remain backend-owned; only the semantic value they compose is shared. This
module imports no Jackson-major or concrete-adapter package.

## Boundary

- Depends on `jsonapi-java-api` (which depends on `jsonapi-java-core`); never on a backend or a
  backend-native library.
- Backend supported public signatures must not expose this package.
- Application code must not depend on this package.

See the [architecture overview](../docs/architecture.md),
[ADR-005](../docs/adr/005-domain-mapping-and-inclusion.md),
[ADR-007](../docs/adr/007-module-boundaries.md), and
[ADR-019](../docs/adr/019-jackson-neutral-implementation-helpers.md).
