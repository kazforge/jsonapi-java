package com.kazforge.jsonapi.patch

import spock.lang.Specification

class StructuredMemberStateSpec extends Specification {

  def "structured member state has exactly atomic and structured variants"() {
    expect:
    (Set) StructuredMemberState.class.getPermittedSubclasses().toSet() ==
        [
          StructuredMemberState.Atomic,
          StructuredMemberState.Structured
        ].toSet()
  }

  def "structured state freezes nested members recursively and atomics shallowly"() {
    given:
    def nestedList = new ArrayList<StructuredMember>()
    nestedList.add(new StructuredMember("lat", "lat", new StructuredMemberState.Atomic("1")))
    def structured = new StructuredMemberState.Structured(nestedList)
    def container = new ArrayList<String>(["a"])
    def atomic = new StructuredMemberState.Atomic(container)

    when:
    nestedList.add(new StructuredMember("lon", "lon", new StructuredMemberState.Atomic("2")))
    container.add("b")

    then:
    structured.members().size() == 1
    atomic.value() == ["a"]

    when:
    def storedArray = new StructuredMemberState.Atomic(["x"] as String[])
    def exposedArray = (String[]) storedArray.value()
    exposedArray[0] = "y"

    then:
    ((String[]) storedArray.value())[0] == "x"
  }
}
