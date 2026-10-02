package com.kazforge.jsonapi.jackson3

import spock.lang.Specification
import tools.jackson.core.json.PackageVersion as CoreVersion
import tools.jackson.databind.cfg.PackageVersion as DatabindVersion

class JacksonVersionSpec extends Specification {
  def "test runtime uses the selected Jackson version"() {
    given:
    def expected = System.getProperty("jackson.expectedVersion")

    expect:
    expected
    DatabindVersion.VERSION.toString() == expected
    CoreVersion.VERSION.toString() == expected
  }
}
