package com.kazforge.jsonapi.mapping.internal

import static com.kazforge.jsonapi.mapping.internal.MappingFakeReadResourceBackend.property
import static com.kazforge.jsonapi.mapping.internal.PropertyRole.ATTRIBUTE
import static com.kazforge.jsonapi.mapping.internal.PropertyRole.ID
import static com.kazforge.jsonapi.mapping.internal.PropertyRole.LOCAL_ID
import static com.kazforge.jsonapi.mapping.internal.PropertyRole.RELATIONSHIP
import static com.kazforge.jsonapi.mapping.internal.PropertyRole.RELATIONSHIP_META
import static com.kazforge.jsonapi.mapping.internal.PropertyRole.RESOURCE_META

import com.kazforge.jsonapi.core.model.Attributes
import com.kazforge.jsonapi.core.model.Meta
import com.kazforge.jsonapi.core.model.Relationship
import com.kazforge.jsonapi.core.model.RelationshipData
import com.kazforge.jsonapi.core.model.Relationships
import com.kazforge.jsonapi.core.model.ResourceIdentifier
import com.kazforge.jsonapi.core.model.ResourceObject
import com.kazforge.jsonapi.diagnostic.JsonApiMappingException
import com.kazforge.jsonapi.diagnostic.MappingDiagnostic
import com.kazforge.jsonapi.diagnostic.MappingLocation
import com.kazforge.jsonapi.mapping.RelationshipLinkage
import spock.lang.Specification

class MappingBasicResourceReaderSpec extends Specification {

  private final MappingFakeReadResourceBackend backend = new MappingFakeReadResourceBackend()
  private final BasicResourceReader<String, String> reader = new BasicResourceReader<>(backend)

  def "requires the mapped resource type through the shared authority"() {
    given:
    backend.define('articles', property(ID, 'id', 'id', 'id'))

    when:
    reader.requireResourceType(resource('people', 'p1', null, null), definition('articles'), Object)

    then:
    def failure = thrown(JsonApiMappingException)
    failure.diagnostic() == MappingDiagnostic.RESOURCE_TYPE_MISMATCH
    failure.propertyPath() == '/type'
  }

  def "accepts a matching resource type"() {
    given:
    backend.define('articles', property(ID, 'id', 'id', 'id'))

    when:
    reader.requireResourceType(resource('articles', '1', null, null), definition('articles'), Object)

    then:
    noExceptionThrown()
  }

  def "binds independent id and lid roles under their configured external names"() {
    given:
    backend.define(
        'articles',
        property(ID, 'identifier', 'blog_id', 'id'),
        property(LOCAL_ID, 'localKey', 'wire_lid', 'lid'),
        property(ATTRIBUTE, 'title', 'title', 'title'))

    when:
    def result = reader.readBasic(
        new ResourceObject('articles', '42', 'tmp-1',
        Attributes.ofAttributes([title: 'T']), null, null, null, Map.of()),
        definition('articles'), Object)

    then:
    result.properties() == [blog_id: 'parsed:42', wire_lid: 'parsed:tmp-1', title: 'T']
    result.identifierLocation() == MappingLocation.of('id')
    result.localIdLocation() == MappingLocation.of('lid')
  }

  def "does not fall a wire lid into a mapped id role"() {
    given:
    backend.define('articles', property(ID, 'id', 'id', 'id'))

    when:
    def result = reader.readBasic(
        new ResourceObject('articles', null, 'tmp-1', null, null, null, null, Map.of()),
        definition('articles'), Object)

    then:
    result.properties().isEmpty()
    result.identifierLocation() == null
    result.localIdLocation() == null
  }

  def "distinguishes an absent attribute member from a present explicit null"() {
    given:
    backend.define(
        'articles',
        property(ID, 'id', 'id', 'id'),
        property(ATTRIBUTE, 'title', 'title', 'title'),
        property(ATTRIBUTE, 'body', 'body', 'body'))

    when:
    def result = reader.readBasic(resource('articles', '1', [title: null], null), definition('articles'), Object)

    then:
    result.properties().containsKey('title')
    result.properties().get('title') == null
    !result.properties().containsKey('body')
  }

