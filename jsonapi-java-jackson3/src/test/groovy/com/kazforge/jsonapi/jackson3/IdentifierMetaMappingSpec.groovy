package com.kazforge.jsonapi.jackson3

import com.kazforge.jsonapi.core.model.Meta
import com.kazforge.jsonapi.core.model.Relationship
import com.kazforge.jsonapi.core.model.RelationshipData
import com.kazforge.jsonapi.core.model.Relationships
import com.kazforge.jsonapi.core.model.ResourceIdentifier
import com.kazforge.jsonapi.core.model.ResourceObject
import com.kazforge.jsonapi.jackson.mapping.IdentifierConverter
import com.kazforge.jsonapi.jackson.diagnostic.JsonApiMappingException
import com.kazforge.jsonapi.jackson.diagnostic.MappingDiagnostic
import com.kazforge.jsonapi.jackson.mapping.RelationshipLinkage
import com.kazforge.jsonapi.jackson3.IdentifierMetaFixtures.EncodedIdMeta
import com.kazforge.jsonapi.jackson3.IdentifierMetaFixtures.GenericIdentifierMetaArticle
import com.kazforge.jsonapi.jackson3.IdentifierMetaFixtures.IdMetaBox
import com.kazforge.jsonapi.jackson3.IdentifierMetaFixtures.NonEmittingIdentifierMetaArticle
import com.kazforge.jsonapi.jackson3.IdentifierMetaFixtures.ScalarIdMeta
import com.kazforge.jsonapi.jackson3.IdentifierMetaFixtures.ScalarSerializedMetaArticle
import com.kazforge.jsonapi.jackson3.IdentifierMetaFixtures.SerializedIdentifierMetaArticle
import com.kazforge.jsonapi.jackson3.IdentifierMetaFixtures.SilentIdMeta
import com.kazforge.jsonapi.jackson3.IdentifierMetaFixtures.SnakeIdMeta
import com.kazforge.jsonapi.jackson3.IdentifierMetaFixtures.SnakeIdentifierMeta
import com.kazforge.jsonapi.jackson3.IdentifierMetaFixtures.WrappedMappedArticle
import com.kazforge.jsonapi.jackson3.IdentifierMetaFixtures.WrappedMappedSetArticle
import com.kazforge.jsonapi.jackson3.LinkageMapperFixtures.FlatAuthor
import com.kazforge.jsonapi.fixtures.domainpatch.AuthorIdMeta
import spock.lang.Specification
import tools.jackson.databind.JavaType
import tools.jackson.databind.PropertyNamingStrategies
import tools.jackson.databind.json.JsonMapper

// Jackson 3 mechanism probes for identifier-meta: JavaType preservation, naming strategies,
// custom serializers (including non-emission and invalid scalar emission), and linkage-mapper
// wiring. Major-neutral RelationshipLinkage container, overlay, inclusion, and PATCH semantics
// live in direct adapter-owned cases.
class IdentifierMetaMappingSpec extends Specification {

  static def mapper() {
    JsonApiJackson3.resourceMapper(JsonMapper.builder().build())
  }

  static def binder() {
    JsonApiJackson3.resourceBinder(JsonMapper.builder().build())
  }

  def "generic JavaType identifier meta is preserved on write and read"() {
    given:
    def article = new GenericIdentifierMetaArticle(
        "1",
        new RelationshipLinkage(ResourceIdentifier.of("people", "p1"), new IdMetaBox<Integer>(7)))

    when:
    def resource = mapper().toResource(article)
    def bound = binder().fromResource(resource, GenericIdentifierMetaArticle)

    then:
    def authorData = resource.relationships().relationships().get("author").data()
    authorData == new RelationshipData.SingleLinkage(
        new ResourceIdentifier("people", "p1", null, Meta.of([value: 7]), [:]))
    bound.author().meta() == new IdMetaBox<Integer>(7)
  }

  def "configured Jackson naming applies to identifier meta members"() {
    given:
    def snakeMapper = JsonApiJackson3.resourceMapper(
        JsonMapper.builder().propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE).build())
    def article = new SnakeIdentifierMeta(
        "1", new RelationshipLinkage(ResourceIdentifier.of("people", "p1"), new SnakeIdMeta("editor")))

    when:
    def resource = snakeMapper.toResource(article)

