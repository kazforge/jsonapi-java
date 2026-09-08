package io.github.kazemek.jsonapi.jackson3

import io.github.kazemek.jsonapi.core.model.DocumentData
import io.github.kazemek.jsonapi.core.model.JsonApiObject
import io.github.kazemek.jsonapi.core.model.Link
import io.github.kazemek.jsonapi.core.model.Links
import io.github.kazemek.jsonapi.core.model.Meta
import io.github.kazemek.jsonapi.core.model.ResourceIdentifier
import io.github.kazemek.jsonapi.core.model.ResourceIdentity
import io.github.kazemek.jsonapi.core.validation.DocumentUsage
import io.github.kazemek.jsonapi.core.validation.EndpointIdentity
import io.github.kazemek.jsonapi.core.validation.JsonApiValidationException
import io.github.kazemek.jsonapi.core.validation.ValidationContext
import io.github.kazemek.jsonapi.jackson.api.ResourceWriteOptions
import io.github.kazemek.jsonapi.jackson.diagnostic.CodecFailureCategory
import io.github.kazemek.jsonapi.jackson.diagnostic.JsonApiDocumentReadException
import io.github.kazemek.jsonapi.jackson.diagnostic.JsonApiMappingException
import io.github.kazemek.jsonapi.jackson.diagnostic.MappingDiagnostic
import io.github.kazemek.jsonapi.jackson.document.DocumentEnvelope
import io.github.kazemek.jsonapi.jackson.document.DocumentReadContext
import io.github.kazemek.jsonapi.jackson.document.PrimaryDataKind
import io.github.kazemek.jsonapi.jackson.mapping.IdentifierConverter
import io.github.kazemek.jsonapi.jackson3.CloseTrackingFixtures.TrackingInputStream
import io.github.kazemek.jsonapi.jackson3.CloseTrackingFixtures.TrackingOutputStream
import io.github.kazemek.jsonapi.jackson3.LinkageMapperFixtures.FlatAuthor
import io.github.kazemek.jsonapi.jackson3.LinkageMapperFixtures.FlatMappedArticle
import io.github.kazemek.jsonapi.jackson.mapping.ResourceDecoration
import io.github.kazemek.jsonapi.jackson.mapping.ResourceDecorator
import io.github.kazemek.jsonapi.jackson.mapping.ResourceDecoratorRegistry
import io.github.kazemek.jsonapi.jackson.representation.IncludePath
import io.github.kazemek.jsonapi.jackson.representation.IncludePolicy
import io.github.kazemek.jsonapi.jackson.representation.RepresentationPolicy
import io.github.kazemek.jsonapi.jackson.representation.RepresentationSelection
import io.github.kazemek.jsonapi.core.model.RelationshipData
import io.github.kazemek.jsonapi.fixtures.domainread.FlatArticle
import io.github.kazemek.jsonapi.fixtures.domainwrite.Article
import io.github.kazemek.jsonapi.fixtures.domainwrite.Comment
import io.github.kazemek.jsonapi.fixtures.domainwrite.Person
import io.github.kazemek.jsonapi.fixtures.localid.LocalIdentityArticle
import io.github.kazemek.jsonapi.fixtures.localid.LocalIdentityArticleWithAuthor
import spock.lang.Shared
import spock.lang.Specification
import io.github.kazemek.jsonapi.jackson3.ParameterizedBindingFixtures.GenericValue
import tools.jackson.core.type.TypeReference
import tools.jackson.databind.JavaType
import tools.jackson.databind.json.JsonMapper

class Jackson3JsonApiResourcesSpec extends Specification {

  @Shared
  Jackson3JsonApi jsonApi = JsonApiJackson3.jsonApi(JsonMapper.builder().build())

  def "round-trips a single resource through writeOne and readOne"() {
    given:
    def article = new Article("1", "Hello", "Body text", [
      new Comment("c1", "Nice", null)
    ], new Person("p1", "Alice"))

    when:
    def json = jsonApi.resources().writeOne(article)
    def actual = jsonApi.resources().readOne(json, FlatArticle)

    then:
    actual.id() == "1"
    actual.title() == "Hello"
    actual.body() == "Body text"
    actual.author() == ResourceIdentifier.of("people", "p1")
    actual.comments() == [
      ResourceIdentifier.of("comments", "c1")
    ]
  }

