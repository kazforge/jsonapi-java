package com.kazforge.jsonapi.jackson3.internal;

import org.jspecify.annotations.Nullable;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.jsonFormatVisitors.JsonObjectFormatVisitor;
import tools.jackson.databind.jsontype.TypeSerializer;
import tools.jackson.databind.ser.BeanPropertyWriter;
import tools.jackson.databind.ser.bean.UnwrappingBeanPropertyWriter;
import tools.jackson.databind.ser.impl.PropertySerializerMap;
import tools.jackson.databind.util.NameTransformer;

/**
 * A {@link BeanPropertyWriter} that applies its resolved writer state to a supplied property value.
 *
 * <p>The ordinary writer reads its accessor inside {@link #serializeAsProperty}. Property-scoped
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
    return new RawValueBeanPropertyWriter(delegate.unwrappingWriter(unwrapper));
  }

  @Override
  public boolean isUnwrapping() {
    return delegate.isUnwrapping();
  }

  @Override
  public void assignSerializer(ValueSerializer<Object> serializer) {
    delegate.assignSerializer(serializer);
    super.assignSerializer(delegate.getSerializer());
  }

  @Override
  public void assignNullSerializer(ValueSerializer<Object> nullSerializer) {
    delegate.assignNullSerializer(nullSerializer);
    super.assignNullSerializer(nullSerializer);
  }

  @Override
  public void assignTypeSerializer(TypeSerializer typeSerializer) {
    delegate.assignTypeSerializer(typeSerializer);
    super.assignTypeSerializer(typeSerializer);
  }

  @Override
  public void serializeAsProperty(
      Object bean, JsonGenerator generator, SerializationContext context) throws Exception {
    delegate.serializeAsProperty(bean, generator, context);
  }

  @Override
  public void serializeAsOmittedProperty(
      Object bean, JsonGenerator generator, SerializationContext context) throws Exception {
    delegate.serializeAsOmittedProperty(bean, generator, context);
  }

  @Override
  public void serializeAsElement(Object bean, JsonGenerator generator, SerializationContext context)
      throws Exception {
    delegate.serializeAsElement(bean, generator, context);
  }

  @Override
  public void serializeAsOmittedElement(
      Object bean, JsonGenerator generator, SerializationContext context) throws Exception {
    delegate.serializeAsOmittedElement(bean, generator, context);
  }

  @Override
  public void depositSchemaProperty(JsonObjectFormatVisitor visitor, SerializationContext context) {
    delegate.depositSchemaProperty(visitor, context);
  }

  void serializeAsRawProperty(
      Object bean, @Nullable Object value, JsonGenerator generator, SerializationContext context) {
    if (usesCustomSerializationBehavior()) {
      throw new UnsupportedOperationException(
          "Property-scoped serialization does not support custom BeanPropertyWriter replacements");
    }
    if (value == null) {
      serializeSuppressedNullProperty(generator, context);
      return;
    }

    ValueSerializer<Object> serializer = resolveSerializer(value, context);
    if (isValueSuppressed(context, value, serializer)) {
      return;
    }

    writePropertyValue(bean, value, generator, context, serializer);
  }

  private void serializeSuppressedNullProperty(
      JsonGenerator generator, SerializationContext context) {
    if (delegate.isUnwrapping()) {
      return;
    }
    if (_suppressableValue != null && context.includeFilterSuppressNulls(_suppressableValue)) {
      return;
    }
    if (_nullSerializer == null) {
      return;
    }
    generator.writeName(_name);
    _nullSerializer.serialize(null, generator, context);
  }

  private ValueSerializer<Object> resolveSerializer(Object value, SerializationContext context) {
    ValueSerializer<Object> serializer = delegate.getSerializer();
    if (serializer != null) {
      return serializer;
    }
    if (delegate instanceof UnwrappingBeanPropertyWriter unwrappingProperty) {
      return unwrappingProperty.findUnwrappingSerializer(context);
    }
    Class<?> rawType = value.getClass();
    PropertySerializerMap serializers = _dynamicSerializers;
    ValueSerializer<Object> dynamicSerializer = serializers.serializerFor(rawType);
    if (dynamicSerializer == null) {
      return _findAndAddDynamic(serializers, rawType, context);
    }
    return dynamicSerializer;
  }

  private boolean isValueSuppressed(
      SerializationContext context, Object value, ValueSerializer<Object> serializer) {
    if (_suppressableValue == null) {
      return false;
    }
    if (MARKER_FOR_EMPTY == _suppressableValue) {
      return serializer.isEmpty(context, value);
    }
    return _suppressableValue.equals(value);
  }

  private void writePropertyValue(
      Object bean,
      Object value,
      JsonGenerator generator,
      SerializationContext context,
      ValueSerializer<Object> serializer) {
    if (value == bean && _handleSelfReference(bean, generator, context, serializer)) {
      return;
    }
    if (!serializer.isUnwrappingSerializer()) {
      generator.writeName(_name);
    }
    if (_typeSerializer == null) {
      serializer.serialize(value, generator, context);
    } else {
      serializer.serializeWithType(value, generator, context, _typeSerializer);
    }
  }

  private boolean usesCustomSerializationBehavior() {
    return delegate.getClass() != BeanPropertyWriter.class
        && !(delegate instanceof UnwrappingBeanPropertyWriter);
  }
}
