package io.github.kazemek.jsonapi.jackson3

import io.github.kazemek.jsonapi.core.model.Attributes
import io.github.kazemek.jsonapi.core.model.DocumentData
import io.github.kazemek.jsonapi.core.model.Meta
import io.github.kazemek.jsonapi.core.model.Relationship
import io.github.kazemek.jsonapi.core.model.RelationshipData
import io.github.kazemek.jsonapi.core.model.Relationships
import io.github.kazemek.jsonapi.core.model.ResourceIdentifier
import io.github.kazemek.jsonapi.core.model.ResourceIdentity
import io.github.kazemek.jsonapi.core.model.ResourceObject
import io.github.kazemek.jsonapi.jackson.diagnostic.JsonApiMappingException
import io.github.kazemek.jsonapi.jackson.diagnostic.MappingDiagnostic
import io.github.kazemek.jsonapi.jackson.mapping.MappedDocument
import io.github.kazemek.jsonapi.jackson.representation.FieldAllowance
import io.github.kazemek.jsonapi.jackson.representation.FieldPolicy
import io.github.kazemek.jsonapi.jackson.representation.IncludePath
import io.github.kazemek.jsonapi.jackson.representation.IncludePolicy
import io.github.kazemek.jsonapi.jackson.representation.RepresentationPolicy
import io.github.kazemek.jsonapi.jackson.representation.RepresentationSelection
import io.github.kazemek.jsonapi.fixtures.domainwrite.Article
import io.github.kazemek.jsonapi.fixtures.domainpatch.ArticleMeta
import io.github.kazemek.jsonapi.fixtures.domainpatch.ArticleWithMeta
import io.github.kazemek.jsonapi.fixtures.domainwrite.BlogWithJsonProperty
import io.github.kazemek.jsonapi.fixtures.domainwrite.Comment
import io.github.kazemek.jsonapi.fixtures.domainwrite.Person
import io.github.kazemek.jsonapi.fixtures.sparsefieldset.ArticleWithRenamedAuthor
import io.github.kazemek.jsonapi.fixtures.sparsefieldset.AccessCountingFieldsetArticle
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll
import tools.jackson.databind.json.JsonMapper

class SparseFieldsetSpec extends Specification {

  @Shared
  JsonApiResourceMapper mapper = JsonApiJackson3.resourceMapper(JsonMapper.builder().build())

  @Unroll
  def "toMappedDocument applies #description"() {
    given:
    def selection = selectionFor(fieldsets)

    when:
    def mapped = mapper.toMappedDocument(article(), null, selection, RepresentationPolicy.defaults())

    then:
    primaryResource(mapped) == expectedResource
    mapped.document().included() == null
    mapped.sparseFieldsetLinkageExemptions().isEmpty()

    where:
    description | fieldsets | expectedResource
    "an absent type fieldset as unrestricted" | [:] | unrestrictedArticleResource()
    "an attribute-only fieldset" | ["articles": ["title"]] | titleOnlyArticleResource()
    "a relationship-only fieldset" | ["articles": ["author"]] | authorOnlyArticleResource()
    "a mixed attribute and relationship fieldset" | ["articles": ["title", "author"]] | mixedArticleResource()
    "a present-empty fieldset as no fields" | ["articles": []] | resourceObject("articles", "1", null, null)
    "the renamed body-text attribute" | ["articles": ["body-text"]] | resourceObject("articles", "1", Attributes.ofAttributes(["body-text": "Body"]), null)
  }

  def "toMappedDocument emits selected fields in mapping-definition order, not fieldset order"() {
    given:
    def selection = selectionFor(["articles": [
        "author",
        "body-text",
        "title"
      ]])

    when:
    def mapped = mapper.toMappedDocument(article(), null, selection, RepresentationPolicy.defaults())

    then:
    def resource = primaryResource(mapped)
    List.copyOf(resource.attributes().attributes().keySet()) == ["title", "body-text"]
    resource.attributes().attributes() == ["title": "Title", "body-text": "Body"]
    List.copyOf(resource.relationships().relationships().keySet()) == ["author"]
    resource.relationships().relationships()["author"].data() == personLinkage(dan())
    mapped.document().included() == null
    mapped.sparseFieldsetLinkageExemptions().isEmpty()
  }

