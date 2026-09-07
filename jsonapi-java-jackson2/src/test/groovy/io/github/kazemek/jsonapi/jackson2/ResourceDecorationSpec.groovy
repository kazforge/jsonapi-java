package io.github.kazemek.jsonapi.jackson2

import io.github.kazemek.jsonapi.core.model.DocumentData
import io.github.kazemek.jsonapi.core.model.Attributes
import io.github.kazemek.jsonapi.core.model.Link
import io.github.kazemek.jsonapi.core.model.Links
import io.github.kazemek.jsonapi.core.model.Relationship
import io.github.kazemek.jsonapi.core.model.RelationshipData
import io.github.kazemek.jsonapi.core.model.Relationships
import io.github.kazemek.jsonapi.core.model.ResourceIdentifier
import io.github.kazemek.jsonapi.core.model.ResourceObject
import io.github.kazemek.jsonapi.jackson.diagnostic.JsonApiMappingException
import io.github.kazemek.jsonapi.jackson.diagnostic.MappingDiagnostic
import io.github.kazemek.jsonapi.jackson.mapping.RelationshipDecoration
import io.github.kazemek.jsonapi.jackson.mapping.ResourceDecoration
import io.github.kazemek.jsonapi.jackson.mapping.ResourceDecorator
import io.github.kazemek.jsonapi.jackson.mapping.ResourceDecoratorRegistry
import io.github.kazemek.jsonapi.jackson.representation.FieldPolicy
import io.github.kazemek.jsonapi.jackson.representation.IncludePath
import io.github.kazemek.jsonapi.jackson.representation.IncludePolicy
import io.github.kazemek.jsonapi.jackson.representation.RepresentationPolicy
import io.github.kazemek.jsonapi.jackson.representation.RepresentationSelection
import io.github.kazemek.jsonapi.fixtures.domainwrite.Article
import io.github.kazemek.jsonapi.fixtures.domainwrite.Comment
import io.github.kazemek.jsonapi.fixtures.domainwrite.Person
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll
import com.fasterxml.jackson.databind.json.JsonMapper

class ResourceDecorationSpec extends Specification {

  @Shared
  Links resourceLinks = Links.ofLinks([self: new Link.StringLink("https://example.test/articles/1")])

  @Shared
  Links commentsLinks = Links.ofLinks([self: new Link.StringLink("https://example.test/articles/1/relationships/comments"), related: new Link.StringLink("https://example.test/articles/1/comments")])

  @Shared
  Links personLinks = Links.ofLinks([self: new Link.StringLink("https://example.test/people/p1")])

  @Unroll
  def "decorates #id"() {
    given:
    def mapper = JsonApiJackson2.resourceMapper(JsonMapper.builder().build(), registry)

    when:
    def resource = mapper.toResource(input)

    then:
    resource == expectedResource

    where:
    id | input | registry | expectedResource
    "resource links preserve attributes and linkage" | new Article("1", "Title", "Body", List.of(), null) | ResourceDecoratorRegistry.builder().register(Article, { Article a -> ResourceDecoration.ofLinks(resourceLinks) } as ResourceDecorator).build() | expectedArticle(resourceLinks, null, nullLinkage(), emptyCommentsLinkage())
    "relationship links preserve linkage" | new Article("1", "Title", "Body", List.of(new Comment("c1", "Nice", null)), null) | ResourceDecoratorRegistry.builder().register(Article, { Article a -> ResourceDecoration.builder().relationship("comments", RelationshipDecoration.of(commentsLinks)).build() } as ResourceDecorator).build() | expectedArticle(null, commentsLinks, nullLinkage(), commentsLinkage())
    "resource and relationship links together preserve author linkage" | new Article("1", "Title", "Body", List.of(new Comment("c1", "Nice", null)), new Person("p1", "Alice")) | ResourceDecoratorRegistry.builder().register(Article, { Article a -> ResourceDecoration.builder().links(resourceLinks).relationship("comments", RelationshipDecoration.of(commentsLinks)).build() } as ResourceDecorator).build() | expectedArticle(resourceLinks, commentsLinks, authorLinkage(), commentsLinkage())
    "present-empty resource links are preserved" | new Article("1", "Title", "Body", List.of(), null) | ResourceDecoratorRegistry.builder().register(Article, { Article a -> ResourceDecoration.ofLinks(Links.empty()) } as ResourceDecorator).build() | expectedArticle(Links.empty(), null, nullLinkage(), emptyCommentsLinkage())
    "present-empty relationship links are preserved" | new Article("1", "Title", "Body", List.of(new Comment("c1", "Nice", null)), null) | ResourceDecoratorRegistry.builder().register(Article, { Article a -> ResourceDecoration.builder().relationship("comments", RelationshipDecoration.of(Links.empty())).build() } as ResourceDecorator).build() | expectedArticle(null, Links.empty(), nullLinkage(), commentsLinkage())
  }

