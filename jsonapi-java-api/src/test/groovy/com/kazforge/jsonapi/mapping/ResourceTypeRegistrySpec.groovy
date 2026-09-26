package com.kazforge.jsonapi.mapping

import com.kazforge.jsonapi.diagnostic.JsonApiMappingException
import com.kazforge.jsonapi.diagnostic.MappingDiagnostic
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import spock.lang.Specification
import spock.lang.Unroll

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

  @Unroll
  def "rejects explicit wire type #wireType as invalid without a location"() {
    when:
    ResourceTypeRegistry.builder().register(wireType, Object).build()

    then:
    def invalid = thrown(JsonApiMappingException)
    invalid.diagnostic() == MappingDiagnostic.INVALID_RESOURCE_TYPE
    invalid.resourceClass() == Object
    invalid.location() == null

    where:
    wireType << ["", "bad/type"]
  }

  def "rejects duplicate wire types without a location"() {
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
