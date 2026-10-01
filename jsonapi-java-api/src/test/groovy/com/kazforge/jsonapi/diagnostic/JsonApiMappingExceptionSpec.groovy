package com.kazforge.jsonapi.diagnostic

import spock.lang.Specification

class JsonApiMappingExceptionSpec extends Specification {

  def "mapping exception carries stable diagnostic, class, and pointer-form location"() {
    when:
    def e = new JsonApiMappingException(
        MappingDiagnostic.MISSING_IDENTIFIER,
        String,
        MappingLocation.of("id"),
        "message")

    then:
    e.diagnostic() == MappingDiagnostic.MISSING_IDENTIFIER
    e.resourceClass() == String
    e.location() == MappingLocation.parse("/id")
    e.propertyPath() == "/id"
    e.message == "message"
  }

  def "mapping exception represents absent location as null, never empty or root"() {
    when:
    def e = JsonApiMappingException.withoutLocation(
        MappingDiagnostic.MISSING_RESOURCE_ANNOTATION, String, "message")

    then:
    e.location() == null
    e.propertyPath() == null
    e.resourceClass() == String
  }

  def "mapping exceptions retain their stable context and optional causes"() {
    given:
    def mappingCause = new IllegalStateException("mapping cause")
    def location = MappingLocation.of("attributes", "title")

    when:
    def mapping = new JsonApiMappingException(
        MappingDiagnostic.UNSUPPORTED_ATTRIBUTE_VALUE, String, location, "mapping", mappingCause)
    def defaultMessage = new JsonApiMappingException(
        MappingDiagnostic.MISSING_IDENTIFIER, String, location)

    then:
    mapping.diagnostic() == MappingDiagnostic.UNSUPPORTED_ATTRIBUTE_VALUE
    mapping.resourceClass() == String
    mapping.location() == location
    mapping.getCause().is(mappingCause)
    defaultMessage.message == "MISSING_IDENTIFIER"
  }
}
