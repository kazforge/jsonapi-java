package com.kazforge.jsonapi.jackson3

import com.kazforge.jsonapi.core.aggregate.ValidationContext
import com.kazforge.jsonapi.core.model.Meta
import com.kazforge.jsonapi.core.model.ResourceIdentifier
import com.kazforge.jsonapi.core.validation.DocumentUsage
import com.kazforge.jsonapi.core.validation.PrimaryDataContext
import com.kazforge.jsonapi.core.validation.ValidationRuleCode
import com.kazforge.jsonapi.diagnostic.CodecFailureCategory
import com.kazforge.jsonapi.diagnostic.JsonApiDocumentReadException
import com.kazforge.jsonapi.document.DocumentReadContext
import com.kazforge.jsonapi.document.PrimaryDataKind
import com.kazforge.jsonapi.fixtures.TestFixtureResources
import com.kazforge.jsonapi.fixtures.domainread.FlatArticle
import com.kazforge.jsonapi.jackson3.CloseTrackingFixtures.TrackingInputStream
import com.kazforge.jsonapi.jackson3.CloseTrackingFixtures.TrackingOutputStream
import spock.lang.Shared
import spock.lang.Specification
import tools.jackson.databind.json.JsonMapper
import static java.nio.charset.StandardCharsets.UTF_8

class Jackson3JsonApiRelationshipsSpec extends Specification {

  @Shared
  Jackson3JsonApi jsonApi = JsonApiJackson3.jsonApi(JsonMapper.builder().build())

  @Shared
  DocumentReadContext relationshipContext = DocumentReadContext.of(new ValidationContext(
  DocumentUsage.RESPONSE_OR_OTHER, PrimaryDataContext.RELATIONSHIP, Set.of('ext'), Set.of(),
  Set.of('links', 'rank'), Set.of(), Map.of(), null), PrimaryDataKind.RESOURCE_IDENTIFIER)

  def "relationship reads reject #member on both single and collection identifiers"() {
    given:
    def identifier = """{"type":"people","id":"2","$member":$value}"""
    def stream = new TrackingInputStream("""{"data":[{"type":"people","id":"1"},$identifier]}""".getBytes(UTF_8))

    when:
    jsonApi.relationships().readToOne("""{"data":$identifier}""")

    then:
    def one = thrown(JsonApiDocumentReadException)
    one.category() == CodecFailureCategory.LOCAL_VALIDATION
    one.ruleCode() == ValidationRuleCode.PRIMARY_DATA_CONTEXT_MISMATCH
    one.jsonPointer() == "/data/$member"

    when:
    jsonApi.relationships().readToMany(stream)

    then:
    def many = thrown(JsonApiDocumentReadException)
    many.category() == one.category()
    many.ruleCode() == one.ruleCode()
    many.jsonPointer() == "/data/1/$member"
    !stream.closed

    where:
    member          | value
    'attributes'    | '{"name":"private value"}'
    'relationships' | 'null'
    'links'         | '{}'
  }

  def "relationship role rejects resource-only members even when allowed by profile"() {
    given:
    def json = TestFixtureResources.readCorpusUtf8('negative/relationship-identifier-with-links.json')

    when:
    jsonApi.documents().read(json, relationshipContext)

    then:
    def ex = thrown(JsonApiDocumentReadException)
    ex.category() == CodecFailureCategory.LOCAL_VALIDATION
    ex.ruleCode() == ValidationRuleCode.PRIMARY_DATA_CONTEXT_MISMATCH
    ex.jsonPointer() == '/data/1/links'

    and: "ordinary identifier decoding still ignores links"
    jsonApi.documents().read(json, DocumentReadContext.identifierDefaults()).data().identifiers() ==
        [
          ResourceIdentifier.of('people', '1'),
          ResourceIdentifier.of('people', '2')
        ]
  }

  def "relationship role preserves identifier members and tolerates resource-only members in nested linkage"() {
    given:
    def json = '''{
      "data":{"type":"people","id":"1","lid":"local","meta":{"name":"A"},
              "ext:tag":1,"rank":2,"@note":3,"unknown":4},
      "included":[{"type":"people","id":"1","relationships":{
        "manager":{"data":{"type":"people","id":"2","attributes":{}}}}}]
    }'''

    when:
    def document = jsonApi.documents().read(json, relationshipContext)

    then:
    document.data().identifier() == new ResourceIdentifier('people', '1', 'local',
        Meta.of([name: 'A']), ['ext:tag': 1, rank: 2, '@note': 3])
    document.included()[0].relationships().relationships().manager.data().identifier() ==
        ResourceIdentifier.of('people', '2')
  }

  def "relationship linkage with only lid still requires id"() {
    when:
    jsonApi.relationships().readToOne('{"data":{"type":"people","lid":"local"}}')

    then:
    def ex = thrown(JsonApiDocumentReadException)
    ex.category() == CodecFailureCategory.AGGREGATE_VALIDATION
    ex.ruleCode() == ValidationRuleCode.RESOURCE_ID_REQUIRED
    ex.jsonPointer() == '/data/id'
  }

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
