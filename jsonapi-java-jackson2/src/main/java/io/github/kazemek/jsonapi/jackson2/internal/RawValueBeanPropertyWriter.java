package io.github.kazemek.jsonapi.jackson2.internal;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.jsonFormatVisitors.JsonObjectFormatVisitor;
import com.fasterxml.jackson.databind.jsontype.TypeSerializer;
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;
import com.fasterxml.jackson.databind.ser.impl.PropertySerializerMap;
import com.fasterxml.jackson.databind.ser.impl.UnwrappingBeanPropertyWriter;
import com.fasterxml.jackson.databind.util.NameTransformer;
import java.io.IOException;
import org.jspecify.annotations.Nullable;

/**
 * A {@link BeanPropertyWriter} that applies its resolved writer state to a supplied property value.
 *
 * <p>The ordinary writer reads its accessor inside {@link #serializeAsField}. Property-scoped
 * JSON:API mapping has already read the accessor to establish the value to map, so reading it again
 * could make inclusion and the mapped value disagree. This adapter keeps the normal writer's
 * resolved suppression, serializer, null serializer, and type serializer state while replacing the
 * accessor read with the supplied value. Arbitrary custom writer replacements are rejected because
 * they have no raw-value contract; rejecting them keeps a custom override from silently rereading
 * the bean accessor.
 */
final class RawValueBeanPropertyWriter extends BeanPropertyWriter {

  private final BeanPropertyWriter delegate;

  RawValueBeanPropertyWriter(BeanPropertyWriter base) {
    super(base);
    this.delegate = base;
  }

  @Override
  public BeanPropertyWriter rename(NameTransformer transformer) {
    return new RawValueBeanPropertyWriter(delegate.rename(transformer));
  }

  @Override
  public BeanPropertyWriter unwrappingWriter(NameTransformer unwrapper) {
    return new RawValueUnwrappingBeanPropertyWriter(
        (UnwrappingBeanPropertyWriter) delegate.unwrappingWriter(unwrapper));
  }

  @Override
  public boolean isUnwrapping() {
    return delegate.isUnwrapping();
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
    if (usesCustomSerializationBehavior()) {
      throw new UnsupportedOperationException(
          "Property-scoped serialization does not support custom BeanPropertyWriter replacements");
    }
    if (delegate instanceof UnwrappingBeanPropertyWriter unwrappingDelegate) {
      new RawValueUnwrappingBeanPropertyWriter(unwrappingDelegate)
          .serializeAsRawProperty(bean, value, generator, provider);
      return;
    }
    if (value == null) {
      serializeSuppressedNullProperty(generator, provider);
      return;
    }

    JsonSerializer<Object> serializer = resolveSerializer(value, provider);
    if (isValueSuppressed(provider, value, serializer)) {
      return;
    }

    writePropertyValue(bean, value, generator, provider, serializer);
  }

  private void serializeSuppressedNullProperty(JsonGenerator generator, SerializerProvider provider)
      throws IOException {
    if (delegate.isUnwrapping()) {
      return;
    }
    if (_suppressableValue != null && provider.includeFilterSuppressNulls(_suppressableValue)) {
      return;
    }
    if (_nullSerializer == null) {
      return;
    }
    generator.writeFieldName(_name);
    _nullSerializer.serialize(null, generator, provider);
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
    if (!serializer.isUnwrappingSerializer()) {
      generator.writeFieldName(_name);
    }
    if (_typeSerializer == null) {
      serializer.serialize(value, generator, provider);
    } else {
      serializer.serializeWithType(value, generator, provider, _typeSerializer);
    }
  }

  private boolean usesCustomSerializationBehavior() {
    return delegate.getClass() != BeanPropertyWriter.class
        && !(delegate instanceof UnwrappingBeanPropertyWriter);
  }
}
