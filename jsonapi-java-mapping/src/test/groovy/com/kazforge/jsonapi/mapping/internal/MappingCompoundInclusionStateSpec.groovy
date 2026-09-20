package com.kazforge.jsonapi.mapping.internal

import com.kazforge.jsonapi.core.model.Attributes
import com.kazforge.jsonapi.core.model.ResourceIdentifier
import com.kazforge.jsonapi.core.model.ResourceIdentity
import com.kazforge.jsonapi.core.model.ResourceObject
import com.kazforge.jsonapi.jackson.diagnostic.JsonApiMappingException
import com.kazforge.jsonapi.jackson.diagnostic.MappingDiagnostic
import com.kazforge.jsonapi.jackson.representation.RepresentationPolicy
import spock.lang.Specification

class MappingCompoundInclusionStateSpec extends Specification {

  def "primary matching recognizes both id and lid aliases"() {
    given:
    def state = new MappingCompoundInclusionState(RepresentationPolicy.defaults())
    state.registerPrimary(new ResourceObject("articles", "a1", "local-a1", null, null, null, null, [:]))

    expect:
    state.matchesPrimary(ResourceIdentifier.of("articles", "a1"))
    state.matchesPrimary(new ResourceIdentifier("articles", null, "local-a1", null, [:]))
    !state.matchesPrimary(ResourceIdentifier.of("articles", "a2"))
  }

  def "preferred identity chooses id before lid"() {
    given:
    def state = new MappingCompoundInclusionState(RepresentationPolicy.defaults())

    expect:
    state.preferredIdentity(
        new ResourceIdentifier("articles", "a1", "local-a1", null, [:])) ==
        ResourceIdentity.ofId("articles", "a1")
    state.preferredIdentity(
        new ResourceIdentifier("articles", null, "local-a1", null, [:])) ==
        ResourceIdentity.ofLid("articles", "local-a1")
  }

  def "linkage exemption records preferred identity"() {
    given:
    def state = new MappingCompoundInclusionState(RepresentationPolicy.defaults())

    when:
    state.addLinkageExemption(ResourceIdentifier.of("people", "p1"))

    then:
    state.result().sparseFieldsetLinkageExemptions() ==
        [
          ResourceIdentity.ofId("people", "p1")
        ] as Set
  }

  def "identityless included resource is ignored"() {
    given:
    def state = new MappingCompoundInclusionState(RepresentationPolicy.defaults())
    def resource = new ResourceObject("people", null, null, null, null, null, null, [:])

    when:
    state.offerIncluded(resource, "author")

    then:
    state.result().included().isEmpty()
  }

  def "same included representation deduplicates across id and lid aliases"() {
    given:
    def state = new MappingCompoundInclusionState(RepresentationPolicy.defaults())
    def resource =
        new ResourceObject("people", "p1", "local-p1", null, null, null, null, [:])

    when:
    state.offerIncluded(resource, "author")
    state.offerIncluded(resource, "editor")

    then:
    state.result().included() == [resource]
  }

  def "conflicting included representation for shared identity fails"() {
    given:
    def state = new MappingCompoundInclusionState(RepresentationPolicy.defaults())
    def alice = new ResourceObject(
        "people",
        "p1",
        null,
        Attributes.ofAttributes([name: "Alice"]),
        null,
        null,
        null,
        [:])
    def bob = new ResourceObject(
        "people",
        "p1",
        null,
        Attributes.ofAttributes([name: "Bob"]),
        null,
        null,
        null,
        [:])
    state.offerIncluded(alice, "author")

    when:
    state.offerIncluded(bob, "editor")

    then:
    def ex = thrown(JsonApiMappingException)
    ex.diagnostic() == MappingDiagnostic.CONFLICTING_INCLUDED_REPRESENTATION
  }
}
