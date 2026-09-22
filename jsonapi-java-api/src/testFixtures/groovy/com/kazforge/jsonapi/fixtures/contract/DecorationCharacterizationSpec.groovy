package com.kazforge.jsonapi.fixtures.contract

import com.kazforge.jsonapi.api.JsonApi
import com.kazforge.jsonapi.api.ResourceWriteOptions
import com.kazforge.jsonapi.core.model.Link
import com.kazforge.jsonapi.core.model.Links
import com.kazforge.jsonapi.core.model.ResourceIdentifier
import com.kazforge.jsonapi.fixtures.domainwrite.Article
import com.kazforge.jsonapi.fixtures.domainwrite.Comment
import com.kazforge.jsonapi.fixtures.domainwrite.Person
import com.kazforge.jsonapi.fixtures.domainwrite.RelationshipLinkageContainerFixtures.RenamedRelationshipLinkageArticle
import com.kazforge.jsonapi.mapping.RelationshipDecoration
import com.kazforge.jsonapi.mapping.RelationshipLinkage
import com.kazforge.jsonapi.mapping.ResourceDecoration
import com.kazforge.jsonapi.mapping.ResourceDecoratorRegistry
import com.kazforge.jsonapi.representation.RepresentationSelection
import groovy.json.JsonSlurper
import spock.lang.Specification

/**
 * Additive link-decoration characterization contract observed through the Level-1 {@code JsonApi}
 * contract: decoration contributes only resource and relationship {@code links}, preserves the
 * identity, attributes, linkage, and meta that ordinary mapping produced, keeps present-empty links
 * wire-visible while never creating a relationship or resurrecting a fieldset-omitted one, resolves
 * a decorated relationship's logical identity through its configured wire name, and matches a
 * decorator only on the exact effective runtime class.
 *
 * <p>Declared-type specialization during a write is not observable through the Level-1 resource
 * operation, whose declared type is the runtime class; the adapter mapping entry points retain that
 * coverage. Concrete adapter subclasses construct the Level-1 runtime from the supplied neutral
 * registry.
 */
abstract class DecorationCharacterizationSpec extends Specification {

  private static final Links RESOURCE_LINKS =
  Links.ofLinks([self: new Link.StringLink("https://example.test/articles/1")])

  private static final Links COMMENTS_LINKS = Links.ofLinks([
    self: new Link.StringLink("https://example.test/articles/1/relationships/comments"),
    related: new Link.StringLink("https://example.test/articles/1/comments")
  ])

  private static final Links PERSON_LINKS =
  Links.ofLinks([self: new Link.StringLink("https://example.test/people/p1")])

  /** Builds the Level-1 runtime configured with the given neutral decorator registry. */
  protected abstract JsonApi api(ResourceDecoratorRegistry decorators)

  private static Map<String, Object> parse(String json) {
    new JsonSlurper().parseText(json) as Map<String, Object>
  }

  private static Article article() {
    new Article("1", "T", "B", List.of(new Comment("c1", "Nice", null)), new Person("p1", "Alice"))
  }

  private static <T> ResourceDecoratorRegistry registry(Class<T> type, ResourceDecoration decoration) {
    ResourceDecoratorRegistry.builder()
        .register(type, new FixedResourceDecorator<Object>(decoration))
        .build()
  }

  private static ResourceDecoration relationshipDecoration(String logicalName, Links links) {
    ResourceDecoration.builder().relationship(logicalName, RelationshipDecoration.of(links)).build()
  }

  def "adds resource links without changing identity, attributes, or relationships"() {
    given:
    def decorators = registry(Article, ResourceDecoration.ofLinks(RESOURCE_LINKS))
    def document = parse(api(decorators).resources().writeOne(article()))

    expect:
    document.data.links == ["self": "https://example.test/articles/1"]
    document.data.id == "1"
    document.data.attributes == ["title": "T", "body-text": "B"]
    document.data.relationships.comments.data*.id == ["c1"]
    document.data.relationships.author.data == ["type": "people", "id": "p1"]
  }

  def "adds relationship links while preserving existing linkage"() {
    given:
    def decorators = registry(Article, relationshipDecoration("comments", COMMENTS_LINKS))
    def document = parse(api(decorators).resources().writeOne(article()))

    expect:
    document.data.relationships.comments.links == [
      "self": "https://example.test/articles/1/relationships/comments",
      "related": "https://example.test/articles/1/comments"
    ]
    document.data.relationships.comments.data*.id == ["c1"]
    document.data.relationships.author.links == null
  }

  def "keeps present-empty resource and relationship links wire-visible"() {
    given:
    def decoration = ResourceDecoration.builder()
        .links(Links.empty())
        .relationship("comments", RelationshipDecoration.of(Links.empty()))
        .build()
    def decorators = registry(Article, decoration)
    def document = parse(api(decorators).resources().writeOne(article()))

    expect:
    document.data.links == [:]
    document.data.relationships.comments.links == [:]
  }

  def "does not resurrect a fieldset-omitted relationship"() {
    given:
    def decorators = registry(Article, relationshipDecoration("comments", COMMENTS_LINKS))
    def selection = RepresentationSelection.builder().fields("articles", "title").build()
    def options = ResourceWriteOptions.defaults().withSelection(selection)
    def document = parse(api(decorators).resources().writeOne(article(), options))

    expect:
    document.data.attributes == ["title": "T"]
    !document.data.containsKey("relationships")
  }

  def "resolves a decorated logical relationship identity through its configured wire name"() {
    given:
    def decorators = registry(
        RenamedRelationshipLinkageArticle, relationshipDecoration("writtenBy", RESOURCE_LINKS))
    def article = new RenamedRelationshipLinkageArticle(
        "1", new RelationshipLinkage<>(ResourceIdentifier.of("people", "p1"), null))
    def document = parse(api(decorators).resources().writeOne(article))

    expect:
    document.data.relationships.author.links == ["self": "https://example.test/articles/1"]
    document.data.relationships.author.data == ["type": "people", "id": "p1"]
    !document.data.relationships.containsKey("writtenBy")
  }

  def "decorates an included resource"() {
    given:
    def decorators = ResourceDecoratorRegistry.builder()
        .register(Article, new FixedResourceDecorator<Object>(ResourceDecoration.empty()))
        .register(Person, new FixedResourceDecorator<Object>(ResourceDecoration.ofLinks(PERSON_LINKS)))
        .build()
    def selection = RepresentationSelection.builder().include("author").build()
    def options = ResourceWriteOptions.defaults().withSelection(selection)
    def document = parse(api(decorators).resources().writeOne(article(), options))

    expect:
    def included = document.included as List
    included.size() == 1
    included[0].type == "people"
    included[0].links == ["self": "https://example.test/people/p1"]
  }

  def "applies a decorator only on the exact effective runtime class"() {
    given:
    def decorators = registry(Person, ResourceDecoration.ofLinks(PERSON_LINKS))
    def document = parse(api(decorators).resources().writeOne(article()))

    expect:
    !document.data.containsKey("links")
    !document.data.relationships.comments.containsKey("links")
  }

  def "leaves the resource undecorated when the decorator contributes no links"() {
    given:
    def decorators = registry(Article, ResourceDecoration.empty())
    def document = parse(api(decorators).resources().writeOne(article()))

    expect:
    !document.data.containsKey("links")
    !document.data.relationships.comments.containsKey("links")
    !document.data.relationships.author.containsKey("links")
  }
}
