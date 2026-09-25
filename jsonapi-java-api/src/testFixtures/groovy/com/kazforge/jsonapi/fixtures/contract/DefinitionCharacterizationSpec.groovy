package com.kazforge.jsonapi.fixtures.contract

import com.kazforge.jsonapi.api.JsonApi
import com.kazforge.jsonapi.core.model.ResourceIdentifier
import com.kazforge.jsonapi.diagnostic.JsonApiMappingException
import com.kazforge.jsonapi.diagnostic.MappingDiagnostic
import com.kazforge.jsonapi.fixtures.domainpatch.ArticleMeta
import com.kazforge.jsonapi.fixtures.domainpatch.AuthorMeta
import com.kazforge.jsonapi.fixtures.domainpatch.WholeMetaTargetFixtures
import com.kazforge.jsonapi.fixtures.domainwrite.WriteDiagnosticsFixtures
import groovy.json.JsonSlurper
import spock.lang.Specification

/**
 * Resource-level mapping-definition characterization contract. Every listed invariant is observed
 * through the Level-1 {@code writeOne} entry point as a {@link JsonApiMappingException} carrying the
 * diagnostic code, the resource class, the resource-relative property path, and the exact message,
 * because these definition semantics are shared by both Jackson adapters rather than adapter-local
 * diagnostics. It also pins the cross-phase precedence that does not depend on property order,
 * relationship-meta target resolution by logical identity under the target's wire name, and a
 * representative read-parity table through {@code readOne}. Concrete adapter subclasses supply the
 * configured runtime only.
 */
abstract class DefinitionCharacterizationSpec extends Specification {

  protected abstract JsonApi api()

  def "rejects invalid mapping definitions through writeOne with the shared diagnostic"() {
    when:
    api().resources().writeOne(carrier)

    then:
    def failure = thrown(JsonApiMappingException)
    failure.diagnostic() == diagnostic
    failure.resourceClass() == carrier.class
    failure.propertyPath() == propertyPath
    failure.message == message

    where:
    carrier | diagnostic | propertyPath | message
    // Resource-type phase.
    new Object() | MappingDiagnostic.MISSING_RESOURCE_ANNOTATION | null |
        "Missing @JsonApiResource on java.lang.Object"
    new WriteDiagnosticsFixtures.EmptyTypeEntity("1") | MappingDiagnostic.INVALID_RESOURCE_TYPE | null |
        "@JsonApiResource.type() must not be empty on " + WriteDiagnosticsFixtures.EmptyTypeEntity.name
    new WriteDiagnosticsFixtures.InvalidTypeEntity("1") | MappingDiagnostic.INVALID_RESOURCE_TYPE | null |
        "Invalid resource type name: bad type!"
    // Per-property JSON:API name phase.
    new WriteDiagnosticsFixtures.InvalidAttrNameEntity("1", "v") | MappingDiagnostic.INVALID_ATTRIBUTE_NAME | null |
        "Invalid JSON:API member name 'bad name!' for property 'value'"
    new WriteDiagnosticsFixtures.ReservedAttrNameEntity("1", "v") | MappingDiagnostic.INVALID_ATTRIBUTE_NAME | null |
        "Invalid JSON:API member name 'type' for property 'value'"
    new WriteDiagnosticsFixtures.InvalidRelNameEntity("1", "o") | MappingDiagnostic.INVALID_RELATIONSHIP_NAME | null |
        "Invalid JSON:API member name 'bad name!' for property 'other'"
    new WriteDiagnosticsFixtures.ReservedRelWireNameEntity("1", "o") | MappingDiagnostic.INVALID_RELATIONSHIP_NAME | null |
        "Invalid JSON:API member name 'id' for property 'other'"
    new WholeMetaTargetFixtures.EmptyRelationshipMetaTargetArticle("1", new AuthorMeta("x")) | MappingDiagnostic.INVALID_RELATIONSHIP_META_TARGET | null |
        "@JsonApiRelationshipMeta.relationship() must not be empty for property 'authorMeta'"
    // Resource-level role phase.
    new WriteDiagnosticsFixtures.NoIdEntity("test") | MappingDiagnostic.MISSING_IDENTIFIER | null |
        "No id or local-id property found for " + WriteDiagnosticsFixtures.NoIdEntity.name
    new WriteDiagnosticsFixtures.DuplicateIdEntity("1", "2") | MappingDiagnostic.DUPLICATE_ROLE | null |
        "Multiple id properties found for " + WriteDiagnosticsFixtures.DuplicateIdEntity.name
    new WriteDiagnosticsFixtures.DuplicateLidEntity("1", "a", "b") | MappingDiagnostic.DUPLICATE_ROLE | null |
        "Multiple local-id properties found for " + WriteDiagnosticsFixtures.DuplicateLidEntity.name
    new WholeMetaTargetFixtures.DuplicateMetaArticle("1", "a", "b") | MappingDiagnostic.DUPLICATE_ROLE | "/meta" |
        "Multiple resource meta properties found for " + WholeMetaTargetFixtures.DuplicateMetaArticle.name +
        "; at most one @JsonApiMeta property is allowed"
    new WriteDiagnosticsFixtures.DuplicateAttrNameEntity("1", "a", "b") | MappingDiagnostic.NAME_COLLISION | "/attributes/same" |
        "Duplicate attribute name: same"
    new WriteDiagnosticsFixtures.DuplicateRelNameEntity("1", "a", "b") | MappingDiagnostic.NAME_COLLISION | "/relationships/same/data" |
        "Duplicate relationship name: same"
    // Relationship-meta binding phase.
    new WholeMetaTargetFixtures.UnmappedRelationshipMetaArticle("1", new AuthorMeta("x")) | MappingDiagnostic.UNRESOLVED_RELATIONSHIP_META | null |
        "@JsonApiRelationshipMeta for property 'authorMeta' references unknown relationship 'nonexistent' on " +
        WholeMetaTargetFixtures.UnmappedRelationshipMetaArticle.name
    new WholeMetaTargetFixtures.DuplicateRelationshipMetaArticle("1", ResourceIdentifier.of("people", "p1"), "a", "b") | MappingDiagnostic.DUPLICATE_ROLE | "/relationships/author/meta" |
        "Multiple relationship meta properties target relationship 'author' on " +
        WholeMetaTargetFixtures.DuplicateRelationshipMetaArticle.name + "; at most one is allowed"
    new WholeMetaTargetFixtures.RenamedDuplicateRelationshipMetaArticle("1", ResourceIdentifier.of("people", "p1"), "a", "b") | MappingDiagnostic.DUPLICATE_ROLE | "/relationships/author/meta" |
        "Multiple relationship meta properties target relationship 'writtenBy' on " +
        WholeMetaTargetFixtures.RenamedDuplicateRelationshipMetaArticle.name + "; at most one is allowed"
    // Cross-phase precedence, independent of property order.
    new WriteDiagnosticsFixtures.InvalidTypeAndAttrNameEntity("1", "v") | MappingDiagnostic.INVALID_RESOURCE_TYPE | null |
        "Invalid resource type name: bad type!"
    new WriteDiagnosticsFixtures.InvalidAttrAndUnmappedMetaEntity("1", "v", new AuthorMeta("x")) | MappingDiagnostic.INVALID_ATTRIBUTE_NAME | null |
        "Invalid JSON:API member name 'bad name!' for property 'value'"
    new WholeMetaTargetFixtures.UnmappedRelationshipMetaNoIdArticle(new AuthorMeta("x")) | MappingDiagnostic.UNRESOLVED_RELATIONSHIP_META | null |
        "@JsonApiRelationshipMeta for property 'authorMeta' references unknown relationship 'nonexistent' on " +
        WholeMetaTargetFixtures.UnmappedRelationshipMetaNoIdArticle.name
    new WholeMetaTargetFixtures.DuplicateMetaNoIdArticle("a", "b") | MappingDiagnostic.MISSING_IDENTIFIER | null |
        "No id or local-id property found for " + WholeMetaTargetFixtures.DuplicateMetaNoIdArticle.name
  }

