package com.kazforge.jsonapi.jackson3.internal;

import java.util.List;
import tools.jackson.databind.BeanDescription;
import tools.jackson.databind.SerializationConfig;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.ser.BeanPropertyWriter;
import tools.jackson.databind.ser.ValueSerializerModifier;

/** Registers raw-value-capable copies of Jackson's bean property writers. */
final class RawValuePropertyModule extends SimpleModule {

  RawValuePropertyModule() {
    super("jsonapi-java-raw-property-values");
    setSerializerModifier(new RawValuePropertyModifier());
  }

  private static final class RawValuePropertyModifier extends ValueSerializerModifier {

    @Override
    public List<BeanPropertyWriter> changeProperties(
        SerializationConfig config,
        BeanDescription.Supplier beanDescription,
        List<BeanPropertyWriter> properties) {
      for (int i = 0; i < properties.size(); i++) {
        BeanPropertyWriter property = properties.get(i);
        if (!property.isUnwrapping() && !(property instanceof RawValueBeanPropertyWriter)) {
          properties.set(i, new RawValueBeanPropertyWriter(property));
        }
      }
      return properties;
    }
  }
}
