package com.kazforge.jsonapi.diagnostic

import com.kazforge.jsonapi.core.validation.ValidationRuleCode
import spock.lang.Specification

class JsonApiDocumentReadExceptionSpec extends Specification {

  def "read exception carries category, pointer, location, and rule code"() {
    given:
    def location = new SourceLocation(1, 2, 3L, 4L)

    when:
    def e = new JsonApiDocumentReadException(
        CodecFailureCategory.MALFORMED_JSON, "/data", location, "message")

    then:
    e.category() == CodecFailureCategory.MALFORMED_JSON
    e.jsonPointer() == "/data"
    e.sourceLocation() == location
    e.ruleCode() == null
    e.message == "message"
  }

  def "read exceptions retain their stable context and optional causes"() {
    given:
    def readCause = new IllegalArgumentException("read cause")
    def sourceLocation = new SourceLocation(1, 2, 3L, 4L)

    when:
    def read = new JsonApiDocumentReadException(
        CodecFailureCategory.LOCAL_VALIDATION,
        "/data",
        sourceLocation,
        ValidationRuleCode.MISSING_RESOURCE_ID,
        "read",
        readCause)

    then:
    read.category() == CodecFailureCategory.LOCAL_VALIDATION
    read.jsonPointer() == "/data"
    read.sourceLocation() == sourceLocation
    read.ruleCode() == ValidationRuleCode.MISSING_RESOURCE_ID
    read.getCause().is(readCause)
  }
}