  def "resolves relationship meta by logical identity under the target's renamed wire name"() {
    when:
    def document =
        new JsonSlurper().parseText(
        api().resources().writeOne(
        new WholeMetaTargetFixtures.RenamedRelationshipMetaArticle(
        "1",
        "T",
        ResourceIdentifier.of("people", "p1"),
        new ArticleMeta("cms", "n"),
        new AuthorMeta("Alice")))) as Map<String, Object>

    then:
    document.data.relationships.author.data == [type: "people", id: "p1"]
    document.data.relationships.author.meta == [displayName: "Alice"]
    document.data.meta == [source: "cms", note: "n"]
    !document.data.relationships.containsKey("writtenBy")
  }

  def "rejects invalid mapping definitions through readOne with the shared diagnostic"() {
    when:
    api().resources().readOne('{"data":{"type":"articles","id":"1"}}', target)

    then:
    def failure = thrown(JsonApiMappingException)
    failure.diagnostic() == diagnostic
    failure.resourceClass() == target
    failure.propertyPath() == propertyPath

    where:
    target | diagnostic | propertyPath
    WriteDiagnosticsFixtures.EmptyTypeEntity | MappingDiagnostic.INVALID_RESOURCE_TYPE | null
    WriteDiagnosticsFixtures.InvalidAttrNameEntity | MappingDiagnostic.INVALID_ATTRIBUTE_NAME | null
    WholeMetaTargetFixtures.DuplicateMetaArticle | MappingDiagnostic.DUPLICATE_ROLE | "/meta"
    WholeMetaTargetFixtures.UnmappedRelationshipMetaArticle | MappingDiagnostic.UNRESOLVED_RELATIONSHIP_META | null
  }
}