  def "readOne rejects collection primary data without coercion"() {
    given:
    def json = jsonApi.resources().writeMany([
      new Article("1", "T", "B", List.of(), null)
    ])

    when:
    jsonApi.resources().readOne(json, FlatArticle)

    then:
    def ex = thrown(JsonApiMappingException)
    ex.diagnostic() == MappingDiagnostic.RESOURCE_TYPE_MISMATCH
    ex.propertyPath() == "/data"
  }

  def "readMany rejects single-resource primary data without coercion"() {
    given:
    def json = jsonApi.resources().writeOne(new Article("1", "T", "B", List.of(), null))

    when:
    jsonApi.resources().readMany(json, FlatArticle)

    then:
    def ex = thrown(JsonApiMappingException)
    ex.diagnostic() == MappingDiagnostic.RESOURCE_TYPE_MISMATCH
    ex.propertyPath() == "/data"
  }

  def "readOneDocument retains top-level state without hydrating relationships"() {
    given:
    def json = '{"data":{"type":"articles","id":"1","attributes":{"title":"Hello"},"relationships":{"author":{"data":{"type":"people","id":"p1"}}}},"included":[{"type":"people","id":"p1","attributes":{"name":"Alice"}}],"meta":{"count":1},"links":{"self":"https://example.test/articles"},"jsonapi":{"version":"1.1"}}'

    when:
    def document = jsonApi.resources().readOneDocument(json, FlatArticle)

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

  def "writeOne carries the document envelope without mapper orchestration"() {
    given:
    def options = new ResourceWriteOptions(
        new DocumentEnvelope(
        Links.ofLinks([self: new Link.StringLink("https://example.test/articles")]),
        Meta.of([copyright: "2026"]),
        JsonApiObject.ofVersion("1.1")),
        RepresentationSelection.none())

    when:
    def json = jsonApi.resources().writeOne(new Article("1", "T", "B", List.of(), null), options)
    def roundTrip = jsonApi.documents().read(json, DocumentReadContext.resourceDefaults())

    then:
    roundTrip.links() == Links.ofLinks([self: new Link.StringLink("https://example.test/articles")])
    roundTrip.meta() == Meta.of([copyright: "2026"])
    roundTrip.jsonapi() == JsonApiObject.ofVersion("1.1")
  }

  def "unconfigured runtime does not inject a jsonapi version"() {
    when:
    def json = jsonApi.resources().writeOne(new Article("1", "T", "B", List.of(), null))

    then:
    !json.contains('"jsonapi"')
  }

  def "configured runtime preserves its jsonapi version string on single resource writes"() {
    given:
    def runtime = JsonApiJackson3.builder(JsonMapper.builder().build())
        .jsonApiVersion("custom-version")
        .build()

    when:
    def document = runtime.documents().read(
        runtime.resources().writeOne(new Article("1", "T", "B", List.of(), null)),
        DocumentReadContext.resourceDefaults())

    then:
    document.jsonapi() == JsonApiObject.ofVersion("custom-version")
  }

  def "configured runtime applies its jsonapi version to collection writes"() {
    given:
    def runtime = JsonApiJackson3.builder(JsonMapper.builder().build())
        .jsonApiVersion("1.1")
        .build()

    when:
    def document = runtime.documents().read(
        runtime.resources().writeMany([
          new Article("1", "A", "B", List.of(), null),
          new Article("2", "C", "D", List.of(), null),
        ]),
        DocumentReadContext.resourceDefaults())

    then:
    document.jsonapi() == JsonApiObject.ofVersion("1.1")
  }

  def "configured runtime applies its jsonapi version to create authoring"() {
    given:
    def runtime = JsonApiJackson3.builder(JsonMapper.builder().build())
        .jsonApiVersion("1.1")
        .build()

    when:
    def document = runtime.documents().read(
        runtime.resources().writeCreateDocument(new Article("1", "T", "B", List.of(), null)),
        DocumentReadContext.resourceDefaults())

    then:
    document.jsonapi() == JsonApiObject.ofVersion("1.1")
  }

  def "configured runtime applies its jsonapi version to update authoring"() {
    given:
    def runtime = JsonApiJackson3.builder(JsonMapper.builder().build())
        .jsonApiVersion("1.1")
        .build()

    when:
    def document = runtime.documents().read(
        runtime.resources().writeUpdateDocument(
        new Article("1", "T", "B", List.of(), null),
        new EndpointIdentity("articles", "1")),
        DocumentReadContext.resourceDefaults())

    then:
    document.jsonapi() == JsonApiObject.ofVersion("1.1")
  }

  def "explicit resource-write jsonapi object takes precedence over the runtime default"() {
    given:
    def runtime = JsonApiJackson3.builder(JsonMapper.builder().build())
        .jsonApiVersion("1.1")
        .build()
    def explicit = JsonApiObject.ofVersion("1.0")
    def options = new ResourceWriteOptions(
        new DocumentEnvelope(null, null, explicit),
        RepresentationSelection.none())

    when:
    def document = runtime.documents().read(
        runtime.resources().writeOne(
        new Article("1", "T", "B", List.of(), null),
        options),
        DocumentReadContext.resourceDefaults())

    then:
    document.jsonapi() == explicit
  }

  def "jsonapi version configuration rejects null"() {
    when:
    JsonApiJackson3.builder(JsonMapper.builder().build()).jsonApiVersion(null)

    then:
    thrown(NullPointerException)
  }

  def "configured jsonapi version reaches resource output streams"() {
    given:
    def runtime = JsonApiJackson3.builder(JsonMapper.builder().build())
        .jsonApiVersion("1.1")
        .build()
    def out = new TrackingOutputStream(new ByteArrayOutputStream())

    when:
    runtime.resources().writeOne(new Article("1", "T", "B", List.of(), null), out)

    then:
    runtime.documents().read(
        new ByteArrayInputStream(out.bytes()),
        DocumentReadContext.resourceDefaults()).jsonapi() == JsonApiObject.ofVersion("1.1")
    !out.closed
  }

  def "writeCreateDocument authors a lid-only document while response writes require id"() {
    given:
    def local = new LocalIdentityArticle(null, "lid-1", "Draft")

    when:
    def createJson = jsonApi.resources().writeCreateDocument(local)

    then:
    createJson.contains('"lid":"lid-1"')
    !createJson.contains('"id"')

    when:
    jsonApi.resources().writeOne(local)

    then:
    thrown(JsonApiValidationException)
  }

  def "writeCreateDocument authors an identity-less create without narrowing core leniency"() {
    given:
    def createContext = DocumentReadContext.of(
        ValidationContext.defaults().withDocumentUsage(DocumentUsage.CREATE_REQUEST),
        PrimaryDataKind.RESOURCE)

    when:
    def json = jsonApi.resources().writeCreateDocument(new LocalIdentityArticle(null, null, "Draft"))

    then:
    def roundTrip = jsonApi.documents().read(json, createContext)
    def primary = (roundTrip.data() as DocumentData.SingleResource).resource()
    !primary.hasId()
    !primary.hasLid()
  }

  def "identity-less create with inclusion emits requested included resources"() {
    given:
    def options = new ResourceWriteOptions(
        new DocumentEnvelope(null, null, null),
        RepresentationSelection.builder()
        .include(IncludePath.of("comments.author"))
        .fields("articles", "title", "comments")
        .build())
    def policy = RepresentationPolicy.defaults().withIncludePolicy(IncludePolicy.allowAll())
    def runtime = JsonApiJackson3.builder(JsonMapper.builder().build()).representationPolicy(policy).build()
    def draft = new LocalIdentityArticleWithAuthor(
        null, null, "Draft", null, [
          new Comment("c1", "Nice", new Person("p1", "Alice"))
        ])
    def createContext = DocumentReadContext.of(
        ValidationContext.defaults().withDocumentUsage(DocumentUsage.CREATE_REQUEST),
        PrimaryDataKind.RESOURCE)

    when:
    def json = runtime.resources().writeCreateDocument(draft, options)

    then:
    def roundTrip = runtime.documents().read(json, createContext)
    def primary = (roundTrip.data() as DocumentData.SingleResource).resource()
    !primary.hasId()
    !primary.hasLid()
    roundTrip.included() != null
    roundTrip.included().collect { [it.type(), it.id()] } == [
      ["comments", "c1"],
      ["people", "p1"]
    ]
  }

  def "identified create with inclusion still traverses strictly"() {
    given:
    def options = new ResourceWriteOptions(
        new DocumentEnvelope(null, null, null),
        RepresentationSelection.builder().include(IncludePath.of("author")).build())
    def policy = RepresentationPolicy.defaults().withIncludePolicy(IncludePolicy.allowAll())
    def runtime = JsonApiJackson3.builder(JsonMapper.builder().build()).representationPolicy(policy).build()
    def draft = new LocalIdentityArticleWithAuthor("9", null, "Draft", new Person("p1", "Alice"), List.of())
    def createContext = DocumentReadContext.of(
        ValidationContext.defaults().withDocumentUsage(DocumentUsage.CREATE_REQUEST),
        PrimaryDataKind.RESOURCE)

    when:
    def json = runtime.resources().writeCreateDocument(draft, options)

    then:
    def roundTrip = runtime.documents().read(json, createContext)
    def primary = (roundTrip.data() as DocumentData.SingleResource).resource()
    primary.id() == "9"
    roundTrip.included() != null
    roundTrip.included().size() == 1
    roundTrip.included()[0].id() == "p1"
  }

  def "ordinary writes still require identity"() {
    when:
    jsonApi.resources().writeOne(new LocalIdentityArticle(null, null, "Draft"))

    then:
    def ex = thrown(JsonApiMappingException)
    ex.diagnostic() == MappingDiagnostic.MISSING_IDENTIFIER
  }

  def "writeUpdateDocument compares the expected endpoint identity"() {
    given:
    def article = new Article("1", "T", "B", List.of(), null)

    expect:
    jsonApi.resources().writeUpdateDocument(article, new EndpointIdentity("articles", "1")).contains('"id":"1"')

    when:
    jsonApi.resources().writeUpdateDocument(article, new EndpointIdentity("articles", "other"))

    then:
    thrown(JsonApiValidationException)
  }

  def "registered decorators apply transparently to domain writes"() {
    given:
    def links = Links.ofLinks([self: new Link.StringLink("https://example.test/articles/1")])
    def registry = ResourceDecoratorRegistry.builder()
        .register(Article, { Article a -> ResourceDecoration.ofLinks(links) } as ResourceDecorator)
        .build()
    def decorated = JsonApiJackson3.builder(JsonMapper.builder().build()).decorators(registry).build()

    when:
    def json = decorated.resources().writeOne(new Article("1", "T", "B", List.of(), null))
    def roundTrip = decorated.documents().read(json, DocumentReadContext.resourceDefaults())
    def primary = (roundTrip.data() as DocumentData.SingleResource).resource()

    then:
    primary.links() == links
  }

  def "inclusion and sparse fieldsets compose without caller choreography"() {
    given:
    def options = new ResourceWriteOptions(
        new DocumentEnvelope(null, null, null),
        RepresentationSelection.builder().include(IncludePath.of("comments")).fields("articles", "title").build())
    def policy = RepresentationPolicy.defaults().withIncludePolicy(IncludePolicy.allowAll())
    def runtime = JsonApiJackson3.builder(JsonMapper.builder().build()).representationPolicy(policy).build()
    def article = new Article("1", "Hello", "Body text", [
      new Comment("c1", "Nice", null)
    ], null)

    when:
    def json = runtime.resources().writeOne(article, options)
    def exemptions = DocumentReadContext.of(
        ValidationContext.defaults().withSparseFieldsetLinkageExemptions(
        Set.of(ResourceIdentity.ofId("comments", "c1"))),
        PrimaryDataKind.RESOURCE)
    def roundTrip = runtime.documents().read(json, exemptions)

    then:
    roundTrip.included() != null
    roundTrip.included().size() == 1
    roundTrip.included()[0].type() == "comments"
    !json.contains("Body text")
    json.contains("Hello")
  }

  def "id and lid bind to independent roles without coercion"() {
    given:
    def article = new LocalIdentityArticle("1", "lid-1", "T")

    when:
    def json = jsonApi.resources().writeOne(article)
    def actual = jsonApi.resources().readOne(json, LocalIdentityArticle)

    then:
    actual.id() == "1"
    actual.localId() == "lid-1"

    when:
    jsonApi.resources().readOne('{"data":{"type":"articles","lid":"lid-9","attributes":{"title":"T"}}}', LocalIdentityArticle)

    then:
    def validation = thrown(JsonApiDocumentReadException)
    validation.category() == CodecFailureCategory.AGGREGATE_VALIDATION
  }

  def "stream sinks mirror string results without closing caller streams"() {
    given:
    def article = new Article("1", "Hello", "B", List.of(), null)
    def out = new TrackingOutputStream(new ByteArrayOutputStream())

    when:
    jsonApi.resources().writeOne(article, out)
    def payload = out.bytes()
    def input = new TrackingInputStream(payload)
    def fromStream = jsonApi.resources().readOne(input, FlatArticle)

    then:
    fromStream == jsonApi.resources().readOne(new String(payload, "UTF-8"), FlatArticle)
    !out.closed
    !input.closed
  }

  def "round-trips a resource collection through writeMany and readMany"() {
    given:
    def articles = [
      new Article("1", "A", "Body a", List.of(), null),
      new Article("2", "B", "Body b", List.of(), null)
    ]
    def options = new ResourceWriteOptions(
        new DocumentEnvelope(
        Links.ofLinks([self: new Link.StringLink("https://example.test/articles")]),
        null,
        null),
        RepresentationSelection.none())

    when:
    def json = jsonApi.resources().writeMany(articles, options)
    def actual = jsonApi.resources().readMany(json, FlatArticle)

    then:
    actual*.id() == ["1", "2"]
    actual*.title() == ["A", "B"]
  }

  def "readManyDocument retains top-level state for collections"() {
    given:
    def json = '{"data":[{"type":"articles","id":"1","attributes":{"title":"A"}},{"type":"articles","id":"2","attributes":{"title":"B"}}],"meta":{"count":2}}'

    when:
    def document = jsonApi.resources().readManyDocument(json, FlatArticle)

    then:
    document.resources()*.id() == ["1", "2"]
    document.meta() == Meta.of([count: 2])
    document.included() == null
  }

  def "generic Type overloads bind through full generic fidelity"() {
    given:
    // A ParameterizedType (not a Class) forces runtime dispatch onto the Type overloads.
    def valueType = new TypeReference<GenericValue<String>>() {}.getType()
    def one = '{"data":{"type":"things","id":"1","attributes":{"value":"v"}}}'
    def many = '{"data":[{"type":"things","id":"1","attributes":{"value":"v"}}]}'

    when:
    def single = jsonApi.resources().readOne(one, valueType)
    def collection = jsonApi.resources().readMany(many, valueType)
    def streamSingle = jsonApi.resources().readOne(new ByteArrayInputStream(one.bytes), valueType)
    def streamMany = jsonApi.resources().readMany(new ByteArrayInputStream(many.bytes), valueType)

    then:
    single == new GenericValue("1", "v")
    collection == [new GenericValue("1", "v")]
    streamSingle == single
    streamMany == collection
  }

  def "remaining stream overloads mirror their string forms"() {
    given:
    def article = new Article("1", "Hello", "B", List.of(), null)
    def one = jsonApi.resources().writeOne(article)
    def many = jsonApi.resources().writeMany([article])
    def manyInput = new TrackingInputStream(many.bytes)
    def oneDocumentInput = new TrackingInputStream(one.bytes)
    def manyDocumentInput = new TrackingInputStream(many.bytes)
    def manyOut = new TrackingOutputStream(new ByteArrayOutputStream())
    def createOut = new TrackingOutputStream(new ByteArrayOutputStream())
    def updateOut = new TrackingOutputStream(new ByteArrayOutputStream())
    def updateOptionsOut = new TrackingOutputStream(new ByteArrayOutputStream())

    when:
    def readMany = jsonApi.resources().readMany(manyInput, FlatArticle)
    def oneDocument = jsonApi.resources().readOneDocument(oneDocumentInput, FlatArticle)
    def manyDocument = jsonApi.resources().readManyDocument(manyDocumentInput, FlatArticle)
    jsonApi.resources().writeMany([article], manyOut)
    jsonApi.resources().writeCreateDocument(article, createOut)
    jsonApi.resources().writeUpdateDocument(article, null, updateOut)
    jsonApi.resources().writeUpdateDocument(article, null, ResourceWriteOptions.defaults(), updateOptionsOut)

    then:
    readMany == jsonApi.resources().readMany(many, FlatArticle)
    oneDocument == jsonApi.resources().readOneDocument(one, FlatArticle)
    manyDocument == jsonApi.resources().readManyDocument(many, FlatArticle)
    jsonApi.resources().readMany(new ByteArrayInputStream(manyOut.bytes()), FlatArticle) == readMany
    jsonApi.resources().readOne(new ByteArrayInputStream(createOut.bytes()), FlatArticle).id() == "1"
    jsonApi.resources().readOne(new ByteArrayInputStream(updateOut.bytes()), FlatArticle).id() == "1"
    jsonApi.resources().readOne(new ByteArrayInputStream(updateOptionsOut.bytes()), FlatArticle).id() == "1"
    !manyInput.closed
    !oneDocumentInput.closed
    !manyDocumentInput.closed
    !manyOut.closed
    !createOut.closed
    !updateOut.closed
    !updateOptionsOut.closed
  }

  def "create and update authoring accept write options"() {
    given:
    def options = new ResourceWriteOptions(
        new DocumentEnvelope(null, Meta.of([note: "shaped"]), null),
        RepresentationSelection.none())
    def article = new Article("1", "T", "B", List.of(), null)

    when:
    def created = jsonApi.resources().writeCreateDocument(article, options)
    def updated = jsonApi.resources().writeUpdateDocument(article, new EndpointIdentity("articles", "1"), options)

    then:
    jsonApi.documents().read(created, DocumentReadContext.resourceDefaults()).meta() == Meta.of([note: "shaped"])
    jsonApi.documents().read(updated, DocumentReadContext.resourceDefaults()).meta() == Meta.of([note: "shaped"])
  }

  def "readOne rejects every non-resource primary shape without coercion"() {
    when:
    jsonApi.resources().readOne(json, FlatArticle)

    then:
    def ex = thrown(JsonApiMappingException)
    ex.diagnostic() == MappingDiagnostic.RESOURCE_TYPE_MISMATCH
    ex.propertyPath() == "/data"

    where:
    json << [
      '{"data":null}',
      '{"meta":{"a":1}}',
      '{"errors":[{"status":"500","title":"boom"}]}',
    ]
  }

  def "builder identifier conversion applies in both directions"() {
    given:
    def converter = [
      convert: { Object value -> value == null ? null : "id-" + value },
      parse: { String wire ->
        wire == null ? null : wire.substring(3)
      }
    ] as IdentifierConverter
    def runtime = JsonApiJackson3.builder(JsonMapper.builder().build()).identifierConverter(converter).build()

    when:
    def json = runtime.resources().writeOne(new Article("7", "T", "B", List.of(), null))

    then:
    json.contains('"id":"id-7"')

    when:
    def actual = runtime.resources().readOne(json, FlatArticle)

    then:
    actual.id() == "7"
  }

  def "builder linkage mappers serve custom relationship targets"() {
    given:
    def mapper = { RelationshipData data, JavaType target ->
      if (data instanceof RelationshipData.SingleLinkage) {
        def identifier = ((RelationshipData.SingleLinkage) data).identifier()
        return new FlatAuthor(identifier.type(), identifier.id())
      }
      ((RelationshipData.IdentifierCollectionLinkage) data).identifiers().collect {
        new FlatAuthor(it.type(), it.id())
      }
    } as RelationshipLinkageMapper
    def runtime = JsonApiJackson3.builder(JsonMapper.builder().build())
        .linkageMappers([(FlatAuthor): mapper]).build()
    def json = '{"data":{"type":"articles","id":"1","attributes":{"title":"T"},' +
        '"relationships":{"author":{"data":{"type":"people","id":"p1"}},' +
        '"contributors":{"data":[{"type":"people","id":"p2"}]}}}}'

    when:
    def article = runtime.resources().readOne(json, FlatMappedArticle)

    then:
    article.author() == new FlatAuthor("people", "p1")
    article.contributors() == [
      new FlatAuthor("people", "p2")
    ]
  }
}
