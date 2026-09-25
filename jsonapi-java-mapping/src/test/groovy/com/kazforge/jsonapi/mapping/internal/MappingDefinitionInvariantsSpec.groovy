package com.kazforge.jsonapi.mapping.internal

import com.kazforge.jsonapi.diagnostic.JsonApiMappingException
import com.kazforge.jsonapi.diagnostic.MappingDiagnostic
import spock.lang.Specification

class MappingDefinitionInvariantsSpec extends Specification {

  private static final Class<?> RAW = String

  def "returns a valid resource type name unchanged"() {
    expect:
    MappingDefinitionInvariants.requireResourceTypeName(name, RAW) == name

    where:
    name << [
      "articles",
      "a-b_c",
      "ext:member"
    ]
  }

  def "rejects an absent, empty, or invalid resource type name"() {
    when:
    MappingDefinitionInvariants.requireResourceTypeName(name, RAW)

    then:
    def failure = thrown(JsonApiMappingException)
    failure.diagnostic() == diagnostic
    failure.resourceClass() == RAW
    failure.location() == null
    failure.message == message

    where:
    name        | diagnostic                                   | message
    null        | MappingDiagnostic.MISSING_RESOURCE_ANNOTATION | "Missing @JsonApiResource on java.lang.String"
    ""          | MappingDiagnostic.INVALID_RESOURCE_TYPE       | "@JsonApiResource.type() must not be empty on java.lang.String"
    "bad type!" | MappingDiagnostic.INVALID_RESOURCE_TYPE       | "Invalid resource type name: bad type!"
  }

  def "accepts valid per-property JSON:API names, including non-name roles"() {
    when:
    MappingDefinitionInvariants.validateJsonApiName(jsonapiName, role, "logical", RAW)

    then:
    noExceptionThrown()

    where:
    role                           | jsonapiName
    PropertyRole.ATTRIBUTE         | "headline"
    PropertyRole.RELATIONSHIP      | "author"
    PropertyRole.RELATIONSHIP_META | "writtenBy"
    PropertyRole.ID                | "id"
    PropertyRole.LOCAL_ID          | "lid"
    PropertyRole.RESOURCE_META     | "meta"
  }

  def "rejects invalid per-property JSON:API names"() {
    when:
    MappingDefinitionInvariants.validateJsonApiName(jsonapiName, role, "logical", RAW)

    then:
    def failure = thrown(JsonApiMappingException)
    failure.diagnostic() == diagnostic
    failure.resourceClass() == RAW
    failure.location() == null
    failure.message == message

    where:
    role                           | jsonapiName | diagnostic                                               | message
    PropertyRole.ATTRIBUTE         | ""          | MappingDiagnostic.INVALID_ATTRIBUTE_NAME                 | "Invalid JSON:API member name '' for property 'logical'"
    PropertyRole.ATTRIBUTE         | "bad name!" | MappingDiagnostic.INVALID_ATTRIBUTE_NAME                 | "Invalid JSON:API member name 'bad name!' for property 'logical'"
    PropertyRole.ATTRIBUTE         | "id"        | MappingDiagnostic.INVALID_ATTRIBUTE_NAME                 | "Invalid JSON:API member name 'id' for property 'logical'"
    PropertyRole.ATTRIBUTE         | "type"      | MappingDiagnostic.INVALID_ATTRIBUTE_NAME                 | "Invalid JSON:API member name 'type' for property 'logical'"
    PropertyRole.RELATIONSHIP      | "bad name!" | MappingDiagnostic.INVALID_RELATIONSHIP_NAME              | "Invalid JSON:API member name 'bad name!' for property 'logical'"
    PropertyRole.RELATIONSHIP      | "id"        | MappingDiagnostic.INVALID_RELATIONSHIP_NAME              | "Invalid JSON:API member name 'id' for property 'logical'"
    PropertyRole.RELATIONSHIP_META | ""          | MappingDiagnostic.INVALID_RELATIONSHIP_META_TARGET       | "@JsonApiRelationshipMeta.relationship() must not be empty for property 'logical'"
  }

  def "allows one id and one local-id role to coexist"() {
    when:
    MappingDefinitionInvariants.validatePropertyRoles(
        [id("blogId")], [localId("localId")], [], [], [], RAW)

    then:
    noExceptionThrown()
  }

  def "rejects duplicate or missing identity roles"() {
    when:
    MappingDefinitionInvariants.validatePropertyRoles(ids, localIds, [], [], [], RAW)

    then:
    def failure = thrown(JsonApiMappingException)
    failure.diagnostic() == diagnostic
    failure.location() == null
    failure.message == message

    where:
    ids                  | localIds             | diagnostic                            | message
    [id("a"), id("b")]   | []                   | MappingDiagnostic.DUPLICATE_ROLE      | "Multiple id properties found for java.lang.String"
    []                   | [localId("a"), localId("b")] | MappingDiagnostic.DUPLICATE_ROLE | "Multiple local-id properties found for java.lang.String"
    []                   | []                   | MappingDiagnostic.MISSING_IDENTIFIER  | "No id or local-id property found for java.lang.String"
  }