  @Unroll
  def "toMappedCollectionDocument applies #description"() {
    given:
    def selection = selectionFor(fieldsets)

    when:
    def mapped = mapper.toMappedCollectionDocument(
        List.of(article()), null, selection, RepresentationPolicy.defaults())

    then:
    primaryResources(mapped) == [expectedResource]
    mapped.document().included() == null
    mapped.sparseFieldsetLinkageExemptions().isEmpty()

    where:
    description | fieldsets | expectedResource
    "an attribute-only fieldset" | ["articles": ["title"]] | titleOnlyArticleResource()
    "a relationship-only fieldset" | ["articles": ["comments"]] | resourceObject("articles", "1", null, Relationships.ofRelationships(["comments": Relationship.withData(commentsLinkage())]))
    "a to-one relationship fieldset" | ["articles": ["author"]] | authorOnlyArticleResource()
    "a present-empty fieldset" | ["articles": []] | resourceObject("articles", "1", null, null)
    "a full fieldset" | ["articles": [
        "title",
        "body-text",
        "comments",
        "author"
      ]] | unrestrictedArticleResource()
  }

  @Unroll
  def "toMappedDocument includes an author with #description"() {
    given:
    def selection = selectionFor(fieldsets, ["author"])
    def policy = RepresentationPolicy.defaults().withIncludePolicy(IncludePolicy.allowAll())

    when:
    def mapped = mapper.toMappedDocument(article(), null, selection, policy)

    then:
    primaryResource(mapped) == expectedPrimary
    mapped.document().included() == [expectedIncluded]
    mapped.sparseFieldsetLinkageExemptions() == expectedExemptions

    where:
    description | fieldsets | expectedPrimary | expectedIncluded | expectedExemptions
    "an absent people fieldset" | ["articles": ["title"]] | titleOnlyArticleResource() | danResource() | Set.of(ResourceIdentity.ofId("people", "9"))
    "a present-empty people fieldset" | ["articles": ["title"], "people": []] | titleOnlyArticleResource() | resourceObject("people", "9", null, null) | Set.of(ResourceIdentity.ofId("people", "9"))
    "a relationship-only primary fieldset" | ["articles": ["author"]] | authorOnlyArticleResource() | danResource() | Set.of()
    "an unrestricted primary fieldset" | [:] | unrestrictedArticleResource() | danResource() | Set.of()
  }

  def "toMappedDocument keeps included absent when no include path is requested"() {
    given:
    def selection = selectionFor(["articles": ["title"]])

    when:
    def mapped = mapper.toMappedDocument(article(), null, selection, RepresentationPolicy.defaults())

    then:
    mapped.document().included() == null
    !mapped.document().hasIncludedMember()
  }

  def "toMappedDocument emits present-empty included when an include resolves to no resources"() {
    given:
    def selection = selectionFor([:], ["author"])
    def policy = RepresentationPolicy.defaults().withIncludePolicy(IncludePolicy.allowAll())

    when:
    def mapped = mapper.toMappedDocument(
        articleWithNullAuthor(), null, selection, policy)

    then:
    mapped.document().included() != null
    mapped.document().included().isEmpty()
    mapped.document().hasIncludedMember()
  }

  def "fieldset provenance identifies included resources whose linkage was omitted"() {
    given:
    def selection = selectionFor(["articles": ["title"]], ["author"])
    def policy = RepresentationPolicy.defaults().withIncludePolicy(IncludePolicy.allowAll())

    when:
    def mapped = mapper.toMappedDocument(article(), null, selection, policy)

    then:
    primaryResource(mapped) == titleOnlyArticleResource()
    mapped.document().included() == [danResource()]
    mapped.sparseFieldsetLinkageExemptions() == Set.of(ResourceIdentity.ofId("people", "9"))
  }

  def "fieldset provenance is empty when the linking relationship survives"() {
    given:
    def selection = selectionFor(["articles": ["title", "author"]], ["author"])
    def policy = RepresentationPolicy.defaults().withIncludePolicy(IncludePolicy.allowAll())

    when:
    def mapped = mapper.toMappedDocument(article(), null, selection, policy)

    then:
    mapped.sparseFieldsetLinkageExemptions().isEmpty()
    primaryResource(mapped).relationships().relationships().keySet() == ["author"] as Set
  }

