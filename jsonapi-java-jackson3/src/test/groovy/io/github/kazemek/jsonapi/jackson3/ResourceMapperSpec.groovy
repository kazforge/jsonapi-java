package io.github.kazemek.jsonapi.jackson3

import io.github.kazemek.jsonapi.core.model.Attributes
import io.github.kazemek.jsonapi.core.model.DocumentData
import io.github.kazemek.jsonapi.core.model.JsonApiDocument
import io.github.kazemek.jsonapi.core.model.JsonApiObject
import io.github.kazemek.jsonapi.core.model.Links
import io.github.kazemek.jsonapi.core.model.Meta
import io.github.kazemek.jsonapi.core.model.Relationship
import io.github.kazemek.jsonapi.core.model.RelationshipData
import io.github.kazemek.jsonapi.core.model.Relationships
import io.github.kazemek.jsonapi.core.model.ResourceIdentifier
import io.github.kazemek.jsonapi.core.model.ResourceObject
import io.github.kazemek.jsonapi.jackson.diagnostic.JsonApiMappingException
import io.github.kazemek.jsonapi.jackson.diagnostic.MappingDiagnostic
import io.github.kazemek.jsonapi.jackson.document.DocumentEnvelope
import io.github.kazemek.jsonapi.jackson.mapping.RelationshipLinkage
import io.github.kazemek.jsonapi.jackson.representation.RepresentationPolicy
import io.github.kazemek.jsonapi.jackson.representation.RepresentationSelection
import io.github.kazemek.jsonapi.fixtures.domainpatch.ArticleMeta
import io.github.kazemek.jsonapi.fixtures.domainpatch.ArticleWithMapMeta
import io.github.kazemek.jsonapi.fixtures.domainpatch.ArticleWithMeta
import io.github.kazemek.jsonapi.fixtures.domainpatch.ArticleWithOptionalMeta
import io.github.kazemek.jsonapi.fixtures.domainpatch.ArticleWithRelationshipLinkage
import io.github.kazemek.jsonapi.fixtures.domainpatch.AuthorIdMeta
import io.github.kazemek.jsonapi.fixtures.domainpatch.AuthorMeta
import io.github.kazemek.jsonapi.fixtures.domainpatch.CommentIdMeta
import io.github.kazemek.jsonapi.fixtures.domainpatch.CommentsRelationshipMeta
import io.github.kazemek.jsonapi.fixtures.domainpatch.WholeMetaTargetFixtures
import io.github.kazemek.jsonapi.fixtures.domainwrite.Article
import io.github.kazemek.jsonapi.fixtures.domainwrite.ArticleWithSet
import io.github.kazemek.jsonapi.fixtures.domainwrite.ArticleWithUnannotatedExtra
import io.github.kazemek.jsonapi.fixtures.domainwrite.BlogWithJsonProperty
import io.github.kazemek.jsonapi.fixtures.domainwrite.Comment
import io.github.kazemek.jsonapi.fixtures.domainwrite.ConventionalId
import io.github.kazemek.jsonapi.fixtures.domainwrite.InheritedBlogFixtures
import io.github.kazemek.jsonapi.fixtures.domainwrite.Person
import io.github.kazemek.jsonapi.fixtures.domainwrite.RelationshipContainerFixtures
import io.github.kazemek.jsonapi.fixtures.domainwrite.RelationshipLinkageContainerFixtures
import io.github.kazemek.jsonapi.fixtures.domainwrite.SamplePojo
import io.github.kazemek.jsonapi.fixtures.domainwrite.Tag
import io.github.kazemek.jsonapi.fixtures.localid.LocalIdentityArticle
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll
import tools.jackson.databind.json.JsonMapper

class ResourceMapperSpec extends Specification {

  @Shared
  JsonApiResourceMapper mapper = JsonApiJackson3.resourceMapper(JsonMapper.builder().build())

