package io.github.kazemek.jsonapi.jackson3

import io.github.kazemek.jsonapi.core.model.JsonApiDocument
import io.github.kazemek.jsonapi.core.validation.ValidationContext
import io.github.kazemek.jsonapi.jackson.document.DocumentReadContext
import io.github.kazemek.jsonapi.jackson.mapping.IdentifierConverter
import io.github.kazemek.jsonapi.jackson.mapping.MappedDocument
import io.github.kazemek.jsonapi.jackson.mapping.ResourceDecoratorRegistry
import java.lang.reflect.Modifier
import spock.lang.Specification
import tools.jackson.databind.json.JsonMapper

class JsonApiJackson3ConstructionSpec extends Specification {

  def "public facade factories use configured mapper instances rather than builders"() {
    given:
    def factories = JsonApiJackson3.declaredMethods.findAll {
      Modifier.isPublic(it.modifiers) && Modifier.isStatic(it.modifiers)
    }

    expect:
    !factories.isEmpty()
    factories.every { it.parameterTypes && it.parameterTypes[0] == JsonMapper }
    factories.every { !it.parameterTypes.contains(JsonMapper.Builder) }
  }

  def "each capability has a mapper-instance canonical factory form"() {
    expect:
    canonicalFactories().every { expected ->
      JsonApiJackson3.declaredMethods.any { method ->
        method.name == expected.name &&
            method.returnType == expected.returnType &&
            method.parameterTypes.toList() == expected.parameters
      }
    }
  }

  def "capability instances are constructed through the facade"() {
    expect:
    capabilityTypes().every { capability ->
      capability.declaredConstructors.every { !Modifier.isPublic(it.modifiers) }
    }
  }

  def "renamed command and collection surface exposes no pre-alpha names"() {
    expect:
    JsonApiJackson3.declaredMethods.every { it.name != 'patchReader' }
    JsonApiJackson3.declaredMethods.any { it.name == 'patchCommandReader' }
    JsonApiResourceMapper.declaredMethods.every {
      it.name != 'toResourceCollection' && it.name != 'toMappedResourceCollection'
    }
    JsonApiResourceMapper.declaredMethods.any {
      it.name == 'toCollectionDocument' && it.returnType == JsonApiDocument
    }
    JsonApiResourceMapper.declaredMethods.any {
      it.name == 'toMappedCollectionDocument' && it.returnType == MappedDocument
    }
  }

  def "registry construction requires an explicit configured mapper"() {
    expect:
    ResourceTypeRegistry.declaredMethods.any {
      Modifier.isPublic(it.modifiers) && Modifier.isStatic(it.modifiers) &&
          it.name == 'builder' && it.parameterTypes.toList() == [JsonMapper]
    }
    !ResourceTypeRegistry.declaredMethods.any {
      Modifier.isPublic(it.modifiers) && Modifier.isStatic(it.modifiers) &&
          it.name == 'builder' && it.parameterTypes.length == 0
    }
  }

  private static List<FactoryShape> canonicalFactories() {
    [
      new FactoryShape('writer', JsonApiDocumentWriter, [JsonMapper, ValidationContext]),
      new FactoryShape('reader', JsonApiDocumentReader, [
        JsonMapper,
        DocumentReadContext
      ]),
      new FactoryShape('resourceMapper', JsonApiResourceMapper,
      [
        JsonMapper,
        IdentifierConverter,
        ResourceDecoratorRegistry
      ]),
      new FactoryShape('resourceBinder', JsonApiResourceBinder,
      [
        JsonMapper,
        IdentifierConverter,
        Map
      ]),
      new FactoryShape('domainDocumentReader', JsonApiDomainDocumentReader,
      [
        JsonMapper,
        DocumentReadContext,
        ResourceTypeRegistry,
        IdentifierConverter,
        Map
      ]),
      new FactoryShape('patchCommandReader', JsonApiPatchCommandReader,
      [
        JsonMapper,
        ValidationContext,
        IdentifierConverter,
        Map
      ]),
      new FactoryShape('patchDtoReader', JsonApiPatchDtoReader,
      [
        JsonMapper,
        ValidationContext,
        IdentifierConverter,
        Map
      ]),
    ]
  }

  private static List<Class<?>> capabilityTypes() {
    [
      JsonApiDocumentWriter,
      JsonApiDocumentReader,
      JsonApiResourceMapper,
      JsonApiResourceBinder,
      JsonApiDomainDocumentReader,
      JsonApiPatchCommandReader,
      JsonApiPatchDtoReader,
    ]
  }

  private static final class FactoryShape {
    final String name
    final Class<?> returnType
    final List<Class<?>> parameters

    FactoryShape(String name, Class<?> returnType, List<Class<?>> parameters) {
      this.name = name
      this.returnType = returnType
      this.parameters = parameters
    }
  }
}
