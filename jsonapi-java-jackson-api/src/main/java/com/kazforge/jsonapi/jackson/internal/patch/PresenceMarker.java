package com.kazforge.jsonapi.jackson.internal.patch;

import org.jspecify.annotations.Nullable;

/**
 * Presence state placed into a synthetic property map by an adapter's typed PATCH binder.
 *
 * <p>The {@code present} boolean is primitive, so caller inclusion settings cannot drop it. The
 * JSON-compatible inner value rides in {@code value}; each adapter's contextual {@code
 * PatchPresence} deserializer performs the major-specific conversion.
 */
public record PresenceMarker(boolean present, @Nullable Object value) {}
