package com.kazforge.jsonapi.mapping.internal

import com.kazforge.jsonapi.core.model.Attributes
import com.kazforge.jsonapi.core.model.Meta
import com.kazforge.jsonapi.core.model.Relationship
import com.kazforge.jsonapi.core.model.RelationshipData
import com.kazforge.jsonapi.core.model.Relationships
import com.kazforge.jsonapi.core.model.ResourceIdentifier
import com.kazforge.jsonapi.core.model.ResourceObject
import com.kazforge.jsonapi.diagnostic.JsonApiMappingException
import com.kazforge.jsonapi.diagnostic.MappingDiagnostic
import spock.lang.Specification

/**
 * Neutral typed PATCH DTO orchestration semantics proven against fake backends: contract phase
 * order, declaration preflight before identity, presence-marker assembly, strict unknown supplied
 * members, and relationship-meta gating on relationship {@code data}.
 */
class MappingTypedPatchBinderSpec extends Specification {

  private FakeTypedBackend backend
  private TypedPatchBinder<String, String> binder

  def setup() {
    backend = new FakeTypedBackend()
    binder = new TypedPatchBinder<>(backend, { wire, declared, pointer, rawType -> wire } as TypedMemberValueBinder)
  }

  def "runs the phase order and assembles presence markers"() {
    given:
    def definition =
        new TypedPatchDefinition<>(
        "articles",
        property(PropertyRole.ID, "id", "id", "id"),
        [
          property(PropertyRole.ATTRIBUTE, "title", "title", "title")
        ],
        [
          property(PropertyRole.RELATIONSHIP, "author", "author", "author")
        ],
        property(PropertyRole.RESOURCE_META, "meta", "meta", "meta"),
        [
          property(PropertyRole.RELATIONSHIP_META, "authorMeta", "authorMeta", "author")
        ])
    def resource =
        resource(
        "articles",
        "1",
        Attributes.ofAttributes([title: "T"]),
        Relationships.ofRelationships([
          author: new Relationship(
          new RelationshipData.SingleLinkage(ResourceIdentifier.of("people", "p1")),
          null,
          null,
          Map.of())
        ]),
        Meta.of([source: "cms"]))

    when:
    def result = binder.bind(resource, definition, "JavaType", Object)

    then:
    result == "constructed"
    backend.callOrder == [
      "validateMeta",
      "identity",
      "relationship:author",
      "construct"
    ]
    backend.constructedProperties.keySet() as List == [
      "id",
      "title",
      "author",
      "meta",
      "authorMeta"
    ]
    backend.constructedProperties.id == "identity:1"
    backend.constructedProperties.title == new PresenceMarker(true, "T")
    backend.constructedProperties.author == new PresenceMarker(true, "converted:author")
    backend.constructedProperties.meta == new PresenceMarker(true, [source: "cms"])
    backend.constructedProperties.authorMeta == new PresenceMarker(false, null)
  }

  def "runs the full declaration preflight before requiring identity"() {
    given:
    def definition =
        new TypedPatchDefinition<>(
        "articles",
        property(PropertyRole.ID, "id", "id", "id"),
        [
          new TypedPatchProperty<>("title", attribute("title", "title"), "String", false, false, false)
        ],
        [],
        null,
        [])

    when:
    binder.bind(resource("articles", "1", null, null, null), definition, "JavaType", Object)

    then:
    def ex = thrown(JsonApiMappingException)
    ex.diagnostic() == MappingDiagnostic.INVALID_PATCH_PROPERTY_TYPE
    ex.propertyPath() == "/attributes/title"
    backend.callOrder == []
  }

  def "rejects wrapper customization and invalid meta targets during preflight"() {
    given:
    def customized =
        new TypedPatchDefinition<>(
        "articles",
        property(PropertyRole.ID, "id", "id", "id"),
        [
          new TypedPatchProperty<>("title", attribute("title", "title"), "PatchPresence", true, true, false)
        ],
        [],
        null,
        [])
    def badMeta =
        new TypedPatchDefinition<>(
        "articles",
        property(PropertyRole.ID, "id", "id", "id"),
        [],
        [],
        new TypedPatchProperty<>("meta", metaProperty(), "PatchPresence", true, false, false),
        [])

    when:
    binder.bind(resource("articles", "1", null, null, null), customized, "JavaType", Object)

    then:
    def customizedFailure = thrown(JsonApiMappingException)
    customizedFailure.diagnostic() == MappingDiagnostic.INVALID_PATCH_PROPERTY_TYPE
    customizedFailure.propertyPath() == "/attributes/title"

    when:
    binder.bind(resource("articles", "1", null, null, null), badMeta, "JavaType", Object)

    then:
    def metaFailure = thrown(JsonApiMappingException)
    metaFailure.diagnostic() == MappingDiagnostic.INVALID_META_TARGET
    metaFailure.propertyPath() == "/meta"
  }

