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
 * materialization, null-item skipping, and mixed-value rejection), relationship {@code data}
 * construction, and base {@link com.kazforge.jsonapi.core.model.ResourceObject} assembly.
 *
 * <p>It additionally owns the neutral {@link com.kazforge.jsonapi.mapping.internal.PropertyRole}
 * enum and the {@link com.kazforge.jsonapi.mapping.internal.SemanticProperty} value that adapters
 * compose into their own write and read mapping records: role, logical backend property identity,
 * configured backend external name, and JSON:API member name, with the adapter-independent role and
 * name invariants enforced on construction.
 *
 * <p>Native type resolution, mapping lookup, property lookup and access, container handling,
 * identifier conversion, configured conversion, whole-meta conversion, and resource rendering stay
 * in each backend. The inclusion engine reaches them through {@link
 * com.kazforge.jsonapi.mapping.internal.InclusionBackend}; the writer reaches a thin {@link
 * com.kazforge.jsonapi.mapping.internal.WriteResourceBackend} native-mechanics boundary and an
 * adapter-supplied {@link com.kazforge.jsonapi.mapping.internal.BasicRelationshipWriter}
 * relationship phase. Advanced relationship normalization runs through a neutral declared {@link
 * com.kazforge.jsonapi.mapping.internal.RelationshipShape} and two narrow adapter callbacks ({@link
 * com.kazforge.jsonapi.mapping.internal.RelationshipTargetResolver} and {@link
 * com.kazforge.jsonapi.mapping.internal.RelationshipMetaEnricher}) that keep native type
 * specialization, unresolved-target validation, and property-scoped meta conversion adapter-owned.
 *
 * <p>This package is not consumer SPI. Its Java-public types exist only so backend artifacts can
 * cooperate on neutral mapping implementation. Application code must not depend on it, and backend
 * supported public signatures must not expose it.
 */
@NullMarked
package com.kazforge.jsonapi.mapping.internal;

import org.jspecify.annotations.NullMarked;
