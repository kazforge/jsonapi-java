package com.kazforge.jsonapi.mapping.internal

import static com.kazforge.jsonapi.mapping.internal.MappingFakeWriteResourceBackend.property
import static com.kazforge.jsonapi.mapping.internal.PropertyRole.ID
import static com.kazforge.jsonapi.mapping.internal.PropertyRole.RELATIONSHIP

import com.kazforge.jsonapi.core.model.Meta
import com.kazforge.jsonapi.core.model.RelationshipData
import com.kazforge.jsonapi.core.model.ResourceIdentifier
import com.kazforge.jsonapi.diagnostic.JsonApiMappingException
import com.kazforge.jsonapi.diagnostic.MappingDiagnostic
import com.kazforge.jsonapi.mapping.RelationshipLinkage
import com.kazforge.jsonapi.mapping.internal.MappingFakeWriteResourceBackend.MetaEnrichment
import com.kazforge.jsonapi.mapping.internal.MappingFakeWriteResourceBackend.TargetResolution
import spock.lang.Specification

class MappingAdvancedRelationshipDataSpec extends Specification {

  private final MappingFakeWriteResourceBackend backend = new MappingFakeWriteResourceBackend()
  private final BasicResourceWriter<String, String> writer = new BasicResourceWriter<>(backend)

  def "writes a to-one null value as explicit-null linkage without resolving a target"() {
    given:
    def shape = RelationshipShape.ordinary(false, 'people')

    when:
    def linkage = writer.relationshipData(domain(), authorProperty(), shape, null, backend.observingTargetResolver(), backend.observingMetaEnricher())

    then:
    linkage == RelationshipData.NullLinkage.INSTANCE
    backend.targetResolutions.isEmpty()
    backend.metaEnrichments.isEmpty()
  }

  def "writes a to-one domain object through the lazy target callback"() {
    given:
    backend.define('articles', property(ID, 'id', 'id', 'id'))
    backend.define('people', property(ID, 'id', 'id', 'id'))
    backend.define('authors', property(ID, 'id', 'id', 'id'))
    def target = domain('id': 'p1')
    backend.mapEffectiveType(target, 'authors')
    def shape = RelationshipShape.ordinary(false, 'people')

    when:
    def linkage = writer.relationshipData(domain(), authorProperty(), shape, target, backend.observingTargetResolver(), backend.observingMetaEnricher())

    then:
    (linkage as RelationshipData.SingleLinkage).identifier() == ResourceIdentifier.of('authors', 'p1')
    backend.targetResolutions.size() == 1
    with(backend.targetResolutions.first()) {
      it.target == target
      it.declaredTargetToken == 'people'
      it.location.pointer() == '/relationships/author/data'
    }
    backend.metaEnrichments.isEmpty()
  }

  def "passes a direct to-one ResourceIdentifier through without resolving a target"() {
    given:
    def identifier = ResourceIdentifier.of('people', 'p1')
    def shape = RelationshipShape.ordinary(false, 'people')

    when:
    def linkage = writer.relationshipData(domain(), authorProperty(), shape, identifier, backend.observingTargetResolver(), backend.observingMetaEnricher())

    then:
    linkage == new RelationshipData.SingleLinkage(identifier)
    backend.targetResolutions.isEmpty()
    backend.metaEnrichments.isEmpty()
  }

  def "passes a direct to-one RelationshipData value through without resolving a target"() {
    given:
    def linkage = new RelationshipData.IdentifierCollectionLinkage([
      ResourceIdentifier.of('comments', 'c1')
    ])
    def shape = RelationshipShape.ordinary(false, 'people')

    when:
    def data = writer.relationshipData(domain(), authorProperty(), shape, linkage, backend.observingTargetResolver(), backend.observingMetaEnricher())

    then:
    data == linkage
    backend.targetResolutions.isEmpty()
    backend.metaEnrichments.isEmpty()
  }

  def "maps a to-one wrapper occurrence with null occurrence meta to its target's linkage"() {
    given:
    backend.define('articles', property(ID, 'id', 'id', 'id'))
    backend.define('people', property(ID, 'id', 'id', 'id'))
    def target = domain('id': 'p1')
    def shape = RelationshipShape.wrapper(false, 'authorMeta', RelationshipShape.ordinary(false, 'people'))

    when:
    def linkage = writer.relationshipData(domain(), authorProperty(), shape, new RelationshipLinkage<>(target, null), backend.observingTargetResolver(), backend.observingMetaEnricher())

    then:
    (linkage as RelationshipData.SingleLinkage).identifier() == ResourceIdentifier.of('people', 'p1')
    backend.targetResolutions.size() == 1
    with(backend.targetResolutions.first()) {
      it.declaredTargetToken == 'people'
      it.location.pointer() == '/relationships/author/data'
    }
    backend.metaEnrichments.isEmpty()
  }

