package com.kazforge.jsonapi.mapping

import com.kazforge.jsonapi.core.model.ResourceIdentifier
import spock.lang.Specification

class DomainDataSpec extends Specification {

  def "resource collection is defensively copied and unmodifiable"() {
    given:
    def source = ["x"] as List<Object>

    when:
    def collection = new DomainData.ResourceCollection(source)
    source.add("y")

    then:
    collection.resources() == ["x"]

    when:
    collection.resources().add("z")
    then:
    thrown(UnsupportedOperationException)
  }

  def "domain data preserves explicit null, resource, and identifier primary-data states"() {
    given:
    def resource = "article dto"
    def identifier = ResourceIdentifier.of("articles", "1")
    def resources = new ArrayList<Object>([resource])
    def identifiers = new ArrayList<ResourceIdentifier>([identifier])

    when:
    def collection = new DomainData.ResourceCollection(resources)
    def identifierCollection = new DomainData.IdentifierCollection(identifiers)
    resources.add("another dto")
    identifiers.add(ResourceIdentifier.of("articles", "2"))

    then:
    (Set) DomainData.class.getPermittedSubclasses().toSet() ==
        [
          DomainData.NullData,
          DomainData.SingleResource,
          DomainData.ResourceCollection,
          DomainData.SingleIdentifier,
          DomainData.IdentifierCollection
        ].toSet()
    DomainData.NullData.INSTANCE == new DomainData.NullData()
    new DomainData.SingleResource(resource).resource().is(resource)
    collection.resources() == [resource]
    new DomainData.SingleIdentifier(identifier).identifier() == identifier
    identifierCollection.identifiers() == [identifier]

    when:
    collection.resources().add("nope")

    then:
    thrown(UnsupportedOperationException)
  }

  def "domain data rejects null required payloads and collection members"() {
    when:
    new DomainData.SingleResource(null)

    then:
    thrown(NullPointerException)

    when:
    new DomainData.ResourceCollection([null])

    then:
    thrown(NullPointerException)

    when:
    new DomainData.SingleIdentifier(null)

    then:
    thrown(NullPointerException)

    when:
    new DomainData.IdentifierCollection([null])

    then:
    thrown(NullPointerException)
  }
}
