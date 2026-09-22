package com.kazforge.jsonapi.mapping.internal

import static com.kazforge.jsonapi.mapping.internal.MappingFakeReadResourceBackend.property
import static com.kazforge.jsonapi.mapping.internal.PropertyRole.ATTRIBUTE
import static com.kazforge.jsonapi.mapping.internal.PropertyRole.ID
import static com.kazforge.jsonapi.mapping.internal.PropertyRole.LOCAL_ID
import static com.kazforge.jsonapi.mapping.internal.PropertyRole.RELATIONSHIP

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
import spock.lang.Specification

class MappingBasicResourceReaderSpec extends Specification {

  private final MappingFakeReadResourceBackend backend = new MappingFakeReadResourceBackend()
  private final BasicResourceReader<String> reader = new BasicResourceReader<>(backend)

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

  def "invokes relationship conversion exactly once for present linkage"() {
    given:
    backend.define(
        'articles',
        property(ID, 'id', 'id', 'id'),
        property(RELATIONSHIP, 'author', 'author', 'author'))
    def linkage = new RelationshipData.SingleLinkage(ResourceIdentifier.of('people', 'p1'))

    when:
    def result = reader.readBasic(
        resource('articles', '1', null, [author: Relationship.withData(linkage)]),
        definition('articles'), Object)

    then:
    result.properties().author == 'converted:author'
    backend.relationshipObservations.size() == 1
    backend.relationshipObservations[0].propertyToken() == 'author'
    backend.relationshipObservations[0].data().is(linkage)
  }

  def "preserves an explicit-null backend relationship conversion in the synthetic input"() {
    given:
    backend.define(
        'articles',
        property(ID, 'id', 'id', 'id'),
        property(RELATIONSHIP, 'author', 'author', 'author'))
    backend.relationshipConversion('author', null)
    def linkage = new RelationshipData.SingleLinkage(ResourceIdentifier.of('people', 'p1'))

    when:
    def result = reader.readBasic(
        resource('articles', '1', null, [author: Relationship.withData(linkage)]),
        definition('articles'), Object)

    then:
    result.properties().containsKey('author')
    result.properties().get('author') == null
    backend.relationshipObservations.size() == 1
  }

  def "propagates a backend relationship conversion failure"() {
    given:
    backend.define(
        'articles',
        property(ID, 'id', 'id', 'id'),
        property(RELATIONSHIP, 'author', 'author', 'author'))
    backend.failRelationshipConversion('author')
    def linkage = new RelationshipData.SingleLinkage(ResourceIdentifier.of('people', 'p1'))

    when:
    reader.readBasic(
        resource('articles', '1', null, [author: Relationship.withData(linkage)]),
        definition('articles'), Object)

    then:
    def failure = thrown(IllegalStateException)
    failure.message == 'relationship conversion failed for author'
  }

  def "does not invoke relationship conversion when the member or its data is absent"() {
    given:
    backend.define(
        'articles',
        property(ID, 'id', 'id', 'id'),
        property(RELATIONSHIP, 'author', 'author', 'author'))

    when:
    def missingMember = reader.readBasic(
        resource('articles', '1', null, [comments: Relationship.metaOnly(Meta.of([note: 'x']))]),
        definition('articles'), Object)
    def missingData = reader.readBasic(
        resource('articles', '1', null, [author: Relationship.metaOnly(Meta.of([note: 'x']))]),
        definition('articles'), Object)

    then:
    missingMember.properties().keySet() as List == ['id']
    missingData.properties().keySet() as List == ['id']
    backend.relationshipObservations.isEmpty()
  }

  def "converts relationships in mapping order and skips absent linkage"() {
    given:
    backend.define(
        'articles',
        property(RELATIONSHIP, 'author', 'author', 'author'),
        property(RELATIONSHIP, 'comments', 'comments', 'comments'),
        property(RELATIONSHIP, 'reviewer', 'reviewer', 'reviewer'))
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
    backend.relationshipObservations*.propertyToken == ['author', 'comments']
    backend.relationshipObservations[0].data().is(author)
    backend.relationshipObservations[1].data().is(comments)
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
    backend.relationshipObservations.isEmpty()
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
