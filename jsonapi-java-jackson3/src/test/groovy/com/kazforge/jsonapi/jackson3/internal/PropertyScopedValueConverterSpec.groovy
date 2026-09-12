package com.kazforge.jsonapi.jackson3.internal

import spock.lang.Specification
import tools.jackson.core.JsonGenerator
import tools.jackson.databind.BeanDescription
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.SerializationConfig
import tools.jackson.databind.SerializationContext
import tools.jackson.databind.ValueSerializer
import tools.jackson.databind.annotation.JsonSerialize
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.module.SimpleModule
import tools.jackson.databind.ser.BeanPropertyWriter
import tools.jackson.databind.ser.ValueSerializerModifier
import tools.jackson.databind.util.NameTransformer

class PropertyScopedValueConverterSpec extends Specification {

  def "property writes remain omitted when a bean serializer has no matching writer"() {
    given:
    def mapper = JsonMapper.builder().build()
    def converter = new PropertyScopedValueConverter(mapper)

    when:
    def result = converter.serialize(
        mapper.constructType(WriteBean), "missing", new WriteBean("value"), "raw", "fallback")

    then:
    !result.emitted()
    result.value() == null
  }

  def "property writes fall back when the containing value has no bean serializer"() {
    given:
    def mapper = JsonMapper.builder().build()
    def converter = new PropertyScopedValueConverter(mapper)

    expect:
    converter.serialize(
        mapper.constructType(Map), "missing", [value: "raw"], "raw", "fallback")
        .value() == "fallback"
  }

  def "property writes reject a replaced property writer without rereading"() {
    given:
    def mapper = JsonMapper.builder()
        .addModule(new ReplacementPropertyModule())
        .build()
    def converter = new PropertyScopedValueConverter(mapper)

    when:
    converter.serialize(
        mapper.constructType(WriteBean), "value", new WriteBean("raw"), "raw", "fallback")

    then:
    thrown(UnsupportedOperationException)
  }

  def "raw property writer delegates ordinary writer lifecycle methods"() {
    given:
    def mapper = JsonMapper.builder().build()
    def context = mapper._serializationContext()
    def root = context.findRootValueSerializer(mapper.constructType(WriteBean))
    def property = root.properties().find { it.name == "value" } as BeanPropertyWriter
    def rawProperty = new RawValueBeanPropertyWriter(property)
    def buffer = context.bufferForValueConversion()
    def bean = new WriteBean("value")

    when:
    rawProperty.serializeAsProperty(bean, buffer, context)
    rawProperty.serializeAsElement(bean, buffer, context)
    rawProperty.serializeAsOmittedProperty(bean, buffer, context)
    rawProperty.serializeAsOmittedElement(bean, buffer, context)
    rawProperty.depositSchemaProperty(null, context)
    rawProperty.rename(NameTransformer.NOP)
    rawProperty.unwrappingWriter(NameTransformer.NOP)
    rawProperty.assignTypeSerializer(null)

    then:
    !rawProperty.isUnwrapping()
  }

  def "property writes return null when the assigned null serializer emits no value"() {
    given:
    def mapper = JsonMapper.builder().build()
    def converter = new PropertyScopedValueConverter(mapper)

    when:
    def result = converter.serialize(
        mapper.constructType(EmptyNullBean), "value", new EmptyNullBean(null), null, null)

    then:
    !result.emitted()
    result.value() == null
  }

  def "property writes preserve configured BigDecimal parsing for numeric values"() {
    given:
    def mapper = JsonMapper.builder()
        .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
        .build()
    def converter = new PropertyScopedValueConverter(mapper)

    when:
    def value = converter.serialize(
        mapper.constructType(DecimalBean), "amount", new DecimalBean(new BigDecimal("1.50")),
        new BigDecimal("1.50"), null).value()

    then:
    value == new BigDecimal("1.50")
    value.class == BigDecimal
  }

  static class WriteBean {
    String value

    WriteBean(String value) {
      this.value = value
    }
  }

  static class EmptyNullBean {
    @JsonSerialize(nullsUsing = EmptyNullSerializer)
    String value

    EmptyNullBean(String value) {
      this.value = value
    }
  }

  static class DecimalBean {
    @JsonSerialize(using = DecimalSerializer)
    BigDecimal amount

    DecimalBean(BigDecimal amount) {
      this.amount = amount
    }
  }

  static class EmptyNullSerializer extends ValueSerializer<Object> {
    @Override
    void serialize(Object value, JsonGenerator generator, SerializationContext context) {}
  }

  static class DecimalSerializer extends ValueSerializer<BigDecimal> {
    @Override
    void serialize(BigDecimal value, JsonGenerator generator, SerializationContext context) {
      generator.writeNumber(value)
    }
  }

  static class ReplacementPropertyModule extends SimpleModule {
    ReplacementPropertyModule() {
      super("replacement-property")
      setSerializerModifier(new ReplacementPropertyModifier())
    }
  }

  static class ReplacementPropertyModifier extends ValueSerializerModifier {
    @Override
    List<BeanPropertyWriter> changeProperties(
        SerializationConfig config,
        BeanDescription.Supplier beanDescription,
        List<BeanPropertyWriter> properties) {
      def index = properties.findIndexOf { it.name == "value" }
      properties[index] = new ReplacementPropertyWriter(properties[index])
      properties
    }
  }

  static class ReplacementPropertyWriter extends BeanPropertyWriter {
    ReplacementPropertyWriter(BeanPropertyWriter base) {
      super(base)
    }

    @Override
    void serializeAsProperty(Object bean, JsonGenerator generator, SerializationContext context) {
      generator.writeName(name)
      generator.writeString("replacement")
    }
  }
}
