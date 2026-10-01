package com.kazforge.jsonapi.mapping

import com.kazforge.jsonapi.core.model.ResourceIdentity
import spock.lang.Specification

class IncludedResourcesSpec extends Specification {

  def "of preserves wire order and resolves identities through declared positions"() {
    given:
    def dto = "dto"
    def included =
        IncludedResources.of(
        ["x", dto] as List<Object>,
        [
          [
            ResourceIdentity.ofId("people", "9"),
            ResourceIdentity.ofLid("people", "9")
          ] as Set<ResourceIdentity>,
          [] as Set<ResourceIdentity>
        ] as List<Set<ResourceIdentity>>)

    expect:
    included.resources() == ["x", "dto"]
    included.find(ResourceIdentity.ofId("people", "9")).get() == "x"
    included.find(ResourceIdentity.ofLid("people", "9")).get() == "x"
    included.find(ResourceIdentity.ofId("people", "99")).isEmpty()
  }

  def "identity resolves only to the position that declared it"() {
    given:
    def first = "first"
    def second = "second"
    def included =
        IncludedResources.of(
        [first, second] as List<Object>,
        [
          [
            ResourceIdentity.ofId("people", "9")
          ] as Set<ResourceIdentity>,
          [
            ResourceIdentity.ofId("people", "2")
          ] as Set<ResourceIdentity>
        ] as List<Set<ResourceIdentity>>)

    expect:
    included.find(ResourceIdentity.ofId("people", "9")).get() == first
    included.find(ResourceIdentity.ofId("people", "2")).get() == second
    // an identity never declared anywhere resolves empty, never a non-declaring position
    included.find(ResourceIdentity.ofId("people", "99")).isEmpty()
  }

  def "of rejects identity declarations that do not match the resource count"() {
    when:
    IncludedResources.of(
        ["x"] as List<Object>,
        [
          [
            ResourceIdentity.ofId("people", "9")
          ] as Set<ResourceIdentity>,
          [] as Set<ResourceIdentity>
        ] as List<Set<ResourceIdentity>>)

    then:
    thrown(IllegalArgumentException)

    when:
    IncludedResources.of(
        ["x", "y"] as List<Object>,
        [
          [
            ResourceIdentity.ofId("people", "9")
          ] as Set<ResourceIdentity>
        ])

    then:
    thrown(IllegalArgumentException)
  }

  def "of rejects the same identity declared for more than one position"() {
    when:
    IncludedResources.of(
        ["x", "y"] as List<Object>,
        [
          [
            ResourceIdentity.ofId("people", "9")
          ] as Set<ResourceIdentity>,
          [
            ResourceIdentity.ofId("people", "9")
          ] as Set<ResourceIdentity>
        ] as List<Set<ResourceIdentity>>)

    then:
    thrown(IllegalArgumentException)
  }

  def "of defensively copies construction sources"() {
    given:
    def sourceList = ["x"] as List<Object>
    def mutableIdentities = new LinkedHashSet<ResourceIdentity>([
      ResourceIdentity.ofId("people", "9")
    ])
    List<Set<ResourceIdentity>> sourceIdentities = [mutableIdentities]

    when:
    def included = IncludedResources.of(sourceList, sourceIdentities)
    sourceList.add("y")
    mutableIdentities.add(ResourceIdentity.ofId("people", "99"))

    then:
    included.resources() == ["x"]
    included.find(ResourceIdentity.ofId("people", "9")).get() == "x"
    included.find(ResourceIdentity.ofId("people", "99")).isEmpty()

    when:
    included.resources().add("z")
    then:
    thrown(UnsupportedOperationException)
  }
}
