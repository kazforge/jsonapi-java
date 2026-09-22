package com.kazforge.jsonapi.fixtures.contract

import com.kazforge.jsonapi.api.JsonApi
import com.kazforge.jsonapi.api.ResourceWriteOptions
import com.kazforge.jsonapi.core.model.Meta
import com.kazforge.jsonapi.core.model.ResourceIdentifier
import com.kazforge.jsonapi.fixtures.domainpatch.ArticleMeta
import com.kazforge.jsonapi.fixtures.domainpatch.ArticleWithMapMeta
import com.kazforge.jsonapi.fixtures.domainpatch.ArticleWithMeta
import com.kazforge.jsonapi.fixtures.domainpatch.ArticleWithRelationshipLinkage
import com.kazforge.jsonapi.fixtures.domainpatch.AuthorIdMeta
import com.kazforge.jsonapi.fixtures.domainpatch.AuthorMeta
import com.kazforge.jsonapi.mapping.RelationshipLinkage
import com.kazforge.jsonapi.representation.RepresentationSelection
import groovy.json.JsonSlurper
import spock.lang.Specification

/**
 * Whole-object write-meta characterization contract observed through the Level-1 {@code writeOne}
 * entry point: resource meta, relationship meta, and identifier meta stay at their distinct
 * locations; absent meta is omitted while present-empty map meta is an empty object; and identifier
 * meta replacement is whole-value, preserving target identity while null wrapper meta leaves an
 * existing direct-identifier meta in place. Additional-member preservation for identifiers is
 * asserted at the adapter mapping boundary rather than here, because Level-1 wire validation admits
 * only configured extension namespaces. Concrete adapter subclasses supply the configured runtime.
 */
abstract class WriteMetaCharacterizationSpec extends Specification {

  protected abstract JsonApi api()

  private static Map<String, Object> parse(String json) {
    new JsonSlurper().parseText(json) as Map<String, Object>
  }

  def "writes resource meta at the resource meta location"() {
    given:
    def json = api().resources().writeOne(
        new ArticleWithMeta("1", "T", null, new ArticleMeta("cms", "n"), null))
    def document = parse(json)

    expect:
    document.data.meta == ["source": "cms", "note": "n"]
  }

  def "writes relationship meta at the matched relationship location"() {
    given:
    def json = api().resources().writeOne(
        new ArticleWithMeta("1", "T", ResourceIdentifier.of("people", "p1"), null, new AuthorMeta("Ann")))
    def document = parse(json)

    expect:
    document.data.relationships.author.meta == ["displayName": "Ann"]
  }

  def "keeps relationship meta and identifier meta distinct on the same relationship"() {
    given:
    def json = api().resources().writeOne(
        new ArticleWithRelationshipLinkage(
        "1",
        "T",
        new RelationshipLinkage<>(ResourceIdentifier.of("people", "p1"), new AuthorIdMeta("editor")),
        null,
        new AuthorMeta("Ann"),
        null))
    def document = parse(json)

    expect:
    document.data.relationships.author.meta == ["displayName": "Ann"]
    document.data.relationships.author.data.meta == ["role": "editor"]
  }

  def "omits absent whole-object meta members"() {
    given:
    def json = api().resources().writeOne(new ArticleWithMeta("1", "T", null, null, null))
    def document = parse(json)

    expect:
    !document.data.containsKey("meta")
    !document.data.relationships.author.containsKey("meta")
    document.data.relationships.author.data == null
  }

  def "writes present-empty map meta as an empty object"() {
    given:
    def json = api().resources().writeOne(
        new ArticleWithMapMeta("1", "T", ResourceIdentifier.of("people", "p1"), Map.of(), Map.of()))
    def document = parse(json)

    expect:
    document.data.meta == [:]
    document.data.relationships.author.meta == [:]
  }

  def "preserves direct identifier meta when wrapper meta is absent"() {
    given:
    def identifier =
        new ResourceIdentifier("people", "p1", null, Meta.of(["role": "kept"]), Map.of())
    def json = api().resources().writeOne(
        new ArticleWithRelationshipLinkage(
        "1", "T", new RelationshipLinkage<>(identifier, null), null, null, null))
    def document = parse(json)

    expect:
    document.data.relationships.author.data ==
        ["type": "people", "id": "p1", "meta": ["role": "kept"]]
  }

  def "replaces identifier meta wholesale while preserving identity"() {
    given:
    def identifier =
        new ResourceIdentifier("people", "p1", null, Meta.of(["role": "old"]), Map.of())
    def json = api().resources().writeOne(
        new ArticleWithRelationshipLinkage(
        "1",
        "T",
        new RelationshipLinkage<>(identifier, new AuthorIdMeta("editor")),
        null,
        null,
        null))
    def document = parse(json)

    expect:
    document.data.relationships.author.data ==
        ["type": "people", "id": "p1", "meta": ["role": "editor"]]
  }

  def "retains resource meta independently of an empty fieldset"() {
    given:
    def selection = RepresentationSelection.builder().fields("articles", List.of()).build()
    def json = api().resources().writeOne(
        new ArticleWithMeta("1", "T", ResourceIdentifier.of("people", "p1"), new ArticleMeta("cms", "n"), null),
        ResourceWriteOptions.defaults().withSelection(selection))
    def document = parse(json)

    expect:
    !document.data.containsKey("attributes")
    document.data.meta == ["source": "cms", "note": "n"]
  }
}
