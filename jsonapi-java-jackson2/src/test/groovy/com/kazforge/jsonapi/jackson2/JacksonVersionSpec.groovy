package com.kazforge.jsonapi.jackson2

import com.fasterxml.jackson.core.json.PackageVersion as CoreVersion
import com.fasterxml.jackson.databind.cfg.PackageVersion as DatabindVersion
import com.fasterxml.jackson.datatype.jdk8.PackageVersion as Jdk8Version
import spock.lang.Specification

class JacksonVersionSpec extends Specification {
  def "test runtime uses the selected Jackson version"() {
    given:
    def expected = System.getProperty("jackson.expectedVersion")

    expect:
    expected
    DatabindVersion.VERSION.toString() == expected
    CoreVersion.VERSION.toString() == expected
    Jdk8Version.VERSION.toString() == expected
  }
}
