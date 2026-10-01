package com.kazforge.jsonapi.patch

import spock.lang.Specification

class StructuredMemberSpec extends Specification {

  def "structured member rejects null names and state"() {
    when:
    new StructuredMember(null, "street", new StructuredMemberState.Atomic("S"))

    then:
    thrown(NullPointerException)

    when:
    new StructuredMember("street", null, new StructuredMemberState.Atomic("S"))

    then:
    thrown(NullPointerException)

    when:
    new StructuredMember("street", "street", null)

    then:
    thrown(NullPointerException)
  }
}