  def "enriches a to-one wrapper occurrence with present identifier meta"() {
    given:
    backend.define('articles', property(ID, 'id', 'id', 'id'))
    backend.define('people', property(ID, 'id', 'id', 'id'))
    def target = domain('id': 'p1')
    def shape = RelationshipShape.wrapper(false, 'authorMeta', RelationshipShape.ordinary(false, 'people'))

    when:
    def linkage = writer.relationshipData(domain(), authorProperty(), shape, new RelationshipLinkage<>(target, 'editor'), backend.observingTargetResolver(), backend.observingMetaEnricher())

    then:
    (linkage as RelationshipData.SingleLinkage).identifier() ==
        new ResourceIdentifier('people', 'p1', null, Meta.of(['observed:authorMeta': 'editor']), [:])
    backend.targetResolutions.size() == 1
    backend.metaEnrichments.size() == 1
    with(backend.metaEnrichments.first() as MetaEnrichment) {
      it.declaredMetaToken == 'authorMeta'
      it.metaValue == 'editor'
      it.relationshipName == 'author'
      it.identifierMetaLocation.pointer() == '/relationships/author/data/meta'
    }
  }

  def "unwraps one present to-one Optional value and keeps empty Optional explicit-null"() {
    given:
    backend.define('articles', property(ID, 'id', 'id', 'id'))
    backend.define('people', property(ID, 'id', 'id', 'id'))
    def target = domain('id': 'p1')
    def shape = RelationshipShape.ordinary(false, 'people')

    when:
    def present = writer.relationshipData(domain(), authorProperty(), shape, Optional.of(target), backend.observingTargetResolver(), backend.observingMetaEnricher())

    then:
    (present as RelationshipData.SingleLinkage).identifier() == ResourceIdentifier.of('people', 'p1')

    when:
    def empty = writer.relationshipData(domain(), authorProperty(), shape, Optional.empty(), backend.observingTargetResolver(), backend.observingMetaEnricher())

    then:
    empty == RelationshipData.NullLinkage.INSTANCE
  }

  def "writes a to-many null value as present-empty linkage without resolving a target"() {
    given:
    def shape = RelationshipShape.ordinary(true, 'comments')

    when:
    def linkage = writer.relationshipData(domain(), commentsProperty(), shape, null, backend.observingTargetResolver(), backend.observingMetaEnricher())

    then:
    linkage == RelationshipData.IdentifierCollectionLinkage.empty()
    backend.targetResolutions.isEmpty()
    backend.metaEnrichments.isEmpty()
  }

  def "writes an empty to-many container as present-empty linkage without resolving a target"() {
    given:
    def shape = RelationshipShape.ordinary(true, 'comments')

    when:
    def linkage = writer.relationshipData(domain(), commentsProperty(), shape, [], backend.observingTargetResolver(), backend.observingMetaEnricher())

    then:
    linkage == RelationshipData.IdentifierCollectionLinkage.empty()
    backend.targetResolutions.isEmpty()
    backend.metaEnrichments.isEmpty()
  }

  def "writes to-many list, array, and iterable containers as ordered collection linkage"() {
    given:
    backend.define('articles', property(ID, 'id', 'id', 'id'))
    backend.define('comments', property(ID, 'id', 'id', 'id'))
    def first = domain('id': 'c1')
    def second = domain('id': 'c2')
    def shape = RelationshipShape.ordinary(true, 'comments')

    expect:
    collectionIdentifiers(writer.relationshipData(domain(), commentsProperty(), shape, [first, second], backend.observingTargetResolver(), backend.observingMetaEnricher())) ==
    [
      ResourceIdentifier.of('comments', 'c1'),
      ResourceIdentifier.of('comments', 'c2')
    ]
    collectionIdentifiers(writer.relationshipData(domain(), commentsProperty(), shape, [first, second] as Object[], backend.observingTargetResolver(), backend.observingMetaEnricher())) ==
    [
      ResourceIdentifier.of('comments', 'c1'),
      ResourceIdentifier.of('comments', 'c2')
    ]
    collectionIdentifiers(writer.relationshipData(domain(), commentsProperty(), shape, new LinkedHashSet<>(List.of(first, second)), backend.observingTargetResolver(), backend.observingMetaEnricher())) ==
        [
          ResourceIdentifier.of('comments', 'c1'),
          ResourceIdentifier.of('comments', 'c2')
        ]
    backend.targetResolutions.collect { it.declaredTargetToken } == [
      'comments',
      'comments',
      'comments'
    ]
    backend.targetResolutions.collect { it.location.pointer() } ==
    [
      '/relationships/comments/data'
    ] * 3
  }

