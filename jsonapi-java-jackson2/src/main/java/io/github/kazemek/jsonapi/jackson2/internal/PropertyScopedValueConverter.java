package io.github.kazemek.jsonapi.jackson2.internal;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;
import com.fasterxml.jackson.databind.ser.impl.UnwrappingBeanPropertyWriter;
import com.fasterxml.jackson.databind.ser.std.BeanSerializerBase;
import com.fasterxml.jackson.databind.util.TokenBuffer;
import java.io.IOException;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Property-scoped Jackson serialization authority for one mapped member value.
 *
 * <p>Used by {@link DomainResourceWriter} for attributes, whole resource/relationship meta, and
 * identifier-meta conversion. The member's fully-contextualized {@link BeanPropertyWriter} is
 * resolved from the containing bean's {@link BeanSerializerBase}. Its inclusion and null handling
 * remain writer-owned; unsuppressed values use that writer's contextual serializer against the
 * already-read value. The resulting tokens are read back as an untyped JSON-compatible value, with
 * property omission kept distinct from an emitted JSON {@code null}.
 */
final class PropertyScopedValueConverter {

  private final JsonMapper mapper;
  private final JsonMapper serializationMapper;

  record SerializationResult(boolean emitted, @Nullable Object value) {}

  PropertyScopedValueConverter(JsonMapper mapper) {
    this.mapper = Objects.requireNonNull(mapper, "mapper");
    JsonMapper derivedSerializationMapper =
        mapper.rebuild().addModule(new RawValuePropertyModule()).build();
    this.serializationMapper =
        derivedSerializationMapper.isEnabled(SerializationFeature.WRAP_ROOT_VALUE)
            ? derivedSerializationMapper
                .rebuild()
                .disable(SerializationFeature.WRAP_ROOT_VALUE)
                .build()
            : derivedSerializationMapper;
  }

  /**
   * Serializes one mapped property through its configured Jackson property writer.
   *
   * <p>{@code fallbackValue} is used only when no property writer can be resolved; callers pass the
   * JSON:API-unwrapped value there to preserve the adapter's existing Optional semantics. When a
   * writer is available, its inclusion and assigned null serializer are preserved. Unsuppressed
   * values use the writer's contextual serializer against the already-read value. The result
   * records whether the writer emitted the property.
   */
  SerializationResult serialize(
      JavaType beanType,
      String wireName,
      Object sourceBean,
      @Nullable Object rawValue,
      @Nullable Object fallbackValue) {
    try {
      SerializerPropertyResolution resolution = matchingSerializerProperty(beanType, wireName);
      if (!resolution.beanSerializerAvailable()) {
        return new SerializationResult(true, mapper.convertValue(fallbackValue, Object.class));
      }
      if (resolution.property() == null) {
        return new SerializationResult(false, null);
      }
      BeanPropertyWriter property = Objects.requireNonNull(resolution.property());
      SerializerProvider provider = serializationMapper.getSerializerProviderInstance();
      try (TokenBuffer buffer = conversionBuffer(provider)) {
        serializePropertyValue(property, sourceBean, rawValue, buffer, provider);
        try (JsonParser parser = buffer.asParser(mapper)) {
          JsonToken token = parser.nextToken();
          if (token == JsonToken.FIELD_NAME) {
            token = parser.nextToken();
          }
          if (token == null) {
            return new SerializationResult(false, null);
          }
          return new SerializationResult(true, mapper.readValue(parser, Object.class));
        }
      }
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new IllegalStateException(
          "Failed to serialize property '" + wireName + "' through Jackson", e);
    }
  }

  private static void serializePropertyValue(
      BeanPropertyWriter property,
      Object sourceBean,
      @Nullable Object rawValue,
      TokenBuffer buffer,
      SerializerProvider provider)
      throws IOException {
    switch (property) {
      case RawValueBeanPropertyWriter rawProperty ->
          rawProperty.serializeAsRawProperty(sourceBean, rawValue, buffer, provider);
      case RawValueUnwrappingBeanPropertyWriter rawUnwrappingProperty ->
          rawUnwrappingProperty.serializeAsRawProperty(sourceBean, rawValue, buffer, provider);
      case UnwrappingBeanPropertyWriter unwrappingProperty ->
          new RawValueUnwrappingBeanPropertyWriter(unwrappingProperty)
              .serializeAsRawProperty(sourceBean, rawValue, buffer, provider);
      default ->
          new RawValueBeanPropertyWriter(property)
              .serializeAsRawProperty(sourceBean, rawValue, buffer, provider);
    }
  }

  @SuppressWarnings("resource")
  private TokenBuffer conversionBuffer(SerializerProvider provider) {
    TokenBuffer buffer = provider.bufferForValueConversion();
    return mapper.isEnabled(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
        ? buffer.forceUseOfBigDecimal(true)
        : buffer;
  }

  /**
   * Resolves the fully-contextualized property writer from the containing bean. A bean serializer
   * without the requested writer means Jackson intentionally omitted the property; only a non-bean
   * serializer uses the ordinary conversion fallback.
   */
  private SerializerPropertyResolution matchingSerializerProperty(
      JavaType beanType, String wireName) {
    SerializerProvider context = serializationMapper.getSerializerProviderInstance();
    com.fasterxml.jackson.databind.JsonSerializer<Object> root;
    try {
      root = context.findValueSerializer(beanType);
    } catch (com.fasterxml.jackson.databind.JsonMappingException e) {
      throw new IllegalStateException("Failed to resolve a serializer for " + beanType, e);
    }
    if (!(root instanceof BeanSerializerBase bean)) {
      return new SerializerPropertyResolution(false, null);
    }
    for (var iterator = bean.properties(); iterator.hasNext(); ) {
      com.fasterxml.jackson.databind.ser.PropertyWriter writer = iterator.next();
      if (writer instanceof BeanPropertyWriter property && property.getName().equals(wireName)) {
        return new SerializerPropertyResolution(true, property);
      }
    }
    return new SerializerPropertyResolution(true, null);
  }

  /**
   * Serializes {@code value} using the configured serializer for {@code declaredType}, preserving
   * generic {@link JavaType} information that {@code convertValue(value, Object.class)} would lose.
   */
  SerializationResult serializeDeclared(JavaType declaredType, Object value) {
    try {
      SerializerProvider provider = serializationMapper.getSerializerProviderInstance();
      com.fasterxml.jackson.databind.JsonSerializer<Object> serializer =
          provider.findTypedValueSerializer(declaredType, true, null);
      try (TokenBuffer buffer = conversionBuffer(provider)) {
        serializer.serialize(value, buffer, provider);
        try (JsonParser parser = buffer.asParser(mapper)) {
          JsonToken token = parser.nextToken();
          if (token == null) {
            return new SerializationResult(false, null);
          }
          return new SerializationResult(true, mapper.readValue(parser, Object.class));
        }
      }
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new IllegalStateException("Failed to serialize value through Jackson", e);
    }
  }

  private record SerializerPropertyResolution(
      boolean beanSerializerAvailable, @Nullable BeanPropertyWriter property) {}
}
