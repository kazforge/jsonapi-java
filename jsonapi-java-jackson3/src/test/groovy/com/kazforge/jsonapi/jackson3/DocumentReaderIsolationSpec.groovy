package com.kazforge.jsonapi.jackson3

import com.kazforge.jsonapi.jackson.document.DocumentReadContext

import java.lang.reflect.Modifier

import tools.jackson.databind.json.JsonMapper

import spock.lang.Specification

class DocumentReaderIsolationSpec extends Specification {

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

  def "deriving a document reader does not change caller ordinary serialization"() {
    given:
    def caller = JsonMapper.builder().build()
    def before = caller.writeValueAsString(new SampleBean('alpha'))

    when:
    def reader = JsonApiJackson3.reader(caller, DocumentReadContext.resourceDefaults())
    def after = caller.writeValueAsString(new SampleBean('alpha'))

    then:
    before == after
    before == '{"name":"alpha"}'
    reader.mapper().is(caller)
  }

  def "JsonApiDocumentReader.mapper is package-private"() {
    expect:
    def method = JsonApiDocumentReader.getDeclaredMethod('mapper')
    !Modifier.isPublic(method.modifiers)
    JsonApiDocumentReader.methods.every { it.name != 'mapper' }
  }
}
