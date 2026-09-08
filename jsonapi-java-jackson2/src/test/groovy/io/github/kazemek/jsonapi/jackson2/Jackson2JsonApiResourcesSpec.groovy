package io.github.kazemek.jsonapi.jackson2

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.JavaType
import com.fasterxml.jackson.databind.json.JsonMapper
import io.github.kazemek.jsonapi.core.model.DocumentData
import io.github.kazemek.jsonapi.core.model.JsonApiObject
import io.github.kazemek.jsonapi.core.model.Link
import io.github.kazemek.jsonapi.core.model.Links
import io.github.kazemek.jsonapi.core.model.Meta
import io.github.kazemek.jsonapi.core.model.RelationshipData
import io.github.kazemek.jsonapi.core.model.ResourceIdentifier
import io.github.kazemek.jsonapi.core.validation.EndpointIdentity
import io.github.kazemek.jsonapi.core.validation.JsonApiValidationException
import io.github.kazemek.jsonapi.core.validation.ValidationContext
import io.github.kazemek.jsonapi.fixtures.domainread.FlatArticle
import io.github.kazemek.jsonapi.fixtures.domainwrite.Article
import io.github.kazemek.jsonapi.fixtures.domainwrite.Comment
import io.github.kazemek.jsonapi.fixtures.domainwrite.Person
import io.github.kazemek.jsonapi.jackson.api.ResourceWriteOptions
import io.github.kazemek.jsonapi.jackson.diagnostic.JsonApiMappingException
import io.github.kazemek.jsonapi.jackson.diagnostic.MappingDiagnostic
import io.github.kazemek.jsonapi.jackson.document.DocumentEnvelope
import io.github.kazemek.jsonapi.jackson.document.DocumentReadContext
import io.github.kazemek.jsonapi.jackson.mapping.ResourceDecoration
import io.github.kazemek.jsonapi.jackson.mapping.IdentifierConverter
import io.github.kazemek.jsonapi.jackson.mapping.ResourceDecorator
import io.github.kazemek.jsonapi.jackson.mapping.ResourceDecoratorRegistry
import io.github.kazemek.jsonapi.jackson.representation.IncludePath
import io.github.kazemek.jsonapi.jackson.representation.IncludePolicy
import io.github.kazemek.jsonapi.jackson.representation.RepresentationPolicy
import io.github.kazemek.jsonapi.jackson.representation.RepresentationSelection
import io.github.kazemek.jsonapi.jackson2.CloseTrackingFixtures.TrackingInputStream
import io.github.kazemek.jsonapi.jackson2.CloseTrackingFixtures.TrackingOutputStream
import io.github.kazemek.jsonapi.jackson2.LinkageMapperFixtures.FlatAuthor
import io.github.kazemek.jsonapi.jackson2.LinkageMapperFixtures.FlatMappedArticle
import io.github.kazemek.jsonapi.jackson2.ParameterizedBindingFixtures.GenericValue
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.UncheckedIOException
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

class Jackson2JsonApiResourcesSpec extends Specification {

  @Shared
  Jackson2JsonApi jsonApi = JsonApiJackson2.jsonApi(JsonMapper.builder().build())

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

