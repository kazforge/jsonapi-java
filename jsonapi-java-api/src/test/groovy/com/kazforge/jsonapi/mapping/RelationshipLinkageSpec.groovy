package com.kazforge.jsonapi.mapping

import spock.lang.Specification

class RelationshipLinkageSpec extends Specification {

  def "relationship linkage requires a non-null target and allows null meta"() {
    when:
    new RelationshipLinkage<String, String>(null, "meta")

    then:
    thrown(NullPointerException)

    when:
    def linkage = new RelationshipLinkage<String, String>("target", null)

    then:
    linkage.target() == "target"
    linkage.meta() == null
    linkage == new RelationshipLinkage<String, String>("target", null)
    linkage != new RelationshipLinkage<String, String>("target", "meta")
  }
}