  private static final String COMMENTS = "comments"
  private static final String PEOPLE = "people"
  private static final String ARTICLES = "articles"
  private static final String TITLE = "title"
  private static final String ALICE = "Alice"
  private static final String EDITOR = "editor"
  private static final String ROLE = "role"
  private static final String DISPLAY_NAME = "displayName"
  private static final String TAGS = "tags"
  private static final String AUTHOR = "author"
  private static final String TITLE_TEXT = "Title"
  private static final String MY_BLOG = "My Blog"
  private static final String GREAT = "Great"
  private static final String PINNED = "pinned"
  private static final String EXT_HREF = "ext:href"
  private static final String EXAMPLE_HREF = "https://example.test/p1"
  private static final String SOURCE = "source"

  private static final Set<Tag> TAGS_SET =
  Collections.unmodifiableSet(new LinkedHashSet<>(List.of(new Tag("java"), new Tag("groovy"))))

  private static final Links ENVELOPE_LINKS = Links.ofLinks(Collections.singletonMap("self", null))
  private static final Meta ENVELOPE_META = Meta.of(Map.of("key", "value"))
  private static final JsonApiObject ENVELOPE_JSONAPI = JsonApiObject.ofVersion("1.1")

  @Unroll
  def "maps #id to resource"() {
    when:
    def actual = mapper.toResource(input)

    then:
    actual == expected

    where:
    id | input | expected
    "explicit @JsonApiId and @JsonApiAttribute" | new Article("1", "Hello", "Body text", List.of(), null) | articleResource("1", "Hello", "Body text", List.of(), null)
    "attribute name override" | new Article("1", TITLE_TEXT, "Content", List.of(), null) | articleResource("1", TITLE_TEXT, "Content", List.of(), null)
    "conventional id property" | new ConventionalId("42", "name value") | new ResourceObject("conventionals", "42", null, Attributes.ofAttributes(singleAttribute("name", "name value")), null, null, null, Map.of())
    "unannotated extra property is not an attribute" | new ArticleWithUnannotatedExtra("1", TITLE_TEXT, "secret") | attributesOnlyArticle("1", Map.of(TITLE, TITLE_TEXT))
    "maps @JsonProperty naming" | new BlogWithJsonProperty("b1", MY_BLOG) | new ResourceObject("blogs", "b1", null, Attributes.ofAttributes(singleAttribute("blog_title", MY_BLOG)), null, null, null, Map.of())
    "nullable to-one relationship to null linkage" | new Article("1", "T", "B", List.of(), null) | articleResource("1", "T", "B", List.of(), null)
    "to-one relationship to single linkage" | new Article("1", "T", "B", List.of(), new Person("p1", ALICE)) | articleResource("1", "T", "B", List.of(), new Person("p1", ALICE))
    "empty to-many relationship to empty linkage" | new Article("1", "T", "B", List.of(), null) | articleResource("1", "T", "B", List.of(), null)
    "populated to-many relationship" | new Article("1", "T", "B", List.of(new Comment("c1", "Nice", null), new Comment("c2", GREAT, null)), null) | articleResource("1", "T", "B", List.of(new Comment("c1", "Nice", null), new Comment("c2", GREAT, null)), null)
    "mutable POJO" | new SamplePojo("p1", "Example", List.of()) | new ResourceObject("pojos", "p1", null, Attributes.ofAttributes(singleAttribute("display-name", "Example")), Relationships.ofRelationships(Map.of(COMMENTS, relationship(RelationshipData.IdentifierCollectionLinkage.empty()))), null, null, Map.of())
    "to-one identifier meta onto linkage" | new ArticleWithRelationshipLinkage("1", "T", new RelationshipLinkage<>(ResourceIdentifier.of(PEOPLE, "p1"), new AuthorIdMeta(EDITOR)), List.of(), null, null) | identifierMetaArticle(identifier(PEOPLE, "p1", Meta.of(Map.of(ROLE, EDITOR))), null, List.of(), null)
    "to-many identifier meta with each wrapper element" | new ArticleWithRelationshipLinkage("1", "T", null, List.of(new RelationshipLinkage<>(ResourceIdentifier.of(COMMENTS, "c1"), new CommentIdMeta(true)), new RelationshipLinkage<>(ResourceIdentifier.of(COMMENTS, "c2"), null)), null, null) | identifierMetaArticle(null, null, List.of(identifier(COMMENTS, "c1", Meta.of(Map.of(PINNED, true))), ResourceIdentifier.of(COMMENTS, "c2")), null)
    "null wrapper meta leaves ResourceIdentifier meta in place" | new ArticleWithRelationshipLinkage("1", "T", new RelationshipLinkage<>(identifier(PEOPLE, "p1", null, Meta.of(Map.of(ROLE, EDITOR)), Map.of(EXT_HREF, EXAMPLE_HREF)), null), List.of(), null, null) | identifierMetaArticle(identifier(PEOPLE, "p1", null, Meta.of(Map.of(ROLE, EDITOR)), Map.of(EXT_HREF, EXAMPLE_HREF)), null, List.of(), null)
    "relationship meta and identifier meta independently" | new ArticleWithRelationshipLinkage("1", "T", new RelationshipLinkage<>(ResourceIdentifier.of(PEOPLE, "p1"), new AuthorIdMeta(EDITOR)), List.of(new RelationshipLinkage<>(ResourceIdentifier.of(COMMENTS, "c1"), new CommentIdMeta(true))), new AuthorMeta(ALICE), new CommentsRelationshipMeta("open")) | identifierMetaArticle(identifier(PEOPLE, "p1", Meta.of(Map.of(ROLE, EDITOR))), Meta.of(Map.of(DISPLAY_NAME, ALICE)), List.of(identifier(COMMENTS, "c1", Meta.of(Map.of(PINNED, true)))), Meta.of(Map.of("status", "open")))
    "empty to-many RelationshipLinkage collection" | new ArticleWithRelationshipLinkage("1", "T", null, List.of(), null, null) | identifierMetaArticle(null, null, List.of(), null)
    "array to-many RelationshipLinkage identifier meta" | new RelationshipLinkageContainerFixtures.ArrayRelationshipLinkageArticle("1", [
      new RelationshipLinkage<>(ResourceIdentifier.of(COMMENTS, "c1"), new CommentIdMeta(true)),
      new RelationshipLinkage<>(ResourceIdentifier.of(COMMENTS, "c2"), null)
    ] as RelationshipLinkage[]) | commentsOnlyArticle(List.of(identifier(COMMENTS, "c1", Meta.of(Map.of(PINNED, true))), ResourceIdentifier.of(COMMENTS, "c2")))
    "Optional RelationshipLinkage identifier meta" | new RelationshipLinkageContainerFixtures.OptionalRelationshipLinkageArticle("1", Optional.of(new RelationshipLinkage<>(ResourceIdentifier.of(PEOPLE, "p1"), new AuthorIdMeta(EDITOR)))) | authorOnlyArticle(identifier(PEOPLE, "p1", Meta.of(Map.of(ROLE, EDITOR))))
    "Map identifier meta on to-many RelationshipLinkage" | new RelationshipLinkageContainerFixtures.MapRelationshipLinkageArticle("1", List.of(new RelationshipLinkage<>(ResourceIdentifier.of(COMMENTS, "c1"), Map.of(PINNED, true)))) | commentsOnlyArticle(List.of(identifier(COMMENTS, "c1", Meta.of(Map.of(PINNED, true)))))
    "renamed RelationshipLinkage identifier meta onto the wire name" | new RelationshipLinkageContainerFixtures.RenamedRelationshipLinkageArticle("1", new RelationshipLinkage<>(ResourceIdentifier.of(PEOPLE, "p1"), new AuthorIdMeta(EDITOR))) | authorOnlyArticle(identifier(PEOPLE, "p1", Meta.of(Map.of(ROLE, EDITOR))))
    "identifier-meta overlay preserves ResourceIdentifier lid" | new ArticleWithRelationshipLinkage("1", "T", new RelationshipLinkage<>(identifier(PEOPLE, null, "lid-1", null, Map.of()), new AuthorIdMeta(EDITOR)), List.of(), null, null) | identifierMetaArticle(identifier(PEOPLE, null, "lid-1", Meta.of(Map.of(ROLE, EDITOR)), Map.of()), null, List.of(), null)
    "identifier-meta overlay preserves additional members" | new ArticleWithRelationshipLinkage("1", "T", new RelationshipLinkage<>(identifier(PEOPLE, "p1", null, Meta.of(Map.of(ROLE, "old")), Map.of(EXT_HREF, EXAMPLE_HREF)), new AuthorIdMeta(EDITOR)), List.of(), null, null) | identifierMetaArticle(identifier(PEOPLE, "p1", null, Meta.of(Map.of(ROLE, EDITOR)), Map.of(EXT_HREF, EXAMPLE_HREF)), null, List.of(), null)
    "resource meta and relationship meta" | new ArticleWithMeta("1", "T", ResourceIdentifier.of(PEOPLE, "p1"), new ArticleMeta("cms", "n"), new AuthorMeta(ALICE)) | articleWithMetaResource(Meta.of(Map.of(SOURCE, "cms", "note", "n")), ResourceIdentifier.of(PEOPLE, "p1"), Meta.of(Map.of(DISPLAY_NAME, ALICE)))
    "null meta properties omit meta members" | new ArticleWithMeta("1", "T", null, null, null) | articleWithMetaResource(null, null, null)
    "empty map meta emits empty members" | new ArticleWithMapMeta("1", "T", null, Map.of(), null) | articleWithMetaResource(Meta.empty(), null, null)
    "populated map meta writes resource and relationship members" | new ArticleWithMapMeta("1", "T", ResourceIdentifier.of(PEOPLE, "p1"), Map.of(SOURCE, "cms"), Map.of(DISPLAY_NAME, ALICE)) | articleWithMetaResource(Meta.of(Map.of(SOURCE, "cms")), ResourceIdentifier.of(PEOPLE, "p1"), Meta.of(Map.of(DISPLAY_NAME, ALICE)))
    "renamed relationship meta onto the wire name" | new WholeMetaTargetFixtures.RenamedRelationshipMetaArticle("1", "T", ResourceIdentifier.of(PEOPLE, "p1"), new ArticleMeta("cms", "n"), new AuthorMeta(ALICE)) | articleWithMetaResource(Meta.of(Map.of(SOURCE, "cms", "note", "n")), ResourceIdentifier.of(PEOPLE, "p1"), Meta.of(Map.of(DISPLAY_NAME, ALICE)))
    "Object whole-meta target writes a map value" | new WholeMetaTargetFixtures.ObjectMetaArticle("1", Map.of(SOURCE, "cms")) | objectMetaArticle(Meta.of(Map.of(SOURCE, "cms")))
    "Optional-wrapped bean meta writes unwrapped members" | new ArticleWithOptionalMeta("1", "T", null, Optional.of(new ArticleMeta("cms", "n")), Optional.of(new AuthorMeta(ALICE))) | articleWithMetaResource(Meta.of(Map.of(SOURCE, "cms", "note", "n")), null, Meta.of(Map.of(DISPLAY_NAME, ALICE)))
    "present Optional attribute is unwrapped" | new RelationshipContainerFixtures.ArticleWithOptionalAttribute("1", TITLE_TEXT, Optional.of("Sub")) | attributesOnlyArticle("1", Map.of(TITLE, TITLE_TEXT, "subtitle", "Sub"))
    "empty Optional attribute is omitted" | new RelationshipContainerFixtures.ArticleWithOptionalAttribute("1", TITLE_TEXT, Optional.empty()) | attributesOnlyArticle("1", Map.of(TITLE, TITLE_TEXT))
    "array to-many relationship produces collection linkage" | new RelationshipContainerFixtures.ArticleWithCommentArray("1", "T", [
      new Comment("c1", "Nice", null),
      new Comment("c2", GREAT, null)
    ] as Comment[]) | titledCommentsArticle("T", List.of(ResourceIdentifier.of(COMMENTS, "c1"), ResourceIdentifier.of(COMMENTS, "c2")))
    "present Optional to-one relationship produces single linkage" | new RelationshipContainerFixtures.ArticleWithOptionalRelationship("1", Optional.of(new Comment("c1", "Nice", null))) | commentRelationshipArticle(new RelationshipData.SingleLinkage(ResourceIdentifier.of(COMMENTS, "c1")))
    "empty Optional to-one relationship produces null linkage" | new RelationshipContainerFixtures.ArticleWithOptionalRelationship("1", Optional.empty()) | commentRelationshipArticle(RelationshipData.NullLinkage.INSTANCE)
    "present Optional id is unwrapped to the identifier string" | new RelationshipContainerFixtures.ArticleWithOptionalId(Optional.of("99"), TITLE_TEXT) | attributesOnlyArticle("99", Map.of(TITLE, TITLE_TEXT))
    "inherited properties from a base class are mapped" | new InheritedBlogFixtures.ExtendedBlog("b1", MY_BLOG, "A description") | new ResourceObject("blogs", "b1", null, Attributes.ofAttributes(Map.of("name", MY_BLOG, "description", "A description")), null, null, null, Map.of())
    "leading null in a to-many ResourceIdentifier collection is skipped" | new RelationshipContainerFixtures.ArticleWithNullableIdentifierList("1", nullableList((ResourceIdentifier) null, ResourceIdentifier.of(COMMENTS, "1"))) | itemsRelationshipArticle(List.of(ResourceIdentifier.of(COMMENTS, "1")))
    "leading null in a to-many ResourceIdentifier array is skipped" | new RelationshipContainerFixtures.ArticleWithNullableIdentifierArray("1", [
      null,
      ResourceIdentifier.of(COMMENTS, "1")
    ] as ResourceIdentifier[]) | itemsRelationshipArticle(List.of(ResourceIdentifier.of(COMMENTS, "1")))
  }


