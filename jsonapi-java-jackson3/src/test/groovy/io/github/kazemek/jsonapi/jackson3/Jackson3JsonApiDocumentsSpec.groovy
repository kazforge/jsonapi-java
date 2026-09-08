package io.github.kazemek.jsonapi.jackson3

import io.github.kazemek.jsonapi.core.model.DocumentData
import io.github.kazemek.jsonapi.core.model.JsonApiDocument
import io.github.kazemek.jsonapi.core.model.Meta
import io.github.kazemek.jsonapi.jackson.document.DocumentReadContext
import io.github.kazemek.jsonapi.jackson3.CloseTrackingFixtures.TrackingInputStream
import io.github.kazemek.jsonapi.jackson3.CloseTrackingFixtures.TrackingOutputStream
import io.github.kazemek.jsonapi.fixtures.domainwrite.Article
import io.github.kazemek.jsonapi.jackson.representation.RepresentationPolicy
import io.github.kazemek.jsonapi.jackson.representation.RepresentationSelection
import spock.lang.Shared
import spock.lang.Specification
import tools.jackson.databind.json.JsonMapper

class Jackson3JsonApiDocumentsSpec extends Specification {

  @Shared
  Jackson3JsonApi jsonApi = JsonApiJackson3.jsonApi(JsonMapper.builder().build())

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
    def mapper = JsonApiJackson3.resourceMapper(JsonMapper.builder().build())

    when:
    def mapped = mapper.toMappedDocument(new Article("1", "T", "B", List.of(), null), null,
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

  def "configured resource version does not affect raw document writes"() {
    given:
    def runtime = JsonApiJackson3.builder(JsonMapper.builder().build())
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
    def mapper = JsonApiJackson3.resourceMapper(JsonMapper.builder().build())

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
}
