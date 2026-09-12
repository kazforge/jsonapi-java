package com.kazforge.jsonapi.jackson2.internal;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.kazforge.jsonapi.core.model.Meta;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Lets the derived mapping mapper round-trip core {@link Meta} when serializing built-in {@code
 * ResourceIdentifier} values that carry identifier meta (ADR-017). Document codecs remain
 * token-driven and do not use this module.
 */
public final class MetaBindingModule extends SimpleModule {

  public MetaBindingModule() {
    super("jsonapi-java-meta-binding");
    addSerializer(Meta.class, new MetaSerializer());
    addDeserializer(Meta.class, new MetaDeserializer());
  }

  private static final class MetaSerializer extends JsonSerializer<Meta> {
    @Override
    public void serialize(Meta value, JsonGenerator generator, SerializerProvider context)
        throws java.io.IOException {
      JsonSerializer<Object> serializer =
          context.findTypedValueSerializer(value.members().getClass(), false, null);
      serializer.serialize(value.members(), generator, context);
    }
  }

  private static final class MetaDeserializer extends JsonDeserializer<Meta> {
    @Override
    public @Nullable Meta deserialize(JsonParser parser, DeserializationContext context)
        throws java.io.IOException {
      if (parser.hasToken(JsonToken.VALUE_NULL)) {
        return null;
      }
      Map<?, ?> raw = parser.readValueAs(Map.class);
      if (raw == null || raw.isEmpty()) {
        return Meta.empty();
      }
      Map<String, @Nullable Object> members = new LinkedHashMap<>();
      for (Map.Entry<?, ?> entry : raw.entrySet()) {
        Object key = entry.getKey();
        if (!(key instanceof String name)) {
          throw new IllegalArgumentException("Meta member name is not a string: " + key);
        }
        members.put(name, entry.getValue());
      }
      return Meta.of(members);
    }
  }
}
