/**
 * Cross-artifact mapping implementation detail shared by backend runtimes.
 *
 * <p>This package owns backend-neutral compound-inclusion semantics: include-path validation,
 * breadth-first traversal, identity aliasing, first-encounter order, deduplication and conflict
 * checks, traversal limits, and sparse-fieldset linkage exemptions. It also owns backend-neutral
 * basic resource-write semantics: fieldset validation and filtering, strict versus create-request
 * identity rules, empty-member omission, attribute and member naming, ordinary domain-object
 * to-one/to-many identifier construction, relationship {@code data} construction, and base {@link
 * com.kazforge.jsonapi.core.model.ResourceObject} assembly.
 *
 * <p>It additionally owns the neutral {@link com.kazforge.jsonapi.mapping.internal.PropertyRole}
 * enum and the {@link com.kazforge.jsonapi.mapping.internal.SemanticProperty} value that adapters
 * compose into their own write and read mapping records: role, logical backend property identity,
 * configured backend external name, and JSON:API member name, with the adapter-independent role and
 * name invariants enforced on construction.
 *
 * <p>Native type resolution, mapping lookup, property lookup and access, relationship-container
 * handling, identifier conversion, configured conversion, relationship normalization, whole-meta
 * conversion, and resource rendering stay in each backend behind the {@link
 * com.kazforge.jsonapi.mapping.internal.InclusionBackend} and {@link
 * com.kazforge.jsonapi.mapping.internal.WriteResourceBackend} capability boundaries.
 *
 * <p>This package is not consumer SPI. Its Java-public types exist only so backend artifacts can
 * cooperate on neutral mapping implementation. Application code must not depend on it, and backend
 * supported public signatures must not expose it.
 */
@NullMarked
package com.kazforge.jsonapi.mapping.internal;

import org.jspecify.annotations.NullMarked;
