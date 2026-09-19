package com.kazforge.jsonapi.jackson2

import com.kazforge.jsonapi.core.model.DocumentData
import com.kazforge.jsonapi.core.model.JsonApiDocument
import com.kazforge.jsonapi.core.model.ResourceObject
import com.kazforge.jsonapi.fixtures.domainwrite.BlogWithJsonProperty
import com.kazforge.jsonapi.jackson.representation.IncludePolicy
import com.kazforge.jsonapi.jackson.representation.RepresentationPolicy
import com.kazforge.jsonapi.jackson.representation.RepresentationSelection
import com.kazforge.jsonapi.mapping.contract.MappingBackendContract
import com.kazforge.jsonapi.mapping.contract.MappingContractAdapter
import com.kazforge.jsonapi.mapping.contract.MappingContractResult
import com.kazforge.jsonapi.mapping.internal.GenericDomainResourceWriter
import com.kazforge.jsonapi.mapping.internal.MappingRepresentation
import com.kazforge.jsonapi.jackson2.internal.Jackson2PrototypeMappingBackend
import spock.lang.Specification
import com.fasterxml.jackson.databind.json.JsonMapper

class Jackson2MappingBackendContractSpec extends Specification {

  def "existing public mapper satisfies the neutral mapping backend contract"() {
    given:
    def mapper = JsonApiJackson2.resourceMapper(JsonMapper.builder().build())
    def adapter = new MappingContractAdapter() {
          @Override
          ResourceObject toResource(Object resource) {
            mapper.toResource(resource)
          }

          @Override
          JsonApiDocument toDocument(Object resource, List<String> includePaths) {
            def selection = RepresentationSelection.builder()
            includePaths.each { selection.include(it) }
            mapper.toDocument(
                resource,
                null,
                selection.build(),
                RepresentationPolicy.defaults().withIncludePolicy(IncludePolicy.allowAll()))
          }

          @Override
          MappingContractResult toMappedDocument(
              Object resource,
              List<String> includePaths,
              Map<String, List<String>> fieldsets) {
            def selection = RepresentationSelection.builder()
            includePaths.each { selection.include(it) }
            fieldsets.each { type, fields -> selection.fields(type, fields) }
            def mapped = mapper.toMappedDocument(
                resource,
                null,
                selection.build(),
                RepresentationPolicy.defaults().withIncludePolicy(IncludePolicy.allowAll()))
            new MappingContractResult(
                mapped.document(),
                mapped.sparseFieldsetLinkageExemptions())
          }
        }

    when:
    MappingBackendContract.verify(adapter)

    then:
    noExceptionThrown()
  }

  def "shared writer keeps JSON library external names separate from JSON API identity members"() {
    given:
    def writer = new GenericDomainResourceWriter<>(
        new Jackson2PrototypeMappingBackend(JsonMapper.builder().build()))

    when:
    def resource = writer.toResource(new BlogWithJsonProperty("b1", "Hello"))

    then:
    resource.type() == "blogs"
    resource.id() == "b1"
    resource.attributes().attributes() == [blog_title: "Hello"]
  }

  def "shared mapping-domain writer satisfies the same contract"() {
    given:
    def base = JsonMapper.builder().build()
    def writer = new GenericDomainResourceWriter<>(new Jackson2PrototypeMappingBackend(base))
    def adapter = new MappingContractAdapter() {
          @Override
          ResourceObject toResource(Object resource) {
            writer.toResource(resource)
          }

          @Override
          JsonApiDocument toDocument(Object resource, List<String> includePaths) {
            def type = writer.inferredType(resource)
            def primary = writer.toResource(resource, type)
            def selection = RepresentationSelection.builder()
            includePaths.each { selection.include(it) }
            def representation = new MappingRepresentation(
                selection.build(),
                RepresentationPolicy.defaults().withIncludePolicy(IncludePolicy.allowAll()))
            def included = writer.collectIncluded(resource, type, primary, representation).included()
            new JsonApiDocument(
                new DocumentData.SingleResource(primary),
                null,
                null,
                null,
                null,
                included,
                [:])
          }

          @Override
          MappingContractResult toMappedDocument(
              Object resource,
              List<String> includePaths,
              Map<String, List<String>> fieldsets) {
            def type = writer.inferredType(resource)
            def selection = RepresentationSelection.builder()
            includePaths.each { selection.include(it) }
            fieldsets.each { resourceType, fields -> selection.fields(resourceType, fields) }
            def representation = new MappingRepresentation(
                selection.build(),
                RepresentationPolicy.defaults().withIncludePolicy(IncludePolicy.allowAll()))
            def primary = writer.toResource(resource, type, representation)
            def includedResult = writer.collectIncluded(resource, type, primary, representation)
            def document = new JsonApiDocument(
                new DocumentData.SingleResource(primary),
                null,
                null,
                null,
                null,
                includedResult.included(),
                [:])
            new MappingContractResult(
                document,
                includedResult.sparseFieldsetLinkageExemptions())
          }
        }

    when:
    MappingBackendContract.verify(adapter)

    then:
    noExceptionThrown()
  }
}
