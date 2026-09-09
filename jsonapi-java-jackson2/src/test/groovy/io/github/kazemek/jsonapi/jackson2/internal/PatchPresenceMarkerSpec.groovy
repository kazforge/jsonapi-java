package io.github.kazemek.jsonapi.jackson2.internal

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.databind.module.SimpleModule
import io.github.kazemek.jsonapi.jackson.patch.PatchPresence
import io.github.kazemek.jsonapi.jackson.internal.patch.PresenceMarker
import spock.lang.Specification

class PatchPresenceMarkerSpec extends Specification {

  def "internal serializer round-trips the full tri-state with exact member names"() {
    given:
    def mapper = JsonMapper.builder()
        .addModule(new PresenceTestModule(PresenceMarkerSerializer.INSTANCE))
        .build()
    def targetType = mapper.constructType(BeanWithPresence)

    expect:
    mapper.convertValue([title: new PresenceMarker(false, null)], targetType).title ==
    PatchPresence.omitted()
    mapper.convertValue([title: new PresenceMarker(true, "x")], targetType).title ==
    PatchPresence.present("x")
    mapper.convertValue([title: new PresenceMarker(true, null)], targetType).title ==
    PatchPresence.present(null)
  }

  def "mangled marker member names fail loudly instead of silently reconstructing Omitted"() {
    given:
    def mapper = JsonMapper.builder()
        .addModule(new PresenceTestModule(new MangledMarkerSerializer()))
        .build()
    def targetType = mapper.constructType(BeanWithPresence)

    when:
    mapper.convertValue([title: new PresenceMarker(true, "x")], targetType)

    then:
    def ex = thrown(IllegalArgumentException)
    ex.cause?.message?.contains("Invalid internal PatchPresence marker")
  }

  def "unresolved abstract inner type binds explicit null to Present(null) without NPE"() {
    given:
    def mapper = JsonMapper.builder()
        .addModule(new PresenceTestModule(PresenceMarkerSerializer.INSTANCE))
        .build()
    def targetType = mapper.constructType(BeanWithAbstractPresence)

    expect:
    mapper.convertValue([title: new PresenceMarker(true, null)], targetType).title ==
    PatchPresence.present(null)
  }

  def "non-object markers fail loudly"() {
    given:
    def mapper = JsonMapper.builder()
        .addModule(new PresenceTestModule(PresenceMarkerSerializer.INSTANCE))
        .build()
    def targetType = mapper.constructType(BeanWithPresence)

    when:
    mapper.convertValue([title: "not-a-marker"], targetType)

    then:
    def ex = thrown(IllegalArgumentException)
    ex.cause?.message?.contains("Invalid internal PatchPresence marker")
  }

  def "markers with a non-boolean present fail loudly"() {
    given:
    def mapper = JsonMapper.builder()
        .addModule(new PresenceTestModule(PresenceMarkerSerializer.INSTANCE))
        .build()

    when:
    mapper.readValue('{"title":{"present":"yes","value":"x"}}', BeanWithPresence)

    then:
    def ex = thrown(Exception)
    ex.message?.contains("Invalid internal PatchPresence marker") || ex.cause?.message?.contains("Invalid internal PatchPresence marker")
  }

  def "markers missing the present member fail loudly"() {
    given:
    def mapper = JsonMapper.builder()
        .addModule(new PresenceTestModule(PresenceMarkerSerializer.INSTANCE))
        .build()

    when:
    mapper.readValue('{"title":{"value":"x"}}', BeanWithPresence)

    then:
    def ex = thrown(Exception)
    ex.message?.contains("Invalid internal PatchPresence marker") || ex.cause?.message?.contains("Invalid internal PatchPresence marker")
  }

  def "markers with unexpected members fail loudly"() {
    given:
    def mapper = JsonMapper.builder()
        .addModule(new PresenceTestModule(PresenceMarkerSerializer.INSTANCE))
        .build()

    when:
    mapper.readValue('{"title":{"present":true,"value":"x","extra":1}}', BeanWithPresence)

    then:
    def ex = thrown(Exception)
    ex.message?.contains("Invalid internal PatchPresence marker") || ex.cause?.message?.contains("Invalid internal PatchPresence marker")
  }

  static class PresenceTestModule extends SimpleModule {
    PresenceTestModule(JsonSerializer markerSerializer) {
      super("presence-test")
      addDeserializer(PatchPresence, new PatchPresenceDeserializer())
      addSerializer(PresenceMarker, markerSerializer)
    }
  }

  static class BeanWithPresence {
    PatchPresence<String> title
  }

  static abstract class AbstractInner {}

  static class BeanWithAbstractPresence {
    PatchPresence<AbstractInner> title
  }

  /**
   * Mimics the pre-fix hazard: a marker serialized with caller-mangled member names (for example
   * an UPPER_CAMEL_CASE strategy without the internal serializer) instead of the deterministic
   * {@code present}/{@code value} names.
   */
  static class MangledMarkerSerializer extends JsonSerializer<PresenceMarker> {
    @Override
    void serialize(PresenceMarker marker, JsonGenerator gen, SerializerProvider serializers) {
      gen.writeStartObject()
      gen.writeBooleanField("Present", marker.present())
      gen.writeFieldName("Value")
      gen.writeString("mangled")
      gen.writeEndObject()
    }
  }
}
