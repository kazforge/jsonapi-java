package com.kazforge.jsonapi.jackson2

import com.kazforge.jsonapi.core.model.DocumentData
import com.kazforge.jsonapi.core.model.ErrorObject
import com.kazforge.jsonapi.core.model.ErrorSource
import com.kazforge.jsonapi.core.model.JsonApiDocument
import com.kazforge.jsonapi.core.model.Link
import com.kazforge.jsonapi.core.model.Links
import com.kazforge.jsonapi.core.model.Meta
import com.kazforge.jsonapi.fixtures.TestFixtureResources
import com.kazforge.jsonapi.fixtures.domainwrite.Article
import com.kazforge.jsonapi.jackson.document.DocumentReadContext
import com.kazforge.jsonapi.jackson.representation.RepresentationPolicy
import com.kazforge.jsonapi.jackson.representation.RepresentationSelection
import com.kazforge.jsonapi.jackson2.CloseTrackingFixtures.TrackingInputStream
import com.kazforge.jsonapi.jackson2.CloseTrackingFixtures.TrackingOutputStream
import spock.lang.Shared
import spock.lang.Specification
import com.fasterxml.jackson.databind.json.JsonMapper

class Jackson2JsonApiDocumentsSpec extends Specification {

  // The passive canonical error fixture intentionally uses this valid HTTP URI.
  //noinspection HttpUrlsUsage
  @SuppressWarnings("HttpUrlsUsage")
  private static final String ERROR_ABOUT_URL = "http://example.com/docs/errors/invalid"

  @Shared
  JsonMapper mapper = JsonMapper.builder().build()

  @Shared
  Jackson2JsonApi jsonApi = JsonApiJackson2.jsonApi(mapper)

  def "reads with an explicit context and writes the raw document back"() {
    given:
    def json = '{"data":{"type":"articles","id":"1","attributes":{"title":"Hello"}}}'

    when:
    def document = jsonApi.documents().read(json, DocumentReadContext.resourceDefaults())

    then:
    (document.data() as DocumentData.SingleResource).resource().id() == "1"

    when:
    def written = jsonApi.documents().write(document)

    then:
    jsonApi.documents().read(written, DocumentReadContext.resourceDefaults()) == document
  }

  def "writes a mapped document with its sparse-fieldset provenance"() {
    given:
    def mapper = JsonApiJackson2.resourceMapper(JsonMapper.builder().build())

    when:
    def mapped = mapper.toMappedDocument(
        new Article("1", "T", "B", List.of(), null),
        null,
        RepresentationSelection.none(),
        RepresentationPolicy.defaults())
    def written = jsonApi.documents().write(mapped)

    then:
    written.contains('"id":"1"')
    jsonApi.documents().read(written, DocumentReadContext.resourceDefaults()).meta() == mapped.document().meta()
  }

  def "meta-only documents read without primary data"() {
    given:
    def document = JsonApiDocument.withMeta(Meta.of([count: 2]))

    when:
    def written = jsonApi.documents().write(document)
    def actual = jsonApi.documents().read(written, DocumentReadContext.resourceDefaults())

    then:
    actual == document
  }

  def "builder-produced error documents match direct construction through the public runtime"() {
    given:
    def built = builderErrorDocument()
    def direct = directErrorDocument()
    def expected = TestFixtureResources.readCorpusUtf8("documents/errors-document.json")

    when:
    def builtJson = jsonApi.documents().write(built)
    def directJson = jsonApi.documents().write(direct)
    def roundTrip = jsonApi.documents().read(builtJson, DocumentReadContext.resourceDefaults())

    then:
    built == direct
    mapper.readTree(builtJson) == mapper.readTree(directJson)
    mapper.readTree(builtJson) == mapper.readTree(expected)
    roundTrip == direct
  }

  def "configured resource version does not affect raw document writes"() {
    given:
    def runtime = JsonApiJackson2.builder(JsonMapper.builder().build())
        .jsonApiVersion("1.1")
        .build()
    def document = JsonApiDocument.withMeta(Meta.of([count: 2]))

    when:
    def written = runtime.documents().write(document)

    then:
    runtime.documents().read(written, DocumentReadContext.resourceDefaults()).jsonapi() == null
  }

  def "stream sinks mirror string results without closing caller streams"() {
    given:
    def document = JsonApiDocument.withMeta(Meta.of([count: 2]))
    def out = new TrackingOutputStream(new ByteArrayOutputStream())
    def mappedOut = new TrackingOutputStream(new ByteArrayOutputStream())
    def mapper = JsonApiJackson2.resourceMapper(JsonMapper.builder().build())

    when:
    jsonApi.documents().write(document, out)
    def mapped = mapper.toMappedDocument(
        new Article("1", "T", "B", List.of(), null),
        null,
        RepresentationSelection.none(),
        RepresentationPolicy.defaults())
    jsonApi.documents().write(mapped, mappedOut)
    def documentInput = new TrackingInputStream(out.bytes())
    def mappedInput = new TrackingInputStream(mappedOut.bytes())

    then:
    jsonApi.documents().read(documentInput, DocumentReadContext.resourceDefaults()) == document
    jsonApi.documents().read(mappedInput, DocumentReadContext.resourceDefaults()) == mapped.document()
    !out.closed
    !mappedOut.closed
    !documentInput.closed
    !mappedInput.closed
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
