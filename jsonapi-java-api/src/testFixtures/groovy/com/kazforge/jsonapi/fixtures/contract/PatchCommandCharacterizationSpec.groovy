package com.kazforge.jsonapi.fixtures.contract

import com.kazforge.jsonapi.api.JsonApi
import com.kazforge.jsonapi.core.model.Attributes
import com.kazforge.jsonapi.core.model.DocumentData
import com.kazforge.jsonapi.core.model.JsonApiDocument
import com.kazforge.jsonapi.core.model.Meta
import com.kazforge.jsonapi.core.model.Relationship
import com.kazforge.jsonapi.core.model.Relationships
import com.kazforge.jsonapi.core.model.ResourceIdentifier
import com.kazforge.jsonapi.core.model.ResourceObject
import com.kazforge.jsonapi.diagnostic.JsonApiMappingException
import com.kazforge.jsonapi.diagnostic.MappingDiagnostic
import com.kazforge.jsonapi.fixtures.domainpatch.ArticleWithMapMeta
import com.kazforge.jsonapi.fixtures.domainpatch.AuthorIdMeta
import com.kazforge.jsonapi.fixtures.domainpatch.CommentIdMeta
import com.kazforge.jsonapi.fixtures.domainread.FlatArticle
import com.kazforge.jsonapi.fixtures.domainread.FlatMappedTargetArticle
import com.kazforge.jsonapi.fixtures.domainread.FlatRelationshipLinkageArticle
import com.kazforge.jsonapi.fixtures.domainread.FlatWrappedMappedTargetArticle
import com.kazforge.jsonapi.fixtures.domainread.MappedReadTarget
import com.kazforge.jsonapi.fixtures.localid.LocalIdentityArticle
import com.kazforge.jsonapi.mapping.RelationshipLinkage
import com.kazforge.jsonapi.patch.PatchChange
import spock.lang.Specification

/**
 * Low-level {@code PatchCommand} characterization contract observed through the Level-1 {@code
 * readCommand} / {@code bindCommand} entry points: required {@code id} identity that never falls
 * back to {@code lid}, absent versus supplied versus explicit-null attributes, whole relationship
 * replacement for absent/null/single/empty/non-empty linkage, resource and relationship meta,
 * identifier meta retained inside direct and wrapped whole linkage, configured custom linkage
 * mapping, and effective-deserialization participation. Concrete adapter subclasses supply the
 * configured runtimes, including a registry with a {@code MappedReadTarget} mapper.
 */
abstract class PatchCommandCharacterizationSpec extends Specification {

  protected abstract JsonApi api()

  /**
   * Returns a runtime whose configured linkage-mapper registry maps {@code MappedReadTarget}. When
   * {@code nullMapper} is true the registered mapper returns {@code null} for every invocation.
   */
  protected abstract JsonApi mappedApi(boolean nullMapper)

  def "projects id identity with an independently present lid and no lid change"() {
    given:
    def document = singleResource("articles", "1", "L1", Attributes.ofAttributes([title: "T"]), null)

    when:
    def command = api().patches().bindCommand(document, LocalIdentityArticle)

    then:
    command.identity() == "1"
    command.changes() == [
      new PatchChange.AttributeChange("title", "title", "T")
    ]
  }

  def "fails a lid-only resource instead of supplying patch identity"() {
    given:
    def document = singleResource("articles", null, "L1", null, null)

    when:
    api().patches().bindCommand(document, LocalIdentityArticle)

    then:
    def failure = thrown(JsonApiMappingException)
    failure.diagnostic() == MappingDiagnostic.IDENTIFIER_CONVERSION_FAILED
    failure.propertyPath() == "/id"
  }

