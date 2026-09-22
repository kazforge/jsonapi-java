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
 * <p>It owns backend-neutral basic resource-read semantics: resource-type matching through the
 * shared {@link com.kazforge.jsonapi.internal.mapping.ResourceTypeMatch} authority, strict and
 * independent {@code id}/{@code lid} role selection, wire-member lookup by JSON:API name, the
 * distinction between an absent attribute and a present JSON null, the distinction between an
 * absent relationship (or absent relationship {@code data}) and present linkage, synthetic input
 * keys by backend external name, the resource-relative locations of supplied members, and the
 * shared non-deserializable and identifier-conversion diagnostics. Each backend reaches configured
 * wire-identifier parsing and configured relationship-linkage conversion through a thin {@link
 * com.kazforge.jsonapi.mapping.internal.ReadResourceBackend} native-mechanics boundary and remains
 * responsible for whole-object meta and relationship-meta binding plus final bean construction.
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
 * meta conversion, effective-type resolution, and resource rendering stay in each backend. The
 * inclusion engine reaches them through {@link
 * com.kazforge.jsonapi.mapping.internal.InclusionBackend}; the writer reaches a thin {@link
 * com.kazforge.jsonapi.mapping.internal.WriteResourceBackend} native-mechanics boundary that also
 * supplies a neutral declared {@link com.kazforge.jsonapi.mapping.internal.RelationshipShape},
 * native target resolution, and the property-scoped whole-meta and declared-type identifier-meta
 * conversion operations. Those keep native type specialization, unresolved-target validation, and
 * configured conversion adapter-owned while the shared writer owns the neutral meta semantics and
 * diagnostics.
 *
 * <p>This package is not consumer SPI. Its Java-public types exist only so backend artifacts can
 * cooperate on neutral mapping implementation. Application code must not depend on it, and backend
 * supported public signatures must not expose it.
 */
@NullMarked
package com.kazforge.jsonapi.mapping.internal;

import org.jspecify.annotations.NullMarked;
