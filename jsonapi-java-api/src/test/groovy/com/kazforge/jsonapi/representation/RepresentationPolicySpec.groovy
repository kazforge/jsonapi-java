package com.kazforge.jsonapi.representation

import spock.lang.Specification

class RepresentationPolicySpec extends Specification {

  def "policy defaults preserve no inclusion and unrestricted fields"() {
    when:
    def policy = RepresentationPolicy.defaults()

    then:
    policy.includePolicy() == IncludePolicy.denyAll()
    policy.maxIncludeDepth() == 10
    policy.maxIncludedResources() == 100
    policy.fieldPolicy() == FieldPolicy.allowAll()
  }

  def "negative limits are rejected"() {
    when:
    RepresentationPolicy.defaults().withMaxIncludeDepth(-1)

    then:
    thrown(IllegalArgumentException)

    when:
    RepresentationPolicy.defaults().withMaxIncludedResources(-1)

    then:
    thrown(IllegalArgumentException)
  }

  def "policy derivations preserve independent values"() {
    given:
    def includePolicy = IncludePolicy.allowing(Set.of(RelationshipAllowance.of("articles", "author")))
    def fieldPolicy = FieldPolicy.allowing(Set.of(FieldAllowance.of("articles", "title")))

    when:
    def policy = RepresentationPolicy.defaults()
        .withIncludePolicy(includePolicy)
        .withMaxIncludeDepth(2)
        .withMaxIncludedResources(3)
        .withFieldPolicy(fieldPolicy)

    then:
    policy.includePolicy().is(includePolicy)
    policy.maxIncludeDepth() == 2
    policy.maxIncludedResources() == 3
    policy.fieldPolicy().is(fieldPolicy)
  }
}
