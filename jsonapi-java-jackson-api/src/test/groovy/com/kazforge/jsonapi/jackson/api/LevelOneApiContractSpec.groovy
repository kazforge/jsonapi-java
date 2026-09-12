package com.kazforge.jsonapi.jackson.api

import com.kazforge.jsonapi.core.model.JsonApiObject
import com.kazforge.jsonapi.core.model.Links
import com.kazforge.jsonapi.core.model.Meta
import com.kazforge.jsonapi.core.model.ResourceObject
import com.kazforge.jsonapi.core.validation.EndpointIdentity
import com.kazforge.jsonapi.jackson.document.DocumentEnvelope
import com.kazforge.jsonapi.jackson.document.DocumentReadContext
import com.kazforge.jsonapi.jackson.representation.RepresentationSelection
import spock.lang.Specification

class LevelOneApiContractSpec extends Specification {

  def "write options defaults compose empty envelope and empty selection"() {
    when:
    def options = ResourceWriteOptions.defaults()

    then:
    options.envelope() == new DocumentEnvelope(null, null, null)
    options.selection() == RepresentationSelection.none()
  }

  def "write options carry envelope and selection only, never policy"() {
    expect:
    ResourceWriteOptions.recordComponents*.name == ["envelope", "selection"]
  }

  def "write options derivations preserve independent values"() {
    given:
    def envelope = new DocumentEnvelope(Links.empty(), Meta.empty(), JsonApiObject.ofVersion("1.1"))
    def selection = RepresentationSelection.builder().include("comments.author").build()

    when:
    def options = ResourceWriteOptions.defaults()
        .withEnvelope(envelope)
        .withSelection(selection)

    then:
    options.envelope().is(envelope)
    options.selection().is(selection)
  }

  def "write options reject null components"() {
    when:
    new ResourceWriteOptions(null, RepresentationSelection.none())

    then:
    thrown(NullPointerException)

    when:
    ResourceWriteOptions.defaults().withEnvelope(null)

    then:
    thrown(NullPointerException)

    when:
    ResourceWriteOptions.defaults().withSelection(null)

    then:
    thrown(NullPointerException)
  }

  def "absent per-write jsonapi stays distinct from an explicit per-write value"() {
    expect:
    ResourceWriteOptions.defaults().envelope().jsonapi() == null
    new ResourceWriteOptions(
        new DocumentEnvelope(null, null, JsonApiObject.ofVersion("1.1")),
        RepresentationSelection.none()).envelope().jsonapi() == JsonApiObject.ofVersion("1.1")
  }

  def "typed single document requires a resource and allows absent top-level members"() {
    when:
    def document = new ResourceDocument<>("dto", null, null, null, null)

    then:
    document.resource() == "dto"
    document.meta() == null
    document.links() == null
    document.jsonapi() == null
    document.included() == null

    when:
    new ResourceDocument<>(null, null, null, null, null)

    then:
    thrown(NullPointerException)
  }

  def "typed collection document copies resources and rejects null elements"() {
    given:
    def source = new ArrayList<>(["a"])

    when:
    def document = new ResourceCollectionDocument<>(source, null, null, null, null)
    source.add("b")

    then:
    document.resources() == ["a"]

    when:
    document.resources().add("c")

    then:
    thrown(UnsupportedOperationException)

    when:
    new ResourceCollectionDocument<>([null], null, null, null, null)

    then:
    thrown(NullPointerException)
  }

  def "included state is carried as core resources with defensive copies"() {
    given:
    def included = [
      ResourceObject.of("people", "9")
    ]
    def source = new ArrayList<>(included)

    when:
    def single = new ResourceDocument<>("dto", null, null, null, source)
    def collection = new ResourceCollectionDocument<>(["dto"], null, null, null, source)
    source.add(ResourceObject.of("people", "10"))

    then:
    single.included() == included
    collection.included() == included

    when:
    single.included().add(ResourceObject.of("people", "11"))

    then:
    thrown(UnsupportedOperationException)

    when:
    new ResourceDocument<>("dto", null, null, null, [null])

    then:
    thrown(NullPointerException)
  }

