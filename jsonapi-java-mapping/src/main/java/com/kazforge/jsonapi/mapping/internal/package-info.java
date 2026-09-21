/**
 * Cross-artifact mapping implementation detail shared by backend runtimes.
 *
 * <p>This package owns backend-neutral compound-inclusion semantics: include-path validation,
 * breadth-first traversal, identity aliasing, first-encounter order, deduplication and conflict
 * checks, traversal limits, and sparse-fieldset linkage exemptions. Native type resolution,
 * property lookup and access, relationship-container handling, identifier conversion, and resource
 * rendering stay in each backend behind the {@link
 * com.kazforge.jsonapi.mapping.internal.InclusionBackend} capability boundary.
 *
 * <p>This package is not consumer SPI. Its Java-public types exist only so backend artifacts can
 * cooperate on neutral mapping implementation. Application code must not depend on it, and backend
 * supported public signatures must not expose it.
 */
@NullMarked
package com.kazforge.jsonapi.mapping.internal;

import org.jspecify.annotations.NullMarked;
