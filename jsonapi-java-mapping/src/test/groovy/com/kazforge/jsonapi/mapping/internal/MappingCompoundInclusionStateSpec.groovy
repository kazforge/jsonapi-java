package com.kazforge.jsonapi.mapping.internal

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

  def "preferred identity chooses id before lid and handles identityless identifiers"() {
    given:
    def state = new MappingCompoundInclusionState(RepresentationPolicy.defaults())

    expect:
    state.preferredIdentity(
        new ResourceIdentifier("articles", "a1", "local-a1", null, [:])) ==
        ResourceIdentity.ofId("articles", "a1")
    state.preferredIdentity(
        new ResourceIdentifier("articles", null, "local-a1", null, [:])) ==
        ResourceIdentity.ofLid("articles", "local-a1")
    state.preferredIdentity(
        new ResourceIdentifier("articles", null, null, null, [:])) == null
  }

  def "linkage exemption records only identifiable resources"() {
    given:
    def state = new MappingCompoundInclusionState(RepresentationPolicy.defaults())

    when:
    state.addLinkageExemption(ResourceIdentifier.of("people", "p1"))
    state.addLinkageExemption(new ResourceIdentifier("people", null, null, null, [:]))

    then:
    state.result().sparseFieldsetLinkageExemptions() ==
        [ResourceIdentity.ofId("people", "p1")] as Set
  }

  def "identityless included resource is ignored"() {
    given:
    def state = new MappingCompoundInclusionState(RepresentationPolicy.defaults())

    when:
    state.offerIncluded(
        new ResourceObject("people", null, null, null, null, null, null, [:]),
        "author")

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
    state.offerIncluded(
        new ResourceObject(
            "people",
            "p1",
            null,
            com.kazforge.jsonapi.core.model.Attributes.ofAttributes([name: "Alice"]),
            null,
            null,
            null,
            [:]),
        "author")

    when:
    state.offerIncluded(
        new ResourceObject(
            "people",
            "p1",
            null,
            com.kazforge.jsonapi.core.model.Attributes.ofAttributes([name: "Bob"]),
            null,
            null,
            null,
            [:]),
        "editor")

    then:
    def ex = thrown(JsonApiMappingException)
    ex.diagnostic() == MappingDiagnostic.CONFLICTING_INCLUDED_REPRESENTATION
  }
}
