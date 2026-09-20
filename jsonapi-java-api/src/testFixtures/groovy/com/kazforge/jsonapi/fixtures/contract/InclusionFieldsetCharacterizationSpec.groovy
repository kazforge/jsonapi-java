package com.kazforge.jsonapi.fixtures.contract

// Shared inclusion and fieldset characterization contract; see the fixture package-info.
import com.kazforge.jsonapi.core.model.ResourceIdentifier
import com.kazforge.jsonapi.core.model.ResourceObject
import com.kazforge.jsonapi.fixtures.domainread.FlatArticle
import com.kazforge.jsonapi.fixtures.domainwrite.Article
import com.kazforge.jsonapi.fixtures.domainwrite.Comment
import com.kazforge.jsonapi.fixtures.domainwrite.Person
import com.kazforge.jsonapi.fixtures.sparsefieldset.ArticleWithRenamedAuthor
import com.kazforge.jsonapi.api.JsonApi
import com.kazforge.jsonapi.api.ResourceWriteOptions
import com.kazforge.jsonapi.representation.RepresentationSelection
import groovy.json.JsonSlurper
import spock.lang.Specification

/**
 * Inclusion and sparse-fieldset characterization contract observed through the Level-1
 * {@code JsonApi} contract: include paths produce compound documents whose {@code included} array
 * keeps wire order, explicit-empty and absent include requests are wire-visible states, fieldsets
 * select fields by JSON:API member name, and typed reads carry included resources as core
 * {@link ResourceObject} values in wire order with absent and empty {@code included} distinct.
 * Deeper traversal invariants belong to the extraction slice that owns compound inclusion. Concrete
 * adapter subclasses supply the configured runtime.
 */
abstract class InclusionFieldsetCharacterizationSpec extends Specification {

  protected abstract JsonApi api()

  private static Map<String, Object> parse(String json) {
    new JsonSlurper().parseText(json) as Map<String, Object>
  }

  private static List<Comment> comments() {
    List.of(new Comment("c1", "Nice", null), new Comment("c2", "Also", null))
  }

  def "writes no included member without an include request"() {
    given:
    def json = api().resources().writeOne(new Article("1", "T", "B", comments(), new Person("p1", "Ann")))
    def document = parse(json)

    expect:
    !document.containsKey("included")
  }

  def "writes a compound document with included resources in wire order for a requested include path"() {
    given:
    def selection = RepresentationSelection.builder().include("comments").build()
    def json = api().resources().writeOne(
        new Article("1", "T", "B", comments(), null), ResourceWriteOptions.defaults().withSelection(selection))
    def document = parse(json)

    expect:
    def included = document.included as List
    included*.type == ["comments", "comments"]
    included*.id == ["c1", "c2"]
    included[0].attributes == ["body": "Nice"]
  }

  def "writes an explicit include request that resolves to no resources as an empty included member"() {
    given:
    def selection = RepresentationSelection.builder().includeRequested().build()
    def json = api().resources().writeOne(
        new Article("1", "T", "B", comments(), null), ResourceWriteOptions.defaults().withSelection(selection))

    expect:
    parse(json).included == []
  }

  def "applies an attribute-only fieldset by JSON:API member name"() {
    given:
    def selection = RepresentationSelection.builder().fields("articles", "title").build()
    def json = api().resources().writeOne(
        new Article("1", "T", "B", comments(), null), ResourceWriteOptions.defaults().withSelection(selection))
    def document = parse(json)

    expect:
    document.data.attributes == ["title": "T"]
    !document.data.containsKey("relationships")
  }

  def "applies a fieldset by the configured renamed member names"() {
    given:
    def selection = RepresentationSelection.builder()
        .fields("articles", "title", "written-by")
        .build()
    def json = api().resources().writeOne(
        new ArticleWithRenamedAuthor("1", "T", new Person("p1", "Ann")),
        ResourceWriteOptions.defaults().withSelection(selection))
    def document = parse(json)

    expect:
    document.data.attributes == ["title": "T"]

    and:
    document.data.relationships.keySet().toList() == ["written-by"]
  }

  def "applies a fieldset to included resources"() {
    given:
    def selection = RepresentationSelection.builder()
        .include("comments")
        .fields("comments", "body")
        .build()
    def json = api().resources().writeOne(
        new Article("1", "T", "B", comments(), null), ResourceWriteOptions.defaults().withSelection(selection))
    def document = parse(json)

    expect:
    document.included*.attributes == [
      ["body": "Nice"],
      ["body": "Also"]
    ]
  }

  def "reads included resources as core resource objects in wire order"() {
    given:
    def json =
        '{"data":{"type":"articles","id":"1","attributes":{"title":"T"},' +
        '"relationships":{' +
        '"author":{"data":{"type":"people","id":"p1"}},' +
        '"comments":{"data":[{"type":"comments","id":"c1"},{"type":"comments","id":"c2"}]}}},' +
        '"included":[' +
        '{"type":"comments","id":"c1","attributes":{"body":"Nice"}},' +
        '{"type":"people","id":"p1","attributes":{"name":"Ann"}}]}'

    when:
    def result = api().resources().readOneDocument(json, FlatArticle)

    then:
    result.resource() == new FlatArticle(
        "1",
        "T",
        null,
        ResourceIdentifier.of("people", "p1"),
        List.of(ResourceIdentifier.of("comments", "c1"), ResourceIdentifier.of("comments", "c2")))

    and:
    result.included()*.type() == ["comments", "people"]
    result.included()*.id() == ["c1", "p1"]

    and:
    result.included().get(0).attributes().attributes() == ["body": "Nice"]
  }

  def "reads absent included state as null and present-empty included state as empty"() {
    when:
    def absent = api().resources().readOneDocument(
        '{"data":{"type":"articles","id":"1","attributes":{"title":"T"}}}', FlatArticle)

    then:
    absent.included() == null

    when:
    def empty = api().resources().readOneDocument(
        '{"data":{"type":"articles","id":"1","attributes":{"title":"T"}},"included":[]}', FlatArticle)

    then:
    empty.included() == []
  }
}
