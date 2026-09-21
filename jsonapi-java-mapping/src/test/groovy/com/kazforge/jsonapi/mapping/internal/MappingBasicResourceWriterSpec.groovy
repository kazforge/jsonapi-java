package com.kazforge.jsonapi.mapping.internal

import static com.kazforge.jsonapi.mapping.internal.MappingFakeWriteResourceBackend.property
import static com.kazforge.jsonapi.mapping.internal.PropertyRole.ATTRIBUTE
import static com.kazforge.jsonapi.mapping.internal.PropertyRole.ID
import static com.kazforge.jsonapi.mapping.internal.PropertyRole.LOCAL_ID
import static com.kazforge.jsonapi.mapping.internal.PropertyRole.RELATIONSHIP

import com.kazforge.jsonapi.core.model.Meta
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
    def resource = writer.writeBasic(domain, 'articles', null, false)

    then:
    resource.type() == 'articles'
    resource.id() == '1'
    resource.lid() == 'tmp-1'
  }

  def "fails at /id when a mapped id role carries no value on the strict path"() {
    given:
    articlesWithIdentity()

    when:
    writer.writeBasic(domain('title': 'T'), 'articles', null, false)

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
    writer.writeBasic(domain([:]), 'articles', null, false)

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
    def resource = writer.writeBasic(domain('title': 'T'), 'articles', null, true)

    then:
    resource.type() == 'articles'
    resource.id() == null
    resource.lid() == null
    resource.attributes().attributes() == [title: 'converted:T']
  }

  def "reports a present identity value that converts to no wire string at its own role"() {
    given:
    articlesWithIdentity()
    backend.unconvertibleIdentity('localId')

    when:
    writer.writeBasic(domain('id': '1', 'localId': 'tmp-1'), 'articles', null, false)

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
    def resource = writer.writeBasic(domain, 'articles', null, false)

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
    def resource = writer.writeBasic(domain, 'articles', null, false)

    then:
    resource.attributes().attributes() == [title: null]
  }

  def "omits empty attributes and relationships members entirely"() {
    given:
    backend.define('articles', property(ID, 'id', 'id', 'id'))

    when:
    def resource = writer.writeBasic(domain('id': '1'), 'articles', null, false)

    then:
    resource.attributes() == null
    resource.relationships() == null
  }

  def "filters attributes and relationships by the selected fields"() {
    given:
    backend.define(
        'articles',
        property(ID, 'id', 'id', 'id'),
        property(ATTRIBUTE, 'title', 'title', 'title'),
        property(ATTRIBUTE, 'body', 'body', 'body'),
        property(RELATIONSHIP, 'author', 'author', 'author'))
    backend.relationshipTarget('author', 'people')
    backend.define('people', property(ID, 'id', 'id', 'id'))
    def domain = domain('id': '1', 'title': 'T', 'body': 'B', 'author': domain('id': 'p1'))

    when:
    def resource = writer.writeBasic(domain, 'articles', ['body', 'author'] as Set, false)

    then:
    resource.attributes().attributes() == [body: 'converted:B']
    resource.relationships().relationships().keySet() == ['author'] as Set
    backend.enrichmentOrder == ['author']
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

  def "writes null and present to-one linkage from the declared target type"() {
    given:
    relationshipArticle()
    def absent = domain('id': '1', 'author': null)
    def present = domain('id': '1', 'author': domain('id': 'p1'))

    when:
    def absentLinkage = writer.writeBasic(absent, 'articles', null, false).relationships().relationships()
    def presentLinkage = writer.writeBasic(present, 'articles', null, false).relationships().relationships()

    then:
    absentLinkage.author.data() == RelationshipData.NullLinkage.INSTANCE
    presentLinkage.author.data() instanceof RelationshipData.SingleLinkage
    (presentLinkage.author.data() as RelationshipData.SingleLinkage).identifier() ==
        ResourceIdentifier.of('people', 'p1')
  }

  def "resolves each ordinary target through the backend effective type"() {
    given:
    relationshipArticle()
    backend.define('authors', property(ID, 'id', 'id', 'id'))
    def author = domain('id': 'p1')
    backend.mapEffectiveType(author, 'authors')

    when:
    def linkage = writer
        .writeBasic(domain('id': '1', 'author': author), 'articles', null, false)
        .relationships()
        .relationships()

    then:
    (linkage.author.data() as RelationshipData.SingleLinkage).identifier().type() == 'authors'
  }

  def "writes null, empty, and populated to-many linkage in value order"() {
    given:
    relationshipArticle()
    backend.toMany('comments')
    def nullComments = domain('id': '1')
    def emptyComments = domain('id': '1', 'comments': [])
    def populated = domain('id': '1', 'comments': [
      domain('id': 'c1'),
      domain('id': 'c2')
    ])

    when:
    def nullLinkage = writer.writeBasic(nullComments, 'articles', null, false).relationships().relationships()
    def emptyLinkage = writer.writeBasic(emptyComments, 'articles', null, false).relationships().relationships()
    def populatedLinkage = writer.writeBasic(populated, 'articles', null, false).relationships().relationships()

    then:
    (nullLinkage.comments.data() as RelationshipData.IdentifierCollectionLinkage).identifiers() == []
    (emptyLinkage.comments.data() as RelationshipData.IdentifierCollectionLinkage).identifiers() == []
    (populatedLinkage.comments.data() as RelationshipData.IdentifierCollectionLinkage).identifiers() ==
        [
          ResourceIdentifier.of('comments', 'c1'),
          ResourceIdentifier.of('comments', 'c2')
        ]
  }

  def "passes adapter-owned prebuilt linkage through unchanged"() {
    given:
    relationshipArticle()
    def identifier = ResourceIdentifier.of('people', 'p1')
    def prebuilt = new RelationshipData.SingleLinkage(identifier)

    when:
    def resource = writer
        .writeBasic(domain('id': '1', 'author': prebuilt), 'articles', null, false)

    then:
    resource.relationships().relationships().author.data().is(prebuilt)
  }

  def "enriches each selected relationship immediately after its linkage in declaration order"() {
    given:
    backend.define(
        'articles',
        property(ID, 'id', 'id', 'id'),
        property(RELATIONSHIP, 'author', 'author', 'author'),
        property(RELATIONSHIP, 'editor', 'editor', 'editor'))
    backend.relationshipTarget('author', 'people')
    backend.relationshipTarget('editor', 'people')
    backend.define('people', property(ID, 'id', 'id', 'id'))
    backend.relationshipMeta('author', Meta.of([note: 'first']))
    backend.relationshipMeta('editor', Meta.of([note: 'second']))
    def domain = domain('id': '1', 'author': domain('id': 'p1'), 'editor': domain('id': 'p2'))

    when:
    def relationships = writer.writeBasic(domain, 'articles', null, false).relationships().relationships()

    then:
    backend.enrichmentOrder == ['author', 'editor']
    relationships.author.meta() == Meta.of([note: 'first'])
    relationships.editor.meta() == Meta.of([note: 'second'])
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

  private void articlesWithIdentity() {
    backend.define(
        'articles',
        property(ID, 'id', 'id', 'id'),
        property(LOCAL_ID, 'localId', 'localId', 'lid'),
        property(ATTRIBUTE, 'title', 'title', 'title'))
  }

  private void relationshipArticle() {
    backend.define(
        'articles',
        property(ID, 'id', 'id', 'id'),
        property(RELATIONSHIP, 'author', 'author', 'author'),
        property(RELATIONSHIP, 'comments', 'comments', 'comments'))
    backend.relationshipTarget('author', 'people')
    backend.relationshipTarget('comments', 'comments')
    backend.toMany('comments')
    backend.define('people', property(ID, 'id', 'id', 'id'))
    backend.define('comments', property(ID, 'id', 'id', 'id'))
  }

  private Map<String, Object> domain(Map<String, Object> propertyValues) {
    def map = new LinkedHashMap<>(propertyValues)
    propertyValues.each { key, value -> backend.value(map, key, value) }
    map
  }
}
