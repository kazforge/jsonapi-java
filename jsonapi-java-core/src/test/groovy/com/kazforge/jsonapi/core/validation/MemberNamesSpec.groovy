package com.kazforge.jsonapi.core.validation

import spock.lang.Specification

class MemberNamesSpec extends Specification {

  def "isValid('#name') is #valid"() {
    expect:
    MemberNames.isValid(name) == valid

    where:
    name               | valid
    "title"            | true
    "author_id"        | true
    "a1"               | true
    "Title"            | true
    "author name"      | true
    "café"             | true
    ""                 | false
    "_bad"             | false
    "bad+name"         | false
    "-bad"             | false
    "myext:version"    | true
    "myext:field-name" | true
    "MyExt:version"    | true
    "1ext:foo"         | true
    "myext:"           | false
    "my-ext:version"   | false
    "@context"         | true
    "@Context"         | true
    "@"                | false
    "@_bad"            | false
    "a:b:c"            | false
    "name "            | false
    "name-"            | false
    null               | false
  }

  def "isValid accepts well-formed non-ASCII member names"() {
    expect:
    MemberNames.isValid(name)

    where:
    name << [
      "aéb",
      "a" + new String(Character.toChars(0x1F600)) + "b",
      new String(Character.toChars(0x1F600)) + "ab",
      "ab" + new String(Character.toChars(0x1F600)),
      new String(Character.toChars(0x1F600)),
    ]
  }

  def "isValid rejects malformed surrogate sequences"() {
    expect:
    !MemberNames.isValid(name)

    where:
    name << [
      String.valueOf(Character.MIN_HIGH_SURROGATE),
      "a" + Character.MIN_HIGH_SURROGATE,
      "a" + Character.MIN_HIGH_SURROGATE + "b",
      "a" + Character.MIN_LOW_SURROGATE + "b",
      String.valueOf(Character.MIN_LOW_SURROGATE) + "a",
      String.valueOf(Character.MIN_LOW_SURROGATE) + Character.MIN_HIGH_SURROGATE,
      "a" + String.valueOf(Character.MIN_HIGH_SURROGATE) + new String(Character.toChars(0x1F600)) + "b",
    ]
  }

  def "isExtensionMember('#name') is #extension"() {
    expect:
    MemberNames.isExtensionMember(name) == extension

    where:
    name             | extension
    "MyExt:version"  | true
    "1ext:foo"       | true
    "my-ext:version" | false
    "@context"       | false
    "a:b:c"          | false
    null             | false
  }

  def "isAtMember('#name') is #atMember"() {
    expect:
    MemberNames.isAtMember(name) == atMember

    where:
    name       | atMember
    "@context" | true
    "context"  | false
    null       | false
  }
}
