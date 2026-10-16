package com.kazforge.jsonapi.jackson3.contract

import com.kazforge.jsonapi.api.JsonApi
import com.kazforge.jsonapi.core.validation.ValidationRuleCode
import com.kazforge.jsonapi.diagnostic.CodecFailureCategory
import com.kazforge.jsonapi.diagnostic.JsonApiDocumentReadException
import com.kazforge.jsonapi.diagnostic.JsonApiMappingException
import com.kazforge.jsonapi.diagnostic.MappingDiagnostic
import com.kazforge.jsonapi.fixtures.domainread.FlatArticle
import com.kazforge.jsonapi.fixtures.generic.ParameterizedBindingFixtures.GenericValue
import com.kazforge.jsonapi.fixtures.localid.LocalIdentityArticle
import com.kazforge.jsonapi.jackson3.CloseTrackingFixtures.TrackingInputStream
import com.kazforge.jsonapi.jackson3.JsonApiJackson3
import spock.lang.Specification
import tools.jackson.core.type.TypeReference
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.PropertyNamingStrategies

import java.lang.reflect.Type
import java.nio.charset.StandardCharsets

class Jackson3CreateReadCharacterizationSpec extends Specification {

  private final JsonApi api = JsonApiJackson3.jsonApi(JsonMapper.builder().build())

  def "create reads use the configured mapper property names"() {
    given:
    JsonApi configured = JsonApiJackson3.jsonApi(JsonMapper.builder()
        .propertyNamingStrategy(PropertyNamingStrategies.UPPER_CAMEL_CASE).build())

    when:
    def result = configured.resources().readCreate(
        '{"data":{"type":"articles","attributes":{"Title":"Draft"}}}', LocalIdentityArticle)

    then:
    result.title() == "Draft"
  }

  def "create reads keep identity roles independent across all overloads"() {
    given:
    def json = '{"data":{"type":"articles",' + identity + '"attributes":{"title":"Draft"}}}'
    def input = new TrackingInputStream(json.getBytes(StandardCharsets.UTF_8))
    def typeInput = new TrackingInputStream(json.getBytes(StandardCharsets.UTF_8))
    Type target = LocalIdentityArticle
    def expected = new LocalIdentityArticle(id, lid, "Draft")

    expect:
    api.resources().readCreate(json, LocalIdentityArticle) == expected
    api.resources().readCreate(input, LocalIdentityArticle) == expected
    api.resources().readCreate(json, target) == expected
    api.resources().readCreate(typeInput, target) == expected
    !input.closed
    !typeInput.closed

    where:
    identity | id | lid
    '' | null | null
    '"id":"1",' | "1" | null
    '"lid":"draft-1",' | null | "draft-1"
    '"id":"1","lid":"draft-1",' | "1" | "draft-1"
  }

  def "create generic reads preserve nested attribute type"() {
    given:
    def target = new TypeReference<GenericValue<List<Integer>>>() {}.getType()
    def json = '{"data":{"type":"things","attributes":{"value":[1,2]}}}'
    def input = new TrackingInputStream(json.getBytes(StandardCharsets.UTF_8))

    expect:
    api.resources().readCreate(json, target) == new GenericValue(null, [1, 2])
    api.resources().readCreate(input, target) == new GenericValue(null, [1, 2])
    !input.closed
  }

  def "create reads accept matching self linkage and ordinary null or empty linkage"() {
    given:
    def json = '{"data":{"type":"articles","lid":"draft-1","relationships":{' +
        '"author":{"data":' + author + '},"comments":{"data":' + comments + '}}}}'

    when:
    def result = api.resources().readCreate(json, FlatArticle)

    then:
    result.id() == null
    result.author()?.lid() == expectedLid
    result.comments().size() == count

    where:
    author | comments | expectedLid | count
    '{"type":"articles","lid":"draft-1"}' | '[{"type":"articles","lid":"draft-1"}]' | "draft-1" | 1
    'null' | '[]' | null | 0
  }

