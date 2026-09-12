package com.kazforge.jsonapi.core.model

import com.kazforge.jsonapi.core.validation.JsonApiValidationException
import com.kazforge.jsonapi.core.validation.ValidationRuleCode
import spock.lang.Specification

class JsonApiObjectSpec extends Specification {

  def "version factory and additional members are preserved"() {
    when:
    def object = new JsonApiObject("1.1", null, null, Meta.of([count: 1]), ["ext:trace": "abc"])

    then:
    JsonApiObject.ofVersion("1.1").version() == "1.1"
    object.meta().members().count == 1
    object.additionalMembers()["ext:trace"] == "abc"
  }

  def "invalid extension uri fails with stable code"() {
    when:
    new JsonApiObject("1.1", ["not a uri"], null, null, [:])

    then:
    def ex = thrown(JsonApiValidationException)
    ex.ruleCode() == ValidationRuleCode.INVALID_EXTENSION_URI
  }

  def "relative extension uri fails with stable code"() {
    when:
    new JsonApiObject("1.1", ["/relative/ext"], null, null, [:])

    then:
    def ex = thrown(JsonApiValidationException)
    ex.ruleCode() == ValidationRuleCode.INVALID_EXTENSION_URI
  }

  def "invalid profile uri fails with stable code"() {
    when:
    new JsonApiObject("1.1", null, ["%%%"], null, [:])

    then:
    def ex = thrown(JsonApiValidationException)
    ex.ruleCode() == ValidationRuleCode.INVALID_PROFILE_URI
  }

  def "relative profile uri fails with stable code"() {
    when:
    new JsonApiObject("1.1", null, ["/profiles/a"], null, [:])

    then:
    def ex = thrown(JsonApiValidationException)
    ex.ruleCode() == ValidationRuleCode.INVALID_PROFILE_URI
  }

  def "valid version and profile uris are accepted"() {
    when:
    def obj = new JsonApiObject("1.1", ["https://example.com/ext"],
    [
      "https://example.com/profiles/a"
    ], null, [:])

    then:
    obj.version() == "1.1"
    obj.ext() == ["https://example.com/ext"]
    obj.profile() == [
      "https://example.com/profiles/a"
    ]
  }

  def "jsonapi object rejects reserved additional member names"() {
    when:
    new JsonApiObject("1.1", null, null, null, [version: "2.0"])

    then:
    def ex = thrown(JsonApiValidationException)
    ex.ruleCode() == ValidationRuleCode.RESERVED_FIELD_NAME
  }
}
