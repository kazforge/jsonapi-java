package io.github.kazemek.jsonapi.jackson2.internal;

import org.jspecify.annotations.Nullable;

/**
 * Internal presence marker placed into the synthetic property map by the typed PATCH DTO binder.
 *
 * <p>The {@code present} boolean is a primitive, so no caller {@code JsonInclude} configuration can
 * drop it; the already-converted inner value rides in {@code value}. The {@link
 * PatchPresenceDeserializer} reconstructs {@link
 * io.github.kazemek.jsonapi.jackson.patch.PatchPresence} from this marker.
 */
record PresenceMarker(boolean present, @Nullable Object value) {}
