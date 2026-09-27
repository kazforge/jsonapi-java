package com.kazforge.jsonapi.fixtures.contract

import com.kazforge.jsonapi.api.JsonApi
import com.kazforge.jsonapi.api.ResourceWriteOptions
import com.kazforge.jsonapi.core.model.DocumentData
import com.kazforge.jsonapi.core.model.ErrorObject
import com.kazforge.jsonapi.core.model.ErrorSource
import com.kazforge.jsonapi.core.model.JsonApiDocument
import com.kazforge.jsonapi.core.model.JsonApiObject
import com.kazforge.jsonapi.core.model.Link
import com.kazforge.jsonapi.core.model.Links
import com.kazforge.jsonapi.core.model.Meta
import com.kazforge.jsonapi.core.model.ResourceIdentifier
import com.kazforge.jsonapi.diagnostic.CodecFailureCategory
import com.kazforge.jsonapi.diagnostic.JsonApiDocumentReadException
import com.kazforge.jsonapi.diagnostic.JsonApiMappingException
import com.kazforge.jsonapi.diagnostic.MappingDiagnostic
import com.kazforge.jsonapi.document.DocumentEnvelope
import com.kazforge.jsonapi.document.DocumentReadContext
import com.kazforge.jsonapi.fixtures.TestFixtureResources
import com.kazforge.jsonapi.fixtures.domainread.FlatArticle
import com.kazforge.jsonapi.fixtures.domainwrite.Article
import com.kazforge.jsonapi.fixtures.domainwrite.Comment
import com.kazforge.jsonapi.fixtures.domainwrite.Person
import com.kazforge.jsonapi.fixtures.localid.LocalIdentityArticle
import com.kazforge.jsonapi.fixtures.localid.LocalIdentityArticleWithAuthor
import com.kazforge.jsonapi.representation.IncludePath
import com.kazforge.jsonapi.representation.RepresentationSelection
import groovy.json.JsonSlurper
import spock.lang.Specification

/**
 * Level-1 facet characterization contract observed through the configured {@code JsonApi} runtime:
 * resource and relationship round-trips, document retention and raw document round-trips, the
 * runtime jsonapi version applied to authored documents, and identity requirements on response
 * writes. Native backend configuration mechanics remain adapter-local. Concrete adapter subclasses
 * construct the runtime from the supplied neutral jsonapi version (null for unconfigured).
 */
abstract class LevelOneFacetCharacterizationSpec extends Specification {

  // The passive canonical error fixture intentionally uses this valid HTTP URI.
  //noinspection HttpUrlsUsage
  @SuppressWarnings("HttpUrlsUsage")
  private static final String ERROR_ABOUT_URL = "http://example.com/docs/errors/invalid"

  protected abstract JsonApi api(String jsonApiVersion)

  private static Map<String, Object> parse(String json) {
    new JsonSlurper().parseText(json) as Map<String, Object>
  }

  private static DocumentReadContext resourceDefaults() {
    DocumentReadContext.resourceDefaults()
  }

  // Resources: round-trips

  def "round-trips a single resource through writeOne and readOne"() {
    given:
    def article = new Article("1", "Hello", "Body text", [
      new Comment("c1", "Nice", null)
    ], new Person("p1", "Alice"))

    when:
    def json = api(null).resources().writeOne(article)
    def actual = api(null).resources().readOne(json, FlatArticle)

    then:
    actual.id() == "1"
    actual.title() == "Hello"
    actual.body() == "Body text"
    actual.author() == ResourceIdentifier.of("people", "p1")
    actual.comments() == [
      ResourceIdentifier.of("comments", "c1")
    ]
  }

  def "round-trips a resource collection through writeMany and readMany"() {
    given:
    def articles = [
      new Article("1", "A", "Body a", List.of(), null),
      new Article("2", "B", "Body b", List.of(), null)
    ]

    when:
    def json = api(null).resources().writeMany(articles)
    def actual = api(null).resources().readMany(json, FlatArticle)

    then:
    actual*.id() == ["1", "2"]
    actual*.title() == ["A", "B"]
  }

  // Resources: document retention

