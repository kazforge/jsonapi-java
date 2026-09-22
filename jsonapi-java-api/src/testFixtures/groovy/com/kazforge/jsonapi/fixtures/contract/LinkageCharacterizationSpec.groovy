package com.kazforge.jsonapi.fixtures.contract

import com.kazforge.jsonapi.core.model.ResourceIdentifier
import com.kazforge.jsonapi.fixtures.domainread.FlatArticle
import com.kazforge.jsonapi.fixtures.domainread.FlatDefaultedRelationshipArticle
import com.kazforge.jsonapi.fixtures.domainwrite.Article
import com.kazforge.jsonapi.fixtures.domainwrite.Comment
import com.kazforge.jsonapi.fixtures.domainwrite.Person
import com.kazforge.jsonapi.fixtures.sparsefieldset.ArticleWithRenamedAuthor
import com.kazforge.jsonapi.api.JsonApi
import groovy.json.JsonSlurper
import spock.lang.Specification

/**
 * Ordinary-linkage characterization contract observed through the Level-1 {@code JsonApi}
 * contract: present to-one and to-many relationships emit linkage under a {@code data} member,
 * absent and present-empty linkage states are wire-visible distinctions, renamed relationship
 * members keep their linkage, and the relationship facet round-trips linkage documents including
 * explicit-null and empty collections. Concrete adapter subclasses supply the configured runtime.
 */
abstract class LinkageCharacterizationSpec extends Specification {

  protected abstract JsonApi api()

  private static Map<String, Object> parse(String json) {
    new JsonSlurper().parseText(json) as Map<String, Object>
  }

  def "writes a present to-one relationship as identifier linkage"() {
    given:
    def json = api().resources().writeOne(new Article("1", "T", "B", List.of(), new Person("p1", "Ann")))
    def document = parse(json)

    expect:
    document.data.relationships.author.data == ["type": "people", "id": "p1"]
  }

  def "writes null and empty relationship states as their own wire-visible linkage states"() {
    given:
    def json = api().resources().writeOne(new Article("1", "T", "B", List.of(), null))
    def document = parse(json)

    expect:
    document.data.relationships.author.data == null
    document.data.relationships.comments.data == []
  }

  def "writes a populated to-many relationship as an identifier array"() {
    given:
    def comments = List.of(new Comment("c1", "Nice", null), new Comment("c2", null, null))
    def json = api().resources().writeOne(new Article("1", "T", "B", comments, null))
    def document = parse(json)

    expect:
    document.data.relationships.comments.data == [
      ["type": "comments", "id": "c1"],
      ["type": "comments", "id": "c2"],
    ]
  }

  def "writes null relationship properties as present-null linkage members"() {
    given:
    def json = api().resources().writeOne(new Article("1", "T", "B", null, null))
    def document = parse(json)

    expect:
    document.data.relationships.author.data == null
    document.data.relationships.comments.data == []
  }

  def "keeps linkage under a backend-renamed relationship member"() {
    given:
    def json = api().resources().writeOne(new ArticleWithRenamedAuthor("1", "T", new Person("p1", "Ann")))
    def document = parse(json)

    expect:
    document.data.relationships."written-by".data == ["type": "people", "id": "p1"]
    !document.data.relationships.containsKey("author")
  }

  def "distinguishes an omitted relationship and a data-absent relationship object from explicit null linkage"() {
    given:
    def omitted = '{"data":{"type":"articles","id":"1"}}'
    def dataAbsent =
        '{"data":{"type":"articles","id":"1","relationships":{"author":{"meta":{"note":"x"}}}}}'
    def explicitNull =
        '{"data":{"type":"articles","id":"1","relationships":{"author":{"data":null}}}}'
    def declaredDefault = ResourceIdentifier.of("people", "default")

    when:
    def omittedBound = api().resources().readOne(omitted, FlatDefaultedRelationshipArticle)
    def dataAbsentBound = api().resources().readOne(dataAbsent, FlatDefaultedRelationshipArticle)
    def nullBound = api().resources().readOne(explicitNull, FlatDefaultedRelationshipArticle)

    then:
    omittedBound.author == declaredDefault
    dataAbsentBound.author == declaredDefault
    nullBound.author == null
  }

  def "writes and reads explicit-null and empty linkage documents through the relationship facet"() {
    expect:
    parse(api().relationships().writeToOne(null)).data == null

    and:
    parse(api().relationships().writeToMany(List.of())).data == []

    and:
    api().relationships().readToOne('{"data":null}') == null

    and:
    api().relationships().readToMany('{"data":[]}') == List.of()
  }

  def "round-trips a present to-one linkage document through the relationship facet"() {
    given:
    def identifier = ResourceIdentifier.of("people", "p1")

    when:
    def json = api().relationships().writeToOne(identifier)

    then:
    parse(json).data == ["type": "people", "id": "p1"]

    and:
    api().relationships().readToOne(json) == identifier
  }

  def "round-trips a to-many linkage document through the relationship facet in identifier order"() {
    given:
    def identifiers = List.of(ResourceIdentifier.of("comments", "c1"), ResourceIdentifier.of("comments", "c2"))

    when:
    def json = api().relationships().writeToMany(identifiers)

    then:
    parse(json).data == [
      ["type": "comments", "id": "c1"],
      ["type": "comments", "id": "c2"],
    ]

    and:
    api().relationships().readToMany(json) == identifiers
  }

  def "binds relationship linkage members back into the flat read shape"() {
    given:
    def json =
        '{"data":{"type":"articles","id":"1","attributes":{"title":"T","body-text":"B"},' +
        '"relationships":{' +
        '"author":{"data":{"type":"people","id":"p1"}},' +
        '"comments":{"data":[{"type":"comments","id":"c1"},{"type":"comments","id":"c2"}]}}}}'

    when:
    def bound = api().resources().readOne(json, FlatArticle)

    then:
    bound == new FlatArticle(
        "1",
        "T",
        "B",
        ResourceIdentifier.of("people", "p1"),
        List.of(ResourceIdentifier.of("comments", "c1"), ResourceIdentifier.of("comments", "c2")))
  }
}
