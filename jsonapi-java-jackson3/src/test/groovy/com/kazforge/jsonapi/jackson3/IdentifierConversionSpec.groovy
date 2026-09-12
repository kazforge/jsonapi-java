package com.kazforge.jsonapi.jackson3

import com.kazforge.jsonapi.annotation.JsonApiAttribute
import com.kazforge.jsonapi.annotation.JsonApiId
import com.kazforge.jsonapi.annotation.JsonApiResource
import com.kazforge.jsonapi.jackson.mapping.IdentifierConverter
import com.kazforge.jsonapi.fixtures.domainwrite.Person
import spock.lang.Specification
import tools.jackson.databind.json.JsonMapper

// Adapter-specific: identifier conversion is wired through each major's mapper factory
// (IdentifierConverter overloads), so it is deliberately kept local to this adapter spec.
class IdentifierConversionSpec extends Specification {

  def "custom IdentifierConverter replaces Object::toString"() {
    given:
    def converter = new IdentifierConverter() {
          @Override
          String convert(Object idValue) {
            return "prefix-" + idValue.toString()
          }
        }
    def mapper = JsonApiJackson3.resourceMapper(JsonMapper.builder().build(), converter)
    def person = new Person("42", "Alice")

    when:
    def resource = mapper.toResource(person)

    then:
    resource.id() == "prefix-42"
  }

  @JsonApiResource(type = "intids")
  static class IntIdEntity {
    @JsonApiId Integer id
    @JsonApiAttribute String name

    IntIdEntity() {}

    IntIdEntity(Integer id, String name) {
      this.id = id
      this.name = name
    }
  }

  def "default converter calls toString on non-string ids"() {
    given:
    def mapper = JsonApiJackson3.resourceMapper(JsonMapper.builder().build())
    def entity = new IntIdEntity(42, "test")

    when:
    def resource = mapper.toResource(entity)

    then:
    resource.id() == "42"
  }
}
