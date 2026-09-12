package com.kazforge.jsonapi.core.internal

import com.kazforge.jsonapi.core.validation.JsonApiValidationException
import com.kazforge.jsonapi.core.validation.ValidationRuleCode
import spock.lang.Specification

import java.util.concurrent.atomic.AtomicInteger

class OpenJsonValuesSpec extends Specification {

  def "rejects non-finite numbers with stable code"() {
    when:
    OpenJsonValues.copy(Double.POSITIVE_INFINITY, "/meta/n")

    then:
    def ex = thrown(JsonApiValidationException)
    ex.ruleCode() == ValidationRuleCode.INVALID_OPEN_JSON_VALUE
    ex.jsonPointer() == "/meta/n"
  }

  def "rejects unsupported types with stable code"() {
    when:
    OpenJsonValues.copy(new Object(), "/meta/x")

    then:
    def ex = thrown(JsonApiValidationException)
    ex.ruleCode() == ValidationRuleCode.INVALID_OPEN_JSON_VALUE
  }

  def "deep copies nested maps and lists"() {
    given:
    def nested = [items: ["a"]]

    when:
    def copy = OpenJsonValues.copy(nested, "/meta") as Map
    nested.items << "b"

    then:
    (copy.items as List).size() == 1
  }

  def "preserves explicit null map values and list elements"() {
    when:
    def mapCopy = OpenJsonValues.copyMap([a: null, b: 1], "/meta")
    def listCopy = OpenJsonValues.copy([null, "x"], "/meta") as List

    then:
    mapCopy.containsKey("a")
    mapCopy.a == null
    mapCopy.b == 1
    listCopy[0] == null
    listCopy[1] == "x"
  }

  def "preserves encounter order for open maps"() {
    when:
    def copy = OpenJsonValues.copyMap([z: 1, a: null, m: 2], "/meta")

    then:
    copy.keySet().toList() == ["z", "a", "m"]
  }

  def "rejects mutable number implementations"() {
    when:
    OpenJsonValues.copy(new AtomicInteger(1), "/meta/n")

    then:
    def ex = thrown(JsonApiValidationException)
    ex.ruleCode() == ValidationRuleCode.INVALID_OPEN_JSON_VALUE
  }

  def "accepts immutable number types"() {
    expect:
    OpenJsonValues.isValid(42)
    OpenJsonValues.isValid(1L)
    OpenJsonValues.isValid(1.5d)
    OpenJsonValues.isValid(new BigDecimal("1.0"))
    OpenJsonValues.copy(42, "/n") == 42
  }

  def "rejects cyclic lists with stable diagnostics"() {
    given:
    def cyclic = []
    cyclic.add(cyclic)

    expect:
    !OpenJsonValues.isValid(cyclic)

    when:
    OpenJsonValues.copy(cyclic, "/meta")

    then:
    def ex = thrown(JsonApiValidationException)
    ex.ruleCode() == ValidationRuleCode.INVALID_OPEN_JSON_VALUE
    ex.jsonPointer() == "/meta/0"
  }

  def "rejects cyclic maps with stable diagnostics"() {
    given:
    def cyclic = [:]
    cyclic.self = cyclic

    expect:
    !OpenJsonValues.isValid(cyclic)

    when:
    OpenJsonValues.copy(cyclic, "/meta")

    then:
    def ex = thrown(JsonApiValidationException)
    ex.ruleCode() == ValidationRuleCode.INVALID_OPEN_JSON_VALUE
    ex.jsonPointer() == "/meta/self"
  }

  def "escapes slash and tilde in diagnostic JSON Pointers"() {
    when:
    OpenJsonValues.copy(["a/b": new Object()], "/meta")

    then:
    def slashEx = thrown(JsonApiValidationException)
    slashEx.ruleCode() == ValidationRuleCode.INVALID_OPEN_JSON_VALUE
    slashEx.jsonPointer() == "/meta/a~1b"

    when:
    OpenJsonValues.copy(["a~b": new Object()], "/meta")

    then:
    def tildeEx = thrown(JsonApiValidationException)
    tildeEx.ruleCode() == ValidationRuleCode.INVALID_OPEN_JSON_VALUE
    tildeEx.jsonPointer() == "/meta/a~0b"
  }
}
