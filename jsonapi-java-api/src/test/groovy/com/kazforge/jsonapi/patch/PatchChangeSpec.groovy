package com.kazforge.jsonapi.patch

import spock.lang.Specification

class PatchChangeSpec extends Specification {

  def "patch change compact constructors reject null names"() {
    when:
    new PatchChange.AttributeChange(null, "title", "x")

    then:
    thrown(NullPointerException)

    when:
    new PatchChange.AttributeChange("title", null, "x")

    then:
    thrown(NullPointerException)

    when:
    new PatchChange.RelationshipChange(null, "author", null)

    then:
    thrown(NullPointerException)

    when:
    new PatchChange.RelationshipChange("author", null, null)

    then:
    thrown(NullPointerException)
  }

  def "patch change sealed variants preserve explicit null and variant identity"() {
    given:
    def changes = [
      new PatchChange.AttributeChange("title", "title", null),
      new PatchChange.RelationshipChange("author", "author", null),
      new PatchChange.ResourceMetaChange("meta", "articleMeta", null),
      new PatchChange.RelationshipMetaChange("author", "authorMeta", null)
    ]

    expect:
    (Set) PatchChange.class.getPermittedSubclasses().toSet() ==
        [
          PatchChange.AttributeChange,
          PatchChange.RelationshipChange,
          PatchChange.ResourceMetaChange,
          PatchChange.RelationshipMetaChange
        ].toSet()
    changes*.value() == [null, null, null, null]
    changes*.jsonapiName() == [
      "title",
      "author",
      "meta",
      "author"
    ]
    changes*.logicalName() == [
      "title",
      "author",
      "articleMeta",
      "authorMeta"
    ]
  }

  def "mutating an array returned from value cannot affect the stored change"() {
    given:
    def change = new PatchChange.AttributeChange("names", "names", ["a"] as String[])
    def exposed = change.value() as String[]

    when:
    exposed[0] = "changed"

    then:
    (change.value() as String[])[0] == "a"
  }

  def "mutating a map value cannot affect the stored change"() {
    given:
    def mutableMap = new LinkedHashMap<String, Object>()
    mutableMap.put("type", "tags")
    mutableMap.put("id", "t1")
    mutableMap.put("lid", null)
    def change = new PatchChange.RelationshipChange("author", "author", mutableMap)

    when:
    mutableMap.put("id", "mutated")
    (change.value() as Map).put("id", "exposed")

    then:
    thrown(UnsupportedOperationException)
    (change.value() as Map).id == "t1"
    (change.value() as Map).type == "tags"
  }

  def "mutating a primitive array returned from value cannot affect the stored change"() {
    given:
    def change = new PatchChange.RelationshipChange("counts", "counts", [1, 2] as int[])
    def exposed = change.value() as int[]

    when:
    exposed[0] = 99

    then:
    (change.value() as int[])[0] == 1
    (change.value() as int[])[1] == 2
  }
}