  def "binds present attributes and skips unmapped members in mapping order"() {
    given:
    backend.define(
        'articles',
        property(ID, 'id', 'id', 'id'),
        property(ATTRIBUTE, 'title', 'title', 'title'),
        property(ATTRIBUTE, 'body', 'body', 'body'))

    when:
    def result = reader.readBasic(
        resource('articles', '1', [body: 'B', unmapped: 'x'], null), definition('articles'), Object)

    then:
    result.properties().keySet() as List == ['id', 'body']
  }

  def "binds a direct to-one identifier preserving meta and dropping additional members"() {
    given:
    backend.define('articles', property(RELATIONSHIP, 'author', 'author', 'author'))
    backend.directRelationship('author', false)
    def identifier =
        new ResourceIdentifier('people', 'p1', null, Meta.of([role: 'editor']), [extra: 'x'])

    when:
    def result = reader.readBasic(
        resource('articles', '1', null,
        [author: Relationship.withData(new RelationshipData.SingleLinkage(identifier))]),
        definition('articles'), Object)

    then:
    def copied = result.properties().author as ResourceIdentifier
    copied.type() == 'people'
    copied.id() == 'p1'
    copied.meta() == Meta.of([role: 'editor'])
    copied.additionalMembers().isEmpty()
    backend.linkageObservations.isEmpty()
  }

  def "binds a direct to-many identifier collection preserving meta and dropping additional members"() {
    given:
    backend.define('articles', property(RELATIONSHIP, 'comments', 'comments', 'comments'))
    backend.directRelationship('comments', true)
    def identifiers = [
      new ResourceIdentifier('comments', 'c1', null, Meta.of([pinned: true]), [extra: 'x']),
      ResourceIdentifier.of('comments', 'c2')
    ]

    when:
    def result = reader.readBasic(
        resource('articles', '1', null,
        [comments: Relationship.withData(new RelationshipData.IdentifierCollectionLinkage(identifiers))]),
        definition('articles'), Object)

    then:
    def values = result.properties().comments as List<ResourceIdentifier>
    values.size() == 2
    values[0].meta() == Meta.of([pinned: true])
    values[0].additionalMembers().isEmpty()
    values[1] == ResourceIdentifier.of('comments', 'c2')
    backend.linkageObservations.isEmpty()
  }

  def "binds explicit-null to-one and present-empty to-many direct linkage"() {
    given:
    backend.define(
        'articles',
        property(RELATIONSHIP, 'author', 'author', 'author'),
        property(RELATIONSHIP, 'comments', 'comments', 'comments'))
    backend.directRelationship('author', false)
    backend.directRelationship('comments', true)

    when:
    def result = reader.readBasic(
        resource('articles', '1', null,
        [author: Relationship.withData(RelationshipData.NullLinkage.INSTANCE),
          comments: Relationship.withData(RelationshipData.IdentifierCollectionLinkage.empty())]),
        definition('articles'), Object)

    then:
    result.properties().containsKey('author')
    result.properties().get('author') == null
    result.properties().comments == []
    backend.linkageObservations.isEmpty()
  }

  def "rejects illegal linkage cardinality at the relationship data location"() {
    given:
    backend.define(
        'articles',
        property(RELATIONSHIP, 'author', 'author', 'author'),
        property(RELATIONSHIP, 'comments', 'comments', 'comments'))
    backend.directRelationship('author', false)
    backend.directRelationship('comments', true)
    backend.rawType('author', ResourceIdentifier)
    backend.rawType('comments', List)

    when:
    reader.readBasic(
        resource('articles', '1', null, [(name): Relationship.withData(linkage)]),
        definition('articles'), Object)

    then:
    def failure = thrown(JsonApiMappingException)
    failure.diagnostic() == MappingDiagnostic.RELATIONSHIP_CARDINALITY_MISMATCH
    failure.propertyPath() == "/relationships/$name/data"
    failure.resourceClass() == propertyRawType
    backend.linkageObservations.isEmpty()

    where:
    name       | linkage                                                              | propertyRawType
    'author'   | new RelationshipData.IdentifierCollectionLinkage([
      ResourceIdentifier.of('people', 'p1')
    ]) | ResourceIdentifier
    'author'   | new RelationshipData.IdentifierCollectionLinkage([])                  | ResourceIdentifier
    'comments' | RelationshipData.NullLinkage.INSTANCE                                 | List
    'comments' | new RelationshipData.SingleLinkage(ResourceIdentifier.of('comments', 'c1')) | List
  }