  @Unroll
  def "decorates #id fails"() {
    given:
    def mapper = JsonApiJackson2.resourceMapper(JsonMapper.builder().build(), registry)

    when:
    mapper.toResource(input)

    then:
    def ex = thrown(JsonApiMappingException)
    ex.diagnostic() == expectedDiagnostic

    where:
    id | input | registry | expectedDiagnostic
    "unknown relationship target is invalid" | new Article("1", "Title", "Body", List.of(), null) | ResourceDecoratorRegistry.builder().register(Article, { Article a -> ResourceDecoration.builder().relationship("unknown", RelationshipDecoration.of(commentsLinks)).build() } as ResourceDecorator).build() | MappingDiagnostic.INVALID_DECORATION_TARGET
    "non-relationship target is invalid" | new Article("1", "Title", "Body", List.of(), null) | ResourceDecoratorRegistry.builder().register(Article, { Article a -> ResourceDecoration.builder().relationship("title", RelationshipDecoration.of(commentsLinks)).build() } as ResourceDecorator).build() | MappingDiagnostic.INVALID_DECORATION_TARGET
    "decorator returns null is invalid" | new Article("1", "Title", "Body", List.of(), null) | ResourceDecoratorRegistry.builder().register(Article, { Article a -> null } as ResourceDecorator).build() | MappingDiagnostic.INVALID_DECORATION_STATE
  }

  @Unroll
  def "decorates document #id"() {
    given:
    def mapper = JsonApiJackson2.resourceMapper(JsonMapper.builder().build(), registry)

    when:
    def document = mapper.toDocument(input, null, selection, policy)
    def primary = (document.data() as DocumentData.SingleResource).resource()

    then:
    primary == expectedPrimary
    document.included() == expectedIncluded

    where:
    id | input | registry | selection | policy | expectedPrimary | expectedIncluded
    "included resource receives decoration" | new Article("1", "Title", "Body", List.of(), new Person("p1", "Alice")) | ResourceDecoratorRegistry.builder().register(Article, { Article a -> ResourceDecoration.empty() } as ResourceDecorator).register(Person, { Person p -> ResourceDecoration.ofLinks(personLinks) } as ResourceDecorator).build() | RepresentationSelection.builder().include(IncludePath.of("author")).build() | RepresentationPolicy.defaults().withIncludePolicy(IncludePolicy.allowAll()).withFieldPolicy(FieldPolicy.allowAll()) | expectedArticle(null, null, authorLinkage(), emptyCommentsLinkage()) | [expectedPerson(personLinks)]
    "decorated relationship survives without inclusion" | new Article("1", "Title", "Body", List.of(new Comment("c1", "Nice", null)), null) | ResourceDecoratorRegistry.builder().register(Article, { Article a -> ResourceDecoration.builder().relationship("comments", RelationshipDecoration.of(commentsLinks)).build() } as ResourceDecorator).build() | RepresentationSelection.none() | RepresentationPolicy.defaults().withIncludePolicy(IncludePolicy.allowAll()).withFieldPolicy(FieldPolicy.allowAll()) | expectedArticle(null, commentsLinks, nullLinkage(), commentsLinkage()) | null
  }

