/**
 * Shared typed-PATCH presence bridge state used only for adapter cooperation.
 *
 * <p>This package is an unsupported implementation namespace shipped inside the neutral API
 * artifact so the Jackson 2 and Jackson 3 adapters can share the neutral supplied/value carrier.
 * Its Java-public types are adapter implementation details, not supported public API, and must not
 * appear in supported public signatures. Jackson serializers, deserializers, and modules remain in
 * their respective adapters.
 */
@NullMarked
package com.kazforge.jsonapi.internal.patch;

import org.jspecify.annotations.NullMarked;
