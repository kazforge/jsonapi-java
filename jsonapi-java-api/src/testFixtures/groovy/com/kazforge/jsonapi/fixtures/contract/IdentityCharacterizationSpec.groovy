package com.kazforge.jsonapi.fixtures.contract

import com.kazforge.jsonapi.fixtures.domainwrite.BlogWithJsonProperty
import com.kazforge.jsonapi.fixtures.domainwrite.ConventionalId
import com.kazforge.jsonapi.fixtures.localid.LocalIdentityArticle
import com.kazforge.jsonapi.api.JsonApi
import groovy.json.JsonSlurper
import spock.lang.Specification

/**
 * Identity characterization contract observed through the Level-1 {@code JsonApi} contract:
 * {@code id} and {@code lid} are independent identity roles that map only to their own JSON:API
 * members, the conventional external name {@code id} supplies the identifier without an explicit
 * annotation, and backend renames of identifier properties never move the wire members. Concrete
 * adapter subclasses supply the configured runtime.
 */
abstract class IdentityCharacterizationSpec extends Specification {

  protected abstract JsonApi api()

  private static Map<String, Object> parse(String json) {
    new JsonSlurper().parseText(json) as Map<String, Object>
  }

  def "writes both identity members from the independent id and lid roles"() {
    given:
    def json = api().resources().writeOne(new LocalIdentityArticle("123", "tmp-1", "Title"))
    def document = parse(json)

    expect:
    document.data.type == "articles"
    document.data.id == "123"
    document.data.lid == "tmp-1"

    and:
    def bound = api().resources().readOne(json, LocalIdentityArticle)
    bound == new LocalIdentityArticle("123", "tmp-1", "Title")
  }

  def "omits the lid member when the lid role carries no value"() {
    given:
    def json = api().resources().writeOne(new LocalIdentityArticle("123", null, "Title"))
    def document = parse(json)

    expect:
    document.data.id == "123"
    !document.data.containsKey("lid")

    and:
    api().resources().readOne(json, LocalIdentityArticle) == new LocalIdentityArticle("123", null, "Title")
  }

  def "treats the conventional id external name as the identifier without an explicit annotation"() {
    given:
    def json = api().resources().writeOne(new ConventionalId("42", "Name"))
    def document = parse(json)

    expect:
    document.data.type == "conventionals"
    document.data.id == "42"

    and:
    document.data.attributes == ["name": "Name"]

    and:
    api().resources().readOne(json, ConventionalId) == new ConventionalId("42", "Name")
  }

  def "keeps the wire member id when the identifier property is backend-renamed"() {
    given:
    def json = api().resources().writeOne(new BlogWithJsonProperty("b1", "Title"))
    def document = parse(json)

    expect:
    document.data.type == "blogs"
    document.data.id == "b1"
    !document.data.containsKey("blog_id")

    and:
    document.data.attributes == ["blog_title": "Title"]

    and:
    api().resources().readOne(json, BlogWithJsonProperty) == new BlogWithJsonProperty("b1", "Title")
  }

  def "keeps the wire member lid when the local identifier property is backend-renamed"() {
    given:
    def json = api().resources().writeOne(new RenamedLidArticle("1", "local-1", "T"))
    def document = parse(json)

    expect:
    document.data.type == "articles"
    document.data.lid == "local-1"
    !document.data.containsKey("wire-local-id")

    and:
    document.data.attributes == ["title": "T"]

    and:
    api().resources().readOne(json, RenamedLidArticle) == new RenamedLidArticle("1", "local-1", "T")
  }

  def "keeps the three property naming concepts distinct at once"() {
    given:
    def json = api().resources().writeOne(new RenamedKeyArticle("k1", "Headline"))
    def document = parse(json)

    expect:
    document.data.type == "articles"
    document.data.id == "k1"
    !document.data.containsKey("key")

    and:
    document.data.attributes == ["wire-headline": "Headline"]

    and:
    api().resources().readOne(json, RenamedKeyArticle) == new RenamedKeyArticle("k1", "Headline")
  }

  def "binds wire id and lid members back into their independent roles on read"() {
    given:
    def json = '{"data":{"type":"articles","id":"123","lid":"tmp-1","attributes":{"title":"Title"}}}'

    expect:
    api().resources().readOne(json, LocalIdentityArticle) == new LocalIdentityArticle("123", "tmp-1", "Title")
  }
}
