package com.kazforge.jsonapi.jackson2

import com.kazforge.jsonapi.core.model.ResourceIdentifier
import com.kazforge.jsonapi.core.validation.ValidationRuleCode
import com.kazforge.jsonapi.diagnostic.CodecFailureCategory
import com.kazforge.jsonapi.diagnostic.JsonApiDocumentReadException
import com.kazforge.jsonapi.fixtures.TestFixtureResources
import com.kazforge.jsonapi.fixtures.domainread.FlatArticle
import com.kazforge.jsonapi.jackson2.CloseTrackingFixtures.TrackingInputStream
import com.kazforge.jsonapi.jackson2.CloseTrackingFixtures.TrackingOutputStream
import spock.lang.Shared
import spock.lang.Specification
import com.fasterxml.jackson.databind.json.JsonMapper

class Jackson2JsonApiRelationshipsSpec extends Specification {

  @Shared
  Jackson2JsonApi jsonApi = JsonApiJackson2.jsonApi(JsonMapper.builder().build())

  def "resource reads reject top-level related"() {
    given:
    def json = TestFixtureResources.readCorpusUtf8('negative/resource-with-related-link.json')

    when:
    jsonApi.resources().readOne(json, FlatArticle)

    then:
    def ex = thrown(JsonApiDocumentReadException)
    ex.category() == CodecFailureCategory.AGGREGATE_VALIDATION
    ex.ruleCode() == ValidationRuleCode.INVALID_LINKS_CONTEXT
    ex.jsonPointer() == "/links/related"
  }

  def "relationship reads reject single-linkage pagination"() {
    given:
    def json = TestFixtureResources.readCorpusUtf8('negative/relationship-single-with-pagination.json')

    when:
    jsonApi.relationships().readToOne(json)

    then:
    def ex = thrown(JsonApiDocumentReadException)
    ex.category() == CodecFailureCategory.AGGREGATE_VALIDATION
    ex.ruleCode() == ValidationRuleCode.PAGINATION_REQUIRES_COLLECTION
    ex.jsonPointer() == "/links/next"
  }

  def "stream sinks mirror string results without closing caller streams"() {
    given:
    def identifier = ResourceIdentifier.of("people", "p1")
    def identifiers = [
      ResourceIdentifier.of("comments", "c1")
    ]
    def oneOut = new TrackingOutputStream(new ByteArrayOutputStream())
    def manyOut = new TrackingOutputStream(new ByteArrayOutputStream())

    when:
    jsonApi.relationships().writeToOne(identifier, oneOut)
    jsonApi.relationships().writeToMany(identifiers, manyOut)
    def oneInput = new TrackingInputStream(oneOut.bytes())
    def manyInput = new TrackingInputStream(manyOut.bytes())

    then:
    jsonApi.relationships().readToOne(oneInput) == identifier
    jsonApi.relationships().readToMany(manyInput) == identifiers
    !oneOut.closed
    !manyOut.closed
    !oneInput.closed
    !manyInput.closed
  }
}