  def "decorated relationship does not resurrect fieldset-omitted relationship"() {
    given:
    def registry = ResourceDecoratorRegistry.builder().register(Article, { Article a -> ResourceDecoration.builder().relationship("comments", RelationshipDecoration.of(commentsLinks)).build() } as ResourceDecorator).build()
    def mapper = JsonApiJackson2.resourceMapper(JsonMapper.builder().build(), registry)
    def article = new Article("1", "Title", "Body", List.of(new Comment("c1", "Nice", null)), null)
    def selection = RepresentationSelection.builder().fields("articles", "title").build()
    def policy = RepresentationPolicy.defaults().withIncludePolicy(IncludePolicy.allowAll()).withFieldPolicy(FieldPolicy.allowAll())

    when:
    def mapped = mapper.toMappedDocument(article, null, selection, policy)
    def primary = (mapped.document().data() as DocumentData.SingleResource).resource()

    then:
    primary.relationships() == null
    primary.attributes().attributes().size() == 1
    primary.attributes().attributes().containsKey("title")
  }

  def "decorated relationship survives when selected via fieldset"() {
    given:
    def registry = ResourceDecoratorRegistry.builder().register(Article, { Article a -> ResourceDecoration.builder().relationship("comments", RelationshipDecoration.of(commentsLinks)).build() } as ResourceDecorator).build()
    def mapper = JsonApiJackson2.resourceMapper(JsonMapper.builder().build(), registry)
    def article = new Article("1", "Title", "Body", List.of(new Comment("c1", "Nice", null)), null)
    def selection = RepresentationSelection.builder().fields("articles", "title", "comments").build()
    def policy = RepresentationPolicy.defaults().withIncludePolicy(IncludePolicy.allowAll()).withFieldPolicy(FieldPolicy.allowAll())

    when:
    def mapped = mapper.toMappedDocument(article, null, selection, policy)
    def primary = (mapped.document().data() as DocumentData.SingleResource).resource()

    then:
    primary.relationships() != null
    primary.relationships().relationships().containsKey("comments")
    primary.relationships().relationships().get("comments").links() == commentsLinks
    primary.relationships().relationships().get("comments").data().identifiers().size() == 1
  }

  def "configured Jackson rename follows logical identity"() {
    given:
    ResourceDecorator<DecorationFixtures.RenamedRelationshipArticle> decorator = { a ->
      ResourceDecoration.builder().relationship("comments", RelationshipDecoration.links(commentsLinks)).build()
    }
    def registry = ResourceDecoratorRegistry.builder().register(DecorationFixtures.RenamedRelationshipArticle, decorator).build()
    def mapper = JsonApiJackson2.resourceMapper(JsonMapper.builder().build(), registry)
    def article = new DecorationFixtures.RenamedRelationshipArticle("1", "Title", [
      new DecorationFixtures.Comment("c1", "B", null)
    ], new DecorationFixtures.Person("p1", "Alice"))

    when:
    def resource = mapper.toResource(article)

    then:
    resource.relationships().relationships().containsKey("article-comments")
    !resource.relationships().relationships().containsKey("comments")
    resource.relationships().relationships().get("article-comments").links() == commentsLinks
    resource.relationships().relationships().get("article-comments").data().identifiers().size() == 1
  }

