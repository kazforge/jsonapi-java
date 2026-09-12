package com.kazforge.jsonapi.jackson3.internal

import com.kazforge.jsonapi.jackson.internal.patch.PresenceMarker
import com.kazforge.jsonapi.jackson.patch.PatchPresence
import spock.lang.Specification
import tools.jackson.core.JsonGenerator
import tools.jackson.databind.SerializationContext
import tools.jackson.databind.ValueSerializer
import tools.jackson.databind.exc.MismatchedInputException
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.module.SimpleModule

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
    def ex = thrown(MismatchedInputException)
    ex.message.contains("Invalid internal PatchPresence marker")
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

  static class PresenceTestModule extends SimpleModule {
    PresenceTestModule(ValueSerializer markerSerializer) {
      super("presence-test")
      addDeserializer(PatchPresence.class, new PatchPresenceDeserializer())
      addSerializer(PresenceMarker.class, markerSerializer)
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
  static class MangledMarkerSerializer extends ValueSerializer<PresenceMarker> {
    @Override
    void serialize(PresenceMarker marker, JsonGenerator gen, SerializationContext ctxt) {
      gen.writeStartObject()
      gen.writeBooleanProperty("Present", marker.present())
      gen.writeName("Value")
      gen.writeString("mangled")
      gen.writeEndObject()
    }
  }
}
