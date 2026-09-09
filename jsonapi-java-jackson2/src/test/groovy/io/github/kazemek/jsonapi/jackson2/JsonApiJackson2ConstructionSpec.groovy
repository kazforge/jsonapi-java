package io.github.kazemek.jsonapi.jackson2

import io.github.kazemek.jsonapi.core.model.DocumentData
import io.github.kazemek.jsonapi.core.model.JsonApiDocument
import io.github.kazemek.jsonapi.core.model.ResourceObject
import io.github.kazemek.jsonapi.core.validation.ValidationContext
import io.github.kazemek.jsonapi.jackson.document.DocumentReadContext
import io.github.kazemek.jsonapi.jackson.mapping.IdentifierConverter
import io.github.kazemek.jsonapi.jackson.mapping.ResourceDecoratorRegistry
import io.github.kazemek.jsonapi.jackson.mapping.ResourceTypeRegistry
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

  def "the reader capability has a mapper-instance canonical factory form"() {
    expect:
    JsonApiJackson2.declaredMethods.any { method ->
      method.name == 'reader' &&
          method.returnType == JsonApiDocumentReader &&
          method.parameterTypes.toList() == [
            JsonMapper,
            DocumentReadContext
          ]
    }
  }

  def "the domain document reader has mapper-instance factory forms"() {
    expect:
    JsonApiJackson2.declaredMethods.any { method ->
      method.name == 'domainDocumentReader' &&
          method.returnType == JsonApiDomainDocumentReader &&
          method.parameterTypes.toList() == [
            JsonMapper,
            DocumentReadContext,
            ResourceTypeRegistry
          ]
    }
    JsonApiJackson2.declaredMethods.any { method ->
      method.name == 'domainDocumentReader' &&
          method.returnType == JsonApiDomainDocumentReader &&
          method.parameterTypes.toList() == [
            JsonMapper,
            DocumentReadContext,
            ResourceTypeRegistry,
            IdentifierConverter
          ]
    }
    JsonApiJackson2.declaredMethods.any { method ->
      method.name == 'domainDocumentReader' &&
          method.returnType == JsonApiDomainDocumentReader &&
          method.parameterTypes.toList() == [
            JsonMapper,
            DocumentReadContext,
            ResourceTypeRegistry,
            IdentifierConverter,
            Map
          ]
    }
  }

  def "the resource mapper capability has a mapper-instance canonical factory form and meaningful conveniences"() {
    expect:
    JsonApiJackson2.declaredMethods.any { method ->
      method.name == 'resourceMapper' &&
          method.returnType == JsonApiResourceMapper &&
          method.parameterTypes.toList() == [JsonMapper]
    }
    JsonApiJackson2.declaredMethods.any { method ->
      method.name == 'resourceMapper' &&
          method.returnType == JsonApiResourceMapper &&
          method.parameterTypes.toList() == [
            JsonMapper,
            IdentifierConverter
          ]
    }
    JsonApiJackson2.declaredMethods.any { method ->
      method.name == 'resourceMapper' &&
          method.returnType == JsonApiResourceMapper &&
          method.parameterTypes.toList() == [
            JsonMapper,
            ResourceDecoratorRegistry
          ]
    }
    JsonApiJackson2.declaredMethods.any { method ->
      method.name == 'resourceMapper' &&
          method.returnType == JsonApiResourceMapper &&
          method.parameterTypes.toList() == [
            JsonMapper,
            IdentifierConverter,
            ResourceDecoratorRegistry
          ]
    }
  }

  def "the resource binder capability has a mapper-instance canonical factory form and meaningful conveniences"() {
    expect:
    JsonApiJackson2.declaredMethods.any { method ->
      method.name == 'resourceBinder' &&
          method.returnType == JsonApiResourceBinder &&
          method.parameterTypes.toList() == [JsonMapper]
    }
    JsonApiJackson2.declaredMethods.any { method ->
      method.name == 'resourceBinder' &&
          method.returnType == JsonApiResourceBinder &&
          method.parameterTypes.toList() == [
            JsonMapper,
            IdentifierConverter
          ]
    }
    JsonApiJackson2.declaredMethods.any { method ->
      method.name == 'resourceBinder' &&
          method.returnType == JsonApiResourceBinder &&
          method.parameterTypes.toList() == [
            JsonMapper,
            IdentifierConverter,
            Map
          ]
    }
  }

  def "the capability instances are constructed through the facade"() {
    expect:
    JsonApiDocumentWriter.declaredConstructors.every { !Modifier.isPublic(it.modifiers) }
    JsonApiDocumentReader.declaredConstructors.every { !Modifier.isPublic(it.modifiers) }
    JsonApiResourceMapper.declaredConstructors.every { !Modifier.isPublic(it.modifiers) }
    JsonApiResourceBinder.declaredConstructors.every { !Modifier.isPublic(it.modifiers) }
    JsonApiDomainDocumentReader.declaredConstructors.every { !Modifier.isPublic(it.modifiers) }
    JsonApiDomainDocument.declaredConstructors.every { !Modifier.isPublic(it.modifiers) }
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

    when:
    JsonApiJackson2.resourceMapper(JsonMapper.builder().build(), (IdentifierConverter) null)

    then:
    def missingConverter = thrown(NullPointerException)
    missingConverter.message == 'identifierConverter'

    when:
    JsonApiJackson2.resourceMapper(JsonMapper.builder().build(), (ResourceDecoratorRegistry) null)

    then:
    def missingDecorators = thrown(NullPointerException)
    missingDecorators.message == 'decorators'

    when:
    JsonApiJackson2.resourceBinder(JsonMapper.builder().build(), (IdentifierConverter) null)

    then:
    def missingBinderConverter = thrown(NullPointerException)
    missingBinderConverter.message == 'identifierConverter'

    when:
    JsonApiJackson2.resourceBinder(
        JsonMapper.builder().build(), IdentifierConverter.defaults(), (Map) null)

    then:
    def missingLinkageMappers = thrown(NullPointerException)
    missingLinkageMappers.message == 'linkageMappers'
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
