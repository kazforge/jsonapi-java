package com.kazforge.jsonapi.mapping.internal

import static com.kazforge.jsonapi.mapping.internal.MappingFakeWriteResourceBackend.property
import static com.kazforge.jsonapi.mapping.internal.PropertyRole.ATTRIBUTE
import static com.kazforge.jsonapi.mapping.internal.PropertyRole.ID
import static com.kazforge.jsonapi.mapping.internal.PropertyRole.LOCAL_ID
import static com.kazforge.jsonapi.mapping.internal.PropertyRole.RELATIONSHIP

import com.kazforge.jsonapi.core.model.Relationship
import com.kazforge.jsonapi.core.model.RelationshipData
import com.kazforge.jsonapi.core.model.ResourceIdentifier
import com.kazforge.jsonapi.diagnostic.JsonApiMappingException
import com.kazforge.jsonapi.diagnostic.MappingDiagnostic
import com.kazforge.jsonapi.representation.FieldPolicy
import spock.lang.Specification

class MappingBasicResourceWriterSpec extends Specification {

  private final MappingFakeWriteResourceBackend backend = new MappingFakeWriteResourceBackend()
  private final BasicResourceWriter<String, String> writer = new BasicResourceWriter<>(backend)

  def "writes independent id and lid members from their roles"() {
    given:
    articlesWithIdentity()
    def domain = domain('id': '1', 'localId': 'tmp-1', 'title': 'T')

    when:
    def resource = writer.writeBasic(domain, 'articles', null, false, relationshipPhase())

    then:
    resource.type() == 'articles'
    resource.id() == '1'
    resource.lid() == 'tmp-1'
  }

  def "fails at /id when a mapped id role carries no value on the strict path"() {
    given:
    articlesWithIdentity()

    when:
    writer.writeBasic(domain('title': 'T'), 'articles', null, false, relationshipPhase())

    then:
    def failure = thrown(JsonApiMappingException)
    failure.diagnostic() == MappingDiagnostic.MISSING_IDENTIFIER
    failure.propertyPath() == '/id'
    failure.message == "Identifier property 'id' is null"
  }

  def "fails at /lid when only a local-id role is mapped"() {
    given:
    backend.define('articles', property(LOCAL_ID, 'localId', 'localId', 'lid'))

    when:
    writer.writeBasic(domain([:]), 'articles', null, false, relationshipPhase())

    then:
    def failure = thrown(JsonApiMappingException)
    failure.diagnostic() == MappingDiagnostic.MISSING_IDENTIFIER
    failure.propertyPath() == '/lid'
    failure.message == "Local-id property 'localId' is null"
  }

  def "allows absent identity on the create path without narrowing the strict rule"() {
    given:
    articlesWithIdentity()

    when:
    def resource = writer.writeBasic(domain('title': 'T'), 'articles', null, true, relationshipPhase())

    then:
    resource.type() == 'articles'
    resource.id() == null
    resource.lid() == null
    resource.attributes().attributes() == [title: 'converted:T']
  }

  def "reports a present identity value that converts to no wire string at its own role"() {
    given:
    articlesWithIdentity()
    backend.unconvertibleIdentity('tmp-1')

    when:
    writer.writeBasic(domain('id': '1', 'localId': 'tmp-1'), 'articles', null, false, relationshipPhase())

    then:
    def failure = thrown(JsonApiMappingException)
    failure.diagnostic() == MappingDiagnostic.MISSING_IDENTIFIER
    failure.propertyPath() == '/lid'
    failure.message == "Local-id converter returned null for property 'localId'"
  }

  def "writes converted attributes under their JSON:API names in declaration order"() {
    given:
    backend.define(
        'articles',
        property(ID, 'id', 'id', 'id'),
        property(ATTRIBUTE, 'headline', 'wire-headline', 'wire-headline'),
        property(ATTRIBUTE, 'body', 'body', 'body'))
    def domain = domain('id': '1', 'headline': 'H', 'body': 'B')

    when:
    def resource = writer.writeBasic(domain, 'articles', null, false, relationshipPhase())

    then:
    resource.attributes().attributes() == ['wire-headline': 'converted:H', body: 'converted:B']
  }

  def "omits absent and backend-omitted attributes and keeps explicit null distinct"() {
    given:
    backend.define(
        'articles',
        property(ID, 'id', 'id', 'id'),
        property(ATTRIBUTE, 'title', 'title', 'title'),
        property(ATTRIBUTE, 'secret', 'secret', 'secret'))
    backend.omitAttribute('secret')
    def domain = domain('id': '1', 'title': null, 'secret': 'hidden')

    when:
    def resource = writer.writeBasic(domain, 'articles', null, false, relationshipPhase())

    then:
    resource.attributes().attributes() == [title: null]
  }

  def "omits empty attributes and relationships members entirely"() {
    given:
    backend.define('articles', property(ID, 'id', 'id', 'id'))

    when:
    def resource = writer.writeBasic(domain('id': '1'), 'articles', null, false, relationshipPhase())

    then:
    resource.attributes() == null
    resource.relationships() == null
  }

