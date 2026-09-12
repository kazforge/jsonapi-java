package com.kazforge.jsonapi.jackson2.internal;

import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;
import com.fasterxml.jackson.databind.ser.BeanSerializerModifier;
import java.util.List;

/** Registers raw-value-capable copies of Jackson's bean property writers. */
final class RawValuePropertyModule extends SimpleModule {

  RawValuePropertyModule() {
    super("jsonapi-java-raw-property-values");
    setSerializerModifier(new RawValuePropertyModifier());
  }

  private static final class RawValuePropertyModifier extends BeanSerializerModifier {

    @Override
    public List<BeanPropertyWriter> changeProperties(
        SerializationConfig config,
        BeanDescription beanDescription,
        List<BeanPropertyWriter> properties) {
      for (int i = 0; i < properties.size(); i++) {
        BeanPropertyWriter property = properties.get(i);
        if (!property.isUnwrapping()
            && !(property instanceof RawValueBeanPropertyWriter)
            && !(property instanceof RawValueUnwrappingBeanPropertyWriter)) {
          properties.set(i, new RawValueBeanPropertyWriter(property));
        }
      }
      return properties;
    }
  }
}
