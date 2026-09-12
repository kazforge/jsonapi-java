package com.kazforge.jsonapi.jackson2

import com.kazforge.jsonapi.core.model.ResourceIdentifier
import com.kazforge.jsonapi.jackson.diagnostic.JsonApiMappingException
import com.kazforge.jsonapi.jackson.diagnostic.MappingDiagnostic
import com.kazforge.jsonapi.jackson.mapping.RelationshipLinkage
import com.kazforge.jsonapi.fixtures.domainpatch.ArticleWithMapMeta
import com.kazforge.jsonapi.fixtures.domainpatch.AuthorIdMeta
import com.kazforge.jsonapi.fixtures.domainpatch.AuthorMeta
import com.kazforge.jsonapi.fixtures.domainpatch.WholeMetaTargetFixtures
import com.kazforge.jsonapi.fixtures.domainwrite.WriteDiagnosticsFixtures
import spock.lang.Specification
import spock.lang.Unroll
import com.fasterxml.jackson.databind.json.JsonMapper

class DomainResourceWriterDiagnosticsSpec extends Specification {

  @Unroll
  def "resource-shape write diagnostic #expectedDiagnostic for #carrier.class.simpleName reports no property path or location"() {
    given:
    def mapper = JsonApiJackson2.resourceMapper(JsonMapper.builder().build())

    when:
    mapper.toResource(carrier)

    then:
    def ex = thrown(JsonApiMappingException)
    ex.diagnostic() == expectedDiagnostic
    ex.propertyPath() == null
    ex.location() == null

    where:
    carrier | expectedDiagnostic
    new Object() | MappingDiagnostic.MISSING_RESOURCE_ANNOTATION
    new WriteDiagnosticsFixtures.EmptyTypeEntity("1") | MappingDiagnostic.INVALID_RESOURCE_TYPE
    new WriteDiagnosticsFixtures.InvalidTypeEntity("1") | MappingDiagnostic.INVALID_RESOURCE_TYPE
    new WriteDiagnosticsFixtures.NoIdEntity("test") | MappingDiagnostic.MISSING_IDENTIFIER
    new WriteDiagnosticsFixtures.DuplicateRoleEntity("1") | MappingDiagnostic.DUPLICATE_ROLE
    new WriteDiagnosticsFixtures.NameCollisionEntity("1", "a", "b") | MappingDiagnostic.NAME_COLLISION
    new WriteDiagnosticsFixtures.FieldOnlyNameCollisionEntity("1", "a", "b") | MappingDiagnostic.NAME_COLLISION
    new WriteDiagnosticsFixtures.InvalidAttrNameEntity("1", "v") | MappingDiagnostic.INVALID_ATTRIBUTE_NAME
    new WriteDiagnosticsFixtures.ReservedAttrNameEntity("1", "v") | MappingDiagnostic.INVALID_ATTRIBUTE_NAME
    new WriteDiagnosticsFixtures.InvalidRelNameEntity("1", "o") | MappingDiagnostic.INVALID_RELATIONSHIP_NAME
    new WriteDiagnosticsFixtures.ReservedRelNameEntity("1", "o") | MappingDiagnostic.NAME_COLLISION
    new WholeMetaTargetFixtures.UnmappedRelationshipMetaArticle("1", new AuthorMeta("x")) | MappingDiagnostic.UNRESOLVED_RELATIONSHIP_META
  }

