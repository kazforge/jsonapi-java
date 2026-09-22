package com.kazforge.jsonapi.mapping.internal

import static com.kazforge.jsonapi.mapping.internal.MappingFakeWriteResourceBackend.property
import static com.kazforge.jsonapi.mapping.internal.PropertyRole.ATTRIBUTE
import static com.kazforge.jsonapi.mapping.internal.PropertyRole.ID
import static com.kazforge.jsonapi.mapping.internal.PropertyRole.RELATIONSHIP
import static com.kazforge.jsonapi.mapping.internal.PropertyRole.RELATIONSHIP_META
import static com.kazforge.jsonapi.mapping.internal.PropertyRole.RESOURCE_META

import com.kazforge.jsonapi.core.model.Attributes
import com.kazforge.jsonapi.core.model.Link
import com.kazforge.jsonapi.core.model.Links
import com.kazforge.jsonapi.core.model.Meta
import com.kazforge.jsonapi.core.model.Relationship
import com.kazforge.jsonapi.core.model.RelationshipData
import com.kazforge.jsonapi.core.model.Relationships
import com.kazforge.jsonapi.core.model.ResourceIdentifier
import com.kazforge.jsonapi.core.model.ResourceObject
import com.kazforge.jsonapi.diagnostic.JsonApiMappingException
import com.kazforge.jsonapi.diagnostic.MappingDiagnostic
import com.kazforge.jsonapi.mapping.RelationshipDecoration
import com.kazforge.jsonapi.mapping.ResourceDecoration
import com.kazforge.jsonapi.mapping.ResourceDecorator
import com.kazforge.jsonapi.mapping.ResourceDecoratorRegistry
import spock.lang.Specification

/**
 * Mapping-local proof that additive link decoration owns exact effective-class lookup, logical-name
 * to JSON:API-name resolution, whole-value link replacement, member preservation, fieldset
 * non-resurrection, and its stable diagnostics without modeling Jackson.
 */
class MappingResourceDecorationWriterSpec extends Specification {

  private static final Links RESOURCE_LINKS =
  Links.ofLinks([self: new Link.StringLink("https://example.test/articles/1")])

  private static final Links COMMENTS_LINKS =
  Links.ofLinks([self: new Link.StringLink("https://example.test/articles/1/relationships/comments")])

  def "adds resource links while preserving identity, attributes, relationships, meta, and members"() {
    given:
    def registry = registry(String, { s -> ResourceDecoration.ofLinks(RESOURCE_LINKS) })
    def base = baseResource()

    when:
    def decorated = decorate(base, registry)

    then:
    decorated.links() == RESOURCE_LINKS
    decorated.id() == '1'
    decorated.attributes() == base.attributes()
    decorated.relationships() == base.relationships()
    decorated.meta() == base.meta()
    decorated.additionalMembers() == base.additionalMembers()
  }

  def "adds relationship links while preserving linkage, meta, and additional members"() {
    given:
    def registry = registry(String, { s ->
      ResourceDecoration.builder().relationship('comments', RelationshipDecoration.of(COMMENTS_LINKS)).build()
    })
    def base = baseResource()

    when:
    def decorated = decorate(base, registry)
    def comments = decorated.relationships().relationships().get('comments')
    def originalComments = base.relationships().relationships().get('comments')

    then:
    comments.links() == COMMENTS_LINKS
    comments.data() == originalComments.data()
    comments.meta() == originalComments.meta()
    comments.additionalMembers() == originalComments.additionalMembers()
    decorated.relationships().relationships().get('author').is(
        base.relationships().relationships().get('author'))
  }

  def "resolves a decorated logical relationship identity through its JSON:API member name"() {
    given:
    def registry = registry(String, { s ->
      ResourceDecoration.builder().relationship('writtenBy', RelationshipDecoration.of(COMMENTS_LINKS)).build()
    })

    when:
    def decorated = decorate(baseResource(), registry)
    def relationships = decorated.relationships().relationships()

    then:
    relationships.get('author').links() == COMMENTS_LINKS
    !relationships.containsKey('writtenBy')
  }

  def "does not create a relationship that ordinary mapping did not emit"() {
    given:
    def registry = registry(String, { s ->
      ResourceDecoration.builder().relationship('writtenBy', RelationshipDecoration.of(COMMENTS_LINKS)).build()
    })
    def base = baseResourceWithoutAuthor()

    when:
    def decorated = decorate(base, registry)

    then:
    decorated.is(base)
    decorated.relationships().relationships().containsKey('comments')
    !decorated.relationships().relationships().containsKey('author')
  }

  def "keeps present-empty resource and relationship links distinct from absence"() {
    given:
    def registry = registry(String, { s ->
      ResourceDecoration.builder()
          .links(Links.empty())
          .relationship('comments', RelationshipDecoration.of(Links.empty()))
          .build()
    })

    when:
    def decorated = decorate(baseResource(), registry)

    then:
    decorated.links() == Links.empty()
    decorated.relationships().relationships().get('comments').links() == Links.empty()
  }

