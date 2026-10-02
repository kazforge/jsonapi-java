package com.kazforge.jsonapi.jackson3.internal

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonUnwrapped
import spock.lang.Specification
import tools.jackson.databind.cfg.MapperConfig
import tools.jackson.databind.introspect.AnnotatedMember
import tools.jackson.databind.introspect.JacksonAnnotationIntrospector
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.ser.BeanPropertyWriter
import tools.jackson.databind.ser.bean.BeanSerializerBase
import tools.jackson.databind.util.NameTransformer

class RawValueUnwrappingSpec extends Specification {
  def "dynamic Object unwrapping retains prefix and suffix"() {
    given:
    def mapper = JsonMapper.builder().build()
    def bean = new Wrapped(new Detail("value"))

    expect:
    writeSuppliedValue(mapper, bean, bean.details) == [pre_value_post: "value"]
    mapper.readValue(mapper.writeValueAsString(bean), Map) == [pre_value_post: "value"]
  }

  def "cached dynamic unwrapping retains transformed member names"() {
    given:
    def mapper = JsonMapper.builder().build()
    def bean = new Wrapped(new Detail("cached"))
    mapper.writeValueAsString(bean)

    expect:
    writeSuppliedValue(mapper, bean, bean.details) == [pre_value_post: "cached"]
  }

  def "dynamic unwrapping retains the declared generic base type"() {
    given:
    def mapper = JsonMapper.builder().build()
    def bean = new GenericWrapped(new GenericDetail<String>("generic"))

    expect:
    writeSuppliedValue(mapper, bean, bean.details) == [generic_value: "generic"]
    mapper.readValue(mapper.writeValueAsString(bean), Map) == [generic_value: "generic"]
  }

  def "configured introspector controls dynamic unwrapping without annotations"() {
    given:
    def mapper = JsonMapper.builder().annotationIntrospector(new CustomUnwrapping()).build()
    def bean = new CustomWrapped(new Detail("custom"))

    expect:
    writeSuppliedValue(mapper, bean, bean.details) == [custom_value_end: "custom"]
    mapper.readValue(mapper.writeValueAsString(bean), Map) == [custom_value_end: "custom"]
  }

  def "nested dynamic unwrapping composes prefix and suffix"() {
    given:
    def mapper = JsonMapper.builder().build()
    def bean = new Wrapped(new Nested(new Detail("nested")))

    expect:
    writeSuppliedValue(mapper, bean, bean.details) == [pre_inner_value_tail_post: "nested"]
    mapper.readValue(mapper.writeValueAsString(bean), Map) == [pre_inner_value_tail_post: "nested"]
  }

  def "raw unwrapping does not read the supplied accessor again"() {
    given:
    def mapper = JsonMapper.builder().build()
    def bean = new CountingWrapped()
    def captured = bean.details

    expect:
    writeSuppliedValue(mapper, bean, captured) == [count_value: "once"]
    bean.reads == 1
  }

  def "renamed raw unwrapping preserves the native composed prefix and suffix"() {
    given:
    def mapper = JsonMapper.builder().build()
    def context = mapper._serializationContext()
    def bean = new CountingWrapped()
    def supplied = bean.details
    def root = context.findRootValueSerializer(mapper.constructType(CountingWrapped))
    def delegate = root.properties().find { it.name == "details" } as BeanPropertyWriter
    def outer = NameTransformer.simpleTransformer("outer_", "_end")
    def renamed = new RawValueBeanPropertyWriter(delegate).rename(outer) as RawValueBeanPropertyWriter
    def output = new StringWriter()

    when:
    mapper.createGenerator(output).withCloseable { generator ->
      generator.writeStartObject()
      renamed.serializeAsRawProperty(bean, supplied, generator, context)
      generator.writeEndObject()
    }

    then:
    mapper.readValue(output.toString(), Map) == [outer_count_value_end: "once"]
    bean.reads == 1

    when:
    def nativeOutput = new StringWriter()
    mapper.createGenerator(nativeOutput).withCloseable { generator ->
      generator.writeStartObject()
      delegate.rename(outer).serializeAsProperty(bean, generator, context)
      generator.writeEndObject()
    }

    then:
    mapper.readValue(nativeOutput.toString(), Map) == [outer_count_value_end: "once"]
  }

