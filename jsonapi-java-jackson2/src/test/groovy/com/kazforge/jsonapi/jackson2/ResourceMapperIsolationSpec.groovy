package com.kazforge.jsonapi.jackson2

import com.kazforge.jsonapi.annotation.JsonApiAttribute
import com.kazforge.jsonapi.annotation.JsonApiId
import com.kazforge.jsonapi.annotation.JsonApiResource
import spock.lang.Specification
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.exc.InvalidDefinitionException
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.databind.module.SimpleModule
import java.io.IOException
import java.util.Optional

// Adapter-specific: mapper isolation behavior and the Optional-support fallback of this major's own
// factory, deliberately kept local to this adapter spec.
class ResourceMapperIsolationSpec extends Specification {

  static class SampleBean {
    String name

    SampleBean() {}

    SampleBean(String name) {
      this.name = name
    }

    String getName() {
      return name
    }

    void setName(String name) {
      this.name = name
    }
  }

  def "deriving a resource mapper does not change caller ordinary serialization"() {
    given:
    def caller = JsonMapper.builder().build()
    def before = caller.writeValueAsString(new SampleBean("alpha"))

    when:
    JsonApiJackson2.resourceMapper(caller)
    def after = caller.writeValueAsString(new SampleBean("alpha"))

    then:
    before == after
    before == '{"name":"alpha"}'
  }

  @JsonApiResource(type = "optional-articles")
  static class OptionalAttributeArticle {
    @JsonApiId String id
    @JsonApiAttribute Optional<String> subtitle

    OptionalAttributeArticle(String id, Optional<String> subtitle) {
      this.id = id
      this.subtitle = subtitle
    }
  }

  def "default mapper lacking Optional support receives the JDK 8 fallback"() {
    given:
    def mapper = JsonApiJackson2.resourceMapper(JsonMapper.builder().build())
    def article = new OptionalAttributeArticle("1", Optional.of("Sub"))

    when:
    def resource = mapper.toResource(article)

    then:
    resource.attributes().attributes() == [subtitle: "Sub"]
  }

  def "the fallback stays on the derived mapper; the caller mapper is not upgraded"() {
    given:
    def caller = JsonMapper.builder().build()

    when:
    JsonApiJackson2.resourceMapper(caller)
    caller.writeValueAsString(Optional.of("x"))

    then:
    def ex = thrown(InvalidDefinitionException)
    ex.message.contains("jackson-datatype-jdk8")
  }

  def "caller-supplied Optional serialization is preserved without the fallback"() {
    given:
    // A caller-configured Optional serializer emits a wrapped shape; the adapter must not replace
    // it with the JDK 8 module.
    def callerMapper = JsonMapper.builder()
        .addModule(new SimpleModule("optional-wrapped").addSerializer(new WrappedOptionalSerializer()))
        .build()
    def mapper = JsonApiJackson2.resourceMapper(callerMapper)
    def article = new OptionalAttributeArticle("1", Optional.of("Sub"))

    when:
    def resource = mapper.toResource(article)

    then:
    resource.attributes().attributes() == [subtitle: ["wrapped": "Sub"]]
  }

  static class WrappedOptionalSerializer extends JsonSerializer<Optional<?>> {
    WrappedOptionalSerializer() {
      super()
    }

    @Override
    Class<Optional<?>> handledType() {
      return (Class<Optional<?>>) (Class<?>) Optional
    }

    @Override
    void serialize(Optional<?> value, JsonGenerator gen, SerializerProvider serializers)
    throws IOException {
      gen.writeStartObject()
      gen.writeFieldName("wrapped")
      serializers.defaultSerializeValue(value.get(), gen)
      gen.writeEndObject()
    }
  }
}
