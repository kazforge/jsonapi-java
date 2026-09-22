package com.kazforge.jsonapi.mapping.internal

import static com.kazforge.jsonapi.mapping.internal.MappingFakeWriteResourceBackend.property
import static com.kazforge.jsonapi.mapping.internal.PropertyRole.ID
import static com.kazforge.jsonapi.mapping.internal.PropertyRole.RELATIONSHIP
import static com.kazforge.jsonapi.mapping.internal.PropertyRole.RELATIONSHIP_META
import static com.kazforge.jsonapi.mapping.internal.PropertyRole.RESOURCE_META

import com.kazforge.jsonapi.core.model.Meta
import com.kazforge.jsonapi.core.model.RelationshipData
import com.kazforge.jsonapi.core.model.ResourceIdentifier
import com.kazforge.jsonapi.core.model.ResourceObject
import com.kazforge.jsonapi.diagnostic.JsonApiMappingException
import com.kazforge.jsonapi.diagnostic.MappingDiagnostic
import com.kazforge.jsonapi.mapping.RelationshipLinkage
import spock.lang.Specification

/**
 * Mapping-local proof that resource, relationship, and identifier meta share one conversion-state,
 * object-shape, and overlay contract without modeling Jackson.
 */
class MappingWriteMetaSpec extends Specification {

  private final MappingFakeWriteResourceBackend backend = new MappingFakeWriteResourceBackend()
  private final BasicResourceWriter<String, String> writer = new BasicResourceWriter<>(backend)

  def "attaches an emitted resource meta object at /meta"() {
    given:
    articlesWithResourceMeta()
    def domain = domain('id': '1', 'meta': [source: 'cms'])

    when:
    def resource = writer.writeBasic(domain, 'articles', null, false)

    then:
    resource.meta() == Meta.of([source: 'cms'])
  }

  def "omits absent resource meta and keeps present-empty meta distinct"() {
    given:
    articlesWithResourceMeta()

    expect:
    writer.writeBasic(domain('id': '1'), 'articles', null, false).meta() == null

    and:
    writer.writeBasic(domain('id': '1', 'meta': [:]), 'articles', null, false).meta() == Meta.empty()
  }

  def "leaves resource meta absent when its conversion is omitted"() {
    given:
    articlesWithResourceMeta()
    backend.wholeMetaConversion('meta', MemberConversion.omitted())
    def domain = domain('id': '1', 'meta': [source: 'cms'])

    expect:
    writer.writeBasic(domain, 'articles', null, false).meta() == null
  }

  def "fails an emitted JSON-null whole meta at /meta"() {
    given:
    articlesWithResourceMeta()
    backend.wholeMetaConversion('meta', MemberConversion.emitted(null))
    def domain = domain('id': '1', 'meta': [source: 'cms'])

    when:
    writer.writeBasic(domain, 'articles', null, false)

    then:
    def failure = thrown(JsonApiMappingException)
    failure.diagnostic() == MappingDiagnostic.INVALID_META_TARGET
    failure.location().pointer() == '/meta'
    failure.message == 'Converted meta value is not an object (expected a JSON object, got null)'
  }

  def "fails a scalar converted whole meta at /meta"() {
    given:
    articlesWithResourceMeta()
    backend.wholeMetaConversion('meta', MemberConversion.emitted('scalar'))
    def domain = domain('id': '1', 'meta': [source: 'cms'])

    when:
    writer.writeBasic(domain, 'articles', null, false)

    then:
    def failure = thrown(JsonApiMappingException)
    failure.diagnostic() == MappingDiagnostic.INVALID_META_TARGET
    failure.location().pointer() == '/meta'
    failure.message ==
        'Converted meta value is not an object (expected a JSON object, got java.lang.String)'
  }

  def "fails a non-string whole-meta key at /meta"() {
    given:
    articlesWithResourceMeta()
    backend.wholeMetaConversion('meta', MemberConversion.emitted([(1): 'x']))
    def domain = domain('id': '1', 'meta': [source: 'cms'])

    when:
    writer.writeBasic(domain, 'articles', null, false)

    then:
    def failure = thrown(JsonApiMappingException)
    failure.diagnostic() == MappingDiagnostic.INVALID_META_TARGET
    failure.location().pointer() == '/meta'
    failure.message == 'Meta object key is not a string: 1'
  }

