package com.kazforge.jsonapi.fixtures.contract

import com.kazforge.jsonapi.core.model.ResourceIdentifier
import com.kazforge.jsonapi.fixtures.domainread.FlatArticle
import com.kazforge.jsonapi.fixtures.domainread.FlatThingWithIgnored
import com.kazforge.jsonapi.fixtures.domainwrite.Article
import com.kazforge.jsonapi.fixtures.domainwrite.ArticleWithUnannotatedExtra
import com.kazforge.jsonapi.fixtures.domainwrite.Comment
import com.kazforge.jsonapi.api.JsonApi
import groovy.json.JsonSlurper
import spock.lang.Specification

/**
 * Attribute characterization contract observed through the Level-1 {@code JsonApi} contract:
 * annotated attributes map under the configured backend external name as the JSON:API member name
 * while the Java logical name stays distinct, unannotated and Jackson-ignored properties never
 * participate, explicit-null attribute values survive both directions, and JSON-compatible open
 * values keep nested explicit nulls through mapping and binding. Concrete adapter subclasses
 * supply the configured runtime.
 */
abstract class AttributesCharacterizationSpec extends Specification {

  protected abstract JsonApi api()

  private static Map<String, Object> parse(String json) {
    new JsonSlurper().parseText(json) as Map<String, Object>
  }

  def "maps annotated attributes under their configured external member name"() {
    given:
    def json = api().resources().writeOne(new Article("1", "T", "B", List.of(), null))
    def document = parse(json)

    expect:
    document.data.attributes == ["title": "T", "body-text": "B"]
    !document.data.attributes.containsKey("body")
  }

  def "never maps a Jackson-visible unannotated property as an attribute"() {
    given:
    def json = api().resources().writeOne(new ArticleWithUnannotatedExtra("1", "T", "extra"))
    def document = parse(json)

    expect:
    document.data.attributes == ["title": "T"]
    !document.data.attributes.containsKey("ignoredExtra")
  }

  def "keeps an explicit-null attribute value as JSON null on write"() {
    given:
    def json = api().resources().writeOne(new Comment("c1", null, null))
    def document = parse(json)

    expect:
    document.data.attributes.containsKey("body")
    document.data.attributes.body == null
  }

  def "binds an explicit-null attribute member as a null logical property value on read"() {
    given:
    def json = '{"data":{"type":"articles","id":"1","attributes":{"title":null,"body-text":null}}}'

    when:
    def bound = api().resources().readOne(json, FlatArticle)

    then:
    bound.title() == null
    bound.body() == null
  }

  def "never participates an attribute whose Jackson visibility is disabled"() {
    given:
    def json = '{"data":{"type":"things","id":"t1","attributes":{"name":"Thing","secret":"hidden"}}}'

    expect:
    def bound = api().resources().readOne(json, FlatThingWithIgnored)
    bound.id == "t1"
    bound.name == "Thing"
    bound.confidential == null

    and:
    def written = parse(api().resources().writeOne(bound))
    written.data.attributes == ["name": "Thing"]
    !written.data.attributes.containsKey("secret")
  }

  def "round-trips a nested-null open value attribute with nested nulls intact"() {
    given:
    def detail = [
      position: [line: "1", column: null],
      note: null,
      tags: ["a", null],
    ]
    def json = api().resources().writeOne(new NestedNullDetailArticle("1", detail))
    def document = parse(json)

    expect:
    document.data.attributes.detail == detail

    and:
    api().resources().readOne(json, NestedNullDetailArticle) == new NestedNullDetailArticle("1", detail)
  }

  def "binds a nested-null open value attribute from wire JSON with nested nulls intact"() {
    given:
    def json =
        '{"data":{"type":"articles","id":"1","attributes":{"detail":' +
        '{"position":{"line":"1","column":null},"note":null,"tags":["a",null],"empty":{}}}}}'

    when:
    def bound = api().resources().readOne(json, NestedNullDetailArticle)

    then:
    bound.detail() == [
      position: [line: "1", column: null],
      note: null,
      tags: ["a", null],
      empty: [:],
    ]
  }

  def "binds renamed attribute members back into the logical property on read"() {
    given:
    def json = '{"data":{"type":"articles","id":"1","attributes":{"title":"T","body-text":"B"}}}'

    when:
    def bound = api().resources().readOne(json, FlatArticle)

    then:
    bound == new FlatArticle("1", "T", "B", null, null)

    and:
    bound.title() == "T"
  }
}
