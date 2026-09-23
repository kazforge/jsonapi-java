package com.kazforge.jsonapi.jackson2.internal

import com.fasterxml.jackson.databind.JavaType
import com.fasterxml.jackson.databind.json.JsonMapper
import com.kazforge.jsonapi.patch.PatchPresence
import java.net.URI
import java.time.Instant
import java.util.UUID
import spock.lang.Specification

/**
 * Focused rules for {@link WholeMetaTarget} read/write and low-level target validity: at most one
 * {@code Optional} wrapper around a Bean / Map / Object target, with primitives, containers,
 * wrapper chains, and scalar/JDK targets rejected.
 */
class WholeMetaTargetSpec extends Specification {

  private final JsonMapper mapper = JsonMapper.builder().build()
  private final WholeMetaTarget target = new WholeMetaTarget(mapper)

  def "accepts Bean, Map, and Object targets"() {
    expect:
    !target.invalidReadWriteTarget(mapper.constructType(Bean))
    !target.invalidReadWriteTarget(mapper.constructType(Map))
    !target.invalidReadWriteTarget(mapper.constructType(Object))
  }

  def "accepts a single Optional wrapper around a target"() {
    expect:
    !target.invalidReadWriteTarget(parameterized(Optional, mapper.constructType(Bean)))
    !target.invalidReadWriteTarget(parameterized(Optional, mapper.constructType(Map)))
    !target.invalidReadWriteTarget(parameterized(Optional, mapper.constructType(Object)))
  }

  def "rejects wrapper chains, primitives, containers, and scalar targets"() {
    expect:
    target.invalidReadWriteTarget(
        parameterized(Optional, parameterized(Optional, mapper.constructType(Bean))))
    target.invalidReadWriteTarget(mapper.constructType(int))
    target.invalidReadWriteTarget(mapper.constructType(Integer))
    target.invalidReadWriteTarget(mapper.constructType(String))
    target.invalidReadWriteTarget(mapper.constructType(String[].class))
    target.invalidReadWriteTarget(mapper.constructType(List))
    target.invalidReadWriteTarget(mapper.constructType(UUID))
    target.invalidReadWriteTarget(mapper.constructType(URI))
    target.invalidReadWriteTarget(mapper.constructType(Instant))
  }

  def "rejects a PatchPresence wrapper as an ordinary read/write meta target"() {
    expect:
    target.invalidReadWriteTarget(parameterized(PatchPresence, mapper.constructType(Bean)))
    target.invalidReadWriteTarget(parameterized(Optional, parameterized(PatchPresence, mapper.constructType(Bean))))
    target.invalidReadWriteTarget(mapper.constructType(PatchPresence.Present))
    target.invalidReadWriteTarget(mapper.constructType(PatchPresence.Omitted))
    target.invalidReadWriteTarget(mapper.constructType(Optional))
  }

  private JavaType parameterized(Class<?> raw, JavaType... arguments) {
    mapper.typeFactory.constructParametricType(raw, arguments)
  }

  static class Bean {
    String value
  }
}