  def "maps Set-based relationships without depending on Set iteration order"() {
    when:
    def actual = mapper.toResource(new ArticleWithSet("1", "T", TAGS_SET))

    then:
    actual.type() == ARTICLES
    actual.id() == "1"
    actual.attributes().attributes() == [title: "T"]
    def linkage = actual.relationships().relationships().tags.data()
    linkage instanceof RelationshipData.IdentifierCollectionLinkage
    linkage.identifiers().size() == 2
    new HashSet<>(linkage.identifiers()) ==
        new HashSet<>(List.of(
        ResourceIdentifier.of(TAGS, "java"),
        ResourceIdentifier.of(TAGS, "groovy")))
  }

  def "maps Set RelationshipLinkage meta without depending on Set iteration order"() {
    given:
    def input = new RelationshipLinkageContainerFixtures.SetRelationshipLinkageArticle(
        "1",
        Set.of(new RelationshipLinkage<>(
        ResourceIdentifier.of(COMMENTS, "c1"), new CommentIdMeta(true))))

    when:
    def actual = mapper.toResource(input)

    then:
    actual.type() == ARTICLES
    actual.id() == "1"
    def linkage = actual.relationships().relationships().comments.data()
    linkage instanceof RelationshipData.IdentifierCollectionLinkage
    linkage.identifiers().size() == 1
    new HashSet<>(linkage.identifiers()) ==
        Set.of(identifier(COMMENTS, "c1", Meta.of(Map.of(PINNED, true))))
  }

