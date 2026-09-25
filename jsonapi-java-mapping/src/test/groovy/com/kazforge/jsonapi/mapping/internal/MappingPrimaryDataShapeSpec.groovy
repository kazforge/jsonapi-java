package com.kazforge.jsonapi.mapping.internal

import com.kazforge.jsonapi.core.model.DocumentData
import com.kazforge.jsonapi.core.model.ErrorObject
import com.kazforge.jsonapi.core.model.JsonApiDocument
import com.kazforge.jsonapi.core.model.Meta
import com.kazforge.jsonapi.core.model.ResourceIdentifier
import com.kazforge.jsonapi.core.model.ResourceObject
import com.kazforge.jsonapi.diagnostic.JsonApiMappingException
import com.kazforge.jsonapi.diagnostic.MappingDiagnostic
import com.kazforge.jsonapi.diagnostic.MappingLocation
import spock.lang.Specification

/**
 * Direct behavioral proof for the shared Level-1 primary-data shape policy: acceptance of the
 * required shape, every description row per check, and data-only linkage-document assembly.
 */
class MappingPrimaryDataShapeSpec extends Specification {

  def "accepts single-resource primary data"() {
    given:
    def resource = ResourceObject.of('articles', '1')

    expect:
    PrimaryDataShape.requireSingleResource(singleResource(resource), String).is(resource)
  }

  def "rejects every other primary-data state for a single-resource read"() {
    when:
    PrimaryDataShape.requireSingleResource(document, String)

    then:
    def failure = thrown(JsonApiMappingException)
    failure.diagnostic() == MappingDiagnostic.RESOURCE_TYPE_MISMATCH
    failure.resourceClass() == String
    failure.location() == MappingLocation.of('data')
    failure.message ==
        'Level-1 read requires single-resource primary data but found ' + actual

    where:
    document | actual
    absent() | 'absent data'
    explicitNull() | 'explicit null data'
    resourceCollection() | 'resource-collection data'
    emptyResourceCollection() | 'resource-collection data'
    singleIdentifier() | 'single-identifier data'
    identifierCollection() | 'identifier-collection data'
    errorDocument() | 'an error document'
  }

  def "accepts resource-collection primary data including empty"() {
    given:
    def resources = [
      ResourceObject.of('articles', '1')
    ]

    expect:
    PrimaryDataShape.requireResourceCollection(resourceCollection(resources)) == resources
    PrimaryDataShape.requireResourceCollection(emptyResourceCollection()) == []
  }

  def "rejects every other primary-data state for a resource-collection read"() {
    when:
    PrimaryDataShape.requireResourceCollection(document)

    then:
    def failure = thrown(JsonApiMappingException)
    failure.diagnostic() == MappingDiagnostic.RESOURCE_TYPE_MISMATCH
    failure.resourceClass() == null
    failure.location() == MappingLocation.of('data')
    failure.message ==
        'Level-1 read requires resource-collection primary data but found ' + actual

    where:
    document | actual
    absent() | 'absent data'
    explicitNull() | 'explicit null data'
    singleResource() | 'single-resource data'
    singleIdentifier() | 'single-identifier data'
    identifierCollection() | 'identifier-collection data'
    errorDocument() | 'an error document'
  }

  def "accepts to-one identifier and explicit null primary data"() {
    given:
    def identifier = ResourceIdentifier.of('articles', '1')

    expect:
    PrimaryDataShape.requireToOne(singleIdentifier(identifier)) == identifier
    PrimaryDataShape.requireToOne(explicitNull()) == null
  }

  def "rejects every other primary-data state for a to-one relationship read"() {
    when:
    PrimaryDataShape.requireToOne(document)

    then:
    def failure = thrown(JsonApiMappingException)
    failure.diagnostic() == MappingDiagnostic.RESOURCE_TYPE_MISMATCH
    failure.resourceClass() == null
    failure.location() == MappingLocation.of('data')
    failure.message ==
        'Level-1 relationship read requires to-one identifier or explicit null primary data but found ' +
        actual

    where:
    document | actual
    absent() | 'absent data'
    singleResource() | 'single-resource data'
    resourceCollection() | 'resource-collection data'
    identifierCollection() | 'identifier-collection data'
    emptyIdentifierCollection() | 'identifier-collection data'
    errorDocument() | 'an error document'
  }