  def "readOneDocument retains top-level document state and included resources"() {
    given:
    def json =
        '{"data":{"type":"articles","id":"1","attributes":{"title":"Hello"},' +
        '"relationships":{"author":{"data":{"type":"people","id":"p1"}}}},' +
        '"included":[{"type":"people","id":"p1","attributes":{"name":"Alice"}}],' +
        '"meta":{"count":1},"links":{"self":"https://example.test/articles"},' +
        '"jsonapi":{"version":"1.1"}}'

    when:
    def document = api(null).resources().readOneDocument(json, FlatArticle)

    then:
    document.resource().id() == "1"
    document.resource().title() == "Hello"
    document.resource().author() == ResourceIdentifier.of("people", "p1")
    document.meta() == Meta.of([count: 1])
    document.links() == Links.ofLinks([self: new Link.StringLink("https://example.test/articles")])
    document.jsonapi() == JsonApiObject.ofVersion("1.1")
    document.included().size() == 1
    document.included()[0].type() == "people"
    document.included()[0].id() == "p1"
  }

  def "readManyDocument retains top-level document state"() {
    given:
    def json =
        '{"data":[{"type":"articles","id":"1","attributes":{"title":"A"}},' +
        '{"type":"articles","id":"2","attributes":{"title":"B"}}],"meta":{"count":2}}'

    when:
    def document = api(null).resources().readManyDocument(json, FlatArticle)

    then:
    document.resources()*.id() == ["1", "2"]
    document.resources()*.title() == ["A", "B"]
    document.meta() == Meta.of([count: 2])
    document.included() == null
  }

  // Resources: write-option envelopes

  def "resource writes carry the explicit write-option envelope"() {
    given:
    def options = new ResourceWriteOptions(
        new DocumentEnvelope(
        Links.ofLinks([self: new Link.StringLink("https://example.test/articles")]),
        Meta.of([copyright: "2026"]),
        JsonApiObject.ofVersion("1.0")),
        RepresentationSelection.none())
    def article = new Article("1", "T", "B", List.of(), null)

    when:
    def document = api(null).documents().read(
        api(null).resources().writeOne(article, options), resourceDefaults())

    then:
    document.links() == options.envelope().links()
    document.meta() == options.envelope().meta()
    document.jsonapi() == options.envelope().jsonapi()
  }

  def "resource collection writes carry the explicit write-option envelope"() {
    given:
    def options = new ResourceWriteOptions(
        new DocumentEnvelope(
        Links.ofLinks([self: new Link.StringLink("https://example.test/articles")]),
        null,
        null),
        RepresentationSelection.none())
    def articles = [
      new Article("1", "A", "B", List.of(), null)
    ]

    when:
    def document = api(null).documents().read(
        api(null).resources().writeMany(articles, options), resourceDefaults())

    then:
    document.links() == options.envelope().links()
  }

  def "create and update authoring accept write options"() {
    given:
    def options = new ResourceWriteOptions(
        new DocumentEnvelope(null, Meta.of([note: "shaped"]), null),
        RepresentationSelection.none())
    def article = new Article("1", "T", "B", List.of(), null)

    when:
    def created = api(null).resources().writeCreateDocument(article, options)
    def updated = api(null).resources().writeUpdateDocument(article, null, options)

    then:
    api(null).documents().read(created, resourceDefaults()).meta() == Meta.of([note: "shaped"])
    api(null).documents().read(updated, resourceDefaults()).meta() == Meta.of([note: "shaped"])
  }

  // Resources: runtime jsonapi version

  def "unconfigured runtime injects no jsonapi version"() {
    when:
    def json = api(null).resources().writeOne(new Article("1", "T", "B", List.of(), null))

    then:
    !json.contains('"jsonapi"')
  }

  def "configured runtime applies its jsonapi version to single resource writes"() {
    given:
    def runtime = api("custom-version")

    when:
    def document = runtime.documents().read(
        runtime.resources().writeOne(new Article("1", "T", "B", List.of(), null)),
        resourceDefaults())

    then:
    document.jsonapi() == JsonApiObject.ofVersion("custom-version")
  }

