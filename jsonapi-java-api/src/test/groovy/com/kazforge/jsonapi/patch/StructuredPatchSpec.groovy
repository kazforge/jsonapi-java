package com.kazforge.jsonapi.patch

import spock.lang.Specification

class StructuredPatchSpec extends Specification {

  def "structured patch rejects null members and freezes the members list"() {
    when:
    new StructuredPatch(null)

    then:
    thrown(NullPointerException)

    when:
    def members = new ArrayList<StructuredMember>()
    members.add(new StructuredMember("street", "street", new StructuredMemberState.Atomic("S")))
    def patch = new StructuredPatch(members)

    then:
    members.add(new StructuredMember("x", "x", new StructuredMemberState.Atomic("x")))
    patch.members().size() == 1
  }

  def "structured patch equality follows nested structure"() {
    expect:
    new StructuredPatch(
        [
          new StructuredMember("street", "street", new StructuredMemberState.Atomic("S"))
        ]) ==
        new StructuredPatch(
        [
          new StructuredMember("street", "street", new StructuredMemberState.Atomic("S"))
        ])
    new StructuredPatch(
        [
          new StructuredMember("street", "street", new StructuredMemberState.Atomic("S"))
        ]) !=
        new StructuredPatch(
        [
          new StructuredMember("city", "city", new StructuredMemberState.Atomic("S"))
        ])
  }
}
