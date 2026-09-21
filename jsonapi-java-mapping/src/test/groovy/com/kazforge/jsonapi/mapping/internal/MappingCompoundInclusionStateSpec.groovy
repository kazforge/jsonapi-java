package com.kazforge.jsonapi.mapping.internal

import com.kazforge.jsonapi.core.model.Attributes
import com.kazforge.jsonapi.core.model.ResourceIdentifier
import com.kazforge.jsonapi.core.model.ResourceIdentity
import com.kazforge.jsonapi.core.model.ResourceObject
import com.kazforge.jsonapi.diagnostic.MappingDiagnostic
import com.kazforge.jsonapi.representation.RepresentationPolicy
import spock.lang.Specification

class MappingCompoundInclusionStateSpec extends Specification {

  def "recognizes both identity aliases of registered primaries"() {
    given:
    def state = new CompoundInclusionState(RepresentationPolicy.defaults())
    def primary = new ResourceObject(
        'articles', 'a1', 'local-a1', null, null, null, null, Map.of())
    def id = ResourceIdentifier.of('articles', 'a1')
    def lid = ResourceIdentifier.withLid('articles', 'local-a1')

    when:
    state.registerPrimary(primary)

    then:
    state.matchesPrimary(id)
    state.matchesPrimary(lid)
    !state.matchesPrimary(ResourceIdentifier.of('articles', 'other'))
    state.preferredIdentity(id) == ResourceIdentity.ofId('articles', 'a1')
    state.preferredIdentity(lid) == ResourceIdentity.ofLid('articles', 'local-a1')
  }

  def "records only the preferred identity for a fieldset exemption"() {
    given:
    def state = new CompoundInclusionState(RepresentationPolicy.defaults())
    def identifier = new ResourceIdentifier('articles', 'a1', 'local-a1', null, Map.of())

    when:
    state.addLinkageExemption(identifier)

    then:
    state.result().sparseFieldsetLinkageExemptions() ==
        [
          ResourceIdentity.ofId('articles', 'a1')
        ] as Set
  }

  def "preserves first-encounter order and deduplicates equivalent representations"() {
    given:
    def state = new CompoundInclusionState(RepresentationPolicy.defaults())
    def first = includedResource('p1', 'Ada')
    def second = includedResource('p2', 'Bea')

    when:
    state.offerIncluded(first, 'author')
    state.offerIncluded(first, 'reviewer')
    state.offerIncluded(second, 'author')

    then:
    state.result().included() == [first, second]
  }

  def "skips offered resources carrying no identity alias"() {
    given:
    def state = new CompoundInclusionState(RepresentationPolicy.defaults())

    when:
    state.offerIncluded(ResourceObject.ofType('people'), 'empty')

    then:
    state.result().included() == []
  }

  def "reports conflicting representations for an identity alias"() {
    given:
    def state = new CompoundInclusionState(RepresentationPolicy.defaults())
    state.offerIncluded(includedResource('p1', 'Ada'), 'author')

    when:
    state.offerIncluded(includedResource('p1', 'Different'), 'reviewer')

    then:
    def exception = thrown(RuntimeException)
    exception.diagnostic() == MappingDiagnostic.CONFLICTING_INCLUDED_REPRESENTATION
    exception.propertyPath() == null
    exception.message.contains("include path 'reviewer'")
  }

  def "enforces the included-resource count limit"() {
    given:
    def state = new CompoundInclusionState(
        RepresentationPolicy.defaults().withMaxIncludedResources(1))
    state.offerIncluded(includedResource('p1', 'Ada'), 'author')

    when:
    state.offerIncluded(includedResource('p2', 'Bea'), 'reviewer')

    then:
    def exception = thrown(RuntimeException)
    exception.diagnostic() == MappingDiagnostic.INCLUDE_COUNT_EXCEEDED
    exception.propertyPath() == null
    exception.message.contains('maxIncludedResources 1')
  }

  def "returns defensive included and exemption values"() {
    given:
    def state = new CompoundInclusionState(RepresentationPolicy.defaults())
    state.offerIncluded(includedResource('p1', 'Ada'), 'author')
    state.addLinkageExemption(ResourceIdentifier.of('people', 'p1'))

    when:
    def result = state.result()

    then:
    result.included() == [includedResource('p1', 'Ada')]
    result.sparseFieldsetLinkageExemptions() ==
        [
          ResourceIdentity.ofId('people', 'p1')
        ] as Set

    when:
    result.included().add(includedResource('p2', 'Bea'))

    then:
    thrown(UnsupportedOperationException)

    when:
    result.sparseFieldsetLinkageExemptions().add(ResourceIdentity.ofId('people', 'p2'))

    then:
    thrown(UnsupportedOperationException)
  }

  def "rejects null arguments"() {
    when:
    new CompoundInclusionState(null)

    then:
    thrown(NullPointerException)

    when:
    new CompoundInclusionState(RepresentationPolicy.defaults()).registerPrimary(null)

    then:
    thrown(NullPointerException)

    when:
    new CompoundInclusionState(RepresentationPolicy.defaults()).matchesPrimary(null)

    then:
    thrown(NullPointerException)

    when:
    new CompoundInclusionState(RepresentationPolicy.defaults()).preferredIdentity(null)

    then:
    thrown(NullPointerException)

    when:
    new CompoundInclusionState(RepresentationPolicy.defaults()).addLinkageExemption(null)

    then:
    thrown(NullPointerException)

    when:
    new CompoundInclusionState(RepresentationPolicy.defaults()).offerIncluded(null, 'author')

    then:
    thrown(NullPointerException)

    when:
    new CompoundInclusionState(RepresentationPolicy.defaults())
        .offerIncluded(includedResource('p1', 'Ada'), null)

    then:
    thrown(NullPointerException)
  }

  private static ResourceObject includedResource(String id, String name) {
    new ResourceObject(
        'people', id, null, Attributes.ofAttributes([name: name]), null, null, null, Map.of())
  }
}
