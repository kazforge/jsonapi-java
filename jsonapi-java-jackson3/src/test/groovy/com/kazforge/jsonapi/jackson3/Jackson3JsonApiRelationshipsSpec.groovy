package com.kazforge.jsonapi.jackson3

import com.kazforge.jsonapi.core.model.ResourceIdentifier
import com.kazforge.jsonapi.core.model.DocumentData
import com.kazforge.jsonapi.core.aggregate.ValidationContext
import com.kazforge.jsonapi.core.validation.PrimaryDataContext
import com.kazforge.jsonapi.document.DocumentReadContext
import com.kazforge.jsonapi.document.PrimaryDataKind
import java.nio.charset.StandardCharsets
import com.kazforge.jsonapi.core.validation.ValidationRuleCode
import com.kazforge.jsonapi.diagnostic.CodecFailureCategory
import com.kazforge.jsonapi.diagnostic.JsonApiDocumentReadException
import com.kazforge.jsonapi.fixtures.TestFixtureResources
import com.kazforge.jsonapi.fixtures.domainread.FlatArticle
import com.kazforge.jsonapi.jackson3.CloseTrackingFixtures.TrackingInputStream
import com.kazforge.jsonapi.jackson3.CloseTrackingFixtures.TrackingOutputStream
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll
import tools.jackson.databind.json.JsonMapper

class Jackson3JsonApiRelationshipsSpec extends Specification {

  @Shared
  Jackson3JsonApi jsonApi = JsonApiJackson3.jsonApi(JsonMapper.builder().build())

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

  @Unroll
  def "relationship #shape rejects primary identifier #member"() {
    given:
    String json = shape == 'to-one'
        ? """{"data":{"type":"people","id":"p1","${member}":${value}}}"""
        : """{"data":[{"type":"people","id":"p1"},{"type":"people","id":"p2","${member}":${value}}]}"""

    when:
    if (shape == 'to-one') {
      jsonApi.relationships().readToOne(json)
    } else {
      jsonApi.relationships().readToMany(json)
    }

    then:
    def ex = thrown(JsonApiDocumentReadException)
    ex.category() == CodecFailureCategory.LOCAL_VALIDATION
    ex.ruleCode() == ValidationRuleCode.PRIMARY_DATA_CONTEXT_MISMATCH
    ex.jsonPointer() == (shape == 'to-one' ? "/data/${member}" : "/data/1/${member}")
    ex.sourceLocation().isKnown()
    ex.cause == null

    where:
    shape     | member          | value
    'to-one'  | 'attributes'    | '{"name":"A"}'
    'to-one'  | 'relationships' | '{}'
    'to-one'  | 'links'         | 'null'
    'to-many' | 'attributes'    | 'null'
    'to-many' | 'relationships' | '{}'
    'to-many' | 'links'         | '{}'
  }

  def "neutral relationship identifier reads reject resource-only members in one pass"() {
    given:
    def relationshipContext = DocumentReadContext.of(
        ValidationContext.defaults().withPrimaryDataContext(PrimaryDataContext.RELATIONSHIP),
        PrimaryDataKind.RESOURCE_IDENTIFIER)

    when:
    jsonApi.documents().read(
        '{"data":[{"type":"people","id":"p1"},{"type":"people","id":"p2","links":{}}]}',
        relationshipContext)

    then:
    def ex = thrown(JsonApiDocumentReadException)
    ex.category() == CodecFailureCategory.LOCAL_VALIDATION
    ex.ruleCode() == ValidationRuleCode.PRIMARY_DATA_CONTEXT_MISMATCH
    ex.jsonPointer() == '/data/1/links'
    ex.sourceLocation().isKnown()

    when:
    def stream = new TrackingInputStream(
        '{"data":{"type":"people","id":"p1","attributes":{}}}'.getBytes(StandardCharsets.UTF_8))
    jsonApi.documents().read(stream, relationshipContext)

    then:
    ex = thrown(JsonApiDocumentReadException)
    ex.ruleCode() == ValidationRuleCode.PRIMARY_DATA_CONTEXT_MISMATCH
    ex.jsonPointer() == '/data/attributes'
    !stream.closed
  }

  def "relationship identifiers retain metadata and supported extra members"() {
    given:
    def id = jsonApi.relationships().readToOne(
        '{"data":{"type":"people","id":"p1","lid":"local-p1","meta":{"role":"reviewer"},"@note":true}}')

    expect:
    id.type() == 'people'
    id.id() == 'p1'
    id.lid() == 'local-p1'
    id.meta().members() == [role: 'reviewer']
    id.additionalMembers().get('@note') == true
    jsonApi.relationships().readToMany('{"data":[]}').isEmpty()
    jsonApi.relationships().readToOne('{"data":null}') == null
  }

  def "ordinary identifier reads remain tolerant of resource-only and unknown members"() {
    given:
    def json = '{"data":{"type":"people","id":"p1","attributes":{"name":"A"},"links":{},"unknown":true}}'

    when:
    def document = jsonApi.documents().read(json, DocumentReadContext.identifierDefaults())

    then:
    (document.data() as DocumentData.SingleIdentifier).identifier() ==
        ResourceIdentifier.of('people', 'p1')
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
