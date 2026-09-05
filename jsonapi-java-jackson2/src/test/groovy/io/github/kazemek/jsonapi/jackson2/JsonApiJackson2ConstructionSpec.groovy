package io.github.kazemek.jsonapi.jackson2

import io.github.kazemek.jsonapi.core.model.DocumentData
import io.github.kazemek.jsonapi.core.model.JsonApiDocument
import io.github.kazemek.jsonapi.core.model.ResourceObject
import io.github.kazemek.jsonapi.core.validation.ValidationContext
import java.lang.reflect.Modifier

import com.fasterxml.jackson.databind.json.JsonMapper

import spock.lang.Specification

class JsonApiJackson2ConstructionSpec extends Specification {

  def "public facade factories use configured mapper instances rather than builders"() {
    given:
    def factories = JsonApiJackson2.declaredMethods.findAll {
      Modifier.isPublic(it.modifiers) && Modifier.isStatic(it.modifiers)
    }

    expect:
    !factories.isEmpty()
    factories.every { it.parameterTypes && it.parameterTypes[0] == JsonMapper }
    factories.every { !it.parameterTypes.contains(JsonMapper.Builder) }
  }

  def "the writer capability has a mapper-instance canonical factory form and a default convenience form"() {
    expect:
    JsonApiJackson2.declaredMethods.any { method ->
      method.name == 'writer' &&
          method.returnType == JsonApiDocumentWriter &&
          method.parameterTypes.toList() == [JsonMapper]
    }
    JsonApiJackson2.declaredMethods.any { method ->
      method.name == 'writer' &&
          method.returnType == JsonApiDocumentWriter &&
          method.parameterTypes.toList() == [JsonMapper, ValidationContext]
    }
  }

  def "the capability instance is constructed through the facade"() {
    expect:
    JsonApiDocumentWriter.declaredConstructors.every { !Modifier.isPublic(it.modifiers) }
  }

  def "factory construction rejects missing inputs with named parameters"() {
    when:
    JsonApiJackson2.writer(null)

    then:
    def missingMapper = thrown(NullPointerException)
    missingMapper.message == 'base'

    when:
    JsonApiJackson2.writer(JsonMapper.builder().build(), null)

    then:
    def missingContext = thrown(NullPointerException)
    missingContext.message == 'context'
  }

  def "the convenience factory binds the documented default validation context"() {
    given:
    def writer = JsonApiJackson2.writer(JsonMapper.builder().build())

    expect:
    writer.context() == ValidationContext.defaults()
  }

  def "the writer emits a valid document through the convenience factory"() {
    given:
    def writer = JsonApiJackson2.writer(JsonMapper.builder().build())
    def document = JsonApiDocument.withData(
        new DocumentData.SingleResource(ResourceObject.of('articles', '1')))

    when:
    def json = writer.writeValueAsString(document)

    then:
    json == '{"data":{"type":"articles","id":"1"}}'
  }
}