  @Unroll
  def "resource read shape #description is rejected without coercion"() {
    when:
    jsonApi.resources().readOne(json, FlatArticle)

    then:
    def ex = thrown(JsonApiMappingException)
    ex.diagnostic() == MappingDiagnostic.RESOURCE_TYPE_MISMATCH
    ex.propertyPath() == (description == "wrong type" ? "/type" : "/data")

    where:
    description       | json
    "explicit null"  | '{"data":null}'
    "absent"         | '{"meta":{"count":1}}'
    "error document" | '{"errors":[{"status":"500","title":"boom"}]}'
    "wrong type"     | '{"data":{"type":"comments","id":"1"}}'
    "collection"     | '{"data":[]}'
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

  def "readOneDocument retains top-level state and included resources"() {
    given:
    def json = '{"data":{"type":"articles","id":"1","attributes":{"title":"Hello"},' +
        '"relationships":{"author":{"data":{"type":"people","id":"p1"}}}},' +
        '"included":[{"type":"people","id":"p1","attributes":{"name":"Alice"}}],' +
        '"meta":{"count":1},"links":{"self":"https://example.test/articles"},' +
        '"jsonapi":{"version":"1.1"}}'

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
  }

  def "readManyDocument retains collection state"() {
    given:
    def json = '{"data":[{"type":"articles","id":"1","attributes":{"title":"A"}},' +
        '{"type":"articles","id":"2","attributes":{"title":"B"}}],"meta":{"count":2}}'

    when:
    def document = jsonApi.resources().readManyDocument(json, FlatArticle)

    then:
    document.resources()*.id() == ["1", "2"]
    document.resources()*.title() == ["A", "B"]
    document.meta() == Meta.of([count: 2])
    document.included() == null
  }

  def "resource writes carry envelope state and use application defaults"() {
    given:
    def options = new ResourceWriteOptions(
        new DocumentEnvelope(
        Links.ofLinks([self: new Link.StringLink("https://example.test/articles")]),
        Meta.of([copyright: "2026"]),
        JsonApiObject.ofVersion("1.0")),
        RepresentationSelection.none())
    def configured = JsonApiJackson2.builder(JsonMapper.builder().build())
        .jsonApiVersion("1.1")
        .build()
    def article = new Article("1", "T", "B", List.of(), null)

    when:
    def explicit = configured.documents().read(
        configured.resources().writeOne(article, options), DocumentReadContext.resourceDefaults())
    def inherited = configured.documents().read(
        configured.resources().writeOne(article), DocumentReadContext.resourceDefaults())
    def unconfigured = jsonApi.documents().read(
        jsonApi.resources().writeOne(article), DocumentReadContext.resourceDefaults())

    then:
    explicit.links() == options.envelope().links()
    explicit.meta() == options.envelope().meta()
    explicit.jsonapi() == options.envelope().jsonapi()
    inherited.jsonapi() == JsonApiObject.ofVersion("1.1")
    unconfigured.jsonapi() == null
  }

  def "configured runtime applies its jsonapi version to collection writes"() {
    given:
    def runtime = JsonApiJackson2.builder(JsonMapper.builder().build())
        .jsonApiVersion("1.1")
        .build()

    when:
    def document = runtime.documents().read(
        runtime.resources().writeMany([
          new Article("1", "A", "B", List.of(), null),
          new Article("2", "C", "D", List.of(), null)
        ]), DocumentReadContext.resourceDefaults())

    then:
    document.jsonapi() == JsonApiObject.ofVersion("1.1")
  }

  def "configured runtime applies its jsonapi version to create authoring"() {
    given:
    def runtime = JsonApiJackson2.builder(JsonMapper.builder().build())
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
    def runtime = JsonApiJackson2.builder(JsonMapper.builder().build())
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

  def "representation policy and decoration are coordinated internally"() {
    given:
    def links = Links.ofLinks([self: new Link.StringLink("https://example.test/articles/1")])
    def decorators = ResourceDecoratorRegistry.builder()
        .register(Article, { Article ignored -> ResourceDecoration.ofLinks(links) } as ResourceDecorator)
        .build()
    def policy = RepresentationPolicy.defaults().withIncludePolicy(IncludePolicy.allowAll())
    def configured = JsonApiJackson2.builder(JsonMapper.builder().build())
        .representationPolicy(policy)
        .decorators(decorators)
        .build()
    def options = new ResourceWriteOptions(
        new DocumentEnvelope(null, null, null),
        RepresentationSelection.builder().include(IncludePath.of("comments")).build())
    def article = new Article("1", "T", "B", [new Comment("c1", "C", null)], null)

    when:
    def json = configured.resources().writeOne(article, options)
    def document = configured.documents().read(json, DocumentReadContext.resourceDefaults()
        .withValidationContext(ValidationContext.defaults()
        .withSparseFieldsetLinkageExemptions(Set.of())))
    def primary = (document.data() as DocumentData.SingleResource).resource()

    then:
    primary.links() == links
    primary.relationships().relationships().containsKey("comments")
    document.included().size() == 1
  }

  def "create and update authoring select core validation usage"() {
    given:
    def article = new Article("1", "T", "B", List.of(), null)
    def local = new LocalIdFixtures.RenamedLocalIdArticle(null, "lid-1", "Draft")

    when:
    def createJson = jsonApi.resources().writeCreateDocument(local)

    then:
    createJson.contains('"lid":"lid-1"')
    !createJson.contains('"id"')

    when:
    jsonApi.resources().writeOne(local)

    then:
    thrown(JsonApiValidationException)

    when:
    def updateJson = jsonApi.resources().writeUpdateDocument(article, new EndpointIdentity("articles", "1"))

    then:
    updateJson.contains('"id":"1"')

    when:
    jsonApi.resources().writeUpdateDocument(article, new EndpointIdentity("articles", "other"))

    then:
    thrown(JsonApiValidationException)
  }

  def "builder identifier and linkage configuration applies to resource reads and writes"() {
    given:
    def converter = [
      convert: { Object value -> value == null ? null : "id-" + value },
      parse: { String wire ->
        wire == null ? null : wire.substring(3)
      }
    ] as IdentifierConverter
    def linkageMapper = { RelationshipData data, JavaType target ->
      if (data instanceof RelationshipData.SingleLinkage) {
        def identifier = ((RelationshipData.SingleLinkage) data).identifier()
        return new FlatAuthor(identifier.type(), identifier.id())
      }
      ((RelationshipData.IdentifierCollectionLinkage) data).identifiers().collect {
        new FlatAuthor(it.type(), it.id())
      }
    } as RelationshipLinkageMapper
    def configured = JsonApiJackson2.builder(JsonMapper.builder().build())
        .identifierConverter(converter)
        .linkageMappers([(FlatAuthor): linkageMapper])
        .build()
    def json = '{"data":{"type":"articles","id":"id-1","attributes":{"title":"T"},' +
        '"relationships":{"author":{"data":{"type":"people","id":"id-p1"}},' +
        '"contributors":{"data":[{"type":"people","id":"id-p2"}]}}}}'

    when:
    def article = configured.resources().readOne(json, FlatMappedArticle)
    def written = configured.resources().writeOne(new Article("1", "T", "B", List.of(), null))

    then:
    article.id() == "1"
    article.author() == new FlatAuthor("people", "id-p1")
    article.contributors() == [
      new FlatAuthor("people", "id-p2")
    ]
    written.contains('"id":"id-1"')
  }

  def "generic Type overloads retain full type fidelity"() {
    given:
    def valueType = new TypeReference<GenericValue<String>>() {}.type
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

  def "all resource stream overloads preserve results and caller ownership"() {
    given:
    def article = new Article("1", "Hello", "B", List.of(), null)
    def one = jsonApi.resources().writeOne(article)
    def many = jsonApi.resources().writeMany([article])
    def options = ResourceWriteOptions.defaults()
    def manyInput = new TrackingInputStream(many.bytes)
    def oneTypeInput = new TrackingInputStream(one.bytes)
    def manyTypeInput = new TrackingInputStream(many.bytes)
    def oneDocumentInput = new TrackingInputStream(one.bytes)
    def manyDocumentInput = new TrackingInputStream(many.bytes)
    def oneOut = new TrackingOutputStream(new ByteArrayOutputStream())
    def oneOptionsOut = new TrackingOutputStream(new ByteArrayOutputStream())
    def manyOut = new TrackingOutputStream(new ByteArrayOutputStream())
    def manyOptionsOut = new TrackingOutputStream(new ByteArrayOutputStream())
    def createOut = new TrackingOutputStream(new ByteArrayOutputStream())
    def createOptionsOut = new TrackingOutputStream(new ByteArrayOutputStream())
    def updateOut = new TrackingOutputStream(new ByteArrayOutputStream())
    def updateOptionsOut = new TrackingOutputStream(new ByteArrayOutputStream())

    when:
    def readMany = jsonApi.resources().readMany(manyInput, FlatArticle)
    def readOneType = jsonApi.resources().readOne(oneTypeInput, FlatArticle)
    def readManyType = jsonApi.resources().readMany(manyTypeInput, FlatArticle)
    def oneDocument = jsonApi.resources().readOneDocument(oneDocumentInput, FlatArticle)
    def manyDocument = jsonApi.resources().readManyDocument(manyDocumentInput, FlatArticle)
    jsonApi.resources().writeOne(article, oneOut)
    jsonApi.resources().writeOne(article, options, oneOptionsOut)
    jsonApi.resources().writeMany([article], manyOut)
    jsonApi.resources().writeMany([article], options, manyOptionsOut)
    jsonApi.resources().writeCreateDocument(article, createOut)
    jsonApi.resources().writeCreateDocument(article, options, createOptionsOut)
    jsonApi.resources().writeUpdateDocument(article, null, updateOut)
    jsonApi.resources().writeUpdateDocument(article, null, options, updateOptionsOut)

    then:
    readMany*.id() == ["1"]
    readOneType.id() == "1"
    readManyType*.id() == ["1"]
    oneDocument.resource().id() == "1"
    manyDocument.resources()*.id() == ["1"]
    jsonApi.resources().readOne(new ByteArrayInputStream(oneOut.bytes()), FlatArticle).id() == "1"
    jsonApi.resources().readOne(new ByteArrayInputStream(oneOptionsOut.bytes()), FlatArticle).id() == "1"
    jsonApi.resources().readMany(new ByteArrayInputStream(manyOut.bytes()), FlatArticle)*.id() == ["1"]
    jsonApi.resources().readMany(new ByteArrayInputStream(manyOptionsOut.bytes()), FlatArticle)*.id() == ["1"]
    jsonApi.resources().readOne(new ByteArrayInputStream(createOut.bytes()), FlatArticle).id() == "1"
    jsonApi.resources().readOne(new ByteArrayInputStream(createOptionsOut.bytes()), FlatArticle).id() == "1"
    jsonApi.resources().readOne(new ByteArrayInputStream(updateOut.bytes()), FlatArticle).id() == "1"
    jsonApi.resources().readOne(new ByteArrayInputStream(updateOptionsOut.bytes()), FlatArticle).id() == "1"
    !manyInput.closed
    !oneTypeInput.closed
    !manyTypeInput.closed
    !oneDocumentInput.closed
    !manyDocumentInput.closed
    !oneOut.closed
    !oneOptionsOut.closed
    !manyOut.closed
    !manyOptionsOut.closed
    !createOut.closed
    !createOptionsOut.closed
    !updateOut.closed
    !updateOptionsOut.closed
  }

  def "Level-1 resource reads and writes adapt unavoidable checked I/O"() {
    given:
    def article = new Article("1", "T", "B", List.of(), null)

    when:
    jsonApi.resources().readOne(new FailingInputStream(new IOException("source boom")), FlatArticle)

    then:
    def sourceFailure = thrown(UncheckedIOException)
    sourceFailure.cause.message == "source boom"

    when:
    jsonApi.resources().writeOne(article, new FailingOutputStream(new IOException("sink boom")))

    then:
    def sinkFailure = thrown(UncheckedIOException)
    sinkFailure.cause.message == "sink boom"
  }

  private static final class FailingInputStream extends InputStream {

    private final IOException failure

    FailingInputStream(IOException failure) {
      this.failure = failure
    }

    @Override
    int read() throws IOException {
      throw failure
    }
  }

  private static final class FailingOutputStream extends OutputStream {

    private final IOException failure

    FailingOutputStream(IOException failure) {
      this.failure = failure
    }

    @Override
    void write(int value) throws IOException {
      throw failure
    }

    @Override
    void write(byte[] bytes, int offset, int length) throws IOException {
      throw failure
    }
  }
}