  def "resolves the relationship shape only for present bindable data"() {
    given:
    backend.define(
        'articles',
        property(RELATIONSHIP, 'author', 'author', 'author'),
        property(RELATIONSHIP, 'comments', 'comments', 'comments'))
    backend.mappedRelationship('author', false, 'authorTarget')
    backend.mappedRelationship('comments', true, 'commentsTarget')

    when:
    reader.readBasic(
        resource('articles', '1', null, [author: Relationship.metaOnly(Meta.of([note: 'x']))]),
        definition('articles'), Object)

    then:
    backend.shapeResolutions.isEmpty()
    backend.linkageObservations.isEmpty()
  }

  def "resolves the shape but skips the mapper for null to-one and empty to-many mapped linkage"() {
    given:
    backend.define(
        'articles',
        property(RELATIONSHIP, 'author', 'author', 'author'),
        property(RELATIONSHIP, 'comments', 'comments', 'comments'))
    backend.mappedRelationship('author', false, 'authorTarget')
    backend.mappedRelationship('comments', true, 'commentsTarget')

    when:
    def result = reader.readBasic(
        resource('articles', '1', null,
        [author: Relationship.withData(RelationshipData.NullLinkage.INSTANCE),
          comments: Relationship.withData(RelationshipData.IdentifierCollectionLinkage.empty())]),
        definition('articles'), Object)

    then:
    result.properties().containsKey('author')
    result.properties().get('author') == null
    result.properties().comments == []
    backend.shapeResolutions == ['author', 'comments']
    backend.linkageObservations.isEmpty()
  }

  def "invokes a mapped to-one mapper once for present single linkage"() {
    given:
    backend.define('articles', property(RELATIONSHIP, 'author', 'author', 'author'))
    backend.mappedRelationship('author', false, 'authorTarget')
    backend.linkageMapping('author', 'mapped-author')
    def linkage = new RelationshipData.SingleLinkage(ResourceIdentifier.of('people', 'p1'))

    when:
    def result = reader.readBasic(
        resource('articles', '1', null, [author: Relationship.withData(linkage)]),
        definition('articles'), Object)

    then:
    result.properties().author == 'mapped-author'
    backend.linkageObservations.size() == 1
    with(backend.linkageObservations[0]) {
      it.propertyToken == 'author'
      it.data.is(linkage)
      it.target == 'authorTarget'
    }
  }

  def "invokes a mapped to-many mapper once with the whole collection linkage"() {
    given:
    backend.define('articles', property(RELATIONSHIP, 'comments', 'comments', 'comments'))
    backend.mappedRelationship('comments', true, 'commentsTarget')
    backend.linkageMapping('comments', ['mapped-1', 'mapped-2'])
    def linkage = new RelationshipData.IdentifierCollectionLinkage([
      ResourceIdentifier.of('comments', 'c1'),
      ResourceIdentifier.of('comments', 'c2')
    ])

    when:
    def result = reader.readBasic(
        resource('articles', '1', null, [comments: Relationship.withData(linkage)]),
        definition('articles'), Object)

    then:
    result.properties().comments == ['mapped-1', 'mapped-2']
    backend.linkageObservations.size() == 1
    backend.linkageObservations[0].data.is(linkage)
    backend.linkageObservations[0].target == 'commentsTarget'
  }

  def "preserves a null mapped to-one result in the synthetic input"() {
    given:
    backend.define('articles', property(RELATIONSHIP, 'author', 'author', 'author'))
    backend.mappedRelationship('author', false, 'authorTarget')
    backend.linkageMapping('author', null)
    def linkage = new RelationshipData.SingleLinkage(ResourceIdentifier.of('people', 'p1'))

    when:
    def result = reader.readBasic(
        resource('articles', '1', null, [author: Relationship.withData(linkage)]),
        definition('articles'), Object)

    then:
    result.properties().containsKey('author')
    result.properties().get('author') == null
    backend.linkageObservations.size() == 1
  }