  def "configured runtime applies its jsonapi version to collection writes"() {
    given:
    def runtime = api("1.1")

    when:
    def document = runtime.documents().read(
        runtime.resources().writeMany([
          new Article("1", "A", "B", List.of(), null),
          new Article("2", "C", "D", List.of(), null)
        ]),
        resourceDefaults())

    then:
    document.jsonapi() == JsonApiObject.ofVersion("1.1")
  }

  def "configured runtime applies its jsonapi version to create authoring"() {
    given:
    def runtime = api("1.1")

    when:
    def document = runtime.documents().read(
        runtime.resources().writeCreateDocument(new Article("1", "T", "B", List.of(), null)),
        resourceDefaults())

    then:
    document.jsonapi() == JsonApiObject.ofVersion("1.1")
  }

  def "configured runtime applies its jsonapi version to update authoring"() {
    given:
    def runtime = api("1.1")

    when:
    def document = runtime.documents().read(
        runtime.resources().writeUpdateDocument(new Article("1", "T", "B", List.of(), null), null),
        resourceDefaults())

    then:
    document.jsonapi() == JsonApiObject.ofVersion("1.1")
  }

  def "explicit per-write jsonapi object takes precedence over the runtime default"() {
    given:
    def runtime = api("1.1")
    def explicit = JsonApiObject.ofVersion("1.0")
    def options = new ResourceWriteOptions(
        new DocumentEnvelope(null, null, explicit),
        RepresentationSelection.none())

    when:
    def document = runtime.documents().read(
        runtime.resources().writeOne(new Article("1", "T", "B", List.of(), null), options),
        resourceDefaults())

    then:
    document.jsonapi() == explicit
  }

  // Resources: identity

  def "a lid-only response read fails aggregate validation"() {
    when:
    api(null).resources().readOne(
        '{"data":{"type":"articles","lid":"lid-9","attributes":{"title":"T"}}}',
        LocalIdentityArticle)

    then:
    def validation = thrown(JsonApiDocumentReadException)
    validation.category() == CodecFailureCategory.AGGREGATE_VALIDATION
  }

  def "ordinary writes require identity"() {
    when:
    api(null).resources().writeOne(new LocalIdentityArticle(null, null, "Draft"))

    then:
    def exception = thrown(JsonApiMappingException)
    exception.diagnostic() == MappingDiagnostic.MISSING_IDENTIFIER
  }

  // Resources: create authoring with inclusion

  def "identity-less create with inclusion emits requested included resources"() {
    given:
    def options = new ResourceWriteOptions(
        new DocumentEnvelope(null, null, null),
        RepresentationSelection.builder()
        .include(IncludePath.of("comments.author"))
        .fields("articles", "title", "comments")
        .build())
    def draft = new LocalIdentityArticleWithAuthor(null, null, "Draft", null, [
      new Comment("c1", "Nice", new Person("p1", "Alice"))
    ])

    when:
    def document = parse(api(null).resources().writeCreateDocument(draft, options))

    then:
    !document.data.containsKey("id")
    !document.data.containsKey("lid")
    document.included*.type == ["comments", "people"]
    document.included*.id == ["c1", "p1"]
  }

  def "identified create with inclusion still traverses strictly"() {
    given:
    def options = new ResourceWriteOptions(
        new DocumentEnvelope(null, null, null),
        RepresentationSelection.builder().include(IncludePath.of("author")).build())
    def draft = new LocalIdentityArticleWithAuthor(
        "9", null, "Draft", new Person("p1", "Alice"), List.of())

    when:
    def document = parse(api(null).resources().writeCreateDocument(draft, options))

    then:
    document.data.id == "9"
    document.included*.type == ["people"]
    document.included*.id == ["p1"]
  }

  // Relationships

  def "round-trips a to-one linkage document"() {
    given:
    def identifier = ResourceIdentifier.of("people", "p1")

    when:
    def json = api(null).relationships().writeToOne(identifier)

    then:
    api(null).relationships().readToOne(json) == identifier
  }

  def "explicit null to-one linkage round-trips as null"() {
    when:
    def json = api(null).relationships().writeToOne(null)

    then:
    json.contains('"data":null')
    api(null).relationships().readToOne(json) == null
  }