    then:
    def authorData = resource.relationships().relationships().get("author").data()
    authorData == new RelationshipData.SingleLinkage(
        new ResourceIdentifier("people", "p1", null, Meta.of([display_role: "editor"]), [:]))
  }

  def "type-level serializer encodes identifier meta"() {
    given:
    def article = new SerializedIdentifierMetaArticle(
        "1", new RelationshipLinkage(ResourceIdentifier.of("people", "p1"), new EncodedIdMeta("editor")))

    when:
    def resource = mapper().toResource(article)

    then:
    def authorMembers = ((RelationshipData.SingleLinkage) resource.relationships()
        .relationships().get("author").data()).identifier().meta().members()
    authorMembers == [encoded: "editor"]
  }

  def "serializer non-emission leaves existing to-one identifier meta in place"() {
    given:
    def article = new NonEmittingIdentifierMetaArticle(
        "1",
        new RelationshipLinkage(
        new ResourceIdentifier("people", "p1", null, Meta.of([role: "editor"]), [:]),
        new SilentIdMeta("ignored")))

    when:
    def resource = mapper().toResource(article)

    then:
    def authorData = resource.relationships().relationships().get("author").data()
    authorData == new RelationshipData.SingleLinkage(
        new ResourceIdentifier("people", "p1", null, Meta.of([role: "editor"]), [:]))
  }

  def "custom linkage mapper receives the unwrapped target type"() {
    given:
    def seenTypes = []
    def linkageMapper = { RelationshipData data, JavaType target ->
      seenTypes.add(target)
      def identifier = ((RelationshipData.SingleLinkage) data).identifier()
      return new FlatAuthor(identifier.type(), identifier.id())
    } as RelationshipLinkageMapper
    def binder = JsonApiJackson3.resourceBinder(
        JsonMapper.builder().build(), IdentifierConverter.defaults(), [(FlatAuthor): linkageMapper])
    def resource = new ResourceObject(
        "articles",
        "1",
        null,
        null,
        Relationships.ofRelationships(
        [author: Relationship.withData(
          new RelationshipData.SingleLinkage(
          new ResourceIdentifier("people", "p1", null, Meta.of([role: "editor"]), [:])))]),
        null,
        null,
        [:])

    when:
    def bound = binder.fromResource(resource, WrappedMappedArticle)

    then:
    seenTypes.every { it.rawClass == FlatAuthor }
    bound.author().target() == new FlatAuthor("people", "p1")
    bound.author().meta() == new AuthorIdMeta("editor")
  }

  def "custom to-many mapper cannot desynchronize wrapper target and meta"() {
    given:
    def seenCollectionMapping = false
    def linkageMapper = { RelationshipData data, JavaType target ->
      if (data instanceof RelationshipData.SingleLinkage) {
        def identifier = ((RelationshipData.SingleLinkage) data).identifier()
        return new FlatAuthor(identifier.type(), identifier.id())
      }
      seenCollectionMapping = true
      def identifiers = ((RelationshipData.IdentifierCollectionLinkage) data).identifiers()
      return identifiers.reverse().collect { new FlatAuthor(it.type(), it.id()) } as Set
    } as RelationshipLinkageMapper
    def binder = JsonApiJackson3.resourceBinder(
        JsonMapper.builder().build(), IdentifierConverter.defaults(), [(FlatAuthor): linkageMapper])
    def resource = new ResourceObject(
        "articles",
        "1",
        null,
        null,
        Relationships.ofRelationships(
        [comments: Relationship.withData(
          new RelationshipData.IdentifierCollectionLinkage([
            new ResourceIdentifier("comments", "c1", null, Meta.of([pinned: true]), [:]),
            new ResourceIdentifier("comments", "c2", null, Meta.of([pinned: false]), [:])
          ]))]),
        null,
        null,
        [:])

    when:
    def bound = binder.fromResource(resource, WrappedMappedSetArticle)

    then:
    !seenCollectionMapping
    def byId = bound.comments().collectEntries { [it.target().id(), it] }
    byId.c1.target() == new FlatAuthor("comments", "c1")
    byId.c1.meta().pinned() == true
    byId.c2.target() == new FlatAuthor("comments", "c2")
    byId.c2.meta().pinned() == false
  }

  def "null mapper result for a wrapped to-many occurrence is LINKAGE_MAPPING_FAILED"() {
    given:
    def linkageMapper = { RelationshipData data, JavaType target ->
      null
    } as RelationshipLinkageMapper
    def binder = JsonApiJackson3.resourceBinder(
        JsonMapper.builder().build(), IdentifierConverter.defaults(), [(FlatAuthor): linkageMapper])
    def resource = new ResourceObject(
        "articles",
        "1",
        null,
        null,
        Relationships.ofRelationships(
        [comments: Relationship.withData(
          new RelationshipData.IdentifierCollectionLinkage([
            new ResourceIdentifier("comments", "c1", null, Meta.of([pinned: true]), [:]),
            new ResourceIdentifier("comments", "c2", null, null, [:])
          ]))]),
        null,
        null,
        [:])

    when:
    binder.fromResource(resource, WrappedMappedSetArticle)

    then:
    def e = thrown(JsonApiMappingException)
    e.diagnostic == MappingDiagnostic.LINKAGE_MAPPING_FAILED
    e.propertyPath() == "/relationships/comments/data/0"
  }

  def "converted scalar identifier meta is INVALID_META_TARGET"() {
    given:
    def article = new ScalarSerializedMetaArticle(
        "1", new RelationshipLinkage(ResourceIdentifier.of("people", "p1"), new ScalarIdMeta("editor")))

    when:
    mapper().toResource(article)

    then:
    def e = thrown(JsonApiMappingException)
    e.diagnostic == MappingDiagnostic.INVALID_META_TARGET
    e.propertyPath() == "/relationships/author/data/meta"
  }
}