  @Unroll
  def "value-level write diagnostic #expectedDiagnostic for #carrier.class.simpleName at #propertyPath reports a location"() {
    given:
    def mapper = JsonApiJackson2.resourceMapper(JsonMapper.builder().build())

    when:
    mapper.toResource(carrier)

    then:
    def ex = thrown(JsonApiMappingException)
    ex.diagnostic() == expectedDiagnostic
    ex.propertyPath() == propertyPath
    ex.location() != null

    where:
    carrier | expectedDiagnostic | propertyPath
    new WriteDiagnosticsFixtures.NullIdEntity(null) | MappingDiagnostic.MISSING_IDENTIFIER | "/id"
    new WriteDiagnosticsFixtures.DuplicateAttrNameEntity("1", "a", "b") | MappingDiagnostic.NAME_COLLISION | "/attributes/same"
    new WriteDiagnosticsFixtures.DuplicateRelNameEntity("1", "a", "b") | MappingDiagnostic.NAME_COLLISION | "/relationships/same/data"
    new WriteDiagnosticsFixtures.FailingAttrEntity("1", "anything") | MappingDiagnostic.UNSUPPORTED_ATTRIBUTE_VALUE | "/attributes/badAttr"
    new WriteDiagnosticsFixtures.RenamedFailingAttrEntity("1", "anything") | MappingDiagnostic.UNSUPPORTED_ATTRIBUTE_VALUE | "/attributes/body-text"
    new WriteDiagnosticsFixtures.FailingIdEntity("1") | MappingDiagnostic.MISSING_IDENTIFIER | "/id"
    new WriteDiagnosticsFixtures.MissingAccessorEntity("1").tap { setSecret("hidden") } | MappingDiagnostic.MISSING_ACCESSOR | "/attributes/secret"
    new WriteDiagnosticsFixtures.RenamedArrayRelEntity("1", [1L, 2L] as long[]) | MappingDiagnostic.UNSUPPORTED_RELATIONSHIP_VALUE | "/relationships/ext-values/data"
    new WriteDiagnosticsFixtures.RenamedMixedRelEntity("1", List.of(ResourceIdentifier.of("comments", "1"), new Object())) | MappingDiagnostic.UNSUPPORTED_RELATIONSHIP_VALUE | "/relationships/ext-items/data"
    new WriteDiagnosticsFixtures.RenamedBagRelEntity("1", new WriteDiagnosticsFixtures.RawBag(new Object())) | MappingDiagnostic.UNSUPPORTED_RELATIONSHIP_COLLECTION_TYPE | "/relationships/ext-bag/data"
    new WriteDiagnosticsFixtures.RawRelationshipLinkageEntity("1", new RelationshipLinkage<>(ResourceIdentifier.of("people", "p1"), null)) | MappingDiagnostic.UNRESOLVED_GENERIC_TYPE | "/relationships/author/data"
    new WriteDiagnosticsFixtures.NestedRelationshipLinkageEntity(
        "1",
        new RelationshipLinkage<RelationshipLinkage<ResourceIdentifier, AuthorIdMeta>, AuthorIdMeta>(
        new RelationshipLinkage<ResourceIdentifier, AuthorIdMeta>(
        ResourceIdentifier.of("people", "p1"), new AuthorIdMeta("editor")),
        new AuthorIdMeta("editor"))) | MappingDiagnostic.INVALID_IDENTIFIER_META_TARGET | "/relationships/author/data/meta"
    new WriteDiagnosticsFixtures.ScalarMetaRelationshipLinkageEntity("1", new RelationshipLinkage<>(ResourceIdentifier.of("people", "p1"), "editor")) | MappingDiagnostic.INVALID_IDENTIFIER_META_TARGET | "/relationships/author/data/meta"
    new WriteDiagnosticsFixtures.ListMetaRelationshipLinkageEntity("1", new RelationshipLinkage<>(ResourceIdentifier.of("people", "p1"), List.of(new AuthorIdMeta("editor")))) | MappingDiagnostic.INVALID_IDENTIFIER_META_TARGET | "/relationships/author/data/meta"
    new WriteDiagnosticsFixtures.EmptyOptionalIdEntity(Optional.empty(), "Title") | MappingDiagnostic.MISSING_IDENTIFIER | "/id"
    new WriteDiagnosticsFixtures.ObjectElementListRelEntity("1", List.of(new Object())) | MappingDiagnostic.UNSUPPORTED_RELATIONSHIP_COLLECTION_TYPE | "/relationships/items/data"
    new WriteDiagnosticsFixtures.RenamedMixedRelEntity("1", List.of(new Object(), ResourceIdentifier.of("comments", "1"))) | MappingDiagnostic.UNSUPPORTED_RELATIONSHIP_VALUE | "/relationships/ext-items/data"
    new WholeMetaTargetFixtures.DuplicateMetaArticle("1", "a", "b") | MappingDiagnostic.DUPLICATE_ROLE | "/meta"
    new WholeMetaTargetFixtures.DuplicateRelationshipMetaArticle("1", ResourceIdentifier.of("people", "p1"), "a", "b") | MappingDiagnostic.DUPLICATE_ROLE | "/relationships/author/meta"
    new WholeMetaTargetFixtures.ScalarMetaArticle("1", "x") | MappingDiagnostic.INVALID_META_TARGET | "/meta"
    new WholeMetaTargetFixtures.ListMetaArticle("1", List.of("x")) | MappingDiagnostic.INVALID_META_TARGET | "/meta"
    new WholeMetaTargetFixtures.UuidMetaArticle("1", null) | MappingDiagnostic.INVALID_META_TARGET | "/meta"
    new WholeMetaTargetFixtures.InstantMetaArticle("1", null) | MappingDiagnostic.INVALID_META_TARGET | "/meta"
    new WholeMetaTargetFixtures.UriMetaArticle("1", null) | MappingDiagnostic.INVALID_META_TARGET | "/meta"
    new WholeMetaTargetFixtures.ObjectMetaArticle("1", "scalar") | MappingDiagnostic.INVALID_META_TARGET | "/meta"
    new ArticleWithMapMeta("1", "T", null, Map.of("", "bad"), null) | MappingDiagnostic.INVALID_META_TARGET | "/meta"
    new ArticleWithMapMeta("1", "T", null, null, Map.of("", "bad")) | MappingDiagnostic.INVALID_META_TARGET | "/relationships/author/meta"
  }
}
