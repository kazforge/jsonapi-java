package com.kazforge.jsonapi.jackson3

import com.kazforge.jsonapi.core.model.ResourceIdentifier
import com.kazforge.jsonapi.jackson.diagnostic.JsonApiMappingException
import com.kazforge.jsonapi.jackson.diagnostic.MappingDiagnostic
import com.kazforge.jsonapi.jackson3.CloseTrackingFixtures.TrackingInputStream
import com.kazforge.jsonapi.jackson3.CloseTrackingFixtures.TrackingOutputStream
import spock.lang.Shared
import spock.lang.Specification
import tools.jackson.databind.json.JsonMapper

class Jackson3JsonApiRelationshipsSpec extends Specification {

  @Shared
  Jackson3JsonApi jsonApi = JsonApiJackson3.jsonApi(JsonMapper.builder().build())

  def "round-trips a to-one linkage document"() {
    given:
    def identifier = ResourceIdentifier.of("people", "p1")

    when:
    def json = jsonApi.relationships().writeToOne(identifier)

    then:
    jsonApi.relationships().readToOne(json) == identifier
  }

  def "explicit null to-one linkage round-trips as null"() {
    when:
    def json = jsonApi.relationships().writeToOne(null)

    then:
    json.contains('"data":null')
    jsonApi.relationships().readToOne(json) == null
  }

  def "configured resource version does not affect linkage writes"() {
    given:
    def runtime = JsonApiJackson3.builder(JsonMapper.builder().build())
        .jsonApiVersion("1.1")
        .build()

    when:
    def json = runtime.relationships().writeToOne(ResourceIdentifier.of("people", "p1"))

    then:
    !json.contains('"jsonapi"')
  }

  def "round-trips a to-many linkage document including the empty collection"() {
    given:
    def identifiers = [
      ResourceIdentifier.of("comments", "c1"),
      ResourceIdentifier.of("comments", "c2")
    ]

    when:
    def json = jsonApi.relationships().writeToMany(identifiers)
    def emptyJson = jsonApi.relationships().writeToMany(List.of())

    then:
    jsonApi.relationships().readToMany(json) == identifiers
    jsonApi.relationships().readToMany(emptyJson) == []
  }

  def "to-one reads never accept collections"() {
    given:
    def json = jsonApi.relationships().writeToMany([
      ResourceIdentifier.of("comments", "c1")
    ])

    when:
    jsonApi.relationships().readToOne(json)

    then:
    def ex = thrown(JsonApiMappingException)
    ex.diagnostic() == MappingDiagnostic.RESOURCE_TYPE_MISMATCH
    ex.propertyPath() == "/data"
  }

  def "to-many reads never accept one identifier or null"() {
    given:
    def single = jsonApi.relationships().writeToOne(ResourceIdentifier.of("people", "p1"))
    def explicitNull = jsonApi.relationships().writeToOne(null)

    when:
    jsonApi.relationships().readToMany(single)

    then:
    thrown(JsonApiMappingException)

    when:
    jsonApi.relationships().readToMany(explicitNull)

    then:
    thrown(JsonApiMappingException)
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

  def "linkage reads reject error documents without coercion"() {
    given:
    def errors = '{"errors":[{"status":"500","title":"boom"}]}'

    when:
    jsonApi.relationships().readToOne(errors)

    then:
    thrown(JsonApiMappingException)

    when:
    jsonApi.relationships().readToOne('{"meta":{"a":1}}')

    then:
    thrown(JsonApiMappingException)

    when:
    jsonApi.relationships().readToMany(errors)

    then:
    def ex = thrown(JsonApiMappingException)
    ex.diagnostic() == MappingDiagnostic.RESOURCE_TYPE_MISMATCH
    ex.propertyPath() == "/data"
  }
}