  @Unroll
  def "maps #id to document"() {
    when:
    def actual = mapper.toDocument(input)

    then:
    actual == expected

    where:
    id | input | expected
    "toDocument wraps resource in single-resource document" | new Article("1", "T", "B", List.of(), null) | new JsonApiDocument(new DocumentData.SingleResource(articleResource("1", "T", "B", List.of(), null)), null, null, null, null, null, Map.of())
  }

  @Unroll
  def "maps #id to resource collection"() {
    when:
    def actual = mapper.toCollectionDocument(input)

    then:
    actual == expected

    where:
    id | input | expected
    "toCollectionDocument wraps in resource-collection document" | List.of(new Article("1", "One", "B1", List.of(), null), new Article("2", "Two", "B2", List.of(), null)) | new JsonApiDocument(new DocumentData.ResourceCollection(List.of(articleResource("1", "One", "B1", List.of(), null), articleResource("2", "Two", "B2", List.of(), null))), null, null, null, null, null, Map.of())
  }

  @Unroll
  def "maps #id to document with envelope"() {
    when:
    def actual = mapper.toDocument(input, envelope)

    then:
    actual == expected

    where:
    id | input | envelope | expected
    "toDocument with envelope passes links, meta, and jsonapi" | new Article("1", "T", "B", List.of(), null) | new DocumentEnvelope(ENVELOPE_LINKS, ENVELOPE_META, ENVELOPE_JSONAPI) | new JsonApiDocument(new DocumentData.SingleResource(articleResource("1", "T", "B", List.of(), null)), null, ENVELOPE_META, ENVELOPE_JSONAPI, ENVELOPE_LINKS, null, Map.of())
  }

