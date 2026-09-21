package com.kazforge.jsonapi.fixtures.contract

import com.kazforge.jsonapi.api.JsonApi
import com.kazforge.jsonapi.core.model.RelationshipData
import com.kazforge.jsonapi.core.model.ResourceIdentifier
import com.kazforge.jsonapi.fixtures.compoundwrite.WrappedLinkageArticle
import com.kazforge.jsonapi.fixtures.domainwrite.ArticleWithSet
import com.kazforge.jsonapi.fixtures.domainwrite.Comment
import com.kazforge.jsonapi.fixtures.domainwrite.Person
import com.kazforge.jsonapi.fixtures.domainwrite.RelationshipContainerFixtures
import com.kazforge.jsonapi.fixtures.domainwrite.Tag
import com.kazforge.jsonapi.mapping.RelationshipLinkage
import groovy.json.JsonSlurper
import spock.lang.Specification

/**
 * Advanced relationship-linkage write characterization contract observed through the Level-1 {@code
 * writeOne} entry point: direct {@code ResourceIdentifier} values pass through with their own
 * identifier meta, direct to-one {@code RelationshipData} values are carried through as linkage,
 * transparent {@code RelationshipLinkage} targets with absent wrapper meta map like the ordinary
 * target, and present/empty to-one {@code Optional} values and array/set to-many containers keep
 * their linkage states. Concrete adapter subclasses supply the configured runtime.
 */
abstract class AdvancedLinkageCharacterizationSpec extends Specification {

  protected abstract JsonApi api()

  private static Map<String, Object> parse(String json) {
    new JsonSlurper().parseText(json) as Map<String, Object>
  }

  def "writes a direct to-one ResourceIdentifier value as identifier linkage"() {
    given:
    def json = api().resources().writeOne(
        new RelationshipContainerFixtures.ArticleWithIdentifierRelationship(
        "1", "T", ResourceIdentifier.of("people", "p1")))
    def document = parse(json)

    expect:
    document.data.relationships.author.data == ["type": "people", "id": "p1"]
  }

  def "writes a direct to-many ResourceIdentifier collection retaining supplied members"() {
    given:
    def json = api().resources().writeOne(
        new RelationshipContainerFixtures.ArticleWithNullableIdentifierList(
        "1", List.of(ResourceIdentifier.of("comments", "c1"), ResourceIdentifier.of("comments", "c2"))))
    def document = parse(json)

    expect:
    document.data.relationships.items.data == [
      ["type": "comments", "id": "c1"],
      ["type": "comments", "id": "c2"],
    ]
  }

  def "carries a direct to-one RelationshipData value through as linkage"() {
    given:
    def linkage = new RelationshipData.SingleLinkage(ResourceIdentifier.of("people", "p1"))
    def json = api().resources().writeOne(
        new RelationshipContainerFixtures.ArticleWithDirectLinkageData("1", "T", linkage))
    def document = parse(json)

    expect:
    document.data.relationships.author.data == ["type": "people", "id": "p1"]
  }

  def "maps a transparent to-one RelationshipLinkage target with null wrapper meta"() {
    given:
    def json = api().resources().writeOne(
        new WrappedLinkageArticle("1", new RelationshipLinkage<>(new Person("p1", "Ann"), null), List.of()))
    def document = parse(json)

    expect:
    document.data.relationships.author.data == ["type": "people", "id": "p1"]
  }

  def "maps a transparent to-many RelationshipLinkage collection with null occurrence meta"() {
    given:
    def json = api().resources().writeOne(
        new WrappedLinkageArticle(
        "1",
        null,
        List.of(
        new RelationshipLinkage<>(new Comment("c1", "Nice", null), null),
        new RelationshipLinkage<>(new Comment("c2", null, null), null))))
    def document = parse(json)

    expect:
    document.data.relationships.comments.data == [
      ["type": "comments", "id": "c1"],
      ["type": "comments", "id": "c2"],
    ]
  }

  def "writes present and empty Optional to-one relationships as their linkage states"() {
    when:
    def present = parse(api().resources().writeOne(
        new RelationshipContainerFixtures.ArticleWithOptionalRelationship(
        "1", Optional.of(new Comment("c1", "Nice", null)))))

    then:
    present.data.relationships.comment.data == ["type": "comments", "id": "c1"]

    when:
    def empty = parse(api().resources().writeOne(
        new RelationshipContainerFixtures.ArticleWithOptionalRelationship("1", Optional.empty())))

    then:
    empty.data.relationships.comment.data == null
  }

  def "writes an array to-many container as identifier linkage in container order"() {
    given:
    def json = api().resources().writeOne(
        new RelationshipContainerFixtures.ArticleWithCommentArray(
        "1", "T", [
          new Comment("c1", "Nice", null),
          new Comment("c2", null, null)
        ] as Comment[]))
    def document = parse(json)

    expect:
    document.data.relationships.comments.data == [
      ["type": "comments", "id": "c1"],
      ["type": "comments", "id": "c2"],
    ]
  }

  def "writes a set to-many container as identifier linkage without depending on set order"() {
    given:
    def json = api().resources().writeOne(
        new ArticleWithSet("1", "T", Set.of(new Tag("java"), new Tag("groovy"))))
    def document = parse(json)
    def linkage = document.data.relationships.tags.data as List
    def identifiers = linkage as Set

    expect:
    linkage.size() == 2
    identifiers ==
        [
          ["type": "tags", "id": "java"],
          ["type": "tags", "id": "groovy"]
        ] as Set
  }
}
