/**
 * Shared wire-reading support used only for adapter cooperation.
 *
 * <p>This package is an unsupported implementation namespace shipped inside the neutral API
 * artifact so the Jackson 2 and Jackson 3 adapters can share neutral pointer, location, and member
 * classification state. Its Java-public types are adapter implementation details, not supported
 * public API, and must not appear in supported public signatures.
 */
@NullMarked
package com.kazforge.jsonapi.internal.wire;

import org.jspecify.annotations.NullMarked;