  def "rejects null input"() {
    when:
    mapper.toResource((Object) null)

    then:
    thrown(NullPointerException)
  }

  private static Map<String, Object> singleAttribute(String name, Object value) {
    Map<String, Object> attributes = new LinkedHashMap<>()
    attributes.put(name, value)
    return attributes
  }

  private static Map<String, Object> articleAttributes(String title, String body) {
    Map<String, Object> attributes = new LinkedHashMap<>()
    attributes.put(TITLE, title)
    attributes.put("body-text", body)
    return attributes
  }

  private static ResourceObject articleResource(
      String id, String title, String body, List<Comment> comments, Person author) {
    return new ResourceObject(
        ARTICLES,
        id,
        null,
        Attributes.ofAttributes(articleAttributes(title, body)),
        Relationships.ofRelationships(
        articleRelationships(personLinkage(author), commentsLinkage(comments))),
        null,
        null,
        Map.of())
  }

  private static ResourceObject articleWithSetResource() {
    return new ResourceObject(
        ARTICLES,
        "1",
        null,
        Attributes.ofAttributes(singleAttribute(TITLE, "T")),
        Relationships.ofRelationships(Map.of(TAGS, relationship(tagsLinkage()))),
        null,
        null,
        Map.of())
  }

  private static Map<String, Relationship> articleRelationships(
      RelationshipData authorLinkage, RelationshipData commentsLinkage) {
    Map<String, Relationship> relationships = new LinkedHashMap<>()
    relationships.put(AUTHOR, relationship(authorLinkage))
    relationships.put(COMMENTS, relationship(commentsLinkage))
    return relationships
  }

