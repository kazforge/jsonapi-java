package com.kazforge.jsonapi.mapping

import spock.lang.Specification

class IdentifierConverterSpec extends Specification {

  def "defaults converter delegates to toString and parse returns the wire string"() {
    given:
    def converter = IdentifierConverter.defaults()

    expect:
    converter.convert(42L) == "42"
    converter.convert(null) == null
    converter.parse("9") == "9"
    converter.parse(null) == null
  }
}
