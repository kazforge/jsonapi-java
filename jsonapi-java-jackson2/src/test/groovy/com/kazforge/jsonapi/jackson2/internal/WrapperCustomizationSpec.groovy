package com.kazforge.jsonapi.jackson2.internal

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.fasterxml.jackson.databind.json.JsonMapper
import spock.lang.Specification

class WrapperCustomizationSpec extends Specification {

  def "detects deserialization customization on either member"() {
    given:
    def mapper = JsonMapper.builder().build()
    def beanType = mapper.constructType(CustomizedBean)
    def introspection = mapper.deserializationConfig.classIntrospector.forDeserialization(
        mapper.deserializationConfig, beanType, mapper.deserializationConfig)
    def plainDef = introspection.findProperties().find { it.internalName == "plain" }
    def customDef = introspection.findProperties().find { it.internalName == "custom" }

    expect:
    !WrapperCustomization.has(mapper, plainDef.primaryType, plainDef.accessor, plainDef.mutator)
    WrapperCustomization.has(mapper, customDef.primaryType, customDef.accessor, customDef.mutator)
    WrapperCustomization.hasDeserialization(mapper, customDef.primaryType, customDef.mutator)
    !WrapperCustomization.hasDeserialization(mapper, plainDef.primaryType, plainDef.mutator)
    !WrapperCustomization.hasDeserialization(mapper, plainDef.primaryType, null)
  }

  def "detects serialization customization on either member"() {
    given:
    def mapper = JsonMapper.builder().build()
    def beanType = mapper.constructType(CustomizedBean)
    def introspection = mapper.serializationConfig.classIntrospector.forSerialization(
        mapper.serializationConfig, beanType, mapper.serializationConfig)
    def plainDef = introspection.findProperties().find { it.internalName == "plain" }
    def customDef = introspection.findProperties().find { it.internalName == "custom" }

    expect:
    WrapperCustomization.has(mapper, customDef.primaryType, customDef.accessor, customDef.mutator)
    !WrapperCustomization.has(mapper, plainDef.primaryType, plainDef.accessor, plainDef.mutator)
  }

  def "null members never count as customization"() {
    given:
    def mapper = JsonMapper.builder().build()
    def type = mapper.constructType(String)

    expect:
    !WrapperCustomization.has(mapper, type, null, null)
    !WrapperCustomization.hasDeserialization(mapper, type, null)
  }

  static class UpperDeserializer extends JsonDeserializer<String> {
    @Override
    String deserialize(JsonParser p, DeserializationContext c) {
      return p.valueAsString?.toUpperCase()
    }
  }

  static class UpperSerializer extends JsonSerializer<String> {
    @Override
    void serialize(String v, JsonGenerator g, SerializerProvider s) {
      g.writeString(v.toUpperCase())
    }
  }

  static class CustomizedBean {
    String plain
    @JsonDeserialize(using = UpperDeserializer)
    @JsonSerialize(using = UpperSerializer)
    String custom
  }
}
