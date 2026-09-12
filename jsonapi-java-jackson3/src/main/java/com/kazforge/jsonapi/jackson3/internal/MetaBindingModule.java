package com.kazforge.jsonapi.jackson3.internal;

import com.kazforge.jsonapi.core.model.Meta;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.module.SimpleModule;

/**
 * Lets derived binder mappers round-trip core {@link Meta} when converting built-in {@code
 * ResourceIdentifier} values that carry identifier meta (ADR-017). Document codecs remain
 * token-driven and do not use this module.
 */
public final class MetaBindingModule extends SimpleModule {

  public MetaBindingModule() {
    super("jsonapi-java-meta-binding");
    addSerializer(Meta.class, new MetaSerializer());
    addDeserializer(Meta.class, new MetaDeserializer());
  }

  private static final class MetaSerializer extends ValueSerializer<Meta> {
    @Override
    public void serialize(Meta value, JsonGenerator generator, SerializationContext context) {
      ValueSerializer<Object> serializer =
          context.findTypedValueSerializer(value.members().getClass(), false);
      serializer.serialize(value.members(), generator, context);
    }
  }

  private static final class MetaDeserializer extends ValueDeserializer<Meta> {
    @Override
    public @Nullable Meta deserialize(JsonParser parser, DeserializationContext context) {
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
