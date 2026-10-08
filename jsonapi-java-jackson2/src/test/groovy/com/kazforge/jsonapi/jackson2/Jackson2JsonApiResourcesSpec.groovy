package com.kazforge.jsonapi.jackson2

import com.kazforge.jsonapi.jackson2.mapping.RelationshipLinkageMapper

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.JavaType
import com.fasterxml.jackson.databind.json.JsonMapper
import com.kazforge.jsonapi.api.ResourceWriteOptions
import com.kazforge.jsonapi.core.aggregate.ValidationContext
import com.kazforge.jsonapi.core.model.JsonApiObject
import com.kazforge.jsonapi.core.model.RelationshipData
import com.kazforge.jsonapi.core.model.ResourceIdentity
import com.kazforge.jsonapi.core.validation.EndpointIdentity
import com.kazforge.jsonapi.core.validation.JsonApiValidationException
import com.kazforge.jsonapi.document.DocumentEnvelope
import com.kazforge.jsonapi.document.DocumentReadContext
import com.kazforge.jsonapi.document.PrimaryDataKind
import com.kazforge.jsonapi.fixtures.domainread.FlatArticle
import com.kazforge.jsonapi.fixtures.domainwrite.Article
import com.kazforge.jsonapi.fixtures.domainwrite.Comment
import com.kazforge.jsonapi.fixtures.localid.LocalIdentityArticle
import com.kazforge.jsonapi.jackson2.CloseTrackingFixtures.TrackingInputStream
import com.kazforge.jsonapi.jackson2.CloseTrackingFixtures.TrackingOutputStream
import com.kazforge.jsonapi.jackson2.LinkageMapperFixtures.FlatAuthor
import com.kazforge.jsonapi.jackson2.LinkageMapperFixtures.FlatMappedArticle
import com.kazforge.jsonapi.fixtures.generic.ParameterizedBindingFixtures.GenericValue
import com.kazforge.jsonapi.mapping.IdentifierConverter
import com.kazforge.jsonapi.representation.IncludePath
import com.kazforge.jsonapi.representation.IncludePolicy
import com.kazforge.jsonapi.representation.RepresentationPolicy
import com.kazforge.jsonapi.representation.RepresentationSelection
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.UncheckedIOException
import spock.lang.Shared
import spock.lang.Specification

class Jackson2JsonApiResourcesSpec extends Specification {

  @Shared
  Jackson2JsonApi jsonApi = JsonApiJackson2.jsonApi(JsonMapper.builder().build())

  def "jsonapi version configuration rejects null"() {
    when:
    JsonApiJackson2.builder(JsonMapper.builder().build()).jsonApiVersion(null)

    then:
    thrown(NullPointerException)
  }

  def "configured jsonapi version reaches resource output streams"() {
    given:
    def runtime = JsonApiJackson2.builder(JsonMapper.builder().build())
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

  def "builder identifier conversion applies in both directions"() {
    given:
    def converter = [
      convert: { Object value -> value == null ? null : "id-" + value },
      parse: { String wire ->
        wire == null ? null : wire.substring(3)
      }
    ] as IdentifierConverter
    def runtime = JsonApiJackson2.builder(JsonMapper.builder().build())
        .identifierConverter(converter)
        .build()

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
    def runtime = JsonApiJackson2.builder(JsonMapper.builder().build())
        .linkageMappers([(FlatAuthor): mapper])
        .build()
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

  def "resource stream overloads preserve results and caller ownership"() {
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

  def "create and update authoring select core validation usage"() {
    given:
    def article = new Article("1", "T", "B", List.of(), null)
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

    when:
    def updateJson = jsonApi.resources().writeUpdateDocument(
        article, new EndpointIdentity("articles", "1"))

    then:
    updateJson.contains('"id":"1"')

    when:
    jsonApi.resources().writeUpdateDocument(article, new EndpointIdentity("articles", "other"))

    then:
    thrown(JsonApiValidationException)
  }

  def "sparse-fieldset write provenance reads back with configured exemptions"() {
    given:
    def options = new ResourceWriteOptions(
        new DocumentEnvelope(null, null, null),
        RepresentationSelection.builder()
        .include(IncludePath.of("comments"))
        .fields("articles", "title")
        .build())
    def policy = RepresentationPolicy.defaults().withIncludePolicy(IncludePolicy.allowAll())
    def runtime = JsonApiJackson2.builder(JsonMapper.builder().build())
        .representationPolicy(policy)
        .build()
    def article = new Article("1", "Hello", "Body text", [
      new Comment("c1", "Nice", null)
    ], null)
    def readContext = DocumentReadContext.of(
        ValidationContext.defaults().withSparseFieldsetLinkageExemptions(
        Set.of(ResourceIdentity.ofId("comments", "c1"))),
        PrimaryDataKind.RESOURCE)

    when:
    def json = runtime.resources().writeOne(article, options)
    def roundTrip = runtime.documents().read(json, readContext)

    then:
    roundTrip.included() != null
    roundTrip.included().size() == 1
    roundTrip.included()[0].type() == "comments"
    !json.contains("Body text")
    json.contains("Hello")
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
