package com.kazforge.jsonapi.patch

import spock.lang.Specification

class PatchCommandSpec extends Specification {

  def "patch command rejects null components"() {
    when:
    new PatchCommand<>(null, "1", List.of())

    then:
    thrown(NullPointerException)

    when:
    new PatchCommand<>(String, null, List.of())

    then:
    thrown(NullPointerException)

    when:
    new PatchCommand<>(String, "1", null)

    then:
    thrown(NullPointerException)

    when:
    new PatchCommand<>(String, "1", [null])

    then:
    thrown(NullPointerException)
  }

  def "mutating changes or list or set or array values cannot affect the command"() {
    given:
    def mutableList = new ArrayList<>(["a"])
    def mutableSet = new LinkedHashSet<>(["s"])
    def mutableArray = ["x"] as String[]
    def changes = new ArrayList<PatchChange>()
    changes.add(new PatchChange.AttributeChange("title", "title", mutableList))
    changes.add(new PatchChange.AttributeChange("tags", "tags", mutableSet))
    changes.add(new PatchChange.AttributeChange("names", "names", mutableArray))
    def command = new PatchCommand<>(String, "1", changes)

    when:
    changes.add(new PatchChange.AttributeChange("extra", "extra", "y"))
    mutableList.add("b")
    mutableSet.add("t")
    mutableArray[0] = "z"
    command.changes().add(new PatchChange.AttributeChange("nope", "nope", "n"))

    then:
    thrown(UnsupportedOperationException)
    command.changes().size() == 3
    (command.changes()[0].value() as List) == ["a"]
    (command.changes()[1].value() as Set) == ["s"] as Set
    (command.changes()[2].value() as String[])[0] == "x"
  }
}
