package io.github.kazemek.jsonapi.jackson2

import java.lang.reflect.Modifier

import com.fasterxml.jackson.databind.json.JsonMapper

import spock.lang.Specification

class DocumentWriterIsolationSpec extends Specification {

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

  def "deriving a document writer does not change caller ordinary serialization"() {
    given:
    def caller = JsonMapper.builder().build()
    def before = caller.writeValueAsString(new SampleBean("alpha"))

    when:
    def writer = JsonApiJackson2.writer(caller)
    def after = caller.writeValueAsString(new SampleBean("alpha"))

    then:
    before == after
    before == '{"name":"alpha"}'
    !writer.mapper().is(caller)
  }

  def "documentMapper is package-private and not a public factory method"() {
    expect:
    def method = JsonApiJackson2.getDeclaredMethod('documentMapper', JsonMapper)
    !Modifier.isPublic(method.modifiers)
    JsonApiJackson2.methods.every { it.name != 'documentMapper' }
  }

  def "JsonApiDocumentWriter.mapper is package-private"() {
    expect:
    def method = JsonApiDocumentWriter.getDeclaredMethod('mapper')
    !Modifier.isPublic(method.modifiers)
    JsonApiDocumentWriter.methods.every { it.name != 'mapper' }
  }
}