  @Unroll
  def "fieldset #description does not read excluded properties"() {
    given:
    def counting = new AccessCountingFieldsetArticle(
        "1", "Title", "Body", dan(), List.of(comment5()))
    def selection = selectionFor(fieldsets, includePaths)
    def policy = includePaths.isEmpty()
        ? RepresentationPolicy.defaults()
        : RepresentationPolicy.defaults().withIncludePolicy(IncludePolicy.allowAll())

    when:
    def mapped = mapper.toMappedDocument(counting, null, selection, policy)

    then:
    mapped != null
    counting.titleReads == titleReads
    counting.bodyReads == bodyReads
    counting.authorReads == authorReads
    counting.commentsReads == commentsReads

    where:
    description | fieldsets | includePaths | titleReads | bodyReads | authorReads | commentsReads
    "title-only without inclusion" | ["articles": ["title"]] | [] | 1 | 0 | 0 | 0
    "title-only with author inclusion" | ["articles": ["title"]] | ["author"] | 1 | 0 | 1 | 0
    "author-only without inclusion" | ["articles": ["author"]] | [] | 0 | 0 | 1 | 0
    "empty without inclusion" | ["articles": []] | [] | 0 | 0 | 0 | 0
  }

  def "selection snapshots the caller fieldset map and lists"() {
    given:
    def mutableFields = new ArrayList<>(["title", "title", "author"])
    def mutableFieldsets = new LinkedHashMap<String, List<String>>()
    mutableFieldsets.put("articles", mutableFields)
    def selection = selectionFor(mutableFieldsets)

    when:
    mutableFields.add("body-text")
    mutableFieldsets.put("people", ["name"])

    then:
    selection.fieldsets() == ["articles": ["title", "author"]]

    when:
    selection.fieldsets().get("articles").add("body-text")

    then:
    thrown(UnsupportedOperationException)
  }

  def "duplicate fieldset names collapse to first-seen order and map once"() {
    given:
    def fieldsets = ["articles": [
        "title",
        "title",
        "author",
        "title"
      ]]
    def selection = selectionFor(fieldsets)

    expect:
    selection.fieldsets() == ["articles": ["title", "author"]]

    when:
    def mapped = mapper.toMappedDocument(article(), null, selection, RepresentationPolicy.defaults())

    then:
    primaryResource(mapped) == mixedArticleResource()
  }

  def "field policy alone does not select fields"() {
    given:
    def policy = RepresentationPolicy.defaults().withFieldPolicy(FieldPolicy.denyAll())

    when:
    def mapped = mapper.toMappedDocument(
        article(), null, RepresentationSelection.none(), policy)

    then:
    primaryResource(mapped) == unrestrictedArticleResource()
    mapped.sparseFieldsetLinkageExemptions().isEmpty()
  }

  def "unmapped document rejects non-empty fieldsets"() {
    given:
    def selection = selectionFor(["articles": ["title"]])

    when:
    mapper.toDocument(article(), null, selection, RepresentationPolicy.defaults())

    then:
    def exception = thrown(JsonApiMappingException)
    exception.diagnostic() == MappingDiagnostic.FIELDSETS_REQUIRE_MAPPED_DOCUMENT
    exception.propertyPath() == null
    exception.resourceClass() == null
    exception.message.contains("types: [articles]")
  }

  def "unmapped resource collection rejects non-empty fieldsets"() {
    given:
    def selection = selectionFor(["articles": ["title"]])

    when:
    mapper.toCollectionDocument(
        [article()], null, selection, RepresentationPolicy.defaults())

    then:
    def exception = thrown(JsonApiMappingException)
    exception.diagnostic() == MappingDiagnostic.FIELDSETS_REQUIRE_MAPPED_DOCUMENT
    exception.propertyPath() == null
    exception.resourceClass() == null
  }

  def "FieldAllowance permits selected wire fields and rejects other fields"() {
    given:
    def fieldPolicy = FieldPolicy.allowing(Set.of(FieldAllowance.of("articles", "title")))
    def allowedPolicy = RepresentationPolicy.defaults().withFieldPolicy(fieldPolicy)

    when:
    def mapped = mapper.toMappedDocument(
        article(), null, selectionFor(["articles": ["title"]]), allowedPolicy)

    then:
    primaryResource(mapped).attributes().attributes() == ["title": "Title"]

    when:
    mapper.toMappedDocument(
        article(),
        null,
        selectionFor(["articles": ["author"]]),
        allowedPolicy)

    then:
    def exception = thrown(JsonApiMappingException)
    exception.diagnostic() == MappingDiagnostic.DENIED_FIELDSET_FIELD
    exception.resourceClass() == Article.class
  }