  def "projects omitted, supplied, and explicit-null attributes"() {
    when:
    def omitted = api().patches().readCommand('{"data":{"type":"articles","id":"1"}}', FlatArticle)
    def supplied =
        api().patches().readCommand(
        '{"data":{"type":"articles","id":"1","attributes":{"title":"T"}}}', FlatArticle)
    def explicitNull =
        api().patches().readCommand(
        '{"data":{"type":"articles","id":"1","attributes":{"title":null}}}', FlatArticle)
    def renamed =
        api().patches().readCommand(
        '{"data":{"type":"articles","id":"1","attributes":{"body-text":"B"}}}', FlatArticle)

    then:
    omitted.changes() == []
    supplied.changes() == [
      new PatchChange.AttributeChange("title", "title", "T")
    ]
    explicitNull.changes() == [
      new PatchChange.AttributeChange("title", "title", null)
    ]
    renamed.changes() == [
      new PatchChange.AttributeChange("body-text", "body", "B")
    ]
  }

  def "replaces absent, null, single, empty, and non-empty relationship linkage"() {
    when:
    def absent =
        api().patches().readCommand('{"data":{"type":"articles","id":"1"}}', FlatArticle)
    def nullLinkage =
        api().patches().readCommand(
        '{"data":{"type":"articles","id":"1","relationships":{"author":{"data":null}}}}',
        FlatArticle)
    def single =
        api().patches().readCommand(
        '{"data":{"type":"articles","id":"1","relationships":{"author":{"data":' +
        '{"type":"people","id":"p1"}}}}}',
        FlatArticle)
    def empty =
        api().patches().readCommand(
        '{"data":{"type":"articles","id":"1","relationships":{"comments":{"data":[]}}}}',
        FlatArticle)
    def nonEmpty =
        api().patches().readCommand(
        '{"data":{"type":"articles","id":"1","relationships":{"comments":{"data":[' +
        '{"type":"comments","id":"c1"},{"type":"comments","id":"c2"}]}}}}',
        FlatArticle)

    then:
    absent.changes() == []
    nullLinkage.changes() == [
      new PatchChange.RelationshipChange("author", "author", null)
    ]
    single.changes() == [
      new PatchChange.RelationshipChange(
      "author", "author", ResourceIdentifier.of("people", "p1"))
    ]
    empty.changes() == [
      new PatchChange.RelationshipChange("comments", "comments", [])
    ]
    nonEmpty.changes() == [
      new PatchChange.RelationshipChange(
      "comments",
      "comments",
      [
        ResourceIdentifier.of("comments", "c1"),
        ResourceIdentifier.of("comments", "c2")
      ])
    ]
  }

  def "projects resource meta and relationship meta with relationship data"() {
    given:
    def json =
        '{"data":{"type":"articles","id":"1","attributes":{"title":"T"},"relationships":' +
        '{"author":{"data":{"type":"people","id":"p1"},"meta":{"displayName":"Alice"}}},' +
        '"meta":{"source":"cms","note":"n"}}}'

    when:
    def command = api().patches().readCommand(json, ArticleWithMapMeta)

    then:
    command.changes() == [
      new PatchChange.ResourceMetaChange("meta", "meta", [source: "cms", note: "n"]),
      new PatchChange.AttributeChange("title", "title", "T"),
      new PatchChange.RelationshipChange(
      "author", "author", ResourceIdentifier.of("people", "p1")),
      new PatchChange.RelationshipMetaChange("author", "authorMeta", [displayName: "Alice"])
    ]
  }

  def "does not project relationship meta without relationship data"() {
    given:
    def relationships =
        Relationships.ofRelationships(
        [author: Relationship.metaOnly(Meta.of([displayName: "Alice"]))])
    def document = singleResource("articles", "1", null, null, relationships)

    when:
    def command = api().patches().bindCommand(document, ArticleWithMapMeta)

    then:
    command.changes() == []
  }