  def "requires identity before binding supplied members"() {
    given:
    def definition =
        new TypedPatchDefinition<>(
        "articles",
        property(PropertyRole.ID, "id", "id", "id"),
        [
          new TypedPatchProperty<>("title", attribute("title", "title"), "PatchPresence", true, false, false)
        ],
        [],
        null,
        [])

    when:
    binder.bind(
        resource("articles", null, Attributes.ofAttributes([title: "T"]), null, null),
        definition,
        "JavaType",
        Object)

    then:
    def ex = thrown(JsonApiMappingException)
    ex.diagnostic() == MappingDiagnostic.IDENTIFIER_CONVERSION_FAILED
    ex.propertyPath() == "/id"
  }

  def "rejects unknown supplied typed members at their wire pointers"() {
    given:
    def definition =
        new TypedPatchDefinition<>(
        "articles",
        property(PropertyRole.ID, "id", "id", "id"),
        [
          new TypedPatchProperty<>("title", attribute("title", "title"), "PatchPresence", true, false, false)
        ],
        [],
        null,
        [])

    when:
    binder.bind(
        resource("articles", "1", Attributes.ofAttributes([bogus: "x"]), null, null),
        definition,
        "JavaType",
        Object)

    then:
    def ex = thrown(JsonApiMappingException)
    ex.diagnostic() == MappingDiagnostic.UNKNOWN_PATCH_MEMBER
    ex.propertyPath() == "/attributes/bogus"
  }

  def "omits relationship meta without relationship data"() {
    given:
    def definition =
        new TypedPatchDefinition<>(
        "articles",
        property(PropertyRole.ID, "id", "id", "id"),
        [],
        [
          property(PropertyRole.RELATIONSHIP, "author", "author", "author")
        ],
        null,
        [
          property(PropertyRole.RELATIONSHIP_META, "authorMeta", "authorMeta", "author")
        ])
    def resource =
        resource(
        "articles",
        "1",
        null,
        Relationships.ofRelationships(
        [author: Relationship.metaOnly(Meta.of([displayName: "A"]))]),
        null)

    when:
    binder.bind(resource, definition, "JavaType", Object)

    then:
    backend.constructedProperties.author == new PresenceMarker(false, null)
    backend.constructedProperties.authorMeta == new PresenceMarker(false, null)
  }

  private static TypedPatchProperty<String, String> property(
      PropertyRole role, String logicalName, String externalName, String jsonapiName) {
    return new TypedPatchProperty<>(
        logicalName,
        new SemanticProperty(role, logicalName, externalName, jsonapiName),
        "PatchPresence",
        role != PropertyRole.ID,
        false,
        role == PropertyRole.RESOURCE_META || role == PropertyRole.RELATIONSHIP_META)
  }

  private static SemanticProperty attribute(String logicalName, String jsonapiName) {
    return new SemanticProperty(PropertyRole.ATTRIBUTE, logicalName, jsonapiName, jsonapiName)
  }

  private static SemanticProperty metaProperty() {
    return new SemanticProperty(PropertyRole.RESOURCE_META, "meta", "meta", "meta")
  }

  private static ResourceObject resource(
      String type, String id, Attributes attributes, Relationships relationships, Meta meta) {
    return new ResourceObject(type, id, null, attributes, relationships, null, meta, Map.of())
  }

  static class FakeTypedBackend implements TypedPatchBackend<String, String> {

    final List<String> callOrder = []
    Map<String, Object> constructedProperties

    @Override
    void validateRelationshipLinkageMeta(TypedPatchDefinition<String, String> definition, Class<?> rawType) {
      callOrder << "validateMeta"
    }

    @Override
    Object parseIdentity(String wireIdentifier, Class<?> rawType) {
      callOrder << "identity"
      return "identity:" + wireIdentifier
    }

    @Override
    Object convertRelationship(TypedPatchProperty<String, String> property, RelationshipData data) {
      callOrder << "relationship:" + property.jsonapiName()
      return "converted:" + property.jsonapiName()
    }

    @Override
    Object construct(
        String targetType,
        Map<String, Object> properties,
        Class<?> rawType,
        TypedPatchProperty<String, String> identifier) {
      callOrder << "construct"
      constructedProperties = properties
      return "constructed"
    }
  }
}
