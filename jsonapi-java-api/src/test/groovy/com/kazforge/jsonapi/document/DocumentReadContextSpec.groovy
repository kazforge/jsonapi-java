package com.kazforge.jsonapi.document

import com.kazforge.jsonapi.core.aggregate.ValidationContext
import spock.lang.Specification

class DocumentReadContextSpec extends Specification {

  def "read context defaults and derivations"() {
    expect:
    DocumentReadContext.resourceDefaults().primaryDataKind() == PrimaryDataKind.RESOURCE
    DocumentReadContext.identifierDefaults().primaryDataKind() == PrimaryDataKind.RESOURCE_IDENTIFIER
    DocumentReadContext.of(ValidationContext.defaults(), PrimaryDataKind.RESOURCE) ==
        DocumentReadContext.resourceDefaults()
    DocumentReadContext.resourceDefaults()
        .withPrimaryDataKind(PrimaryDataKind.RESOURCE_IDENTIFIER) ==
        DocumentReadContext.identifierDefaults()
  }

  def "read context rejects missing policy components"() {
    when:
    new DocumentReadContext(null, PrimaryDataKind.RESOURCE)

    then:
    thrown(NullPointerException)

    when:
    new DocumentReadContext(ValidationContext.defaults(), null)

    then:
    thrown(NullPointerException)
  }
}
