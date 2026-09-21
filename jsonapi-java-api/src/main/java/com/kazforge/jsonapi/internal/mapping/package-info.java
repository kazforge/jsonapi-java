/**
 * Shared mapping bookkeeping used only for adapter cooperation.
 *
 * <p>This package is an unsupported implementation namespace shipped inside the Jackson API
 * artifact so the Jackson 2 and Jackson 3 adapters can share neutral identifier-meta bookkeeping,
 * resource-type matching, and their diagnostics. Its Java-public types are adapter implementation
 * details, not supported public API, and must not appear in supported public signatures. Neutral
 * mapping roles and per-property naming metadata are owned by {@code
 * com.kazforge.jsonapi.mapping.internal} in the mapping artifact.
 */
@NullMarked
package com.kazforge.jsonapi.internal.mapping;

import org.jspecify.annotations.NullMarked;