  def "skips null items in to-many collections without consuming occurrence order"() {
    given:
    backend.define('articles', property(ID, 'id', 'id', 'id'))
    backend.define('comments', property(ID, 'id', 'id', 'id'))
    def comment = domain('id': 'c1')
    def shape = RelationshipShape.ordinary(true, 'comments')

    when:
    def linkage = writer.relationshipData(domain(), commentsProperty(), shape, nullableList(null, comment, null), backend.observingTargetResolver(), backend.observingMetaEnricher())

    then:
    collectionIdentifiers(linkage) == [
      ResourceIdentifier.of('comments', 'c1')
    ]
    backend.targetResolutions.size() == 1
  }

  def "retains all supplied members of a direct to-many ResourceIdentifier collection"() {
    given:
    def identifiers = [
      ResourceIdentifier.of('comments', 'c1'),
      ResourceIdentifier.of('comments', 'c2'),
      ResourceIdentifier.of('comments', 'c3')
    ]
    def shape = RelationshipShape.ordinary(true, 'comments')

    when:
    def linkage = writer.relationshipData(domain(), commentsProperty(), shape, identifiers, backend.observingTargetResolver(), backend.observingMetaEnricher())

    then:
    collectionIdentifiers(linkage) == identifiers
    backend.targetResolutions.isEmpty()
    backend.metaEnrichments.isEmpty()
  }

  def "rejects a mixed direct to-many collection without resolving a target"() {
    given:
    def shape = RelationshipShape.ordinary(true, 'comments')

    when:
    writer.relationshipData(domain(), commentsProperty(), shape, nullableList(ResourceIdentifier.of('comments', 'c1'), domain('id': 'c1')), backend.observingTargetResolver(), backend.observingMetaEnricher())

    then:
    def failure = thrown(JsonApiMappingException)
    failure.diagnostic() == MappingDiagnostic.UNSUPPORTED_RELATIONSHIP_VALUE
    failure.location().pointer() == '/relationships/comments/data'
    failure.message == 'Mixed element types in to-many relationship collection: expected ResourceIdentifier, got java.util.LinkedHashMap'
    backend.targetResolutions.isEmpty()
    backend.metaEnrichments.isEmpty()
  }

  def "rejects a to-many value that is not a supported runtime collection type"() {
    given:
    def shape = RelationshipShape.ordinary(true, 'comments')

    when:
    writer.relationshipData(domain(), commentsProperty(), shape, [c1: 'not a collection'], backend.observingTargetResolver(), backend.observingMetaEnricher())

    then:
    def failure = thrown(JsonApiMappingException)
    failure.diagnostic() == MappingDiagnostic.UNSUPPORTED_RELATIONSHIP_VALUE
    failure.location().pointer() == '/relationships/comments/data'
    failure.message.startsWith('To-many relationship value is not a supported collection type: ')
    backend.targetResolutions.isEmpty()
    backend.metaEnrichments.isEmpty()
  }

  def "validates each to-many wrapper item and keeps occurrence order and indexes"() {
    given:
    backend.define('articles', property(ID, 'id', 'id', 'id'))
    backend.define('comments', property(ID, 'id', 'id', 'id'))
    def first = domain('id': 'c1')
    def second = domain('id': 'c2')
    def shape = RelationshipShape.wrapper(true, 'commentMeta', RelationshipShape.ordinary(false, 'comments'))

    when:
    def linkage = writer.relationshipData(
        domain(),
        commentsProperty(),
        shape,
        nullableList(
        new RelationshipLinkage<>(first, 'meta-1'),
        null,
        new RelationshipLinkage<>(second, null)),
        backend.observingTargetResolver(),
        backend.observingMetaEnricher())

    then:
    collectionIdentifiers(linkage) == [
      new ResourceIdentifier('comments', 'c1', null, Meta.of(['observed:commentMeta': 'meta-1']), [:]),
      ResourceIdentifier.of('comments', 'c2')
    ]
    backend.metaEnrichments.collect { it.identifierMetaLocation.pointer() } == [
      '/relationships/comments/data/0/meta'
    ]
    backend.targetResolutions.collect { it.location.pointer() } ==
    [
      '/relationships/comments/data'
    ] * 2
  }

