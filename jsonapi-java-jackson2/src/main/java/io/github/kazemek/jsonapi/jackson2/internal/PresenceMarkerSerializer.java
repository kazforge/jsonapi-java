package io.github.kazemek.jsonapi.jackson2.internal;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import java.io.IOException;

/**
 * Internal serializer that gives the presence marker a deterministic wire shape independent of any
 * caller property naming strategy: it always emits exactly the {@code present} and {@code value}
 * members. The inner value is serialized through the caller-derived configuration, so inner-type
 * serializers and modules remain authoritative. Only the derived PATCH DTO binder mapper registers
 * this serializer; the caller's mapper is never mutated.
 */
final class PresenceMarkerSerializer extends JsonSerializer<PresenceMarker> {

  private static final String PRESENT = "present";
  private static final String VALUE = "value";

  static final PresenceMarkerSerializer INSTANCE = new PresenceMarkerSerializer();

  private PresenceMarkerSerializer() {}

  @Override
  public void serialize(PresenceMarker marker, JsonGenerator gen, SerializerProvider serializers)
      throws IOException {
    gen.writeStartObject();
    gen.writeBooleanField(PRESENT, marker.present());
    gen.writeFieldName(VALUE);
    if (marker.value() == null) {
      serializers.defaultSerializeNull(gen);
    } else {
      JsonSerializer<Object> serializer =
          serializers.findTypedValueSerializer(marker.value().getClass(), false, null);
      serializer.serialize(marker.value(), gen, serializers);
    }
    gen.writeEndObject();
  }
}
