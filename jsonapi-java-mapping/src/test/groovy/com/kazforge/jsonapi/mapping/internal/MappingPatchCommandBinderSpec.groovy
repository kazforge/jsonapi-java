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
import com.kazforge.jsonapi.mapping.RelationshipLinkage
import com.kazforge.jsonapi.patch.PatchChange
import spock.lang.Specification

import static com.kazforge.jsonapi.mapping.internal.MappingFakePatchResourceBackend.property

/**
 * Neutral low-level PATCH binder semantics proven against a fake backend: phase order, wire
 * encounter order, absent versus explicit-null attributes, relationship gating and short-circuiting,
 * resource/relationship meta placement, identifier-meta sequencing, and callback boundaries without
 * Jackson.
 */
class MappingPatchCommandBinderSpec extends Specification {

  private MappingFakePatchResourceBackend backend
  private PatchCommandBinder<String, String> binder

  def setup() {
    backend = new MappingFakePatchResourceBackend()
    binder = new PatchCommandBinder<>(backend)
  }

  def "runs the phase order and emits changes in wire encounter order"() {
    given:
    backend.define(
        "articles",
        property(PropertyRole.ID, "id", "id", "id"),
        property(PropertyRole.RESOURCE_META, "meta", "meta", "meta"),
        property(PropertyRole.ATTRIBUTE, "a1", "a1", "a1"),
        property(PropertyRole.ATTRIBUTE, "a2", "a2", "a2"),
        property(PropertyRole.RELATIONSHIP, "r1", "r1", "r1"),
        property(PropertyRole.RELATIONSHIP, "r2", "r2", "r2"),
        property(PropertyRole.RELATIONSHIP_META, "r2Meta", "r2Meta", "r2"))
    backend.directRelationship("r1", false)
    backend.directRelationship("r2", false)
    def resource =
        resource(
        "articles",
        "1",
        Attributes.ofAttributes([a2: "v2", a1: "v1"]),
        Relationships.ofRelationships([
          r2: relationship(
          new RelationshipData.SingleLinkage(ResourceIdentifier.of("people", "p2")),
          Meta.of([rm: 2])),
          r1: relationship(
          new RelationshipData.SingleLinkage(ResourceIdentifier.of("people", "p1")), null)
        ]),
        Meta.of([m: 1]))

    when:
    def command = bind(resource)

    then:
    command.changes() == [
      new PatchChange.ResourceMetaChange("meta", "meta", [m: 1]),
      new PatchChange.AttributeChange("a2", "a2", "v2"),
      new PatchChange.AttributeChange("a1", "a1", "v1"),
      new PatchChange.RelationshipChange(
      "r2", "r2", ResourceIdentifier.of("people", "p2")),
      new PatchChange.RelationshipMetaChange("r2", "r2Meta", [rm: 2]),
      new PatchChange.RelationshipChange(
      "r1", "r1", ResourceIdentifier.of("people", "p1"))
    ]
    backend.callOrder == [
      "validateMetaTargets",
      "identity:id",
      "meta:meta",
      "attribute:a2",
      "attribute:a1",
      "shape:r2",
      "coerce:r2",
      "meta:r2Meta",
      "shape:r1",
      "coerce:r1"
    ]
  }

  def "rejects a resource type mismatch before any backend callback"() {
    given:
    backend.define("articles", property(PropertyRole.ID, "id", "id", "id"))

    when:
    binder.bind(
        resource("people", "1", null, null, null),
        backend.definitionOrFail("articles"),
        "bean",
        Object.class)

    then:
    def failure = thrown(JsonApiMappingException)
    failure.diagnostic() == MappingDiagnostic.RESOURCE_TYPE_MISMATCH
    backend.callOrder == []
  }

  def "validates declared meta targets before identity conversion"() {
    given:
    backend.define("articles", property(PropertyRole.ID, "id", "id", "id"))
    backend.failMetaTargetValidation = true

    when:
    bind(resource("articles", "1", null, null, null))

    then:
    thrown(IllegalStateException)
    backend.metaTargetValidations == ["articles"]
    backend.parsedIdentifiers == []
  }

  def "requires a non-null id and never falls back to another role"() {
    given:
    backend.define("articles", property(PropertyRole.ID, "id", "id", "id"))

    when:
    bind(resource("articles", null, null, null, null))

    then:
    def failure = thrown(JsonApiMappingException)
    failure.diagnostic() == MappingDiagnostic.IDENTIFIER_CONVERSION_FAILED
    failure.propertyPath() == "/id"
    backend.parsedIdentifiers == []
  }

  def "rejects a supplied mapped member without an effective deserialization target"() {
    given:
    backend.define(
        "articles",
        property(PropertyRole.ID, "id", "id", "id"),
        property(PropertyRole.ATTRIBUTE, "a1", "a1", "a1", false))
    def resource = resource("articles", "1", Attributes.ofAttributes([a1: "v1"]), null, null)

    when:
    bind(resource)

    then:
    def failure = thrown(JsonApiMappingException)
    failure.diagnostic() == MappingDiagnostic.NON_DESERIALIZABLE_PROPERTY
    failure.propertyPath() == "/attributes/a1"
    backend.attributeConversions == []
  }

