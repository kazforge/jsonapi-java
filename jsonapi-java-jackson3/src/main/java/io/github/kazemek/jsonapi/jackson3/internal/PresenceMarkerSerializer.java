package io.github.kazemek.jsonapi.jackson3.internal;

import io.github.kazemek.jsonapi.jackson.internal.patch.PresenceMarker;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;

/**
 * Internal serializer that gives the presence marker a deterministic wire shape independent of any
 * caller property naming strategy: it always emits exactly the {@code present} and {@code value}
 * members. The marker value is serialized through the caller-derived configuration, so
 * JSON-compatible wire values retain normal mapper serialization semantics (ADR-004). Only the
 * derived PATCH DTO binder mapper registers this serializer; the caller's mapper is never mutated.
 */
final class PresenceMarkerSerializer extends ValueSerializer<PresenceMarker> {

  private static final String PRESENT = "present";
  private static final String VALUE = "value";

  static final PresenceMarkerSerializer INSTANCE = new PresenceMarkerSerializer();

  private PresenceMarkerSerializer() {}

  @Override
  public void serialize(PresenceMarker marker, JsonGenerator gen, SerializationContext context) {
    gen.writeStartObject();
    gen.writeBooleanProperty(PRESENT, marker.present());
    gen.writeName(VALUE);
    if (marker.value() == null) {
      gen.writeNull();
    } else {
      ValueSerializer<Object> serializer =
          context.findTypedValueSerializer(marker.value().getClass(), false);
      serializer.serialize(marker.value(), gen, context);
    }
    gen.writeEndObject();
  }
}
