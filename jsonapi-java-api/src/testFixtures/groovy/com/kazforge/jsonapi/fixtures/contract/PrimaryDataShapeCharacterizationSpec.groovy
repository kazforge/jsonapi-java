package com.kazforge.jsonapi.fixtures.contract

import com.kazforge.jsonapi.api.JsonApi
import com.kazforge.jsonapi.core.model.ResourceIdentifier
import com.kazforge.jsonapi.diagnostic.JsonApiMappingException
import com.kazforge.jsonapi.diagnostic.MappingDiagnostic
import com.kazforge.jsonapi.diagnostic.MappingLocation
import com.kazforge.jsonapi.fixtures.TestFixtureResources
import com.kazforge.jsonapi.fixtures.domainread.FlatArticle
import groovy.json.JsonSlurper
import spock.lang.Specification

/**
 * Level-1 primary-data shape characterization contract observed through {@code JsonApi}: resource
 * and relationship reads reject every reachable mismatched primary-data state with the shared
 * diagnostic, and relationship writes emit a data-only linkage document. Concrete adapter
 * subclasses supply the configured runtime.
 */
abstract class PrimaryDataShapeCharacterizationSpec extends Specification {

  protected abstract JsonApi api()

  def "readOne rejects every reachable mismatched primary-data state"() {
    when:
    api().resources().readOne(json, FlatArticle)

    then:
    def failure = thrown(JsonApiMappingException)
    failure.diagnostic() == MappingDiagnostic.RESOURCE_TYPE_MISMATCH
    failure.resourceClass() == FlatArticle
    failure.location() == MappingLocation.of("data")
    failure.message ==
        "Level-1 read requires single-resource primary data but found " + actual

    where:
    json | actual
    corpus("documents/meta-only.json") | "absent data"
    corpus("documents/null-data.json") | "explicit null data"
    corpus("documents/resource-collection.json") | "resource-collection data"
    '{"data":[]}' | "resource-collection data"
    corpus("documents/errors-document.json") | "an error document"
  }

  def "readMany rejects every reachable mismatched primary-data state"() {
    when:
    api().resources().readMany(json, FlatArticle)

    then:
    def failure = thrown(JsonApiMappingException)
    failure.diagnostic() == MappingDiagnostic.RESOURCE_TYPE_MISMATCH
    failure.resourceClass() == null
    failure.location() == MappingLocation.of("data")
    failure.message ==
        "Level-1 read requires resource-collection primary data but found " + actual

    where:
    json | actual
    corpus("documents/meta-only.json") | "absent data"
    corpus("documents/null-data.json") | "explicit null data"
    corpus("documents/single-resource.json") | "single-resource data"
    corpus("documents/errors-document.json") | "an error document"
  }

  def "readToOne rejects every reachable mismatched primary-data state"() {
    when:
    api().relationships().readToOne(json)

    then:
    def failure = thrown(JsonApiMappingException)
    failure.diagnostic() == MappingDiagnostic.RESOURCE_TYPE_MISMATCH
    failure.resourceClass() == null
    failure.location() == MappingLocation.of("data")
    failure.message ==
        "Level-1 relationship read requires to-one identifier or explicit null primary data but found " +
        actual

    where:
    json | actual
    corpus("documents/meta-only.json") | "absent data"
    corpus("documents/identifier-collection.json") | "identifier-collection data"
    '{"data":[]}' | "identifier-collection data"
    corpus("documents/errors-document.json") | "an error document"
  }

  def "readToMany rejects every reachable mismatched primary-data state"() {
    when:
    api().relationships().readToMany(json)

    then:
    def failure = thrown(JsonApiMappingException)
    failure.diagnostic() == MappingDiagnostic.RESOURCE_TYPE_MISMATCH
    failure.resourceClass() == null
    failure.location() == MappingLocation.of("data")
    failure.message ==
        "Level-1 relationship read requires to-many identifier collection primary data but found " +
        actual

    where:
    json | actual
    corpus("documents/meta-only.json") | "absent data"
    corpus("documents/null-data.json") | "explicit null data"
    corpus("documents/single-identifier.json") | "single-identifier data"
    corpus("documents/errors-document.json") | "an error document"
  }

  def "relationship writes emit a data-only linkage document"() {
    expect:
    parse(api().relationships().writeToOne(ResourceIdentifier.of("people", "p1"))).keySet() ==
        ["data"] as Set
    parse(api().relationships().writeToOne(null)).keySet() == ["data"] as Set
    parse(api().relationships().writeToMany([
      ResourceIdentifier.of("comments", "c1")
    ])).keySet() == ["data"] as Set
    parse(api().relationships().writeToMany(List.of())).keySet() == ["data"] as Set
  }

  private static Map<String, Object> parse(String json) {
    new JsonSlurper().parseText(json) as Map<String, Object>
  }

  private static String corpus(String relativePath) {
    TestFixtureResources.readCorpusUtf8(relativePath)
  }
}