  def "accepts to-many identifier-collection primary data including empty"() {
    given:
    def identifiers = [
      ResourceIdentifier.of('articles', '1')
    ]

    expect:
    PrimaryDataShape.requireToMany(identifierCollection(identifiers)) == identifiers
    PrimaryDataShape.requireToMany(emptyIdentifierCollection()) == []
  }

  def "rejects every other primary-data state for a to-many relationship read"() {
    when:
    PrimaryDataShape.requireToMany(document)

    then:
    def failure = thrown(JsonApiMappingException)
    failure.diagnostic() == MappingDiagnostic.RESOURCE_TYPE_MISMATCH
    failure.resourceClass() == null
    failure.location() == MappingLocation.of('data')
    failure.message ==
        'Level-1 relationship read requires to-many identifier collection primary data but found ' +
        actual

    where:
    document | actual
    absent() | 'absent data'
    explicitNull() | 'explicit null data'
    singleResource() | 'single-resource data'
    resourceCollection() | 'resource-collection data'
    singleIdentifier() | 'single-identifier data'
    errorDocument() | 'an error document'
  }

  def "assembles a data-only to-one linkage document"() {
    given:
    def identifier = ResourceIdentifier.of('people', 'p1')

    expect:
    PrimaryDataShape.linkageDocument(null) ==
        JsonApiDocument.withData(DocumentData.NullData.INSTANCE)
    PrimaryDataShape.linkageDocument(identifier) ==
        JsonApiDocument.withData(new DocumentData.SingleIdentifier(identifier))
  }

  def "assembles a data-only to-many linkage document"() {
    given:
    def identifiers = [
      ResourceIdentifier.of('comments', 'c1')
    ]

    expect:
    PrimaryDataShape.linkageCollectionDocument(identifiers) ==
        JsonApiDocument.withData(new DocumentData.IdentifierCollection(identifiers))
    PrimaryDataShape.linkageCollectionDocument(List.of()) ==
        JsonApiDocument.withData(new DocumentData.IdentifierCollection(List.of()))
  }

  private static JsonApiDocument absent() {
    JsonApiDocument.withMeta(Meta.of([a: 1]))
  }

  private static JsonApiDocument explicitNull() {
    JsonApiDocument.withData(DocumentData.NullData.INSTANCE)
  }

  private static JsonApiDocument singleResource(ResourceObject resource = ResourceObject.of('articles', '1')) {
    JsonApiDocument.withData(new DocumentData.SingleResource(resource))
  }

  private static JsonApiDocument resourceCollection(
      List<ResourceObject> resources = [
        ResourceObject.of('articles', '1')
      ]) {
    JsonApiDocument.withData(new DocumentData.ResourceCollection(resources))
  }

  private static JsonApiDocument emptyResourceCollection() {
    JsonApiDocument.withData(new DocumentData.ResourceCollection(List.of()))
  }

  private static JsonApiDocument singleIdentifier(
      ResourceIdentifier identifier = ResourceIdentifier.of('articles', '1')) {
    JsonApiDocument.withData(new DocumentData.SingleIdentifier(identifier))
  }

  private static JsonApiDocument identifierCollection(
      List<ResourceIdentifier> identifiers = [
        ResourceIdentifier.of('articles', '1')
      ]) {
    JsonApiDocument.withData(new DocumentData.IdentifierCollection(identifiers))
  }

  private static JsonApiDocument emptyIdentifierCollection() {
    JsonApiDocument.withData(new DocumentData.IdentifierCollection(List.of()))
  }

  private static JsonApiDocument errorDocument() {
    JsonApiDocument.withError(ErrorObject.ofTitle('boom'))
  }
}