  def "returns the base resource when no decorator matches the effective class"() {
    given:
    def registry = registry(Integer, { i -> ResourceDecoration.ofLinks(RESOURCE_LINKS) })
    def base = baseResource()

    expect:
    decorate(base, registry).is(base)
  }

  def "looks up the effective class exactly rather than by assignability"() {
    given:
    def registry = registry(CharSequence, { c -> ResourceDecoration.ofLinks(RESOURCE_LINKS) })
    def base = baseResource()

    expect:
    decorate(base, registry).is(base)
  }

  def "returns the base resource when the decorator contributes no links"() {
    given:
    def registry = registry(String, { s -> ResourceDecoration.empty() })
    def base = baseResource()

    expect:
    decorate(base, registry).is(base)
  }

  def "leaves an unselected relationship undecorated"() {
    given:
    def registry = registry(String, { s ->
      ResourceDecoration.builder().relationship('comments', RelationshipDecoration.of(COMMENTS_LINKS)).build()
    })
    def base = baseResource()

    when:
    def decorated = decorate(base, registry, ['title'] as Set)

    then:
    decorated.is(base)
  }

  def "fails a non-relationship decoration target"() {
    given:
    def registry = registry(String, { s ->
      ResourceDecoration.builder().relationship('title', RelationshipDecoration.of(COMMENTS_LINKS)).build()
    })

    when:
    decorate(baseResource(), registry)

    then:
    def failure = thrown(JsonApiMappingException)
    failure.diagnostic() == MappingDiagnostic.INVALID_DECORATION_TARGET
    failure.propertyPath() == null
    failure.message == "Decoration target 'title' is a attribute, not a relationship on articles"
  }

  def "fails an unknown decoration target"() {
    given:
    def registry = registry(String, { s ->
      ResourceDecoration.builder().relationship('bogus', RelationshipDecoration.of(COMMENTS_LINKS)).build()
    })

    when:
    decorate(baseResource(), registry)

    then:
    def failure = thrown(JsonApiMappingException)
    failure.diagnostic() == MappingDiagnostic.INVALID_DECORATION_TARGET
    failure.message == "Unknown decoration target 'bogus' on articles"
  }

  def "fails a null decorator result"() {
    given:
    def registry = registry(String, { s -> null })

    when:
    decorate(baseResource(), registry)

    then:
    def failure = thrown(JsonApiMappingException)
    failure.diagnostic() == MappingDiagnostic.INVALID_DECORATION_STATE
    failure.propertyPath() == null
    failure.message == 'Decorator returned null for articles'
  }

  def "fails a decorator that throws and retains the cause"() {
    given:
    def registry = registry(String, { s -> throw new IllegalStateException('boom') })

    when:
    decorate(baseResource(), registry)

    then:
    def failure = thrown(JsonApiMappingException)
    failure.diagnostic() == MappingDiagnostic.INVALID_DECORATION_STATE
    failure.message == 'Decorator failed for articles: boom'
    failure.cause instanceof IllegalStateException
  }

  private static ResourceObject decorate(
      ResourceObject base, ResourceDecoratorRegistry registry, Set<String> allowedFields = null) {
    ResourceDecorationWriter.decorate(
        base, 'domain', String, articleDefinition(), allowedFields, registry)
  }

  private static <T> ResourceDecoratorRegistry registry(Class<T> type, Closure<?> decoration) {
    ResourceDecoratorRegistry.builder().register(type, decoration as ResourceDecorator<T>).build()
  }

  private static WriteResourceDefinition<String> articleDefinition() {
    new WriteResourceDefinition<>(
        'articles',
        property(ID, 'id', 'id', 'id'),
        null,
        [
          property(ATTRIBUTE, 'title', 'title', 'title')
        ],
        [
          property(RELATIONSHIP, 'writtenBy', 'author', 'author'),
          property(RELATIONSHIP, 'comments', 'comments', 'comments')
        ],
        property(RESOURCE_META, 'meta', 'meta', 'meta'),
        [
          property(RELATIONSHIP_META, 'authorMeta', 'authorMeta', 'author')
        ])
  }

  private static ResourceObject baseResource() {
    new ResourceObject(
        'articles',
        '1',
        null,
        Attributes.ofAttributes([title: 'T']),
        Relationships.ofRelationships([
          author: new Relationship(
          new RelationshipData.SingleLinkage(ResourceIdentifier.of('people', 'p1')),
          null,
          Meta.of([role: 'author']),
          [ext: 'kept']),
          comments: new Relationship(
          new RelationshipData.IdentifierCollectionLinkage(
          [
            ResourceIdentifier.of('comments', 'c1')
          ]),
          null,
          Meta.of([note: 'n']),
          [extra: 'e'])
        ]),
        null,
        Meta.of([source: 'cms']),
        [custom: 'member'])
  }

  private static ResourceObject baseResourceWithoutAuthor() {
    new ResourceObject(
        'articles',
        '1',
        null,
        Attributes.ofAttributes([title: 'T']),
        Relationships.ofRelationships([
          comments: new Relationship(
          new RelationshipData.IdentifierCollectionLinkage(
          [
            ResourceIdentifier.of('comments', 'c1')
          ]),
          null,
          null,
          [:])
        ]),
        null,
        null,
        [:])
  }
}
