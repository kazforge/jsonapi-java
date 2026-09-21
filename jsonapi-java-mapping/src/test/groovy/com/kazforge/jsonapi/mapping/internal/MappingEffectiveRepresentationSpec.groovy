package com.kazforge.jsonapi.mapping.internal

import com.kazforge.jsonapi.core.model.ResourceIdentity
import com.kazforge.jsonapi.core.model.ResourceObject
import com.kazforge.jsonapi.representation.RepresentationPolicy
import com.kazforge.jsonapi.representation.RepresentationSelection
import java.util.LinkedHashSet
import spock.lang.Specification

class MappingEffectiveRepresentationSpec extends Specification {

  def "retains the caller selection and policy values"() {
    given:
    def selection = RepresentationSelection.none()
    def policy = RepresentationPolicy.defaults()

    when:
    def effective = new EffectiveRepresentation(selection, policy)

    then:
    effective.selection().is(selection)
    effective.policy().is(policy)
  }

  def "resolves fieldsets by resource type with absent and present-empty distinct"() {
    given:
    def selection = RepresentationSelection.builder()
        .fields('people', 'name')
        .fields('comments')
        .build()
    def effective = new EffectiveRepresentation(selection, RepresentationPolicy.defaults())

    expect:
    effective.fieldsFor('people') == ['name']
    effective.fieldsFor('comments') == []
    effective.fieldsFor('articles') == null
  }

  def "rejects null inputs and resource type lookups"() {
    given:
    def selection = RepresentationSelection.none()
    def policy = RepresentationPolicy.defaults()

    when:
    new EffectiveRepresentation(null, policy)

    then:
    thrown(NullPointerException)

    when:
    new EffectiveRepresentation(selection, null)

    then:
    thrown(NullPointerException)

    when:
    new EffectiveRepresentation(selection, policy).fieldsFor(null)

    then:
    thrown(NullPointerException)
  }

  def "included result defensively copies its exemption identities"() {
    given:
    def resource = ResourceObject.of('people', '1')
    def identities = new LinkedHashSet<ResourceIdentity>([
      ResourceIdentity.ofId('people', '1')
    ])

    when:
    def result = new IncludedResourcesResult([resource], identities)
    identities.add(ResourceIdentity.ofId('people', '2'))

    then:
    result.included() == [resource]
    result.sparseFieldsetLinkageExemptions() ==
        [
          ResourceIdentity.ofId('people', '1')
        ] as Set

    when:
    result.sparseFieldsetLinkageExemptions().add(ResourceIdentity.ofId('people', '3'))

    then:
    thrown(UnsupportedOperationException)

    when:
    new IncludedResourcesResult([], null)

    then:
    thrown(NullPointerException)
  }
}
