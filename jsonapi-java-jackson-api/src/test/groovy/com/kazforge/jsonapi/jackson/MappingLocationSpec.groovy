package com.kazforge.jsonapi.jackson

import com.kazforge.jsonapi.jackson.diagnostic.JsonApiMappingException
import com.kazforge.jsonapi.jackson.diagnostic.MappingDiagnostic
import com.kazforge.jsonapi.jackson.diagnostic.MappingLocation
import spock.lang.Specification

/** Escaping, parsing, and composition invariants of the mapping-diagnostic location type. */
class MappingLocationSpec extends Specification {

  def "segment factories escape each segment independently per RFC 6901"() {
    expect:
    MappingLocation.of("attributes", "external/name").pointer() == "/attributes/external~1name"
    MappingLocation.of("attributes", "a~b").pointer() == "/attributes/a~0b"
    // Tilde escapes before slash so an escaped tilde can never be re-read as an escape sequence.
    MappingLocation.of("a/b~c/d").pointer() == "/a~1b~0c~1d"
    MappingLocation.of("meta").pointer() == "/meta"
    MappingLocation.of("relationships", "author", "data", "meta").pointer() ==
        "/relationships/author/data/meta"
    MappingLocation.of("relationships", "comments", "data", "0", "meta").pointer() ==
        "/relationships/comments/data/0/meta"
  }

  def "parse accepts canonical pointers and rejects malformed ones"() {
    expect:
    MappingLocation.parse("/data/2").pointer() == "/data/2"
    MappingLocation.parse("/attributes/a~1b~0c").pointer() == "/attributes/a~1b~0c"

    when:
    MappingLocation.parse(bad)

    then:
    thrown(IllegalArgumentException)

    where:
    bad << [
      null,
      "",
      "attributes/title",
      "data",
      "/da//ta",
      "/",
      "~",
      "/x~2",
      "/x~"
    ]
  }

  def "append escapes single segments and joins locations structurally"() {
    given:
    def prefix = MappingLocation.parse("/data/2")
    def resourceLocal = MappingLocation.parse("/attributes/headline")

    expect:
    prefix.append(resourceLocal).pointer() == "/data/2/attributes/headline"
    prefix.append(resourceLocal).pointer() == prefix.pointer() + resourceLocal.pointer()
    MappingLocation.parse("/included").append("weird/name~1").pointer() ==
        "/included/weird~1name~01"
    MappingLocation.of("data").append("title").pointer() == "/data/title"
  }

  def "instances are values"() {
    expect:
    MappingLocation.of("data") == MappingLocation.parse("/data")
    MappingLocation.of("data").hashCode() == MappingLocation.parse("/data").hashCode()
    MappingLocation.of("data").toString() == "/data"
    MappingLocation.of("data") != MappingLocation.of("included")
  }

  def "mapping locations preserve exception diagnostics during serialization"() {
    given:
    def original = new JsonApiMappingException(
        MappingDiagnostic.UNSUPPORTED_ATTRIBUTE_VALUE,
        null,
        MappingLocation.of("attributes", "title"),
        "bad value")
    def bytes = new ByteArrayOutputStream()
    new ObjectOutputStream(bytes).withCloseable { it.writeObject(original) }

    when:
    def restored = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray())).withCloseable {
      it.readObject()
    }

    then:
    restored instanceof JsonApiMappingException
    ((JsonApiMappingException) restored).location() == original.location()
    ((JsonApiMappingException) restored).propertyPath() == "/attributes/title"
  }

  def "empty segments are rejected at every entry point"() {
    when:
    MappingLocation.of("attributes", "")

    then:
    thrown(IllegalArgumentException)

    when:
    MappingLocation.of("attributes").append("")

    then:
    thrown(IllegalArgumentException)
  }

  def "null segments fail fast as NullPointerException"() {
    when:
    MappingLocation.of("attributes", (String) null)

    then:
    thrown(NullPointerException)

    when:
    MappingLocation.of("attributes").append((String) null)

    then:
    thrown(NullPointerException)
  }
}