  def "root exposes exactly the four cohesive facets"() {
    expect:
    JsonApi.getMethods().collect { it.name }.toSet() == [
      "resources",
      "relationships",
      "documents",
      "patches"
    ] as Set
    JsonApi.getMethod("resources").returnType == JsonApiResources
    JsonApi.getMethod("relationships").returnType == JsonApiRelationships
    JsonApi.getMethod("documents").returnType == JsonApiDocuments
    JsonApi.getMethod("patches").returnType == JsonApiPatches
    JsonApi.isInterface()
    [
      JsonApiResources,
      JsonApiRelationships,
      JsonApiDocuments,
      JsonApiPatches
    ].every { it.isInterface() }
  }

  def "ordinary read and write overloads exist without leaking advanced seams"() {
    expect:
    JsonApiResources.getMethod("readOne", String, Class) != null
    JsonApiResources.getMethod("readOne", InputStream, Class) != null
    JsonApiResources.getMethod("readMany", String, Class) != null
    JsonApiResources.getMethod("readMany", InputStream, Class) != null
    JsonApiResources.getMethod("readOneDocument", String, Class) != null
    JsonApiResources.getMethod("readManyDocument", String, Class) != null
    JsonApiResources.getMethod("writeOne", Object, ResourceWriteOptions) != null
    JsonApiResources.getMethod("writeMany", Iterable, ResourceWriteOptions) != null
    JsonApiResources.getMethod("writeCreateDocument", Object, ResourceWriteOptions) != null
    JsonApiResources.getMethod("writeUpdateDocument", Object, EndpointIdentity, ResourceWriteOptions) != null
    JsonApiRelationships.getMethod("readToOne", String) != null
    JsonApiRelationships.getMethod("readToMany", String) != null
    JsonApiRelationships.getMethod("writeToOne", com.kazforge.jsonapi.core.model.ResourceIdentifier) != null
    JsonApiRelationships.getMethod("writeToMany", List).genericParameterTypes[0].typeName.endsWith("? extends com.kazforge.jsonapi.core.model.ResourceIdentifier>")
    JsonApiDocuments.getMethod("read", String, DocumentReadContext) != null
    JsonApiPatches.getMethod("readPatch", String, Class) != null
    JsonApiPatches.getMethod("readCommand", String, Class) != null
    JsonApiPatches.getMethod("bindPatch", com.kazforge.jsonapi.core.model.JsonApiDocument, Class) != null
    JsonApiPatches.getMethod("bindCommand", com.kazforge.jsonapi.core.model.JsonApiDocument, Class) != null
  }

  def "neutrality scan flags forbidden packages anywhere in a rendered signature"() {
    expect:
    isForbiddenSignature("tools.jackson.databind.JsonNode")
    isForbiddenSignature("java.util.List<tools.jackson.databind.JsonNode>")
    isForbiddenSignature("com.fasterxml.jackson.annotation.JsonProperty")
    isForbiddenSignature("java.util.Map<java.lang.String, com.fasterxml.jackson.databind.JsonNode>")
    isForbiddenSignature("com.kazforge.jsonapi.jackson3.JsonApiResourceMapper")
    isForbiddenSignature("com.kazforge.jsonapi.jackson2.JsonApiResourceMapper")

    !isForbiddenSignature("java.util.List<java.lang.String>")
    !isForbiddenSignature("com.kazforge.jsonapi.jackson.api.JsonApi")
    !isForbiddenSignature("java.util.List<com.kazforge.jsonapi.core.model.ResourceIdentifier>")
  }

  def "no neutral level-1 signature references Jackson implementation types"() {
    given:
    def apiTypes = [
      JsonApi,
      JsonApiResources,
      JsonApiRelationships,
      JsonApiDocuments,
      JsonApiPatches,
      ResourceWriteOptions,
      ResourceDocument,
      ResourceCollectionDocument
    ]

    when:
    def offending = []
    apiTypes.each { type ->
      (type.methods.toList() + type.declaredMethods.toList()).each { method ->
        ([method.genericReturnType] + method.genericParameterTypes.toList()).each { param ->
          // typeName renders the full parameterized form; the forbidden roots are matched
          // anywhere in it so nested type arguments cannot hide an implementation type.
          def name = param.typeName
          if (isForbiddenSignature(name)) {
            offending.add(type.simpleName + "#" + method.name + " -> " + name)
          }
        }
      }
    }

    then:
    offending.isEmpty()
  }

  private static boolean isForbiddenSignature(String typeName) {
    return [
      "tools.jackson.",
      "com.fasterxml.",
      ".jackson2.",
      ".jackson3."
    ].any {
      typeName.contains(it)
    }
  }
}