  private static Relationship relationship(RelationshipData data) {
    return new Relationship(data, null, null, Map.of())
  }

  private static RelationshipData personLinkage(Person author) {
    if (author == null) {
      return RelationshipData.NullLinkage.INSTANCE
    }
    return new RelationshipData.SingleLinkage(
        new ResourceIdentifier(PEOPLE, author.id(), null, null, Map.of()))
  }

  private static RelationshipData commentsLinkage(List<Comment> comments) {
    List<ResourceIdentifier> identifiers = new ArrayList<>(comments.size())
    for (Comment comment : comments) {
      identifiers.add(new ResourceIdentifier(COMMENTS, comment.id(), null, null, Map.of()))
    }
    return new RelationshipData.IdentifierCollectionLinkage(identifiers)
  }

  private static RelationshipData tagsLinkage() {
    List<ResourceIdentifier> identifiers = new ArrayList<>(TAGS_SET.size())
    for (Tag tag : TAGS_SET) {
      identifiers.add(new ResourceIdentifier(TAGS, tag.name(), null, null, Map.of()))
    }
    return new RelationshipData.IdentifierCollectionLinkage(identifiers)
  }

  private static ResourceObject identifierMetaArticle(
      ResourceIdentifier author, Meta authorRelationshipMeta,
      List<ResourceIdentifier> comments, Meta commentsRelationshipMeta) {
    Map<String, Relationship> relationships = new LinkedHashMap<>()
    relationships.put(
        AUTHOR,
        new Relationship(
        author == null
        ? RelationshipData.NullLinkage.INSTANCE
        : new RelationshipData.SingleLinkage(author),
        null,
        authorRelationshipMeta,
        Map.of()))
    relationships.put(
        COMMENTS,
        new Relationship(
        new RelationshipData.IdentifierCollectionLinkage(comments),
        null,
        commentsRelationshipMeta,
        Map.of()))
    return new ResourceObject(
        ARTICLES,
        "1",
        null,
        Attributes.ofAttributes(singleAttribute(TITLE, "T")),
        Relationships.ofRelationships(relationships),
        null,
        null,
        Map.of())
  }

  private static ResourceIdentifier identifier(String type, String id, Meta meta) {
    return identifier(type, id, null, meta, Map.of())
  }

  private static ResourceIdentifier identifier(
      String type, String id, String lid, Meta meta, Map<String, Object> additionalMembers) {
    return new ResourceIdentifier(type, id, lid, meta, additionalMembers)
  }