  def "create validation reports stable diagnostics before binding for every overload"(String overload, List<Object> invalid) {
    given:
    String json = invalid[0] as String
    ValidationRuleCode code = invalid[1] as ValidationRuleCode
    String pointer = invalid[2] as String
    def input = new TrackingInputStream(json.getBytes(StandardCharsets.UTF_8))
    Type target = LocalIdentityArticle

    when:
    switch (overload) {
      case "text-class": api.resources().readCreate(json, LocalIdentityArticle); break
      case "stream-class": api.resources().readCreate(input, LocalIdentityArticle); break
      case "text-type": api.resources().readCreate(json, target); break
      case "stream-type": api.resources().readCreate(input, target); break
    }

    then:
    def failure = thrown(JsonApiDocumentReadException)
    failure.category() == CodecFailureCategory.AGGREGATE_VALIDATION
    failure.ruleCode() == code
    failure.jsonPointer() == pointer
    !input.closed

    where:
    [overload, invalid] << [
      [
        "text-class",
        "stream-class",
        "text-type",
        "stream-type"
      ],
      [
        [
          '{"meta":{}}',
          ValidationRuleCode.CREATE_REQUIRES_SINGLE_RESOURCE,
          '/data'
        ],
        [
          '{"errors":[{"title":"Failed"}]}',
          ValidationRuleCode.CREATE_REQUIRES_SINGLE_RESOURCE,
          '/data'
        ],
        [
          '{"data":null}',
          ValidationRuleCode.CREATE_REQUIRES_SINGLE_RESOURCE,
          '/data'
        ],
        [
          '{"data":[]}',
          ValidationRuleCode.CREATE_REQUIRES_SINGLE_RESOURCE,
          '/data'
        ],
        [
          '{"data":[{"type":"articles","id":"1"}]}',
          ValidationRuleCode.CREATE_REQUIRES_SINGLE_RESOURCE,
          '/data'
        ],
        [
          '{"data":{"type":"articles","relationships":{"author":{"meta":{}}}}}',
          ValidationRuleCode.RELATIONSHIP_DATA_REQUIRED,
          '/data/relationships/author/data'
        ],
        [
          '{"data":{"type":"articles","lid":"a","relationships":{"author":{"data":{"type":"articles","lid":"b"}}}}}',
          ValidationRuleCode.RESOURCE_ID_REQUIRED,
          '/data/relationships/author/data/id'
        ],
        [
          '{"data":{"type":"articles","lid":"a","relationships":{"author":{"data":{"type":"people","lid":"a"}}}}}',
          ValidationRuleCode.RESOURCE_ID_REQUIRED,
          '/data/relationships/author/data/id'
        ],
        [
          '{"data":{"type":"articles","relationships":{"author":{"data":{"type":"articles","lid":"a"}}}}}',
          ValidationRuleCode.RESOURCE_ID_REQUIRED,
          '/data/relationships/author/data/id'
        ],
        [
          '{"data":{"type":"articles"},"included":[{"type":"people","lid":"a"}]}',
          ValidationRuleCode.RESOURCE_ID_REQUIRED,
          '/included/0/id'
        ],
        [
          '{"data":{"type":"articles"},"included":[{"type":"people","id":"1"}]}',
          ValidationRuleCode.FULL_LINKAGE_VIOLATION,
          '/included'
        ]
      ]
    ].combinations()
  }

  def "included resources validate without DTO hydration"() {
    given:
    def json = '{"data":{"type":"articles","relationships":{"author":{"data":{"type":"people","id":"p1"}}}},' +
        '"included":[{"type":"people","id":"p1","attributes":{"name":"Ada"},"relationships":{"avatar":{"links":{"related":"/avatar"}}}}]}'

    when:
    def result = api.resources().readCreate(json, FlatArticle)

    then:
    result.author().id() == "p1"
  }

  def "response reads still require primary ids"() {
    when:
    api.resources().readOne('{"data":{"type":"articles","lid":"a"}}', LocalIdentityArticle)

    then:
    def failure = thrown(JsonApiDocumentReadException)
    failure.category() == CodecFailureCategory.AGGREGATE_VALIDATION
    failure.ruleCode() == ValidationRuleCode.RESOURCE_ID_REQUIRED
    failure.jsonPointer() == '/data/id'
  }

  def "create reads retain mapping failures"() {
    when:
    api.resources().readCreate('{"data":{"type":"people"}}', LocalIdentityArticle)

    then:
    def failure = thrown(JsonApiMappingException)
    failure.diagnostic() == MappingDiagnostic.RESOURCE_TYPE_MISMATCH
    failure.location().pointer() == '/type'
  }

  def "malformed create input preserves diagnostics and caller stream ownership"() {
    given:
    def input = new TrackingInputStream('{'.getBytes(StandardCharsets.UTF_8))

    when:
    api.resources().readCreate(input, LocalIdentityArticle)

    then:
    def failure = thrown(JsonApiDocumentReadException)
    failure.category() == CodecFailureCategory.MALFORMED_JSON
    !input.closed
  }
}