  def "fails core-invalid whole-meta members at /meta"() {
    given:
    articlesWithResourceMeta()
    backend.wholeMetaConversion('meta', MemberConversion.emitted(['': 'bad']))
    def domain = domain('id': '1', 'meta': [source: 'cms'])

    when:
    writer.writeBasic(domain, 'articles', null, false)

    then:
    def failure = thrown(JsonApiMappingException)
    failure.diagnostic() == MappingDiagnostic.INVALID_META_TARGET
    failure.location().pointer() == '/meta'
    failure.message == 'Invalid meta members'
  }

  def "fails a throwing whole-meta conversion at /meta"() {
    given:
    articlesWithResourceMeta()
    backend.failWholeMetaConversion('meta')
    def domain = domain('id': '1', 'meta': [source: 'cms'])

    when:
    writer.writeBasic(domain, 'articles', null, false)

    then:
    def failure = thrown(JsonApiMappingException)
    failure.diagnostic() == MappingDiagnostic.INVALID_META_TARGET
    failure.location().pointer() == '/meta'
    failure.message == 'Failed to convert meta value'
  }

  def "retains resource meta independently of an empty fieldset"() {
    given:
    articlesWithResourceMeta()
    def domain = domain('id': '1', 'meta': [source: 'cms'])

    when:
    def resource = writer.writeBasic(domain, 'articles', [] as Set, false)

    then:
    resource.meta() == Meta.of([source: 'cms'])
  }

  def "attaches matched relationship meta at the relationship meta location"() {
    given:
    articlesWithRelationshipMeta()
    def domain = domain('id': '1', 'author': ResourceIdentifier.of('people', 'p1'), 'authorMeta': [source: 'cms'])

    when:
    def resource = writer.writeBasic(domain, 'articles', null, false)

    then:
    resource.relationships().relationships().get('author').meta() == Meta.of([source: 'cms'])
  }

  def "omits relationship meta when its relationship is excluded by the fieldset"() {
    given:
    articlesWithRelationshipMeta()
    def domain = domain('id': '1', 'author': ResourceIdentifier.of('people', 'p1'), 'authorMeta': [source: 'cms'])

    when:
    def resource = writer.writeBasic(domain, 'articles', [] as Set, false)

    then:
    resource.meta() == null
    resource.relationships() == null
  }

  def "fails relationship meta conversion at the relationship meta location"() {
    given:
    articlesWithRelationshipMeta()
    backend.wholeMetaConversion('authorMeta', MemberConversion.emitted(null))
    def domain = domain('id': '1', 'author': ResourceIdentifier.of('people', 'p1'), 'authorMeta': [source: 'cms'])

    when:
    writer.writeBasic(domain, 'articles', null, false)

    then:
    def failure = thrown(JsonApiMappingException)
    failure.diagnostic() == MappingDiagnostic.INVALID_META_TARGET
    failure.location().pointer() == '/relationships/author/meta'
  }

  def "replaces identifier meta wholesale with an emitted object while preserving identity and additional members"() {
    given:
    articlesWithWrappedAuthor()
    backend.identifierMetaConversion('authorMeta', MemberConversion.emitted([role: 'editor']))
    def identifier = new ResourceIdentifier('people', 'p1', null, Meta.of([role: 'old']), ['note': 'n'])
    def domain = domain('id': '1', 'author': new RelationshipLinkage<>(identifier, 'editor'))

    when:
    def resource = writer.writeBasic(domain, 'articles', null, false)

    then:
    authorIdentifier(resource) ==
        new ResourceIdentifier('people', 'p1', null, Meta.of([role: 'editor']), ['note': 'n'])
  }

  def "preserves existing identifier meta when wrapper meta is absent"() {
    given:
    articlesWithWrappedAuthor()
    def identifier = new ResourceIdentifier('people', 'p1', null, Meta.of([role: 'old']), ['note': 'n'])
    def domain = domain('id': '1', 'author': new RelationshipLinkage<>(identifier, null))

    when:
    def resource = writer.writeBasic(domain, 'articles', null, false)

    then:
    authorIdentifier(resource) ==
        new ResourceIdentifier('people', 'p1', null, Meta.of([role: 'old']), ['note': 'n'])
  }

