package com.kazforge.jsonapi.jackson2

import com.kazforge.jsonapi.core.model.JsonApiDocument
import com.kazforge.jsonapi.core.model.Meta
import com.kazforge.jsonapi.document.DocumentReadContext
import com.kazforge.jsonapi.fixtures.domainwrite.Article
import com.kazforge.jsonapi.jackson2.CloseTrackingFixtures.TrackingInputStream
import com.kazforge.jsonapi.jackson2.CloseTrackingFixtures.TrackingOutputStream
import com.kazforge.jsonapi.representation.RepresentationPolicy
import com.kazforge.jsonapi.representation.RepresentationSelection
import spock.lang.Shared
import spock.lang.Specification
import com.fasterxml.jackson.databind.json.JsonMapper

class Jackson2JsonApiDocumentsSpec extends Specification {

  @Shared
  JsonMapper mapper = JsonMapper.builder().build()

  @Shared
  Jackson2JsonApi jsonApi = JsonApiJackson2.jsonApi(mapper)

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
}
