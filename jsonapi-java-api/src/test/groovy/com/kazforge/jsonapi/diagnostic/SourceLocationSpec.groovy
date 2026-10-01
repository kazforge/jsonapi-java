package com.kazforge.jsonapi.diagnostic

import spock.lang.Specification

class SourceLocationSpec extends Specification {

  def "source location distinguishes known and unknown positions"() {
    expect:
    !SourceLocation.UNKNOWN.isKnown()
    new SourceLocation(0, 0, 0L, 0L).isKnown()
    new SourceLocation(-1, 2, -1L, -1L).isKnown()
  }
}