  private static ResourceObject commentsOnlyArticle(List<ResourceIdentifier> comments) {
    return new ResourceObject(
        ARTICLES,
        "1",
        null,
        null,
        Relationships.ofRelationships(
        Map.of(
        COMMENTS,
        new Relationship(
        new RelationshipData.IdentifierCollectionLinkage(comments),
        null,
        null,
        Map.of()))),
        null,
        null,
        Map.of())
  }

  private static ResourceObject authorOnlyArticle(ResourceIdentifier author) {
    return new ResourceObject(
        ARTICLES,
        "1",
        null,
        null,
        Relationships.ofRelationships(
        Map.of(
        AUTHOR,
        new Relationship(
        new RelationshipData.SingleLinkage(author), null, null, Map.of()))),
        null,
        null,
        Map.of())
  }

  private static ResourceObject articleWithMetaResource(
      Meta resourceMeta, ResourceIdentifier author, Meta authorRelationshipMeta) {
    Map<String, Relationship> relationships = new LinkedHashMap<>()
    relationships.put(
        AUTHOR,
        new Relationship(
        author == null
        ? RelationshipData.NullLinkage.INSTANCE
        : new RelationshipData.SingleLinkage(author),
        null,
        authorRelationshipMeta,
        Map.of()))
    return new ResourceObject(
        ARTICLES,
        "1",
        null,
        Attributes.ofAttributes(singleAttribute(TITLE, "T")),
        Relationships.ofRelationships(relationships),
        null,
        resourceMeta,
        Map.of())
  }

  private static ResourceObject objectMetaArticle(Meta resourceMeta) {
    return new ResourceObject(ARTICLES, "1", null, null, null, null, resourceMeta, Map.of())
  }

  private static ResourceObject attributesOnlyArticle(String id, Map<String, Object> attributes) {
    return new ResourceObject(
        ARTICLES, id, null, Attributes.ofAttributes(attributes), null, null, null, Map.of())
  }

  private static ResourceObject titledCommentsArticle(
      String title, List<ResourceIdentifier> comments) {
    return new ResourceObject(
        ARTICLES,
        "1",
        null,
        Attributes.ofAttributes(singleAttribute(TITLE, title)),
        Relationships.ofRelationships(
        Map.of(
        COMMENTS,
        new Relationship(
        new RelationshipData.IdentifierCollectionLinkage(comments),
        null,
        null,
        Map.of()))),
        null,
        null,
        Map.of())
  }

  private static ResourceObject commentRelationshipArticle(RelationshipData data) {
    return new ResourceObject(
        ARTICLES,
        "1",
        null,
        null,
        Relationships.ofRelationships(
        Map.of("comment", new Relationship(data, null, null, Map.of()))),
        null,
        null,
        Map.of())
  }

  private static ResourceObject itemsRelationshipArticle(List<ResourceIdentifier> items) {
    return new ResourceObject(
        ARTICLES,
        "1",
        null,
        null,
        Relationships.ofRelationships(
        Map.of(
        "items",
        new Relationship(
        new RelationshipData.IdentifierCollectionLinkage(items),
        null,
        null,
        Map.of()))),
        null,
        null,
        Map.of())
  }

  def "toMappedCreateDocument omits absent primary identity while ordinary mapping requires it"() {
    given:
    def draft = new LocalIdentityArticle(null, null, "Draft")
    def declared = JsonMapper.builder().build().constructType(LocalIdentityArticle)

    when:
    def mapped = mapper.toMappedCreateDocument(
        draft, declared, null, RepresentationSelection.none(), RepresentationPolicy.defaults())

    then:
    def primary = (mapped.document().data() as DocumentData.SingleResource).resource()
    !primary.hasId()
    !primary.hasLid()

    when:
    mapper.toMappedDocument(draft, null, RepresentationSelection.none(), RepresentationPolicy.defaults())

    then:
    def ex = thrown(JsonApiMappingException)
    ex.diagnostic() == MappingDiagnostic.MISSING_IDENTIFIER
  }

  @SafeVarargs
  private static <T> List<T> nullableList(T... values) {
    List<T> list = new ArrayList<>(values.length)
    Collections.addAll(list, values)
    return list
  }
}
