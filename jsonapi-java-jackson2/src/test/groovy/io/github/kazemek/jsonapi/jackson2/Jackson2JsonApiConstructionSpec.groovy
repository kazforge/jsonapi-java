package io.github.kazemek.jsonapi.jackson2

import com.fasterxml.jackson.databind.json.JsonMapper
import io.github.kazemek.jsonapi.jackson.api.JsonApi
import io.github.kazemek.jsonapi.jackson.mapping.IdentifierConverter
import io.github.kazemek.jsonapi.jackson.mapping.ResourceDecoratorRegistry
import io.github.kazemek.jsonapi.jackson.representation.RepresentationPolicy
import java.util.HashMap
import java.util.Map
import java.lang.reflect.Modifier
import spock.lang.Specification

class Jackson2JsonApiConstructionSpec extends Specification {

  def "factory creates one coordinated immutable runtime with stable capability facets"() {
    given:
    def runtime = JsonApiJackson2.jsonApi(JsonMapper.builder().build())

    expect:
    runtime instanceof JsonApi
    runtime.resources() != null
    runtime.relationships() != null
    runtime.documents() != null
    runtime.patches() != null
    runtime.resources().is(runtime.resources())
    runtime.relationships().is(runtime.relationships())
    runtime.documents().is(runtime.documents())
    runtime.patches().is(runtime.patches())
  }

  def "builder uses configured application collaborators"() {
    given:
    def mapper = JsonMapper.builder().build()
    def converter = IdentifierConverter.defaults()
    def policy = RepresentationPolicy.defaults()
    def decorators = ResourceDecoratorRegistry.empty()
    Map<Class<?>, RelationshipLinkageMapper> linkageMappers = new HashMap<>()

    when:
    def runtime = JsonApiJackson2.builder(mapper)
        .identifierConverter(converter)
        .linkageMappers(linkageMappers)
        .representationPolicy(policy)
        .decorators(decorators)
        .jsonApiVersion("1.1")
        .build()

    then:
    runtime.resources() != null

    when:
    JsonApiJackson2.builder(null)

    then:
    def missingMapper = thrown(NullPointerException)
    missingMapper.message == "base"
  }

  def "builder snapshots linkage mapper configuration before runtime construction"() {
    given:
    Map<Class<?>, RelationshipLinkageMapper> linkageMappers = new HashMap<>()
    def builder = JsonApiJackson2.builder(JsonMapper.builder().build())
        .linkageMappers(linkageMappers)
    linkageMappers[Object] = { ignored, ignoredType -> null } as RelationshipLinkageMapper

    when:
    def runtime = builder.build()

    then:
    runtime.resources() != null
  }

  def "runtime and capability constructors are not public"() {
    expect:
    Jackson2JsonApi.declaredConstructors.every { !Modifier.isPublic(it.modifiers) }
    Jackson2JsonApiResources.declaredConstructors.every { !Modifier.isPublic(it.modifiers) }
    Jackson2JsonApiRelationships.declaredConstructors.every { !Modifier.isPublic(it.modifiers) }
    Jackson2JsonApiDocuments.declaredConstructors.every { !Modifier.isPublic(it.modifiers) }
    Jackson2JsonApiPatches.declaredConstructors.every { !Modifier.isPublic(it.modifiers) }
  }
}