  def "round-trips a to-many linkage document including the empty collection"() {
    given:
    def identifiers = [
      ResourceIdentifier.of("comments", "c1"),
      ResourceIdentifier.of("comments", "c2")
    ]

    when:
    def json = api(null).relationships().writeToMany(identifiers)
    def emptyJson = api(null).relationships().writeToMany(List.of())

    then:
    api(null).relationships().readToMany(json) == identifiers
    api(null).relationships().readToMany(emptyJson) == []
  }

  def "configured resource version does not affect linkage writes"() {
    when:
    def json = api("1.1").relationships().writeToOne(ResourceIdentifier.of("people", "p1"))

    then:
    !json.contains('"jsonapi"')
  }

  def "explicit null relationship reads accept top-level related"() {
    given:
    def json = TestFixtureResources.readCorpusUtf8("documents/relationship-null-with-related.json")

    expect:
    api(null).relationships().readToOne(json) == null
  }

  def "to-one relationship reads accept top-level related"() {
    given:
    def json = TestFixtureResources.readCorpusUtf8("documents/relationship-single-with-related.json")

    expect:
    api(null).relationships().readToOne(json) == ResourceIdentifier.of("people", "9")
  }

  def "to-many relationship reads accept top-level related"() {
    given:
    def json =
        TestFixtureResources.readCorpusUtf8("documents/relationship-collection-with-related.json")

    expect:
    api(null).relationships().readToMany(json) == [
      ResourceIdentifier.of("people", "9"),
      ResourceIdentifier.of("people", "10")
    ]
  }

  def "to-many relationship reads accept collection pagination"() {
    given:
    def json =
        TestFixtureResources.readCorpusUtf8("documents/relationship-collection-with-pagination.json")

    expect:
    api(null).relationships().readToMany(json) == [
      ResourceIdentifier.of("people", "9"),
      ResourceIdentifier.of("people", "10")
    ]
  }

  // Documents

  def "reads a raw document with an explicit context and writes it back"() {
    given:
    def json = '{"data":{"type":"articles","id":"1","attributes":{"title":"Hello"}}}'

    when:
    def document = api(null).documents().read(json, resourceDefaults())

    then:
    (document.data() as DocumentData.SingleResource).resource().id() == "1"

    when:
    def written = api(null).documents().write(document)

    then:
    api(null).documents().read(written, resourceDefaults()) == document
  }

  def "reads and writes a meta-only document"() {
    given:
    def document = JsonApiDocument.withMeta(Meta.of([count: 2]))

    when:
    def written = api(null).documents().write(document)

    then:
    api(null).documents().read(written, resourceDefaults()) == document
  }

  def "builder-produced error documents match direct construction and the canonical corpus"() {
    given:
    def built = builderErrorDocument()
    def direct = directErrorDocument()
    def expected = TestFixtureResources.readCorpusUtf8("documents/errors-document.json")

    when:
    def builtJson = api(null).documents().write(built)
    def directJson = api(null).documents().write(direct)
    def roundTrip = api(null).documents().read(builtJson, resourceDefaults())

    then:
    built == direct
    parse(builtJson) == parse(directJson)
    parse(builtJson) == parse(expected)
    roundTrip == direct
  }

  def "configured runtime does not affect raw document writes"() {
    given:
    def document = JsonApiDocument.withMeta(Meta.of([count: 2]))

    when:
    def written = api("1.1").documents().write(document)

    then:
    api("1.1").documents().read(written, resourceDefaults()).jsonapi() == null
  }

  private static JsonApiDocument builderErrorDocument() {
    return JsonApiDocument.withError(
        ErrorObject.builder()
        .id("1")
        .links(Links.ofLinks([about: new Link.StringLink(ERROR_ABOUT_URL)]))
        .status("422")
        .code("invalid")
        .title("Invalid Attribute")
        .detail("Title is required")
        .source(ErrorSource.builder().pointer("/data/attributes/title").build())
        .build())
  }

  private static JsonApiDocument directErrorDocument() {
    return JsonApiDocument.withErrors([
      new ErrorObject(
      "1",
      Links.ofLinks([about: new Link.StringLink(ERROR_ABOUT_URL)]),
      "422",
      "invalid",
      "Invalid Attribute",
      "Title is required",
      new ErrorSource("/data/attributes/title", null, null, Map.of()),
      null,
      Map.of())
    ])
  }
}
