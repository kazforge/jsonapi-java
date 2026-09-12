package com.kazforge.jsonapi.jackson3

import spock.lang.Specification
import tools.jackson.databind.json.JsonMapper

// Adapter-specific: mapper isolation behavior of this major's own factory, deliberately kept local
// to this adapter spec.
class ResourceMapperIsolationSpec extends Specification {

  static class SampleBean {
    String name

    SampleBean() {}

    SampleBean(String name) {
      this.name = name
    }

    String getName() {
      return name
    }

    void setName(String name) {
      this.name = name
    }
  }

  def "deriving a resource mapper does not change caller ordinary serialization"() {
    given:
    def caller = JsonMapper.builder().build()
    def before = caller.writeValueAsString(new SampleBean("alpha"))

    when:
    JsonApiJackson3.resourceMapper(caller)
    def after = caller.writeValueAsString(new SampleBean("alpha"))

    then:
    before == after
    before == '{"name":"alpha"}'
  }
}