  def "wraps a direct to-one occurrence with converted identifier meta"() {
    given:
    backend.define('articles', property(RELATIONSHIP, 'author', 'author', 'author'))
    backend.wrappedRelationship('author', false, 'authorMeta', new ReadRelationshipShape.Direct<String>(false))
    backend.identifierMetaConversion('authorMeta', 'converted-meta')
    def identifier =
        new ResourceIdentifier('people', 'p1', null, Meta.of([role: 'editor']), [extra: 'x'])

    when:
    def result = reader.readBasic(
        resource('articles', '1', null,
        [author: Relationship.withData(new RelationshipData.SingleLinkage(identifier))]),
        definition('articles'), Object)

    then:
    def wrapper = result.properties().author as RelationshipLinkage
    wrapper.target() == new ResourceIdentifier('people', 'p1', null, Meta.of([role: 'editor']), [:])
    wrapper.meta() == 'converted-meta'
    backend.identifierMetaObservations.size() == 1
    with(backend.identifierMetaObservations[0]) {
      it.metaToken == 'authorMeta'
      it.occurrenceIndex == -1
      it.meta == Meta.of([role: 'editor'])
    }
  }

  def "leaves wrapper meta null when the identifier carries no meta"() {
    given:
    backend.define('articles', property(RELATIONSHIP, 'author', 'author', 'author'))
    backend.wrappedRelationship('author', false, 'authorMeta', new ReadRelationshipShape.Direct<String>(false))
    def identifier = ResourceIdentifier.of('people', 'p1')

    when:
    def result = reader.readBasic(
        resource('articles', '1', null,
        [author: Relationship.withData(new RelationshipData.SingleLinkage(identifier))]),
        definition('articles'), Object)

    then:
    def wrapper = result.properties().author as RelationshipLinkage
    wrapper.target() == identifier
    wrapper.meta() == null
    backend.identifierMetaObservations.isEmpty()
  }

  def "propagates a backend identifier-meta conversion failure unchanged"() {
    given:
    backend.define('articles', property(RELATIONSHIP, 'author', 'author', 'author'))
    backend.wrappedRelationship('author', false, 'authorMeta', new ReadRelationshipShape.Direct<String>(false))
    backend.failIdentifierMetaConversion('authorMeta')
    def identifier =
        new ResourceIdentifier('people', 'p1', null, Meta.of([role: 'editor']), [:])

    when:
    reader.readBasic(
        resource('articles', '1', null,
        [author: Relationship.withData(new RelationshipData.SingleLinkage(identifier))]),
        definition('articles'), Object)

    then:
    def failure = thrown(IllegalStateException)
    failure.message == 'identifier meta conversion failed for authorMeta'
  }

  def "yields no wrapper when a wrapped to-one mapped target returns null"() {
    given:
    backend.define('articles', property(RELATIONSHIP, 'author', 'author', 'author'))
    backend.wrappedRelationship(
        'author', false, 'authorMeta', new ReadRelationshipShape.Mapped<String>(false, 'authorTarget'))
    backend.linkageMapping('author', null)

    when:
    def result = reader.readBasic(
        resource('articles', '1', null,
        [author: Relationship.withData(new RelationshipData.SingleLinkage(ResourceIdentifier.of('people', 'p1')))]),
        definition('articles'), Object)

    then:
    result.properties().containsKey('author')
    result.properties().get('author') == null
    backend.identifierMetaObservations.isEmpty()
  }