  def "preserves existing identifier meta when wrapper conversion is omitted"() {
    given:
    articlesWithWrappedAuthor()
    backend.identifierMetaConversion('authorMeta', MemberConversion.omitted())
    def identifier = new ResourceIdentifier('people', 'p1', null, Meta.of([role: 'old']), ['note': 'n'])
    def domain = domain('id': '1', 'author': new RelationshipLinkage<>(identifier, 'editor'))

    when:
    def resource = writer.writeBasic(domain, 'articles', null, false)

    then:
    authorIdentifier(resource) ==
        new ResourceIdentifier('people', 'p1', null, Meta.of([role: 'old']), ['note': 'n'])
  }

  def "clears existing identifier meta with an emitted JSON null while preserving identity"() {
    given:
    articlesWithWrappedAuthor()
    backend.identifierMetaConversion('authorMeta', MemberConversion.emitted(null))
    def identifier = new ResourceIdentifier('people', 'p1', null, Meta.of([role: 'old']), ['note': 'n'])
    def domain = domain('id': '1', 'author': new RelationshipLinkage<>(identifier, 'editor'))

    when:
    def resource = writer.writeBasic(domain, 'articles', null, false)

    then:
    authorIdentifier(resource) ==
        new ResourceIdentifier('people', 'p1', null, null, ['note': 'n'])
  }

  def "fails a scalar identifier meta at the occurrence location"() {
    given:
    articlesWithWrappedAuthor()
    backend.identifierMetaConversion('authorMeta', MemberConversion.emitted('scalar'))
    def domain = domain('id': '1', 'author': new RelationshipLinkage<>(ResourceIdentifier.of('people', 'p1'), 'editor'))

    when:
    writer.writeBasic(domain, 'articles', null, false)

    then:
    def failure = thrown(JsonApiMappingException)
    failure.diagnostic() == MappingDiagnostic.INVALID_META_TARGET
    failure.location().pointer() == '/relationships/author/data/meta'
    failure.message ==
        'Converted identifier meta value is not an object (expected a JSON object, got java.lang.String)'
  }

  def "fails a throwing identifier-meta conversion at the occurrence location"() {
    given:
    articlesWithWrappedAuthor()
    backend.failIdentifierMetaConversion('authorMeta')
    def domain = domain('id': '1', 'author': new RelationshipLinkage<>(ResourceIdentifier.of('people', 'p1'), 'editor'))

    when:
    writer.writeBasic(domain, 'articles', null, false)

    then:
    def failure = thrown(JsonApiMappingException)
    failure.diagnostic() == MappingDiagnostic.INVALID_META_TARGET
    failure.location().pointer() == '/relationships/author/data/meta'
    failure.message == "Failed to convert identifier meta for relationship 'author'"
  }

  private static ResourceIdentifier authorIdentifier(ResourceObject resource) {
    (resource.relationships().relationships().get('author').data() as RelationshipData.SingleLinkage)
        .identifier()
  }

  private void articlesWithResourceMeta() {
    backend.define(
        'articles',
        property(ID, 'id', 'id', 'id'),
        property(RESOURCE_META, 'meta', 'meta', 'meta'))
  }

  private void articlesWithRelationshipMeta() {
    backend.define(
        'articles',
        property(ID, 'id', 'id', 'id'),
        property(RELATIONSHIP, 'author', 'author', 'author'),
        property(RELATIONSHIP_META, 'authorMeta', 'authorMeta', 'author'))
    backend.define('people', property(ID, 'id', 'id', 'id'))
    backend.relationshipShape('author', RelationshipShape.ordinary(false, 'people'))
  }

  private void articlesWithWrappedAuthor() {
    backend.define(
        'articles',
        property(ID, 'id', 'id', 'id'),
        property(RELATIONSHIP, 'author', 'author', 'author'))
    backend.relationshipShape(
        'author',
        RelationshipShape.wrapper(
        false,
        'authorMeta',
        RelationshipShape.ordinary(false, 'people')))
  }

  private Map<String, Object> domain(Map<String, Object> propertyValues) {
    def map = new LinkedHashMap<>(propertyValues)
    propertyValues.each { key, value -> backend.value(map, key, value) }
    map
  }
}
