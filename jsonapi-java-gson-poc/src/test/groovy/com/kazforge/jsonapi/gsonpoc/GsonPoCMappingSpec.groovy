package com.kazforge.jsonapi.gsonpoc

import com.google.gson.GsonBuilder
import com.kazforge.jsonapi.core.model.Attributes
import com.kazforge.jsonapi.core.model.JsonApiDocument
import com.kazforge.jsonapi.core.model.ResourceObject
import com.kazforge.jsonapi.mapping.contract.BindingBackendContract
import com.kazforge.jsonapi.mapping.contract.BindingContractAdapter
import com.kazforge.jsonapi.mapping.contract.MappingBackendContract
import com.kazforge.jsonapi.mapping.contract.MappingContractAdapter
import com.kazforge.jsonapi.mapping.contract.MappingContractFixtures
import com.kazforge.jsonapi.mapping.contract.MappingContractResult
import com.kazforge.jsonapi.mapping.internal.GenericDomainResourceBinder
import spock.lang.Specification

class GsonPoCMappingSpec extends Specification {

  def "Gson PoC satisfies the representative neutral mapping contract"() {
    given:
    def mapper = new GsonPoCResourceMapper(new GsonBuilder().create())
    def adapter = new MappingContractAdapter() {
          @Override
          ResourceObject toResource(Object resource) {
            mapper.toResource(resource)
          }

          @Override
          JsonApiDocument toDocument(Object resource, List<String> includePaths) {
            mapper.toDocument(resource, includePaths)
          }

          @Override
          MappingContractResult toMappedDocument(
              Object resource,
              List<String> includePaths,
              Map<String, List<String>> fieldsets) {
            mapper.toMappedDocument(resource, includePaths, fieldsets)
          }
        }

    when:
    MappingBackendContract.verify(adapter)

    then:
    noExceptionThrown()
  }

  def "Gson PoC satisfies the representative neutral binding contract"() {
    given:
    def binder = new GenericDomainResourceBinder<>(
        new GsonPrototypeBindingBackend(new GsonBuilder().create()))
    def adapter = new BindingContractAdapter() {
          @Override
          def <T> T fromResource(ResourceObject resource, Class<T> targetClass) {
            binder.fromResource(resource, targetClass)
          }
        }

    when:
    BindingBackendContract.verify(adapter)

    then:
    noExceptionThrown()
  }

  def "Gson tree codec can round-trip the representative mapped document"() {
    given:
    def gson = new GsonBuilder().create()
    def mapper = new GsonPoCResourceMapper(gson)
    def codec = new GsonPoCCodec(gson)
    def article = new MappingContractFixtures.Article(
        "a1",
        "Hello",
        new MappingContractFixtures.Person("p1", "Alice"),
        [
          new MappingContractFixtures.Comment("c1", "First"),
          new MappingContractFixtures.Comment("c2", "Second")
        ])

    and:
    def document = mapper.toDocument(article, ["author", "comments"])

    expect:
    codec.read(codec.write(document)) == document
  }

  def "Gson-specific SerializedName remains a backend concern"() {
    given:
    def mapper = new GsonPoCResourceMapper(new GsonBuilder().create())

    when:
    def resource = mapper.toResource(new GsonNamingFixture("a1", "Hello"))

    then:
    resource.type() == "named-articles"
    resource.id() == "a1"
    resource.attributes() == Attributes.ofAttributes([headline: "Hello"])
  }
}
