package io.github.kazemek.jsonapi.jackson2.internal;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.jsonFormatVisitors.JsonObjectFormatVisitor;
import com.fasterxml.jackson.databind.jsontype.TypeSerializer;
import com.fasterxml.jackson.databind.ser.impl.PropertySerializerMap;
import com.fasterxml.jackson.databind.ser.impl.UnwrappingBeanPropertyWriter;
import com.fasterxml.jackson.databind.util.NameTransformer;
import java.io.IOException;
import org.jspecify.annotations.Nullable;

/**
 * Raw-value variant of {@link UnwrappingBeanPropertyWriter}.
 *
 * <p>The unwrapping subclass exists so the delegate's unwrapping serializer state stays reachable
 * through the inherited dynamic-serializer lookup. The unwrapping name transformer cannot be read
 * back from the delegate (its {@code _nameTransformer} field is protected and inaccessible across
 * packages), so it is reconstructed the same way Jackson built the delegate: from the mapper's
 * {@code AnnotationIntrospector#findUnwrappingNameTransformer} on the property member, falling back
 * to the {@link JsonUnwrapped} annotation. The introspector lookup honors custom introspectors; the
 * annotation fallback covers construction paths without a serializer provider.
 */
final class RawValueUnwrappingBeanPropertyWriter extends UnwrappingBeanPropertyWriter {

  private final UnwrappingBeanPropertyWriter delegate;

  RawValueUnwrappingBeanPropertyWriter(UnwrappingBeanPropertyWriter base) {
    super(base, annotationTransformer(base));
    this.delegate = base;
  }

  private RawValueUnwrappingBeanPropertyWriter(
      UnwrappingBeanPropertyWriter base, NameTransformer transformer) {
    super(base, transformer);
    this.delegate = base;
  }

  /** Reconstructs the delegate's transformer from the mapper-configured introspector. */
  static RawValueUnwrappingBeanPropertyWriter of(
      UnwrappingBeanPropertyWriter base, SerializerProvider provider) {
    NameTransformer supplied =
        provider
            .getConfig()
            .getAnnotationIntrospector()
            .findUnwrappingNameTransformer(base.getMember());
    return new RawValueUnwrappingBeanPropertyWriter(
        base, supplied != null ? supplied : annotationTransformer(base));
  }

  // BeanPropertyWriter.getAnnotation returns null for absent annotations (null when the member is
  // unavailable), so the null check is live even though the dataflow inspection cannot see it.
  @SuppressWarnings("ConstantConditions")
  private static NameTransformer annotationTransformer(UnwrappingBeanPropertyWriter base) {
    JsonUnwrapped annotation = base.getAnnotation(JsonUnwrapped.class);
    if (annotation == null || !annotation.enabled()) {
      return NameTransformer.NOP;
    }
    return NameTransformer.simpleTransformer(annotation.prefix(), annotation.suffix());
  }

  @Override
  public UnwrappingBeanPropertyWriter rename(NameTransformer transformer) {
    return new RawValueUnwrappingBeanPropertyWriter(delegate.rename(transformer));
  }

  @Override
  public void assignSerializer(JsonSerializer<Object> serializer) {
    delegate.assignSerializer(serializer);
    super.assignSerializer(delegate.getSerializer());
  }

  @Override
  public void assignNullSerializer(JsonSerializer<Object> nullSerializer) {
    delegate.assignNullSerializer(nullSerializer);
    super.assignNullSerializer(nullSerializer);
  }

  @Override
  public void assignTypeSerializer(TypeSerializer typeSerializer) {
    delegate.assignTypeSerializer(typeSerializer);
    super.assignTypeSerializer(typeSerializer);
  }

  @Override
  public void serializeAsField(Object bean, JsonGenerator generator, SerializerProvider provider)
      throws Exception {
    delegate.serializeAsField(bean, generator, provider);
  }

  @Override
  public void serializeAsOmittedField(
      Object bean, JsonGenerator generator, SerializerProvider provider) throws Exception {
    delegate.serializeAsOmittedField(bean, generator, provider);
  }

  @Override
  public void serializeAsElement(Object bean, JsonGenerator generator, SerializerProvider provider)
      throws Exception {
    delegate.serializeAsElement(bean, generator, provider);
  }

  @Override
  public void depositSchemaProperty(JsonObjectFormatVisitor visitor, SerializerProvider provider)
      throws JsonMappingException {
    delegate.depositSchemaProperty(visitor, provider);
  }

  void serializeAsRawProperty(
      Object bean, @Nullable Object value, JsonGenerator generator, SerializerProvider provider)
      throws IOException {
    if (value == null) {
      return;
    }
    JsonSerializer<Object> serializer = resolveSerializer(value, provider);
    if (isValueSuppressed(provider, value, serializer)) {
      return;
    }
    writePropertyValue(bean, value, generator, provider, serializer);
  }

  private JsonSerializer<Object> resolveSerializer(Object value, SerializerProvider provider)
      throws JsonMappingException {
    JsonSerializer<Object> serializer = delegate.getSerializer();
    if (serializer != null) {
      return serializer;
    }
    Class<?> rawType = value.getClass();
    PropertySerializerMap serializers = _dynamicSerializers;
    JsonSerializer<Object> dynamicSerializer = serializers.serializerFor(rawType);
    if (dynamicSerializer == null) {
      return _findAndAddDynamic(serializers, rawType, provider);
    }
    return dynamicSerializer;
  }

  private boolean isValueSuppressed(
      SerializerProvider provider, Object value, JsonSerializer<Object> serializer) {
    if (_suppressableValue == null) {
      return false;
    }
    if (MARKER_FOR_EMPTY == _suppressableValue) {
      return serializer.isEmpty(provider, value);
    }
    return _suppressableValue.equals(value);
  }

  private void writePropertyValue(
      Object bean,
      Object value,
      JsonGenerator generator,
      SerializerProvider provider,
      JsonSerializer<Object> serializer)
      throws IOException {
    if (value == bean && _handleSelfReference(bean, generator, provider, serializer)) {
      return;
    }
    if (_typeSerializer == null) {
      serializer.serialize(value, generator, provider);
    } else {
      serializer.serializeWithType(value, generator, provider, _typeSerializer);
    }
  }
}