  def "emits no change for absent members and resolves no relationship shape"() {
    given:
    backend.define(
        "articles",
        property(PropertyRole.ID, "id", "id", "id"),
        property(PropertyRole.ATTRIBUTE, "a1", "a1", "a1"),
        property(PropertyRole.RELATIONSHIP, "r1", "r1", "r1"))
    backend.directRelationship("r1", false)

    when:
    def command = bind(resource("articles", "1", null, null, null))

    then:
    command.identity() == "identity:1"
    command.changes() == []
    backend.shapeResolutions == []
    backend.linkageObservations == []
  }

  def "distinguishes explicit-null from present attribute values"() {
    given:
    backend.define(
        "articles",
        property(PropertyRole.ID, "id", "id", "id"),
        property(PropertyRole.ATTRIBUTE, "a1", "a1", "a1"))

    when:
    def explicitNull = bind(resource("articles", "1", Attributes.ofAttributes([a1: null]), null, null))

    then:
    explicitNull.changes() == [
      new PatchChange.AttributeChange("a1", "a1", null)
    ]
    backend.attributeConversions*.rawValue == [null]
  }

  def "short-circuits null and empty linkage without invoking the mapper"() {
    given:
    backend.define(
        "articles",
        property(PropertyRole.ID, "id", "id", "id"),
        property(PropertyRole.RELATIONSHIP, "r1", "r1", "r1"),
        property(PropertyRole.RELATIONSHIP, "r2", "r2", "r2"))
    backend.mappedRelationship("r1", false, "toOneTarget")
    backend.mappedRelationship("r2", true, "toManyTarget")

    when:
    def command =
        bind(
        resource(
        "articles",
        "1",
        null,
        Relationships.ofRelationships([
          r1: relationship(new RelationshipData.NullLinkage(), null),
          r2: relationship(
          new RelationshipData.IdentifierCollectionLinkage(List.of()), null)
        ]),
        null))

    then:
    command.changes() == [
      new PatchChange.RelationshipChange("r1", "r1", null),
      new PatchChange.RelationshipChange("r2", "r2", [])
    ]
    backend.shapeResolutions == ["r1", "r2"]
    backend.linkageObservations == []
  }

  def "emits relationship meta only beside supplied relationship data"() {
    given:
    backend.define(
        "articles",
        property(PropertyRole.ID, "id", "id", "id"),
        property(PropertyRole.RELATIONSHIP, "r1", "r1", "r1"),
        property(PropertyRole.RELATIONSHIP_META, "r1Meta", "r1Meta", "r1"))
    backend.directRelationship("r1", false)
    def resource =
        resource(
        "articles",
        "1",
        null,
        Relationships.ofRelationships([r1: Relationship.metaOnly(Meta.of([rm: 1]))]),
        null)

    when:
    def command = bind(resource)

    then:
    command.changes() == []
    backend.shapeResolutions == []
    backend.metaConversions == []
  }

  def "invokes the linkage mapper and pairs wrapped occurrence identifier meta"() {
    given:
    backend.define(
        "articles",
        property(PropertyRole.ID, "id", "id", "id"),
        property(PropertyRole.RELATIONSHIP, "r1", "r1", "r1"))
    backend.wrappedRelationship(
        "r1", true, "metaToken", new ReadRelationshipShape.Mapped<String>(false, "target"))
    backend.linkageMapping("r1", "mapped-target")
    backend.identifierMetaConversion("metaToken", "converted-meta")
    def resource =
        resource(
        "articles",
        "1",
        null,
        Relationships.ofRelationships([
          r1: relationship(
          new RelationshipData.IdentifierCollectionLinkage([
            new ResourceIdentifier("people", "p1", null, Meta.of([x: 1]), [:]),
            ResourceIdentifier.of("people", "p2")
          ]),
          null)
        ]),
        null)

    when:
    def command = bind(resource)

    then:
    command.changes() == [
      new PatchChange.RelationshipChange("r1", "r1", [
        new RelationshipLinkage<>("mapped-target", "converted-meta"),
        new RelationshipLinkage<>("mapped-target", null)
      ])
    ]
    backend.shapeResolutions == ["r1"]
    backend.linkageObservations*.target == ["target", "target"]
    backend.identifierMetaObservations*.occurrenceIndex == [0]
    backend.coercionInvocations == ["r1"]
  }

  def "skips supplied members unknown to the definition"() {
    given:
    backend.define("articles", property(PropertyRole.ID, "id", "id", "id"))
    def resource =
        resource(
        "articles",
        "1",
        Attributes.ofAttributes([unknownAttr: "v"]),
        Relationships.ofRelationships([
          unknownRel: relationship(
          new RelationshipData.SingleLinkage(ResourceIdentifier.of("people", "p1")), null)
        ]),
        null)

    when:
    def command = bind(resource)

    then:
    command.changes() == []
    backend.attributeConversions == []
    backend.shapeResolutions == []
  }

  private def bind(ResourceObject resource) {
    binder.bind(resource, backend.definitionOrFail(resource.type()), "bean", Object.class)
  }

  private static ResourceObject resource(
      String type, String id, Attributes attributes, Relationships relationships, Meta meta) {
    new ResourceObject(type, id, null, attributes, relationships, null, meta, Map.of())
  }

  private static Relationship relationship(RelationshipData data, Meta meta) {
    new Relationship(data, null, meta, Map.of())
  }
}
