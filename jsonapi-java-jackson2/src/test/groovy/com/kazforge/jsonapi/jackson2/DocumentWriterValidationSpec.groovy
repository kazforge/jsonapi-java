package com.kazforge.jsonapi.jackson2

import com.fasterxml.jackson.databind.json.JsonMapper

import com.kazforge.jsonapi.core.model.Attributes
import com.kazforge.jsonapi.core.model.DocumentData
import com.kazforge.jsonapi.core.model.JsonApiDocument
import com.kazforge.jsonapi.core.model.Relationship
import com.kazforge.jsonapi.core.model.RelationshipData
import com.kazforge.jsonapi.core.model.Relationships
import com.kazforge.jsonapi.core.model.ResourceObject
import com.kazforge.jsonapi.core.validation.JsonApiValidationException
import com.kazforge.jsonapi.core.validation.ValidationRuleCode

import spock.lang.Specification

class DocumentWriterValidationSpec extends Specification {

  def "invalid aggregate documents fail before string output"() {
    given:
    def writer = JsonApiJackson2.writer(JsonMapper.builder().build())

    when:
    writer.writeValueAsString(invalidDocument())

    then:
    def ex = thrown(JsonApiValidationException)
    ex.ruleCode() == ValidationRuleCode.DUPLICATE_RESOURCE_IDENTITY
  }

  def "invalid aggregate documents fail before bytes output"() {
    given:
    def writer = JsonApiJackson2.writer(JsonMapper.builder().build())

    when:
    writer.writeValueAsBytes(invalidDocument())

    then:
    def ex = thrown(JsonApiValidationException)
    ex.ruleCode() == ValidationRuleCode.DUPLICATE_RESOURCE_IDENTITY
  }

  def "invalid aggregate documents fail before OutputStream output"() {
    given:
    def writer = JsonApiJackson2.writer(JsonMapper.builder().build())
    def sink = new ByteArrayOutputStream()

    when:
    writer.writeValue(sink, invalidDocument())

    then:
    def ex = thrown(JsonApiValidationException)
    ex.ruleCode() == ValidationRuleCode.DUPLICATE_RESOURCE_IDENTITY
    sink.size() == 0
  }

  def "invalid aggregate documents fail before Writer output"() {
    given:
    def writer = JsonApiJackson2.writer(JsonMapper.builder().build())
    def sink = new StringWriter()

    when:
    writer.writeValue(sink, invalidDocument())

    then:
    def ex = thrown(JsonApiValidationException)
    ex.ruleCode() == ValidationRuleCode.DUPLICATE_RESOURCE_IDENTITY
    sink.toString().isEmpty()
  }

  def "invalid aggregate documents fail before JsonGenerator output"() {
    given:
    def writer = JsonApiJackson2.writer(JsonMapper.builder().build())
    def sink = new ByteArrayOutputStream()
    def generator = writer.mapper().createGenerator(sink)

    when:
    try {
      writer.writeValue(generator, invalidDocument())
    } finally {
      generator.close()
    }

    then:
    def ex = thrown(JsonApiValidationException)
    ex.ruleCode() == ValidationRuleCode.DUPLICATE_RESOURCE_IDENTITY
    sink.size() == 0
  }

  def "field-namespace collisions fail before any writer output"() {
    given:
    def writer = JsonApiJackson2.writer(JsonMapper.builder().build())

    when: 'a resource whose non-@ pass-through attribute collides with a relationship is written'
    writer.writeValueAsString(collidingDocument())

    then:
    def ex = thrown(JsonApiValidationException)
    ex.ruleCode() == ValidationRuleCode.MEMBER_NAME_COLLISION
    ex.jsonPointer() == "/data/relationships/author"
  }

  private static JsonApiDocument collidingDocument() {
    def attributes = Attributes.of([:], [author: "pass-through-attribute"])
    def relationships = Relationships.ofRelationships([
      author: Relationship.withData(RelationshipData.NullLinkage.INSTANCE)
    ])
    def resource = new ResourceObject(
        "articles", "1", null, attributes, relationships, null, null, [:])
    return JsonApiDocument.withData(new DocumentData.SingleResource(resource))
  }

  private static JsonApiDocument invalidDocument() {
    return new JsonApiDocument(
        new DocumentData.ResourceCollection([
          ResourceObject.of("articles", "1"),
          ResourceObject.of("articles", "1"),
        ]),
        null,
        null,
        null,
        null,
        null,
        [:])
  }
}