  def "retains identifier meta inside direct whole linkage"() {
    when:
    def toOne =
        api().patches().readCommand(
        '{"data":{"type":"articles","id":"1","relationships":{"author":{"data":' +
        '{"type":"people","id":"p1","meta":{"role":"editor"}}}}}}',
        FlatArticle)
    def toMany =
        api().patches().readCommand(
        '{"data":{"type":"articles","id":"1","relationships":{"comments":{"data":[' +
        '{"type":"comments","id":"c1","meta":{"pinned":true}}]}}}}',
        FlatArticle)

    then:
    toOne.changes() == [
      new PatchChange.RelationshipChange(
      "author",
      "author",
      new ResourceIdentifier("people", "p1", null, Meta.of([role: "editor"]), [:]))
    ]
    toMany.changes() == [
      new PatchChange.RelationshipChange(
      "comments",
      "comments",
      [
        new ResourceIdentifier("comments", "c1", null, Meta.of([pinned: true]), [:])
      ])
    ]
  }

  def "retains identifier meta inside wrapped whole linkage"() {
    when:
    def toOne =
        api().patches().readCommand(
        '{"data":{"type":"articles","id":"1","relationships":{"author":{"data":' +
        '{"type":"people","id":"p1","meta":{"role":"editor"}}}}}}',
        FlatRelationshipLinkageArticle)
    def toMany =
        api().patches().readCommand(
        '{"data":{"type":"articles","id":"1","relationships":{"comments":{"data":[' +
        '{"type":"comments","id":"c1","meta":{"pinned":true}}]}}}}',
        FlatRelationshipLinkageArticle)

    then:
    toOne.changes() == [
      new PatchChange.RelationshipChange(
      "author",
      "author",
      new RelationshipLinkage<>(
      new ResourceIdentifier("people", "p1", null, Meta.of([role: "editor"]), [:]),
      new AuthorIdMeta("editor")))
    ]
    toMany.changes() == [
      new PatchChange.RelationshipChange(
      "comments",
      "comments",
      [
        new RelationshipLinkage<>(
        new ResourceIdentifier("comments", "c1", null, Meta.of([pinned: true]), [:]),
        new CommentIdMeta(true))
      ])
    ]
  }

  def "maps configured custom linkage targets"() {
    given:
    def ordinary =
        '{"data":{"type":"articles","id":"1","relationships":{' +
        '"author":{"data":{"type":"people","id":"p1"}},' +
        '"contributors":{"data":[{"type":"people","id":"p1"},{"type":"people","id":"p2"}]}}}}'
    def wrapped =
        '{"data":{"type":"articles","id":"1","relationships":{"comments":{"data":[' +
        '{"type":"comments","id":"c1","meta":{"pinned":true}},{"type":"comments","id":"c2"}]}}}}'

    when:
    def ordinaryCommand = mappedApi(false).patches().readCommand(ordinary, FlatMappedTargetArticle)
    def wrappedCommand =
        mappedApi(false).patches().readCommand(wrapped, FlatWrappedMappedTargetArticle)

    then:
    ordinaryCommand.changes() == [
      new PatchChange.RelationshipChange("author", "author", new MappedReadTarget("people", "p1")),
      new PatchChange.RelationshipChange(
      "contributors",
      "contributors",
      [
        new MappedReadTarget("people", "p1"),
        new MappedReadTarget("people", "p2")
      ])
    ]
    wrappedCommand.changes() == [
      new PatchChange.RelationshipChange(
      "comments",
      "comments",
      [
        new RelationshipLinkage<>(new MappedReadTarget("comments", "c1"), new CommentIdMeta(true)),
        new RelationshipLinkage<>(new MappedReadTarget("comments", "c2"), null)
      ])
    ]
  }

  def "projects null custom-mapper results as null relationship values"() {
    given:
    def toOne =
        '{"data":{"type":"articles","id":"1","relationships":{"author":{"data":' +
        '{"type":"people","id":"p1"}}}}}'
    def toMany =
        '{"data":{"type":"articles","id":"1","relationships":{"comments":{"data":[' +
        '{"type":"comments","id":"c1"},{"type":"comments","id":"c2"}]}}}}'

    when:
    def ordinary = mappedApi(true).patches().readCommand(toOne, FlatMappedTargetArticle)
    def wrapped = mappedApi(true).patches().readCommand(toOne, FlatWrappedMappedTargetArticle)

    then:
    ordinary.changes() == [
      new PatchChange.RelationshipChange("author", "author", null)
    ]
    wrapped.changes() == [
      new PatchChange.RelationshipChange("author", "author", null)
    ]

    when:
    mappedApi(true).patches().readCommand(toMany, FlatWrappedMappedTargetArticle)

    then:
    def failure = thrown(JsonApiMappingException)
    failure.diagnostic() == MappingDiagnostic.LINKAGE_MAPPING_FAILED
    failure.propertyPath() == "/relationships/comments/data/0"
  }