  def "programmatic unwrapping retains its transformer through renaming"() {
    given:
    def mapper = JsonMapper.builder().build()
    def context = mapper._serializationContext()
    def bean = new CustomWrapped(new Detail("programmatic"))
    def root = context.findRootValueSerializer(mapper.constructType(CustomWrapped))
    def delegate = root.properties().find { it.name == "details" } as BeanPropertyWriter
    def inner = NameTransformer.simpleTransformer("inner_", "_tail")
    def outer = NameTransformer.simpleTransformer("outer_", "_end")
    def writer = new RawValueBeanPropertyWriter(delegate)
        .unwrappingWriter(inner).rename(outer) as RawValueBeanPropertyWriter
    def output = new StringWriter()

    when:
    mapper.createGenerator(output).withCloseable { generator ->
      generator.writeStartObject()
      writer.serializeAsRawProperty(bean, bean.details, generator, context)
      generator.writeEndObject()
    }

    then:
    mapper.readValue(output.toString(), Map) == [outer_inner_value_tail_end: "programmatic"]
  }

  def "a previously renamed native writer retains its resolved transformer without mutation"() {
    given:
    def mapper = JsonMapper.builder().build()
    def context = mapper._serializationContext()
    def bean = new CountingWrapped()
    def supplied = bean.details
    def root = context.findRootValueSerializer(mapper.constructType(CountingWrapped))
    def property = root.properties().find { it.name == "details" } as BeanPropertyWriter
    def delegate = property.rename(NameTransformer.simpleTransformer("outer_", "_end"))
    def writer = new RawValueBeanPropertyWriter(delegate)
    def output = new StringWriter()

    when:
    mapper.createGenerator(output).withCloseable { generator ->
      generator.writeStartObject()
      writer.serializeAsRawProperty(bean, supplied, generator, context)
      generator.writeEndObject()
    }

    then:
    mapper.readValue(output.toString(), Map) == [outer_count_value_end: "once"]
    bean.reads == 1
    delegate.serializer == null
  }

  def "unwrapped null and empty values preserve native suppression"() {
    given:
    def mapper = JsonMapper.builder().build()
    def bean = new Wrapped(value)

    expect:
    writeSuppliedValue(mapper, bean, value) == [:]
    mapper.readValue(mapper.writeValueAsString(bean), Map) == [:]

    where:
    value << [null, ""]
  }

  private static Map writeSuppliedValue(JsonMapper mapper, Object bean, Object supplied) {
    def context = mapper._serializationContext()
    def serializer = (BeanSerializerBase) context.findRootValueSerializer(mapper.constructType(bean.class))
    def delegate = serializer.properties().find { it.name == "details" } as BeanPropertyWriter
    def writer = new RawValueBeanPropertyWriter(delegate)
    def output = new StringWriter()
    mapper.createGenerator(output).withCloseable { generator ->
      generator.writeStartObject()
      writer.serializeAsRawProperty(bean, supplied, generator, context)
      generator.writeEndObject()
    }
    mapper.readValue(output.toString(), Map)
  }

  static class Detail {
    public String value
    Detail(String value) {
      this.value = value
    }
  }

  static class Wrapped {
    @JsonUnwrapped(prefix = "pre_", suffix = "_post")
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public Object details
    Wrapped(Object details) {
      this.details = details
    }
  }

  static class Nested {
    @JsonUnwrapped(prefix = "inner_", suffix = "_tail")
    public Object child
    Nested(Object child) {
      this.child = child
    }
  }

  static class CustomWrapped {
    public Object details
    CustomWrapped(Object details) {
      this.details = details
    }
  }

  static class GenericWrapped {
    @JsonUnwrapped(prefix = "generic_")
    public GenericDetail<String> details
    GenericWrapped(GenericDetail<String> details) {
      this.details = details
    }
  }

  static class GenericDetail<T> {
    public T value
    GenericDetail(T value) {
      this.value = value
    }
  }

  static class CustomUnwrapping extends JacksonAnnotationIntrospector {
    @Override
    NameTransformer findUnwrappingNameTransformer(MapperConfig config, AnnotatedMember member) {
      member.declaringClass == CustomWrapped && member.name == "details"
          ? NameTransformer.simpleTransformer("custom_", "_end")
          : super.findUnwrappingNameTransformer(config, member)
    }
  }

  static class CountingWrapped {
    private int reads
    @JsonUnwrapped(prefix = "count_")
    Object getDetails() {
      reads++
      new Detail("once")
    }
  }
}
