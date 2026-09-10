package io.github.kazemek.jsonapi.core.model

import io.github.kazemek.jsonapi.core.validation.JsonApiValidationException
import io.github.kazemek.jsonapi.core.validation.ValidationRuleCode
import spock.lang.Specification

class ErrorSourceSpec extends Specification {

  def "source retains pointer, parameter, header, and additional members"() {
    when:
    def source = new ErrorSource("/data", "include", "Authorization", ["ext:source": true])

    then:
    source.pointer() == "/data"
    source.parameter() == "include"
    source.header() == "Authorization"
    source.additionalMembers()["ext:source"] == true
  }

  def "ofParameter creates a parameter-only source"() {
    when:
    def source = ErrorSource.ofParameter("include")

    then:
    source.pointer() == null
    source.parameter() == "include"
    source.header() == null
    source.additionalMembers().isEmpty()
  }

  def "builder retains pointer, parameter, header, and additional members"() {
    when:
    def source = ErrorSource.builder()
        .pointer("/data")
        .parameter("include")
        .header("Authorization")
        .additionalMember("ext:source", true)
        .additionalMembers(["@ctx": "x"])
        .build()

    then:
    source == new ErrorSource(
        "/data", "include", "Authorization", ["ext:source": true, "@ctx": "x"])
  }

  def "null pointer remains valid"() {
    when:
    def source = new ErrorSource(null, "include", null, [:])

    then:
    source.pointer() == null
    source.parameter() == "include"
  }

  def "valid pointer '#pointer' is accepted"() {
    when:
    def source = new ErrorSource(pointer, null, null, [:])

    then:
    source.pointer() == pointer

    where:
    pointer << [
      "",
      "/",
      "/data",
      "/data/0/id",
      "/a~0b",
      "/a~1b",
      "/a~01b",
      "/données"
    ]
  }

  def "invalid pointer '#pointer' fails with INVALID_JSON_POINTER"() {
    when:
    new ErrorSource(pointer, null, null, [:])

    then:
    def ex = thrown(JsonApiValidationException)
    ex.ruleCode() == ValidationRuleCode.INVALID_JSON_POINTER
    ex.jsonPointer() == "/errors/source/pointer"

    where:
    pointer << [
      "data",
      "/a~",
      "/a~2",
      "/a~x",
      "#/data"
    ]
  }

  def "builder delegates invalid pointer validation"() {
    when:
    ErrorSource.builder().pointer("#/data").build()

    then:
    def ex = thrown(JsonApiValidationException)
    ex.ruleCode() == ValidationRuleCode.INVALID_JSON_POINTER
    ex.jsonPointer() == "/errors/source/pointer"
  }

  def "builder delegates reserved additional member validation"() {
    when:
    ErrorSource.builder().parameter("include").additionalMember("parameter", "duplicate").build()

    then:
    def ex = thrown(JsonApiValidationException)
    ex.ruleCode() == ValidationRuleCode.RESERVED_FIELD_NAME
  }
}
