package io.github.kazemek.jsonapi.jackson2.internal

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.fasterxml.jackson.databind.json.JsonMapper
import io.github.kazemek.jsonapi.jackson.patch.PatchPresence
import spock.lang.Specification

class PropertyScopedConvertSpec extends Specification {

  def "convert falls back to convertValue when no bean property exists"() {
    given:
    def mapper = JsonMapper.builder().build()
    def converter = new PropertyScopedValueConverter(mapper)
    def beanType = mapper.constructType(PlainBean)

    when:
    def result = converter.convert(beanType, "missing", mapper.constructType(String), mapper.constructType(String), "hello")

    then:
    result == "hello"
  }

  def "convert falls back for non-bean containing types"() {
    given:
    def mapper = JsonMapper.builder().build()
    def converter = new PropertyScopedValueConverter(mapper)

    when:
    def result = converter.convert(mapper.constructType(Map), "key", mapper.constructType(String), mapper.constructType(String), "v")

    then:
    result == "v"
  }

  def "convert runs the property deserializer when customization is present"() {
    given:
    def mapper = JsonMapper.builder().build()
    def converter = new PropertyScopedValueConverter(mapper)
    def beanType = mapper.constructType(LoudBean)

    when:
    def declared = mapper.deserializationConfig.classIntrospector.forDeserialization(
        mapper.deserializationConfig, beanType, mapper.deserializationConfig)
        .findProperties().find { it.internalName == "title" }.primaryType
    def result = converter.convert(beanType, "title", declared, declared, "hello")

    then:
    result == "HELLO"
  }

  def "convert skips the property route when PatchPresence is unwrapped"() {
    given:
    def mapper = JsonMapper.builder().build()
    def converter = new PropertyScopedValueConverter(mapper)
    def beanType = mapper.constructType(PlainBean)
    def declared = mapper.typeFactory.constructParametricType(PatchPresence, String)
    def target = mapper.constructType(String)

    when:
    def result = converter.convert(beanType, "title", declared, target, "hello")

    then:
    result == "hello"
  }

  static class PlainBean {
    String title
  }

  static class LoudBean {
    @JsonDeserialize(using = UpperDeserializer)
    String title
  }

  static class UpperDeserializer extends StdDeserializer<String> {
    UpperDeserializer() {
      super(String)
    }
    @Override
    String deserialize(JsonParser p, DeserializationContext c) {
      return p.valueAsString?.toUpperCase()
    }
  }
}
