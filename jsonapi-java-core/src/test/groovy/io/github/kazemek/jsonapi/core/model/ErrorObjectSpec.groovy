package io.github.kazemek.jsonapi.core.model

import io.github.kazemek.jsonapi.core.validation.JsonApiValidationException
import io.github.kazemek.jsonapi.core.validation.ValidationRuleCode
import spock.lang.Specification

class ErrorObjectSpec extends Specification {

  def "builder creates a minimal valid error"() {
    expect:
    ErrorObject.builder().title("Invalid Attribute").build() == ErrorObject.ofTitle("Invalid Attribute")
  }

  def "builder creates a fully populated error"() {
    given:
    def links = Links.ofLinks([about: new Link.StringLink("https://example.test/errors/invalid")])
    def source = ErrorSource.builder().pointer("/data/attributes/title").build()
    def meta = Meta.of([retryable: false])

    when:
    def error = ErrorObject.builder()
        .id("1")
        .links(links)
        .status("422")
        .code("invalid")
        .title("Invalid Attribute")
        .detail("Title is required")
        .source(source)
        .meta(meta)
        .build()

    then:
    error == new ErrorObject(
        "1", links, "422", "invalid", "Invalid Attribute", "Title is required", source, meta, Map.of())
  }

  def "builder merges additional members and snapshots nested values at build"() {
    given:
    def nested = [labels: ["first"]]
    def supplied = new LinkedHashMap<String, Object>()
    supplied.put("ext:nested", nested)
    supplied.put("ext:note", "from map")

    when:
    def error = ErrorObject.builder()
        .title("Invalid Attribute")
        .additionalMember("ext:note", "first")
        .additionalMembers(supplied)
        .additionalMember("ext:note", "replacement")
        .build()
    nested.labels << "later"
    supplied.put("ext:later", true)

    then:
    error.additionalMembers() == [
      "ext:note": "replacement",
      "ext:nested": [labels: ["first"]]
    ]
    error.additionalMembers().keySet().toList() == ["ext:note", "ext:nested"]
  }

  def "builder delegates missing standard member validation"() {
    when:
    ErrorObject.builder().additionalMember("@ignored", "value").build()

    then:
    def ex = thrown(JsonApiValidationException)
    ex.ruleCode() == ValidationRuleCode.MISSING_ERROR_MEMBER
    ex.jsonPointer() == "/errors"
  }

  def "builder delegates reserved additional member validation"() {
    when:
    ErrorObject.builder().title("Invalid Attribute").additionalMembers([title: "duplicate"]).build()

    then:
    def ex = thrown(JsonApiValidationException)
    ex.ruleCode() == ValidationRuleCode.RESERVED_FIELD_NAME
  }
}
