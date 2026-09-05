package io.github.kazemek.jsonapi.jackson2

import com.fasterxml.jackson.databind.json.JsonMapper

import io.github.kazemek.jsonapi.core.model.DocumentData
import io.github.kazemek.jsonapi.core.model.JsonApiDocument
import io.github.kazemek.jsonapi.core.model.ResourceObject
import io.github.kazemek.jsonapi.core.validation.JsonApiValidationException
import io.github.kazemek.jsonapi.core.validation.ValidationRuleCode

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
