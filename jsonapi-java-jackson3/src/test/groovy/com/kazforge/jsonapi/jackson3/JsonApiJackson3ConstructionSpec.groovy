package com.kazforge.jsonapi.jackson3

import com.kazforge.jsonapi.jackson.mapping.ResourceTypeRegistry

import com.kazforge.jsonapi.core.model.JsonApiDocument
import com.kazforge.jsonapi.core.validation.ValidationContext
import com.kazforge.jsonapi.jackson.document.DocumentReadContext
import com.kazforge.jsonapi.jackson.mapping.IdentifierConverter
import com.kazforge.jsonapi.jackson.mapping.MappedDocument
import com.kazforge.jsonapi.jackson.mapping.ResourceDecoratorRegistry
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

  def "registry construction is Jackson-major-neutral"() {
    expect:
    ResourceTypeRegistry.declaredMethods.any {
      Modifier.isPublic(it.modifiers) && Modifier.isStatic(it.modifiers) &&
          it.name == 'builder' && it.parameterTypes.length == 0
    }
    !ResourceTypeRegistry.declaredMethods.any {
      Modifier.isPublic(it.modifiers) && Modifier.isStatic(it.modifiers) &&
          it.name == 'builder' && it.parameterTypes.any { type -> type.name.contains('JsonMapper') }
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