  def "filters attributes and passes only selected relationships to the phase in order"() {
    given:
    backend.define(
        'articles',
        property(ID, 'id', 'id', 'id'),
        property(ATTRIBUTE, 'title', 'title', 'title'),
        property(ATTRIBUTE, 'body', 'body', 'body'),
        property(RELATIONSHIP, 'author', 'author', 'author'),
        property(RELATIONSHIP, 'editor', 'editor', 'editor'))
    backend.relationshipMember('author', relationship('people', 'p1'))
    backend.relationshipMember('editor', relationship('people', 'p2'))
    def domain = domain('id': '1', 'title': 'T', 'body': 'B')

    when:
    def resource = writer.writeBasic(domain, 'articles', ['body', 'editor', 'author'] as Set, false, relationshipPhase())

    then:
    resource.attributes().attributes() == [body: 'converted:B']
    backend.relationshipPhaseOrder == ['author', 'editor']
    resource.relationships().relationships().keySet() == ['author', 'editor'] as Set
  }

  def "validates an unknown fieldset field before any selective read"() {
    given:
    backend.define('articles', property(ID, 'id', 'id', 'id'), property(ATTRIBUTE, 'title', 'title', 'title'))

    when:
    writer.validateFieldset(domain([:]), 'articles', ['bogus'], FieldPolicy.allowAll())

    then:
    def failure = thrown(JsonApiMappingException)
    failure.diagnostic() == MappingDiagnostic.INVALID_FIELDSET_FIELD
    failure.propertyPath() == null
    failure.message == "Unknown fieldset field 'bogus' on articles"
  }

  def "reports a denied fieldset field through the field policy"() {
    given:
    backend.define('articles', property(ID, 'id', 'id', 'id'), property(ATTRIBUTE, 'title', 'title', 'title'))

    when:
    writer.validateFieldset(domain([:]), 'articles', ['title'], FieldPolicy.denyAll())

    then:
    def failure = thrown(JsonApiMappingException)
    failure.diagnostic() == MappingDiagnostic.DENIED_FIELDSET_FIELD
    failure.message == 'Fieldset field denied for articles.title'
  }

  def "does not consult the field policy for an empty fieldset"() {
    when:
    writer.validateFieldset(domain([:]), 'articles', [], FieldPolicy.denyAll())

    then:
    noExceptionThrown()
  }

  def "builds single linkage through the declared target type"() {
    given:
    backend.define('articles', property(ID, 'id', 'id', 'id'))
    backend.define('people', property(ID, 'id', 'id', 'id'))
    def target = domain('id': 'p1')

    when:
    def linkage = writer.singleLinkage(target, 'people')

    then:
    linkage instanceof RelationshipData.SingleLinkage
    (linkage as RelationshipData.SingleLinkage).identifier() == ResourceIdentifier.of('people', 'p1')
  }

  def "resolves each ordinary target through the backend effective type"() {
    given:
    backend.define('people', property(ID, 'id', 'id', 'id'))
    backend.define('authors', property(ID, 'id', 'id', 'id'))
    def author = domain('id': 'p1')
    backend.mapEffectiveType(author, 'authors')

    when:
    def linkage = writer.singleLinkage(author, 'people')

    then:
    (linkage as RelationshipData.SingleLinkage).identifier().type() == 'authors'
  }

  def "builds collection linkage in value order and keeps the empty state"() {
    given:
    backend.define('comments', property(ID, 'id', 'id', 'id'))
    def first = domain('id': 'c1')
    def second = domain('id': 'c2')

    when:
    def populated = writer.collectionLinkage([first, second], 'comments')
    def empty = writer.collectionLinkage([], 'comments')

    then:
    (populated as RelationshipData.IdentifierCollectionLinkage).identifiers() ==
        [
          ResourceIdentifier.of('comments', 'c1'),
          ResourceIdentifier.of('comments', 'c2')
        ]
    (empty as RelationshipData.IdentifierCollectionLinkage).identifiers() == []
  }

  def "extracts a strict identifier for one mapped domain object"() {
    given:
    articlesWithIdentity()

    when:
    def identifier = writer.identifier(domain('id': '1', 'localId': 'tmp-1'), 'articles')

    then:
    identifier == new ResourceIdentifier('articles', '1', 'tmp-1', null, [:])

    when:
    writer.identifier(domain('title': 'T'), 'articles')

    then:
    thrown(JsonApiMappingException)
  }

  def "reads the independent identity roles for identity checks"() {
    given:
    articlesWithIdentity()
    def domain = domain('id': '1', 'localId': 'tmp-1')

    expect:
    writer.extractId(domain, 'articles') == '1'
    writer.extractLocalId(domain, 'articles') == 'tmp-1'
  }

  private BasicRelationshipWriter<String, String> relationshipPhase() {
    backend.&writeRelationships as BasicRelationshipWriter
  }

  private static Relationship relationship(String type, String id) {
    new Relationship(new RelationshipData.SingleLinkage(ResourceIdentifier.of(type, id)), null, null, [:])
  }

  private void articlesWithIdentity() {
    backend.define(
        'articles',
        property(ID, 'id', 'id', 'id'),
        property(LOCAL_ID, 'localId', 'localId', 'lid'),
        property(ATTRIBUTE, 'title', 'title', 'title'))
  }

  private Map<String, Object> domain(Map<String, Object> propertyValues) {
    def map = new LinkedHashMap<>(propertyValues)
    propertyValues.each { key, value -> backend.value(map, key, value) }
    map
  }
}
