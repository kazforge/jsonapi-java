package com.kazforge.jsonapi.jackson3.internal;

import java.util.Objects;
import org.jspecify.annotations.Nullable;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.PropertyName;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.deser.DeserializationContextExt;
import tools.jackson.databind.deser.SettableBeanProperty;
import tools.jackson.databind.deser.bean.BeanDeserializerBase;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.ser.BeanPropertyWriter;
import tools.jackson.databind.ser.PropertyWriter;
import tools.jackson.databind.ser.SerializationContextExt;
import tools.jackson.databind.ser.bean.BeanSerializerBase;
import tools.jackson.databind.util.TokenBuffer;

/**
 * Location-neutral property-scoped Jackson conversion authority for one member value.
 *
 * <p>Shared by {@link DomainResourceWriter} (ordinary mapped writes), {@link PatchMemberConverter}
 * (top-level attributes and identifiers), and {@link StructuredValueBinder} (low-level nested
 * atomic members) so the locations cannot silently drift on which configured Jackson authority
 * applies to a supplied member. It has no {@link ResourceMapping} / {@link MappingProperty} /
 * {@code @JsonApiAttribute} / {@link com.kazforge.jsonapi.jackson.patch.PatchChange} / location
 * dependency: callers supply the containing bean's {@link JavaType}, the member's Jackson-resolved
 * wire name, the conversion-target {@link JavaType}, and the raw wire value, so a later structured
 * JSON:API {@code meta} mapping can reuse the same machinery at its own location (ADR-014).
 *
 * <p>The member's fully-contextualized property is resolved from the containing bean's {@link
 * BeanDeserializerBase} (the same {@link SettableBeanProperty} Jackson would use during normal
 * binding) and the value converts through {@link SettableBeanProperty#deserialize}, so all
 * property-scoped deserialization semantics are honored exactly as Jackson applies them —
 * {@code @JsonDeserialize using / contentUsing / keyUsing}, deserialization converters, type
 * refinement (including a property-level {@code TypeDeserializer} for polymorphic values), and the
 * property's null provider — regardless of whether the annotation sits on a field, accessor, or
 * creator parameter. When no bean-based property can be resolved, the value falls back to ordinary
 * {@code convertValue}, which still applies type-level and module authority. A null {@code
 * rawValue} converts through the property's null value (for example {@code Optional.empty()} for an
 * {@link java.util.Optional} target).
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
    DeserializationContextExt context = mapper._deserializationContext();
    SerializationContextExt serializationContext = serializationMapper._serializationContext();
    try (TokenBuffer buffer = conversionBuffer(serializationContext)) {
      serializationContext.serializeValue(buffer, rawValue);
      try (JsonParser parser = buffer.asParser(context)) {
        context.assignParser(parser);
        parser.nextToken();
        return property.deserialize(parser, context);
      }
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
    SerializerPropertyResolution resolution = matchingSerializerProperty(beanType, wireName);
    if (!resolution.beanSerializerAvailable()) {
      return new SerializationResult(true, mapper.convertValue(fallbackValue, Object.class));
    }
    if (resolution.property() == null) {
      return new SerializationResult(false, null);
    }
    BeanPropertyWriter property = Objects.requireNonNull(resolution.property());
    SerializationContextExt serializationContext = serializationMapper._serializationContext();
    try (TokenBuffer buffer = conversionBuffer(serializationContext)) {
      serializePropertyValue(property, sourceBean, rawValue, buffer, serializationContext);
      DeserializationContextExt deserializationContext = mapper._deserializationContext();
      try (JsonParser parser = buffer.asParser(deserializationContext)) {
        deserializationContext.assignParser(parser);
        JsonToken token = parser.nextToken();
        if (token == JsonToken.PROPERTY_NAME) {
          token = parser.nextToken();
        }
        if (token == null) {
          return new SerializationResult(false, null);
        }
        return new SerializationResult(
            true, deserializationContext.readValue(parser, Object.class));
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
      SerializationContextExt context) {
    RawValueBeanPropertyWriter rawValueProperty =
        property instanceof RawValueBeanPropertyWriter rawProperty
            ? rawProperty
            : new RawValueBeanPropertyWriter(property);
    rawValueProperty.serializeAsRawProperty(sourceBean, rawValue, buffer, context);
  }

  @SuppressWarnings("resource")
  private TokenBuffer conversionBuffer(SerializationContextExt context) {
    TokenBuffer buffer = context.bufferForValueConversion();
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
    DeserializationContextExt context = mapper._deserializationContext();
    ValueDeserializer<Object> root = context.findRootValueDeserializer(beanType);
    if (!(root instanceof BeanDeserializerBase bean)) {
      return null;
    }
    return bean.findProperty(PropertyName.construct(wireName));
  }

  /**
   * Resolves the fully-contextualized property writer from the containing bean. A bean serializer
   * without the requested writer means Jackson intentionally omitted the property; only a non-bean
   * serializer uses the ordinary conversion fallback.
   */
  private SerializerPropertyResolution matchingSerializerProperty(
      JavaType beanType, String wireName) {
    SerializationContextExt context = serializationMapper._serializationContext();
    ValueSerializer<Object> root = context.findRootValueSerializer(beanType);
    if (!(root instanceof BeanSerializerBase bean)) {
      return new SerializerPropertyResolution(false, null);
    }
    for (var iterator = bean.properties(); iterator.hasNext(); ) {
      PropertyWriter writer = iterator.next();
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
    SerializationContextExt serializationContext = serializationMapper._serializationContext();
    ValueSerializer<Object> serializer =
        serializationContext.findTypedValueSerializer(declaredType, true);
    try (TokenBuffer buffer = conversionBuffer(serializationContext)) {
      serializer.serialize(value, buffer, serializationContext);
      DeserializationContextExt deserializationContext = mapper._deserializationContext();
      try (JsonParser parser = buffer.asParser(deserializationContext)) {
        deserializationContext.assignParser(parser);
        JsonToken token = parser.nextToken();
        if (token == null) {
          return new SerializationResult(false, null);
        }
        return new SerializationResult(
            true, deserializationContext.readValue(parser, Object.class));
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