  def "rejects a to-many wrapper item that is not a RelationshipLinkage"() {
    given:
    def shape = RelationshipShape.wrapper(true, 'commentMeta', RelationshipShape.ordinary(false, 'comments'))

    when:
    writer.relationshipData(domain(), commentsProperty(), shape, nullableList('not a wrapper'), backend.observingTargetResolver(), backend.observingMetaEnricher())

    then:
    def failure = thrown(JsonApiMappingException)
    failure.diagnostic() == MappingDiagnostic.UNSUPPORTED_RELATIONSHIP_VALUE
    failure.location().pointer() == '/relationships/comments/data'
    failure.message == 'To-many RelationshipLinkage collection contains java.lang.String'
    backend.targetResolutions.isEmpty()
    backend.metaEnrichments.isEmpty()
  }

  def "fails a to-one wrapper occurrence whose target maps to explicit-null linkage"() {
    given:
    def shape = RelationshipShape.wrapper(false, 'authorMeta', RelationshipShape.ordinary(false, 'people'))

    when:
    writer.relationshipData(domain(), authorProperty(), shape, new RelationshipLinkage<>(Optional.empty(), null), backend.observingTargetResolver(), backend.observingMetaEnricher())

    then:
    def failure = thrown(JsonApiMappingException)
    failure.diagnostic() == MappingDiagnostic.INVALID_IDENTIFIER_META_TARGET
    failure.location().pointer() == '/relationships/author/data/meta'
    failure.message == "RelationshipLinkage requires a mappable target for relationship 'author'"
    backend.targetResolutions.isEmpty()
    backend.metaEnrichments.isEmpty()
  }

  def "fails a to-one wrapper occurrence whose target maps to collection linkage"() {
    given:
    backend.define('articles', property(ID, 'id', 'id', 'id'))
    backend.define('comments', property(ID, 'id', 'id', 'id'))
    def collectionTarget =
        new RelationshipData.IdentifierCollectionLinkage([
          ResourceIdentifier.of('comments', 'c1')
        ])
    def shape = RelationshipShape.wrapper(false, 'authorMeta', RelationshipShape.ordinary(false, 'people'))

    when:
    writer.relationshipData(domain(), authorProperty(), shape, new RelationshipLinkage<>(collectionTarget, null), backend.observingTargetResolver(), backend.observingMetaEnricher())

    then:
    def failure = thrown(JsonApiMappingException)
    failure.diagnostic() == MappingDiagnostic.INVALID_IDENTIFIER_META_TARGET
    failure.location().pointer() == '/relationships/author/data/meta'
    failure.message == "RelationshipLinkage requires a mappable target for relationship 'author'"
    backend.targetResolutions.isEmpty()
    backend.metaEnrichments.isEmpty()

    when:
    writer.relationshipData(domain(), authorProperty(), shape, new RelationshipLinkage<>(collectionTarget, 'editor'), backend.observingTargetResolver(), backend.observingMetaEnricher())

    then:
    def metaFailure = thrown(JsonApiMappingException)
    metaFailure.diagnostic() == MappingDiagnostic.INVALID_IDENTIFIER_META_TARGET
    metaFailure.location().pointer() == '/relationships/author/data/meta'
    backend.metaEnrichments.isEmpty()
  }

  def "rejects a wrapper-declared to-one value that is not a RelationshipLinkage"() {
    given:
    def shape = RelationshipShape.wrapper(false, 'authorMeta', RelationshipShape.ordinary(false, 'people'))

    when:
    writer.relationshipData(domain(), authorProperty(), shape, domain('id': 'p1'), backend.observingTargetResolver(), backend.observingMetaEnricher())

    then:
    def failure = thrown(JsonApiMappingException)
    failure.diagnostic() == MappingDiagnostic.UNSUPPORTED_RELATIONSHIP_VALUE
    failure.location().pointer() == '/relationships/author/data'
    failure.message == "Relationship 'author' requires RelationshipLinkage values, got java.util.LinkedHashMap"
    backend.targetResolutions.isEmpty()
    backend.metaEnrichments.isEmpty()
  }

  private static List<ResourceIdentifier> collectionIdentifiers(RelationshipData linkage) {
    return (linkage as RelationshipData.IdentifierCollectionLinkage).identifiers()
  }

  private static List<Object> nullableList(Object... values) {
    return Arrays.asList(values)
  }

  private static WriteProperty<String> authorProperty() {
    property(RELATIONSHIP, 'author', 'author', 'author')
  }

  private static WriteProperty<String> commentsProperty() {
    property(RELATIONSHIP, 'comments', 'comments', 'comments')
  }

  private Map<String, Object> domain(Map<String, Object> propertyValues) {
    def map = new LinkedHashMap<>(propertyValues)
    propertyValues.each { key, value -> backend.value(map, key, value) }
    map
  }

  private Map<String, Object> domain() {
    domain('id': '1')
  }
}