  def "generic type specialization retains decoration registry lookup on raw class"() {
    given:
    Links thingLinks = Links.ofLinks([self: new Link.StringLink("https://example.test/things/t1")])
    ResourceDecorator<DecorationFixtures.Thing> thingDecorator = { t -> ResourceDecoration.builder().links(thingLinks).build() }
    ResourceDecorator<DecorationFixtures.GenericContainer<DecorationFixtures.Thing>> containerDecorator = { c -> ResourceDecoration.builder().links(resourceLinks).build() }
    def registry = ResourceDecoratorRegistry.builder()
        .register(DecorationFixtures.Thing, thingDecorator)
        .register(DecorationFixtures.GenericContainer, containerDecorator)
        .build()
    def base = JsonMapper.builder().build()
    def mapper = JsonApiJackson2.resourceMapper(base, registry)
    def thing = new DecorationFixtures.Thing("t1", "Thing")
    def container = new DecorationFixtures.GenericContainer<DecorationFixtures.Thing>("c1", "Container", thing)
    def containerType = base.typeFactory.constructParametricType(DecorationFixtures.GenericContainer, DecorationFixtures.Thing)

    when:
    def resource = mapper.toResource(container, containerType)

    then:
    resource.links() == resourceLinks
  }

  def "decorator registry is immutable and safe for concurrent use"() {
    given:
    def registry = ResourceDecoratorRegistry.builder()
        .register(DecorationFixtures.ArticleWithMeta, { a -> ResourceDecoration.empty() } as ResourceDecorator)
        .build()

    when:
    registry.asMap().put(String, { s -> ResourceDecoration.empty() } as ResourceDecorator)

    then:
    thrown(UnsupportedOperationException)
  }

  def "present-empty resource and relationship links are preserved"() {
    given:
    Links empty = Links.empty()
    ResourceDecorator<DecorationFixtures.ArticleWithMeta> decorator = { a ->
      ResourceDecoration.builder().links(empty).relationship("comments", RelationshipDecoration.of(empty)).build()
    }
    def registry = ResourceDecoratorRegistry.builder().register(DecorationFixtures.ArticleWithMeta, decorator).build()
    def mapper = JsonApiJackson2.resourceMapper(JsonMapper.builder().build(), registry)
    def article = new DecorationFixtures.ArticleWithMeta("1", "Title", null, [
      new DecorationFixtures.Comment("c1", "B", null)
    ], null)

    when:
    def resource = mapper.toResource(article)
    def doc = mapper.toDocument(article)
    def json = JsonApiJackson2.writer(JsonMapper.builder().build()).writeValueAsString(doc)
    def tree = JsonMapper.builder().build().readTree(json)

    then:
    resource.links() == empty
    resource.links() != null
    resource.relationships().relationships().comments.links() == empty
    tree.get("data").get("links").isObject()
    tree.get("data").get("links").isEmpty()
    tree.get("data").get("relationships").get("comments").get("links").isObject()
    tree.get("data").get("relationships").get("comments").get("links").isEmpty()
  }

  private static ResourceObject expectedArticle(
      Links resourceLinks,
      Links commentsLinks,
      RelationshipData authorData,
      RelationshipData commentsData) {
    new ResourceObject(
        "articles",
        "1",
        null,
        Attributes.ofAttributes([title: "Title", "body-text": "Body"]),
        Relationships.ofRelationships([
          author: new Relationship(authorData, null, null, Map.of()),
          comments: new Relationship(commentsData, commentsLinks, null, Map.of())
        ]),
        resourceLinks,
        null,
        Map.of())
  }

  private static ResourceObject expectedPerson(Links links) {
    new ResourceObject(
        "people",
        "p1",
        null,
        Attributes.ofAttributes([name: "Alice"]),
        null,
        links,
        null,
        Map.of())
  }

  private static RelationshipData nullLinkage() {
    RelationshipData.NullLinkage.INSTANCE
  }

  private static RelationshipData emptyCommentsLinkage() {
    RelationshipData.IdentifierCollectionLinkage.empty()
  }

  private static RelationshipData commentsLinkage() {
    new RelationshipData.IdentifierCollectionLinkage([
      new ResourceIdentifier("comments", "c1", null, null, Map.of())
    ])
  }

  private static RelationshipData authorLinkage() {
    new RelationshipData.SingleLinkage(
        new ResourceIdentifier("people", "p1", null, null, Map.of()))
  }
}
