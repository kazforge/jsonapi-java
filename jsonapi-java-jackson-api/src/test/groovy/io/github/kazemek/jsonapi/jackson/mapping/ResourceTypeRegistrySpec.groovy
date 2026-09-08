package io.github.kazemek.jsonapi.jackson.mapping

import io.github.kazemek.jsonapi.jackson.diagnostic.JsonApiMappingException
import io.github.kazemek.jsonapi.jackson.diagnostic.MappingDiagnostic
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import spock.lang.Specification

class ResourceTypeRegistrySpec extends Specification {

  def "registers literal raw and parameterized targets in insertion order"() {
    given:
    Type parameterized = parameterizedType(List, String)

    when:
    def registry = ResourceTypeRegistry.builder()
        .register("articles", String)
        .register("article-lists", parameterized)
        .build()

    then:
    registry.resolve("articles").targetType() == String
    registry.resolve("article-lists").targetType() == parameterized
    registry.registrations()*.jsonApiType() == ["articles", "article-lists"]

    when:
    registry.registrations().add(registry.resolve("articles"))

    then:
    thrown(UnsupportedOperationException)
  }

  def "rejects invalid and duplicate wire types without a location"() {
    when:
    ResourceTypeRegistry.builder().register("bad/type", String).build()

    then:
    def invalid = thrown(JsonApiMappingException)
    invalid.diagnostic() == MappingDiagnostic.INVALID_RESOURCE_TYPE
    invalid.location() == null

    when:
    ResourceTypeRegistry.builder()
        .register("articles", String)
        .register("articles", Integer)
        .build()

    then:
    def duplicate = thrown(JsonApiMappingException)
    duplicate.diagnostic() == MappingDiagnostic.CONFLICTING_TYPE_REGISTRATION
    duplicate.location() == null
  }

  private static ParameterizedType parameterizedType(Class<?> raw, Type argument) {
    [
      getActualTypeArguments: { [argument] as Type[] },
      getRawType: { raw },
      getOwnerType: {
        null
      }
    ] as ParameterizedType
  }
}