  def "rejects duplicate attribute and relationship member names with container locations"() {
    when:
    MappingDefinitionInvariants.validatePropertyRoles(
        [id("id")], [], attributes, relationships, [], RAW)

    then:
    def failure = thrown(JsonApiMappingException)
    failure.diagnostic() == MappingDiagnostic.NAME_COLLISION
    failure.propertyPath() == propertyPath
    failure.message == message

    where:
    attributes                        | relationships | propertyPath                     | message
    [
      attribute("a", "same"),
      attribute("b", "same")
    ] | [] | "/attributes/same"               | "Duplicate attribute name: same"
    []                                | [
      relationship("a", "same"),
      relationship("b", "same")
    ] | "/relationships/same/data" | "Duplicate relationship name: same"
  }

  def "rejects an attribute and relationship sharing one member name without a location"() {
    when:
    MappingDefinitionInvariants.validatePropertyRoles(
        [id("id")], [], [attribute("a", "same")], [relationship("b", "same")], [], RAW)

    then:
    def failure = thrown(JsonApiMappingException)
    failure.diagnostic() == MappingDiagnostic.NAME_COLLISION
    failure.location() == null
    failure.message == "Attribute and relationship name collision: same"
  }

  def "rejects more than one resource meta property at the meta location"() {
    when:
    MappingDefinitionInvariants.validatePropertyRoles(
        [id("id")], [], [], [], [
          resourceMeta("a"),
          resourceMeta("b")
        ], RAW)

    then:
    def failure = thrown(JsonApiMappingException)
    failure.diagnostic() == MappingDiagnostic.DUPLICATE_ROLE
    failure.propertyPath() == "/meta"
    failure.message ==
        "Multiple resource meta properties found for java.lang.String; " +
        "at most one @JsonApiMeta property is allowed"
  }

  def "resolves relationship meta to the target's JSON:API name by logical identity"() {
    given:
    def seen = [] as Set<String>

    when:
    def metadata =
        MappingDefinitionInvariants.resolveRelationshipMeta(
        "authorMeta", "author-meta", "writtenBy",
        [
          relationship("writtenBy", "author")
        ], seen, RAW)

    then:
    metadata == new SemanticProperty(PropertyRole.RELATIONSHIP_META, "authorMeta", "author-meta", "author")
    seen == ["writtenBy"] as Set<String>
  }

  def "rejects a relationship meta target that is not a mapped relationship"() {
    when:
    MappingDefinitionInvariants.resolveRelationshipMeta(
        "authorMeta", "author-meta", "nonexistent", [], [] as Set<String>, RAW)

    then:
    def failure = thrown(JsonApiMappingException)
    failure.diagnostic() == MappingDiagnostic.UNRESOLVED_RELATIONSHIP_META
    failure.location() == null
    failure.message ==
        "@JsonApiRelationshipMeta for property 'authorMeta' references unknown relationship " +
        "'nonexistent' on java.lang.String"
  }

  def "rejects a second relationship meta property targeting the same relationship"() {
    given:
    def seen = ["writtenBy"] as Set<String>

    when:
    MappingDefinitionInvariants.resolveRelationshipMeta(
        "otherMeta", "other-meta", "writtenBy",
        [
          relationship("writtenBy", "author")
        ], seen, RAW)

    then:
    def failure = thrown(JsonApiMappingException)
    failure.diagnostic() == MappingDiagnostic.DUPLICATE_ROLE
    failure.propertyPath() == "/relationships/author/meta"
    failure.message ==
        "Multiple relationship meta properties target relationship 'writtenBy' on " +
        "java.lang.String; at most one is allowed"
  }

  def "builds resource-relative container locations"() {
    expect:
    MappingDefinitionInvariants.attributeLocation("a/b~c").pointer() == "/attributes/a~1b~0c"
    MappingDefinitionInvariants.relationshipLocation("author").pointer() == "/relationships/author/data"
  }

  private static SemanticPropertyCarrier attribute(String logicalName, String wireName) {
    new Carrier(new SemanticProperty(PropertyRole.ATTRIBUTE, logicalName, wireName, wireName))
  }

  private static SemanticPropertyCarrier relationship(String logicalName, String wireName) {
    new Carrier(new SemanticProperty(PropertyRole.RELATIONSHIP, logicalName, wireName, wireName))
  }

  private static SemanticPropertyCarrier id(String logicalName) {
    new Carrier(new SemanticProperty(PropertyRole.ID, logicalName, logicalName, "id"))
  }

  private static SemanticPropertyCarrier localId(String logicalName) {
    new Carrier(new SemanticProperty(PropertyRole.LOCAL_ID, logicalName, logicalName, "lid"))
  }

  private static SemanticPropertyCarrier resourceMeta(String logicalName) {
    new Carrier(new SemanticProperty(PropertyRole.RESOURCE_META, logicalName, "meta", "meta"))
  }

  private static final class Carrier implements SemanticPropertyCarrier {
    private final SemanticProperty metadata

    Carrier(SemanticProperty metadata) {
      this.metadata = metadata
    }

    @Override
    SemanticProperty metadata() {
      return metadata
    }
  }
}