  def "unknown field names win over field-policy denial"() {
    given:
    def selection = selectionFor(["articles": ["nope", "title"]])
    def policy = RepresentationPolicy.defaults().withFieldPolicy(FieldPolicy.denyAll())

    when:
    mapper.toMappedDocument(article(), null, selection, policy)

    then:
    def exception = thrown(JsonApiMappingException)
    exception.diagnostic() == MappingDiagnostic.INVALID_FIELDSET_FIELD
    exception.resourceClass() == Article.class
  }

  def "renamed fieldsets use configured Jackson wire names"() {
    when:
    def blog = mapper.toMappedDocument(
        new BlogWithJsonProperty("b1", "Hello"),
        null,
        selectionFor(["blogs": ["blog_title"]]),
        RepresentationPolicy.defaults())
    def article = mapper.toMappedDocument(
        new ArticleWithRenamedAuthor("1", "Title", dan()),
        null,
        selectionFor(["articles": ["written-by"]]),
        RepresentationPolicy.defaults())

    then:
    primaryResource(blog) == resourceObject(
        "blogs", "b1", Attributes.ofAttributes(["blog_title": "Hello"]), null)
    primaryResource(article) == resourceObject(
        "articles",
        "1",
        null,
        Relationships.ofRelationships(["written-by": Relationship.withData(personLinkage(dan()))]))
  }

  def "a renamed relationship rejects its Java logical name in a fieldset"() {
    when:
    mapper.toMappedDocument(
        new ArticleWithRenamedAuthor("1", "Title", dan()),
        null,
        selectionFor(["articles": ["author"]]),
        RepresentationPolicy.defaults())

    then:
    def exception = thrown(JsonApiMappingException)
    exception.diagnostic() == MappingDiagnostic.INVALID_FIELDSET_FIELD
    exception.resourceClass() == ArticleWithRenamedAuthor.class
  }

  def "nested included resources apply their own fieldset by type"() {
    given:
    def selection = selectionFor(
        ["comments": ["body"]],
        ["comments.author"])
    def policy = RepresentationPolicy.defaults().withIncludePolicy(IncludePolicy.allowAll())

    when:
    def mapped = mapper.toMappedDocument(article(), null, selection, policy)

    then:
    primaryResource(mapped) == unrestrictedArticleResource()
    mapped.document().included() == [
      resourceObject("comments", "5", Attributes.ofAttributes(["body": "First!"]), null),
      resourceObject("comments", "12", Attributes.ofAttributes(["body": "I like XML better"]), null),
      resourceObject("people", "2", Attributes.ofAttributes(["name": "Ezra"]), null),
      danResource()
    ]
    mapped.sparseFieldsetLinkageExemptions() == Set.of(
        ResourceIdentity.ofId("people", "2"), ResourceIdentity.ofId("people", "9"))
  }

  @Unroll
  def "fieldset #description preserves primary identity"() {
    when:
    def mapped = mapper.toMappedDocument(
        article(), null, selectionFor(fieldsets), RepresentationPolicy.defaults())

    then:
    primaryResource(mapped).type() == "articles"
    primaryResource(mapped).id() == "1"

    where:
    description | fieldsets
    "no fieldset" | [:]
    "an empty fieldset" | ["articles": []]
    "an attribute-only fieldset" | ["articles": ["title"]]
    "a relationship-only fieldset" | ["articles": ["author"]]
  }

  def "empty fieldsets retain resource meta independently of field policy"() {
    given:
    def article = new ArticleWithMeta(
        "1",
        "T",
        ResourceIdentifier.of("people", "p1"),
        new ArticleMeta("cms", "n"),
        null)
    def policy = RepresentationPolicy.defaults().withFieldPolicy(FieldPolicy.denyAll())

    when:
    def mapped = mapper.toMappedDocument(
        article, null, selectionFor(["articles": []]), policy)

    then:
    primaryResource(mapped).attributes() == null
    primaryResource(mapped).relationships() == null
    primaryResource(mapped).meta() == Meta.of(["source": "cms", "note": "n"])
  }