  def "wraps to-many occurrences pairing each target with that occurrence's meta"() {
    given:
    backend.define('articles', property(RELATIONSHIP, 'comments', 'comments', 'comments'))
    backend.wrappedRelationship(
        'comments', true, 'commentMeta', new ReadRelationshipShape.Direct<String>(false))
    backend.identifierMetaConversion('commentMeta', 'converted')
    def identifiers = [
      new ResourceIdentifier('comments', 'c1', null, Meta.of([pinned: true]), [:]),
      ResourceIdentifier.of('comments', 'c2')
    ]

    when:
    def result = reader.readBasic(
        resource('articles', '1', null,
        [comments: Relationship.withData(new RelationshipData.IdentifierCollectionLinkage(identifiers))]),
        definition('articles'), Object)

    then:
    def values = result.properties().comments as List<RelationshipLinkage>
    values.size() == 2
    values[0].target() == new ResourceIdentifier('comments', 'c1', null, Meta.of([pinned: true]), [:])
    values[0].meta() == 'converted'
    values[1].target() == ResourceIdentifier.of('comments', 'c2')
    values[1].meta() == null
    backend.identifierMetaObservations*.occurrenceIndex == [0]
  }

  def "returns present-empty for an empty wrapped to-many linkage without invoking the mapper"() {
    given:
    backend.define('articles', property(RELATIONSHIP, 'comments', 'comments', 'comments'))
    backend.wrappedRelationship(
        'comments', true, 'commentMeta', new ReadRelationshipShape.Direct<String>(false))

    when:
    def result = reader.readBasic(
        resource('articles', '1', null,
        [comments: Relationship.withData(RelationshipData.IdentifierCollectionLinkage.empty())]),
        definition('articles'), Object)

    then:
    result.properties().comments == []
    backend.linkageObservations.isEmpty()
    backend.identifierMetaObservations.isEmpty()
  }

  def "fails a wrapped to-many occurrence whose mapped target is null at the indexed location"() {
    given:
    backend.define('articles', property(RELATIONSHIP, 'comments', 'comments', 'comments'))
    backend.wrappedRelationship(
        'comments', true, 'commentMeta', new ReadRelationshipShape.Mapped<String>(false, 'commentTarget'))
    backend.linkageMapping('comments', null)
    backend.rawType('comments', List)

    when:
    reader.readBasic(
        resource('articles', '1', null,
        [comments: Relationship.withData(new RelationshipData.IdentifierCollectionLinkage([
            ResourceIdentifier.of('comments', 'c1'),
            ResourceIdentifier.of('comments', 'c2')
          ]))]),
        definition('articles'), Object)

    then:
    def failure = thrown(JsonApiMappingException)
    failure.diagnostic() == MappingDiagnostic.LINKAGE_MAPPING_FAILED
    failure.propertyPath() == '/relationships/comments/data/0'
    failure.resourceClass() == List
    backend.identifierMetaObservations.isEmpty()
  }

  def "propagates a backend linkage-mapper failure unchanged"() {
    given:
    backend.define('articles', property(RELATIONSHIP, 'author', 'author', 'author'))
    backend.mappedRelationship('author', false, 'authorTarget')
    backend.failLinkageMapping('author')
    def linkage = new RelationshipData.SingleLinkage(ResourceIdentifier.of('people', 'p1'))

    when:
    reader.readBasic(
        resource('articles', '1', null, [author: Relationship.withData(linkage)]),
        definition('articles'), Object)

    then:
    def failure = thrown(IllegalStateException)
    failure.message == 'linkage mapping failed for author'
  }

  def "binds resource meta and matched relationship meta under their external names"() {
    given:
    backend.define(
        'articles',
        property(ID, 'id', 'id', 'id'),
        property(RELATIONSHIP, 'author', 'author', 'author'),
        property(RESOURCE_META, 'meta', 'meta', 'meta'),
        property(RELATIONSHIP_META, 'authorMeta', 'authorMeta', 'author'))
    backend.directRelationship('author', false)
    def relationship = new Relationship(
        new RelationshipData.SingleLinkage(ResourceIdentifier.of('people', 'p1')),
        null,
        Meta.of([displayName: 'Alice']),
        [:])

    when:
    def result = reader.readBasic(
        new ResourceObject('articles', '1', null, null,
        Relationships.ofRelationships([author: relationship]), null, Meta.of([source: 'cms']), [:]),
        definition('articles'), Object)

    then:
    result.properties().meta == [source: 'cms']
    result.properties().authorMeta == [displayName: 'Alice']
    result.properties().author == ResourceIdentifier.of('people', 'p1')
  }

