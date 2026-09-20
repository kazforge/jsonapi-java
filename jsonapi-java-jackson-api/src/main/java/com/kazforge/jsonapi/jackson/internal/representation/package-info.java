/**
 * Shared representation composition and inclusion bookkeeping used only for adapter cooperation.
 *
 * <p>This package is an unsupported implementation namespace shipped inside the Jackson API
 * artifact so the Jackson 2 and Jackson 3 adapters can share representation composition and
 * compound-document bookkeeping. Its Java-public types are adapter implementation details, not
 * supported public API, and must not appear in supported public signatures. Jackson mapping,
 * traversal, type, and writer mechanics remain adapter-local.
 */
@NullMarked
package com.kazforge.jsonapi.jackson.internal.representation;

import org.jspecify.annotations.NullMarked;
