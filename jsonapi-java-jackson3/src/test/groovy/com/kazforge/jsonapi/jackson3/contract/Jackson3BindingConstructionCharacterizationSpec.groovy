package com.kazforge.jsonapi.jackson3.contract

import com.kazforge.jsonapi.fixtures.contract.BindingCreatorOnlyArticle
import com.kazforge.jsonapi.fixtures.contract.BindingGetterOnlyArticle
import com.kazforge.jsonapi.fixtures.contract.BindingNestedArticle
import com.kazforge.jsonapi.fixtures.contract.BindingSetterOnlyArticle
import com.kazforge.jsonapi.jackson3.JsonApiJackson3
import tools.jackson.databind.json.JsonMapper

import com.kazforge.jsonapi.api.JsonApi
import com.kazforge.jsonapi.diagnostic.JsonApiMappingException
import com.kazforge.jsonapi.diagnostic.MappingDiagnostic
import com.kazforge.jsonapi.fixtures.domainread.FlatCountedThing
import spock.lang.Specification

/**
 * Binding-construction characterization contract observed through the Level-1 {@code readOne} entry
 * point: setter-only and creator-only properties bind through the configured effective
 * deserialization model, a nested construction failure translates to the complete escaped JSON:API
 * pointer, and the neutral diagnostics for a supplied malformed value and a supplied
 * non-deserializable member stay stable.
 *
 * <p>Assertions stay at the JSON:API member level. Backend exception classes, message text,
 * cause-chain details, and native type-refinement mechanics remain adapter-local.
 */
class Jackson3BindingConstructionCharacterizationSpec extends Specification {

  private JsonApi api() {
    JsonApiJackson3.jsonApi(JsonMapper.builder().build())
  }

  def "binds a setter-only property through its effective deserialization target"() {
    when:
    def bound = api().resources().readOne(
        '{"data":{"type":"binding-setter","id":"1","attributes":{"title":"bound"}}}',
        BindingSetterOnlyArticle)

    then:
    bound.idValue() == "1"
    bound.titleValue() == "bound"
  }

  def "binds a creator-only property through its effective deserialization target"() {
    when:
    def bound = api().resources().readOne(
        '{"data":{"type":"binding-creator","id":"1","attributes":{"title":"bound"}}}',
        BindingCreatorOnlyArticle)

    then:
    bound == new BindingCreatorOnlyArticle("1", "bound")
  }

  def "translates a nested construction failure through the nested attribute shape"() {
    when:
    api().resources().readOne(
        '{"data":{"type":"binding-nested","id":"1","attributes":{"point":{"x":"boom"}}}}',
        BindingNestedArticle)

    then:
    def failure = thrown(JsonApiMappingException)
    failure.diagnostic() == MappingDiagnostic.UNSUPPORTED_ATTRIBUTE_VALUE
    failure.propertyPath() == "/attributes/point/x"
  }

  def "reports a supplied malformed attribute value at its JSON:API member"() {
    when:
    api().resources().readOne(
        '{"data":{"type":"things","id":"1","attributes":{"count":[1]}}}',
        FlatCountedThing)

    then:
    def failure = thrown(JsonApiMappingException)
    failure.diagnostic() == MappingDiagnostic.UNSUPPORTED_ATTRIBUTE_VALUE
    failure.propertyPath() == "/attributes/count"
  }

  def "reports a supplied non-deserializable member at its JSON:API member"() {
    when:
    api().resources().readOne(
        '{"data":{"type":"binding-getter","id":"1","attributes":{"title":"supplied"}}}',
        BindingGetterOnlyArticle)

    then:
    def failure = thrown(JsonApiMappingException)
    failure.diagnostic() == MappingDiagnostic.NON_DESERIALIZABLE_PROPERTY
    failure.propertyPath() == "/attributes/title"
  }
}
