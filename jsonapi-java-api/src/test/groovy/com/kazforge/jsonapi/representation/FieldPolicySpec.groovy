package com.kazforge.jsonapi.representation

import spock.lang.Specification

class FieldPolicySpec extends Specification {

  def "field policy modes gate fieldset fields"() {
    given:
    def allowance = FieldAllowance.of("articles", "title")

    expect:
    !FieldPolicy.denyAll().allows("articles", "title")
    FieldPolicy.allowAll().allows("articles", "title")
    FieldPolicy.allowing(Set.of(allowance)).allows("articles", "title")
    !FieldPolicy.allowing(Set.of(allowance)).allows("articles", "author")
  }

  def "allowing field policy defensively copies its allowance set"() {
    given:
    def mutable = new HashSet<FieldAllowance>([
      FieldAllowance.of("articles", "title")
    ])

    when:
    def policy = FieldPolicy.allowing(mutable)
    mutable.add(FieldAllowance.of("articles", "author"))

    then:
    !policy.allows("articles", "author")
    policy.allows("articles", "title")
  }
}