  def "leaves resource and relationship meta absent when the wire members are absent"() {
    given:
    backend.define(
        'articles',
        property(ID, 'id', 'id', 'id'),
        property(RESOURCE_META, 'meta', 'meta', 'meta'),
        property(RELATIONSHIP_META, 'authorMeta', 'authorMeta', 'author'))

    when:
    def result = reader.readBasic(
        new ResourceObject('articles', '1', null, null, null, null, null, [:]),
        definition('articles'), Object)

    then:
    result.properties().keySet() as List == ['id']
  }

  def "binds relationship meta for a meta-only relationship without binding linkage"() {
    given:
    backend.define(
        'articles',
        property(ID, 'id', 'id', 'id'),
        property(RELATIONSHIP, 'author', 'author', 'author'),
        property(RELATIONSHIP_META, 'authorMeta', 'authorMeta', 'author'))

    when:
    def result = reader.readBasic(
        resource('articles', '1', null,
        [author: Relationship.metaOnly(Meta.of([displayName: 'Alice']))]),
        definition('articles'), Object)

    then:
    result.properties().authorMeta == [displayName: 'Alice']
    !result.properties().containsKey('author')
    backend.shapeResolutions.isEmpty()
  }

  def "binds an empty-object meta as a present empty member map"() {
    given:
    backend.define(
        'articles',
        property(ID, 'id', 'id', 'id'),
        property(RESOURCE_META, 'meta', 'meta', 'meta'))

    when:
    def result = reader.readBasic(
        new ResourceObject('articles', '1', null, null, null, null, Meta.of([:]), [:]),
        definition('articles'), Object)

    then:
    result.properties().containsKey('meta')
    result.properties().meta == [:]
  }

  def "rejects a supplied non-bindable resource meta at the meta location"() {
    given:
    backend.define(
        'articles',
        property(ID, 'id', 'id', 'id'),
        property(RESOURCE_META, 'meta', 'meta', 'meta', false))

    when:
    reader.readBasic(
        new ResourceObject('articles', '1', null, null, null, null, Meta.of([source: 'cms']), [:]),
        definition('articles'), Object)

    then:
    def failure = thrown(JsonApiMappingException)
    failure.diagnostic() == MappingDiagnostic.NON_DESERIALIZABLE_PROPERTY
    failure.propertyPath() == '/meta'
  }

  def "rejects supplied non-bindable relationship meta at the relationship meta location"() {
    given:
    backend.define(
        'articles',
        property(ID, 'id', 'id', 'id'),
        property(RELATIONSHIP, 'author', 'author', 'author'),
        property(RELATIONSHIP_META, 'authorMeta', 'authorMeta', 'author', false))
    backend.directRelationship('author', false)
    def relationship = new Relationship(
        null, null, Meta.of([displayName: 'Alice']), [:])

    when:
    reader.readBasic(
        new ResourceObject('articles', '1', null, null,
        Relationships.ofRelationships([author: relationship]), null, null, [:]),
        definition('articles'), Object)

    then:
    def failure = thrown(JsonApiMappingException)
    failure.diagnostic() == MappingDiagnostic.NON_DESERIALIZABLE_PROPERTY
    failure.propertyPath() == '/relationships/author/meta'
  }

  def "converts relationships in mapping order and skips absent linkage"() {
    given:
    backend.define(
        'articles',
        property(RELATIONSHIP, 'author', 'author', 'author'),
        property(RELATIONSHIP, 'comments', 'comments', 'comments'),
        property(RELATIONSHIP, 'reviewer', 'reviewer', 'reviewer'))
    backend.mappedRelationship('author', false, 'authorTarget')
    backend.mappedRelationship('comments', true, 'commentsTarget')
    def author = new RelationshipData.SingleLinkage(ResourceIdentifier.of('people', 'p1'))
    def comments = RelationshipData.IdentifierCollectionLinkage.empty()
    def relationships = Relationships.ofRelationships([
      reviewer: Relationship.metaOnly(Meta.of([note: 'x'])),
      comments: Relationship.withData(comments),
      author: Relationship.withData(author)])

    when:
    def result = reader.readBasic(
        new ResourceObject('articles', null, null, null, relationships, null, null, Map.of()),
        definition('articles'), Object)

    then:
    result.properties().keySet() as List == ['author', 'comments']
    backend.shapeResolutions == ['author', 'comments']
    backend.linkageObservations*.propertyToken == ['author']
    backend.linkageObservations[0].data().is(author)
  }