  def "concurrent fieldset mappings isolate documents and linkage exemptions"() {
    given:
    def shared = JsonApiJackson3.resourceMapper(JsonMapper.builder().build())
    def start = new CountDownLatch(1)
    def done = new CountDownLatch(2)
    def firstResult = new AtomicReference<MappedDocument>()
    def secondResult = new AtomicReference<MappedDocument>()
    def failure = new AtomicReference<Throwable>()
    def pool = Executors.newFixedThreadPool(2)

    when:
    pool.submit({
      try {
        start.await()
        100.times {
          firstResult.set(shared.toMappedDocument(
              article(),
              null,
              selectionFor(["articles": ["title"]], ["author"]),
              RepresentationPolicy.defaults().withIncludePolicy(IncludePolicy.allowAll())))
        }
      } catch (Throwable throwable) {
        failure.compareAndSet(null, throwable)
      } finally {
        done.countDown()
      }
    } as Runnable)
    pool.submit({
      try {
        start.await()
        100.times {
          secondResult.set(shared.toMappedDocument(
              article(),
              null,
              selectionFor(["articles": ["title", "author", "comments"]], ["author"]),
              RepresentationPolicy.defaults().withIncludePolicy(IncludePolicy.allowAll())))
        }
      } catch (Throwable throwable) {
        failure.compareAndSet(null, throwable)
      } finally {
        done.countDown()
      }
    } as Runnable)
    start.countDown()

    then:
    done.await(10, TimeUnit.SECONDS)
    failure.get() == null
    firstResult.get().sparseFieldsetLinkageExemptions() == Set.of(ResourceIdentity.ofId("people", "9"))
    secondResult.get().sparseFieldsetLinkageExemptions().isEmpty()
    primaryResource(firstResult.get()).attributes().attributes() == ["title": "Title"]
    primaryResource(secondResult.get()).attributes().attributes() == ["title": "Title"]

    cleanup:
    pool.shutdownNow()
  }

  private static RepresentationSelection selectionFor(
      Map<String, List<String>> fieldsets, List<String> includePaths = []) {
    def builder = RepresentationSelection.builder()
    includePaths.each { path -> builder.include(IncludePath.of(path as String)) }
    fieldsets.each { type, fields ->
      builder.fields(type as String, fields as List<String>)
    }
    builder.build()
  }

  private static ResourceObject primaryResource(MappedDocument mapped) {
    def data = mapped.document().data()
    assert data instanceof DocumentData.SingleResource
    ((DocumentData.SingleResource) data).resource()
  }

  private static List<ResourceObject> primaryResources(MappedDocument mapped) {
    def data = mapped.document().data()
    assert data instanceof DocumentData.ResourceCollection
    ((DocumentData.ResourceCollection) data).resources()
  }

  private static ResourceObject resourceObject(
      String type, String id, Attributes attributes, Relationships relationships) {
    new ResourceObject(type, id, null, attributes, relationships, null, null, Map.of())
  }

  private static ResourceObject unrestrictedArticleResource() {
    resourceObject(
        "articles",
        "1",
        Attributes.ofAttributes(["title": "Title", "body-text": "Body"]),
        Relationships.ofRelationships([
          "comments": Relationship.withData(commentsLinkage()),
          "author": Relationship.withData(personLinkage(dan()))
        ]))
  }

  private static ResourceObject titleOnlyArticleResource() {
    resourceObject("articles", "1", Attributes.ofAttributes(["title": "Title"]), null)
  }

  private static ResourceObject authorOnlyArticleResource() {
    resourceObject(
        "articles",
        "1",
        null,
        Relationships.ofRelationships(["author": Relationship.withData(personLinkage(dan()))]))
  }

  private static ResourceObject mixedArticleResource() {
    resourceObject(
        "articles",
        "1",
        Attributes.ofAttributes(["title": "Title"]),
        Relationships.ofRelationships(["author": Relationship.withData(personLinkage(dan()))]))
  }

  private static ResourceObject danResource() {
    resourceObject("people", "9", Attributes.ofAttributes(["name": "Dan"]), null)
  }

  private static Article article() {
    new Article("1", "Title", "Body", List.of(comment5(), comment12()), dan())
  }

  private static Article articleWithNullAuthor() {
    new Article("1", "Title", "Body", List.of(comment5()), null)
  }

  private static Person dan() {
    new Person("9", "Dan")
  }

  private static Comment comment5() {
    new Comment("5", "First!", new Person("2", "Ezra"))
  }

  private static Comment comment12() {
    new Comment("12", "I like XML better", dan())
  }

  private static RelationshipData personLinkage(Person person) {
    new RelationshipData.SingleLinkage(ResourceIdentifier.of("people", person.id()))
  }

  private static RelationshipData commentsLinkage() {
    new RelationshipData.IdentifierCollectionLinkage(
        List.of(ResourceIdentifier.of("comments", "5"), ResourceIdentifier.of("comments", "12")))
  }
}
