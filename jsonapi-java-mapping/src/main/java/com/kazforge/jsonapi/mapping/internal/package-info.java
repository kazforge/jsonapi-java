/**
 * Cross-artifact mapping implementation detail shared by backend runtimes.
 *
 * <p>This package owns backend-neutral compound-inclusion semantics: include-path validation,
 * breadth-first traversal, identity aliasing, first-encounter order, deduplication and conflict
 * checks, traversal limits, and sparse-fieldset linkage exemptions. It also owns backend-neutral
 * basic and advanced resource-write semantics: fieldset validation and filtering, strict versus
 * create-request identity rules, empty-member omission, attribute and member naming, ordinary
 * domain-object to-one/to-many identifier construction, advanced relationship-value normalization
 * (direct identifiers, direct linkage data, wrapper occurrence handling, to-many container
 * materialization, null-item skipping, and mixed-value rejection), relationship assembly, resource
 * and relationship meta construction and attachment, identifier-meta overlay, and base {@link
 * com.kazforge.jsonapi.core.model.ResourceObject} assembly.
 *
 * <p>It owns backend-neutral resource-read semantics, basic and advanced: resource-type matching
 * through the shared {@link ResourceTypeMatch} authority, strict and independent {@code id}/{@code
 * lid} role selection, wire-member lookup by JSON:API name, the distinction between an absent
 * attribute and a present JSON null, the distinction between an absent relationship (or absent
 * relationship {@code data}) and present linkage, synthetic input keys by backend external name,
 * the resource-relative locations of supplied members, and the shared non-deserializable and
 * identifier-conversion diagnostics. It also owns relationship cardinality validation, null/empty
 * short-circuiting, direct {@link com.kazforge.jsonapi.core.model.ResourceIdentifier} copies that
 * preserve identifier meta and drop additional members, opt-in {@link
 * com.kazforge.jsonapi.mapping.RelationshipLinkage} occurrence orchestration with per-occurrence
 * target and identifier-meta pairing, and resource/relationship meta presence and raw-member
 * binding. Each backend reaches configured wire-identifier parsing, lazy relationship-shape
 * resolution with configured linkage-mapper invocation and declared identifier-meta conversion, and
 * final bean construction through the thin {@link
 * com.kazforge.jsonapi.mapping.internal.ReadResourceBackend} native-mechanics boundary.
 *
 * <p>It owns the backend-neutral low-level PATCH command semantics: resource-type matching,
 * required {@code id} identity that never falls back to {@code lid}, supplied-member lookup by
 * JSON:API name, effective-deserialization bindability enforcement, {@link
 * com.kazforge.jsonapi.patch.PatchChange} construction, and {@link
 * com.kazforge.jsonapi.patch.PatchCommand} assembly in the contract phase order. Whole linkage
 * replacement, cardinality, direct identifier copies, wrapper occurrence orchestration, and
 * identifier-meta sequencing are shared with the reader through {@link
 * com.kazforge.jsonapi.mapping.internal.RelationshipLinkageBinder}. Each backend reaches declared
 * meta-target validation against the effective inbound PATCH property types, identity and
 * attribute/meta conversion, final relationship container coercion, configured structured-shape
 * introspection and caching, native atomic conversion, typed relationship conversion,
 * construction-path translation, and the shared linkage native operations through the thin {@link
 * com.kazforge.jsonapi.mapping.internal.PatchResourceBackend} and {@link
 * com.kazforge.jsonapi.mapping.internal.StructuredShapeBackend} and {@link
 * com.kazforge.jsonapi.mapping.internal.TypedPatchBackend} boundaries. Ordinary reads and low-level
 * PATCH are separate projections of one adapter-resolved deserialization mapping, so they never
 * resolve competing configured-Jackson models. The typed PATCH DTO projection is the adapter's
 * serialization-oriented mapping, projected into a neutral {@link
 * com.kazforge.jsonapi.mapping.internal.TypedPatchDefinition}.
 *
 * <p>The backend-neutral recursive structured-value engine and typed PATCH DTO orchestrator live
 * here too. {@link com.kazforge.jsonapi.mapping.internal.StructuredPatchBinder} owns typed
 * marker-tree assembly, low-level supplied-only {@link com.kazforge.jsonapi.patch.StructuredPatch}
 * assembly, the present/omitted/explicit-null/empty-object distinctions, strict typed versus skip
 * low-level unknown-member policy, pointer accumulation, and the typed-only nature of
 * presence-aware shapes. {@link com.kazforge.jsonapi.mapping.internal.TypedPatchBinder} owns the
 * typed DTO contract phase order, synthetic {@link PresenceMarker} assembly, complete declaration
 * preflight, strict supplied unknown-member handling, and meta/data gating. The neutral resolved
 * {@link com.kazforge.jsonapi.mapping.internal.StructuredShape} carries only adapter-resolved
 * facts, so the adapters never decide atomic-versus-recursive behavior.
 *
 * <p>{@link com.kazforge.jsonapi.mapping.internal.ReadResourceDefinition#constructionStarts(
 * com.kazforge.jsonapi.diagnostic.MappingLocation,
 * com.kazforge.jsonapi.diagnostic.MappingLocation)} owns the neutral top-level construction-start
 * translation: each bindable read property's backend external name maps to its resource-relative
 * JSON:API start location, paired with the same opaque property token, so adapters do not duplicate
 * that translation. Nested shape walking, effective native types, configured deserialization,
 * native failure-path extraction, and the single final bean construction remain adapter-owned.
 *
 * <p>It additionally owns the neutral {@link com.kazforge.jsonapi.mapping.internal.PropertyRole}
 * enum and the {@link com.kazforge.jsonapi.mapping.internal.SemanticProperty} value that adapters
 * compose into their own write and read mapping records: role, logical backend property identity,
 * configured backend external name, and JSON:API member name, with the adapter-independent role and
 * name invariants enforced on construction.
 *
 * <p>{@link com.kazforge.jsonapi.mapping.internal.ResourceDecorationWriter} owns the additive link
 * decoration phase after the basic write: exact effective-class decorator lookup, decorator
 * failure/null translation, relationship target classification and logical-to-wire name resolution,
 * whole-value resource and relationship link replacement, fieldset non-resurrection, and
 * preservation of the other members the basic write produced. The adapter resolves the effective
 * runtime raw class and supplies the configured registry; the decorator contracts and registry
 * remain neutral API.
 *
 * <p>Native type resolution, mapping lookup, property lookup and access, native container
 * type-shape derivation, identifier conversion, configured conversion, whole-meta and declared-type
 * meta conversion, effective-type resolution, relationship target/type resolution and mapper
 * selection, and resource rendering stay in each backend. The inclusion engine reaches them through
 * {@link com.kazforge.jsonapi.mapping.internal.InclusionBackend}; the writer reaches a thin {@link
 * com.kazforge.jsonapi.mapping.internal.WriteResourceBackend} native-mechanics boundary that also
 * supplies a neutral declared {@link com.kazforge.jsonapi.mapping.internal.RelationshipShape},
 * native target resolution, and the property-scoped whole-meta and declared-type identifier-meta
 * conversion operations, keeping native type specialization, unresolved-target validation, and
 * configured conversion adapter-owned while the shared writer owns the neutral meta semantics and
 * diagnostics. The reader reaches a thin {@link
 * com.kazforge.jsonapi.mapping.internal.ReadResourceBackend} boundary that supplies a neutral
 * declared {@link com.kazforge.jsonapi.mapping.internal.ReadRelationshipShape}, the configured
 * linkage-mapper invocation, and the declared identifier-meta conversion, keeping native
 * target/type resolution, mapper selection, and configured conversion adapter-owned while the
 * shared reader owns the neutral relationship and meta semantics and diagnostics. The low-level
 * PATCH binder reaches a thin {@link com.kazforge.jsonapi.mapping.internal.PatchResourceBackend}
 * boundary that supplies declared meta-target validation, identity and attribute/meta conversion,
 * and final relationship container coercion alongside the shared native linkage operations, keeping
 * native conversion adapter-owned while the shared binder owns the neutral PATCH phase order and
 * change assembly. The recursive structured engine reaches {@link
 * com.kazforge.jsonapi.mapping.internal.StructuredShapeBackend} for resolved shape facts and atomic
 * conversion, and the typed PATCH DTO orchestrator reaches {@link
 * com.kazforge.jsonapi.mapping.internal.TypedPatchBackend} for identity parsing, relationship
 * conversion, and the single construction, keeping configured Jackson introspection and caching,
 * wrapper-customization detection, property-scoped conversion, and construction-path translation
 * adapter-owned.
 *
 * <p>This package is not consumer SPI. Its Java-public types exist only so backend artifacts can
 * cooperate on neutral mapping implementation. Application code must not depend on it, and backend
 * supported public signatures must not expose it.
 */
@NullMarked
package com.kazforge.jsonapi.mapping.internal;

import org.jspecify.annotations.NullMarked;