  def "rejects a supplied non-bindable identity role at its wire location"() {
    given:
    backend.define('articles', property(ID, 'id', 'id', 'id', false))

    when:
    reader.readBasic(resource('articles', '1', null, null), definition('articles'), Object)

    then:
    def failure = thrown(JsonApiMappingException)
    failure.diagnostic() == MappingDiagnostic.NON_DESERIALIZABLE_PROPERTY
    failure.propertyPath() == '/id'
    failure.resourceClass() == Object
  }

  def "rejects a supplied non-bindable attribute at its wire location"() {
    given:
    backend.define(
        'articles',
        property(ID, 'id', 'id', 'id'),
        property(ATTRIBUTE, 'title', 'title', 'title', false))

    when:
    reader.readBasic(resource('articles', '1', [title: 'T'], null), definition('articles'), Object)

    then:
    def failure = thrown(JsonApiMappingException)
    failure.diagnostic() == MappingDiagnostic.NON_DESERIALIZABLE_PROPERTY
    failure.propertyPath() == '/attributes/title'
  }

  def "rejects a supplied non-bindable relationship at its wire location"() {
    given:
    backend.define(
        'articles',
        property(ID, 'id', 'id', 'id'),
        property(RELATIONSHIP, 'author', 'author', 'author', false))

    when:
    reader.readBasic(
        resource('articles', '1', null, [author: Relationship.withData(new RelationshipData.SingleLinkage(ResourceIdentifier.of('people', 'p1')))]),
        definition('articles'), Object)

    then:
    def failure = thrown(JsonApiMappingException)
    failure.diagnostic() == MappingDiagnostic.NON_DESERIALIZABLE_PROPERTY
    failure.propertyPath() == '/relationships/author/data'
    backend.shapeResolutions.isEmpty()
    backend.linkageObservations.isEmpty()
  }

  def "reports a null identifier conversion at the member location and property raw type"() {
    given:
    backend.define('articles', property(ID, 'id', 'id', 'id'))
    backend.rawType('id', Integer)
    backend.parseReturnsNull = true

    when:
    reader.readBasic(resource('articles', '1', null, null), definition('articles'), Object)

    then:
    def failure = thrown(JsonApiMappingException)
    failure.diagnostic() == MappingDiagnostic.IDENTIFIER_CONVERSION_FAILED
    failure.propertyPath() == '/id'
    failure.resourceClass() == Integer
  }

  def "reports a throwing identifier conversion at the member location and property raw type"() {
    given:
    backend.define('articles', property(LOCAL_ID, 'localKey', 'wire_lid', 'lid'))
    backend.rawType('localKey', Long)
    backend.parseThrows = true

    when:
    reader.readBasic(
        new ResourceObject('articles', null, 'tmp-1', null, null, null, null, Map.of()),
        definition('articles'), Object)

    then:
    def failure = thrown(JsonApiMappingException)
    failure.diagnostic() == MappingDiagnostic.IDENTIFIER_CONVERSION_FAILED
    failure.propertyPath() == '/lid'
    failure.resourceClass() == Long
  }

  def "requires resource and definition non-null on the read entry points"() {
    given:
    backend.define('articles', property(ID, 'id', 'id', 'id'))

    when:
    reader.readBasic(null, definition('articles'), Object)

    then:
    thrown(NullPointerException)
  }

  private ReadResourceDefinition<String> definition(String resourceType) {
    backend.definitionOrFail(resourceType)
  }

  private static ResourceObject resource(String type, String id, Map attrs, Map rels) {
    new ResourceObject(
        type,
        id,
        null,
        attrs == null ? null : Attributes.ofAttributes(attrs),
        rels == null ? null : Relationships.ofRelationships(rels),
        null,
        null,
        Map.of())
  }
}
