package com.kazforge.jsonapi.representation

import com.kazforge.jsonapi.diagnostic.JsonApiMappingException
import com.kazforge.jsonapi.diagnostic.MappingDiagnostic
import spock.lang.Specification

class IncludePathSpec extends Specification {

  def "factory-time malformed paths fail with raw input in the message and no location"() {
    when:
    IncludePath.of(input)

    then:
    def e = thrown(JsonApiMappingException)
    e.diagnostic() == MappingDiagnostic.INVALID_INCLUDE_PATH
    e.resourceClass() == null
    e.location() == null
    e.message.contains(input)

    where:
    input << [
      "",
      ".a",
      "a.",
      "a..b",
      " ",
      "a. .b"
    ]
  }

  def "canonical constructor rejects whitespace and dotted segments"() {
    when:
    new IncludePath(List.of(segment))

    then:
    def e = thrown(JsonApiMappingException)
    e.diagnostic() == MappingDiagnostic.INVALID_INCLUDE_PATH
    e.resourceClass() == null

    where:
    segment << [" ", "comments.author"]
  }

  def "dotted forms round-trip segments"() {
    expect:
    IncludePath.of("comments.author").segments() == ["comments", "author"]
    IncludePath.of("comments.author").dotted() == "comments.author"
    IncludePath.of("comments.author").dottedThrough(1) == "comments.author"
    IncludePath.of("comments.author").dottedThrough(0) == "comments"
  }
}
