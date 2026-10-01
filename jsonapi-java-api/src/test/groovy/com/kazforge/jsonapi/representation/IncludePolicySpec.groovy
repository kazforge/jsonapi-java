package com.kazforge.jsonapi.representation

import spock.lang.Specification

class IncludePolicySpec extends Specification {

  def "equivalent policies compare equal"() {
    expect:
    IncludePolicy.denyAll() == IncludePolicy.denyAll()
    IncludePolicy.allowAll() == IncludePolicy.allowAll()
    IncludePolicy.allowing(Set.of(RelationshipAllowance.of("articles", "author"))) ==
        IncludePolicy.allowing(Set.of(RelationshipAllowance.of("articles", "author")))
  }

  def "policy modes gate relationship traversal"() {
    given:
    def allowance = RelationshipAllowance.of("articles", "author")

    expect:
    !IncludePolicy.denyAll().allows("articles", "author")
    IncludePolicy.allowAll().allows("articles", "author")
    IncludePolicy.allowing(Set.of(allowance)).allows("articles", "author")
    !IncludePolicy.allowing(Set.of(allowance)).allows("articles", "comments")
  }

  def "allowing policy defensively copies its allowance set"() {
    given:
    def mutable = new HashSet<RelationshipAllowance>([
      RelationshipAllowance.of("articles", "author")
    ])

    when:
    def policy = IncludePolicy.allowing(mutable)
    mutable.add(RelationshipAllowance.of("articles", "comments"))

    then:
    !policy.allows("articles", "comments")
    policy.allows("articles", "author")
  }
}
