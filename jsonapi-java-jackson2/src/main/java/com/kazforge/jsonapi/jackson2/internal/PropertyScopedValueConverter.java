package com.kazforge.jsonapi.jackson2.internal;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationConfig;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.deser.BeanDeserializerBase;
import com.fasterxml.jackson.databind.deser.DefaultDeserializationContext;
import com.fasterxml.jackson.databind.deser.SettableBeanProperty;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;
import com.fasterxml.jackson.databind.ser.impl.UnwrappingBeanPropertyWriter;
import com.fasterxml.jackson.databind.ser.std.BeanSerializerBase;
import com.fasterxml.jackson.databind.util.TokenBuffer;
import java.io.IOException;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Location-neutral property-scoped Jackson conversion authority for one member value.
 *
 * <p>Shared by {@link DomainResourceWriter} (ordinary mapped writes), {@code PatchMemberConverter}
 * (top-level attributes and identifiers), and {@code StructuredValueBinder} (low-level nested
 * atomic members) so the locations cannot silently drift on which configured Jackson authority
 * applies to a supplied member. It has no {@code ResourceMapping} / {@code MappingProperty} /
 * {@code @JsonApiAttribute} / {@code PatchChange} / location dependency: callers supply the
 * containing bean's {@link JavaType}, the member's Jackson-resolved wire name, the
 * conversion-target {@link JavaType}, and the raw wire value, so a later structured JSON:API {@code
 * meta} mapping can reuse the same machinery at its own location.
 *
 * <p>The member's fully-contextualized property is resolved from the containing bean's {@link
 * com.fasterxml.jackson.databind.deser.BeanDeserializerBase} (the same {@link
 * com.fasterxml.jackson.databind.deser.SettableBeanProperty} Jackson would use during normal
 * binding) and the value converts through {@link
 * com.fasterxml.jackson.databind.deser.SettableBeanProperty#deserialize}, so all property-scoped
 * deserialization semantics are honored exactly as Jackson applies them — {@code @JsonDeserialize
 * using / contentUsing / keyUsing}, deserialization converters, type refinement (including a
 * property-level {@code TypeDeserializer} for polymorphic values), and the property's null provider
 * — regardless of whether the annotation sits on a field, accessor, or creator parameter. When no
 * bean-based property can be resolved, the value falls back to ordinary {@code convertValue}, which
 * still applies type-level and module authority. A null {@code rawValue} converts through the
 * property's null value (for example {@code Optional.empty()} for an {@link java.util.Optional}
 * target).
 *
 * <p>On write, the member's fully-contextualized {@link BeanPropertyWriter} is resolved from the
 * containing bean's {@link BeanSerializerBase}. Its inclusion and null handling remain
 * writer-owned; unsuppressed values use that writer's contextual serializer against the
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
   * Converts {@code rawValue} for a single member of the containing bean {@code beanType},
   * identified by its Jackson-resolved {@code wireName}. {@code declaredType} is the member's
   * declared type and {@code targetType} the effective conversion target. When the caller unwraps a
   * {@code PatchPresence} wrapper ({@code declaredType != targetType}), the bean property
   * deserializer (which targets the declared {@code PatchPresence} type) is not applicable and the
   * value converts against {@code targetType} directly.
   */
  @Nullable Object convert(
      JavaType beanType,
      String wireName,
      JavaType declaredType,
      JavaType targetType,
      @Nullable Object rawValue) {
    SettableBeanProperty property = null;
    if (declaredType.equals(targetType)) {
      property = matchingProperty(beanType, wireName);
    }
    if (property == null) {
      return mapper.convertValue(rawValue, targetType);
    }
    SerializerProvider serializationProvider = serializationMapper.getSerializerProviderInstance();
    try (TokenBuffer buffer = conversionBuffer(serializationProvider)) {
      serializationMapper.writeValue(buffer, rawValue);
      try (JsonParser parser = buffer.asParser(mapper)) {
        DeserializationConfig deserConfig = mapper.getDeserializationConfig();
        DeserializationContext context =
            ((DefaultDeserializationContext) mapper.getDeserializationContext())
                .createInstance(deserConfig, parser, null);
        JsonToken token = parser.nextToken();
        if (token == null) {
          return mapper.convertValue(rawValue, targetType);
        }
        return property.deserialize(parser, context);
      }
    } catch (Exception e) {
      throw new IllegalStateException(
          "Failed to convert property '" + wireName + "' through Jackson", e);
    }
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
      SerializerProvider provider = serializationMapper.getSerializerProviderInstance();
      SerializerPropertyResolution resolution =
          matchingSerializerProperty(provider, beanType, wireName);
      if (!resolution.beanSerializerAvailable()) {
        return new SerializationResult(true, mapper.convertValue(fallbackValue, Object.class));
      }
      if (resolution.property() == null) {
        return new SerializationResult(false, null);
      }
      BeanPropertyWriter property = Objects.requireNonNull(resolution.property());
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
          RawValueUnwrappingBeanPropertyWriter.of(unwrappingProperty, provider)
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
   * Resolves the fully-contextualized {@link SettableBeanProperty} from the containing bean, or
   * {@code null} when the bean has no property-driven deserializer or the wire-named property is
   * absent.
   */
  private @Nullable SettableBeanProperty matchingProperty(JavaType beanType, String wireName) {
    try {
      DeserializationConfig config = mapper.getDeserializationConfig();
      DefaultDeserializationContext context =
          ((DefaultDeserializationContext) mapper.getDeserializationContext())
              .createInstance(config, null, null);
      JsonDeserializer<?> root = context.findRootValueDeserializer(beanType);
      if (!(root instanceof BeanDeserializerBase bean)) {
        return null;
      }
      return bean.findProperty(wireName);
    } catch (com.fasterxml.jackson.databind.JsonMappingException e) {
      throw new IllegalStateException("Failed to resolve a deserializer for " + beanType, e);
    }
  }

  /**
   * Resolves the fully-contextualized property writer from the containing bean. A bean serializer
   * without the requested writer means Jackson intentionally omitted the property; only a non-bean
   * serializer uses the ordinary conversion fallback.
   */
  private SerializerPropertyResolution matchingSerializerProperty(
      SerializerProvider context, JavaType beanType, String wireName) {
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
