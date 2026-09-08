package io.github.kazemek.jsonapi.jackson3.internal;

import org.jspecify.annotations.Nullable;

/**
 * Internal presence marker placed into the synthetic property map by the typed PATCH DTO binder.
 *
 * <p>The {@code present} boolean is a primitive, so no caller {@code JsonInclude} configuration can
 * drop it; the JSON-compatible inner value rides in {@code value} and is converted by the {@link
 * PatchPresenceDeserializer}, which reconstructs {@link
 * io.github.kazemek.jsonapi.jackson.patch.PatchPresence} from this marker.
 */
record PresenceMarker(boolean present, @Nullable Object value) {}