  def "does not block a PATCH on serialization-only meta declarations"() {
    when:
    def resourceMeta =
        api().patches().readCommand(
        '{"data":{"type":"serialization-only-meta","id":"1"}}',
        SerializationOnlyResourceMetaArticle)
    def relationshipMeta =
        api().patches().readCommand(
        '{"data":{"type":"serialization-only-rel-meta","id":"1"}}',
        SerializationOnlyRelationshipMetaArticle)
    def identifierMeta =
        api().patches().readCommand(
        '{"data":{"type":"serialization-only-id-meta","id":"1"}}',
        SerializationOnlyIdentifierMetaArticle)

    then:
    resourceMeta.changes() == []
    relationshipMeta.changes() == []
    identifierMeta.changes() == []
  }

  def "fails supplied serialization-only meta as non-deserializable at the member location"() {
    when:
    api().patches().readCommand(
        '{"data":{"type":"serialization-only-meta","id":"1","meta":{"source":"cms"}}}',
        SerializationOnlyResourceMetaArticle)

    then:
    def resourceMetaFailure = thrown(JsonApiMappingException)
    resourceMetaFailure.diagnostic() == MappingDiagnostic.NON_DESERIALIZABLE_PROPERTY
    resourceMetaFailure.propertyPath() == "/meta"

    when:
    api().patches().readCommand(
        '{"data":{"type":"serialization-only-rel-meta","id":"1","relationships":' +
        '{"author":{"data":{"type":"people","id":"p1"},"meta":{"displayName":"Alice"}}}}}',
        SerializationOnlyRelationshipMetaArticle)

    then:
    def relationshipMetaFailure = thrown(JsonApiMappingException)
    relationshipMetaFailure.diagnostic() == MappingDiagnostic.NON_DESERIALIZABLE_PROPERTY
    relationshipMetaFailure.propertyPath() == "/relationships/author/meta"

    when:
    api().patches().readCommand(
        '{"data":{"type":"serialization-only-id-meta","id":"1","relationships":' +
        '{"author":{"data":{"type":"people","id":"p1","meta":{"role":"editor"}}}}}}',
        SerializationOnlyIdentifierMetaArticle)

    then:
    def identifierMetaFailure = thrown(JsonApiMappingException)
    identifierMetaFailure.diagnostic() == MappingDiagnostic.NON_DESERIALIZABLE_PROPERTY
    identifierMetaFailure.propertyPath() == "/relationships/author/data"
  }

  def "binds a deserialization-only mapped property"() {
    when:
    def command =
        api().patches().readCommand(
        '{"data":{"type":"binding-setter","id":"1","attributes":{"title":"T"}}}',
        BindingSetterOnlyArticle)

    then:
    command.changes() == [
      new PatchChange.AttributeChange("title", "title", "T")
    ]
  }

  def "fails a supplied mapped property without an effective deserialization target"() {
    when:
    api().patches().readCommand(
        '{"data":{"type":"binding-getter","id":"1","attributes":{"title":"T"}}}',
        BindingGetterOnlyArticle)

    then:
    def failure = thrown(JsonApiMappingException)
    failure.diagnostic() == MappingDiagnostic.NON_DESERIALIZABLE_PROPERTY
    failure.propertyPath() == "/attributes/title"
  }

  private static JsonApiDocument singleResource(
      String type,
      String id,
      String lid,
      Attributes attributes,
      Relationships relationships) {
    JsonApiDocument.withData(
        new DocumentData.SingleResource(
        new ResourceObject(type, id, lid, attributes, relationships, null, null, Map.of())))
  }
}
