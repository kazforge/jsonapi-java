package com.kazforge.jsonapi.mapping.internal

import com.kazforge.jsonapi.core.model.Attributes
import com.kazforge.jsonapi.core.model.Relationship
import com.kazforge.jsonapi.core.model.RelationshipData
import com.kazforge.jsonapi.core.model.Relationships
import com.kazforge.jsonapi.core.model.ResourceIdentifier
import com.kazforge.jsonapi.core.model.ResourceObject
import com.kazforge.jsonapi.jackson.diagnostic.JsonApiMappingException
import com.kazforge.jsonapi.jackson.diagnostic.MappingDiagnostic
import spock.lang.Specification

class GenericDomainResourceBinderSpec extends Specification {

  def "binds id attributes and linkage through backend construction"() {
    given:
    def binder = binder()
    def resource = new ResourceObject(
        "articles",
        "a1",
        null,
        Attributes.ofAttributes([title: "Hello"]),
        Relationships.ofRelationships([
          author: Relationship.withData(
          new RelationshipData.SingleLinkage(ResourceIdentifier.of("people", "p1"))),
          comments: Relationship.withData(
          new RelationshipData.IdentifierCollectionLinkage([
            ResourceIdentifier.of("comments", "c1"),
            ResourceIdentifier.of("comments", "c2")
          ]))
        ]),
        null,
        null,
        [:])

    when:
    def article = binder.fromResource(resource, BoundArticle)

    then:
    article.id == "a1"
    article.title == "Hello"
    article.author == ResourceIdentifier.of("people", "p1")
    article.comments == [
      ResourceIdentifier.of("comments", "c1"),
      ResourceIdentifier.of("comments", "c2")
    ]
  }

  def "binds local id independently of id"() {
    given:
    def resource = new ResourceObject(
        "drafts",
        null,
        "draft-1",
        Attributes.ofAttributes([title: "Draft"]),
        null,
        null,
        null,
        [:])

    when:
    def draft = binder().fromResource(resource, BoundDraft)

    then:
    draft.localId == "draft-1"
    draft.title == "Draft"
  }

  def "relationship without data remains absent from synthetic input"() {
    given:
    def backend = backend()
    def resource = new ResourceObject(
        "articles",
        "a1",
        null,
        null,
        Relationships.ofRelationships([
          author: Relationship.metaOnly(com.kazforge.jsonapi.core.model.Meta.of([note: "x"]))
        ]),
        null,
        null,
        [:])

    when:
    def article = new GenericDomainResourceBinder<Class<?>, String>(backend)
        .fromResource(resource, BoundArticle)

    then:
    !backend.lastConstructed.containsKey("author")
    article.author == null
  }

  def "explicit null relationship is supplied to backend"() {
    given:
    def backend = backend()
    def resource = new ResourceObject(
        "articles",
        "a1",
        null,
        null,
        Relationships.ofRelationships([
          author: Relationship.withData(RelationshipData.NullLinkage.INSTANCE)
        ]),
        null,
        null,
        [:])

    when:
    new GenericDomainResourceBinder<Class<?>, String>(backend)
        .fromResource(resource, BoundArticle)

    then:
    backend.lastConstructed.containsKey("author")
    backend.lastConstructed.author == null
  }

  def "resource type mismatch stays in mapping diagnostic family"() {
    when:
    binder().fromResource(ResourceObject.of("people", "p1"), BoundArticle)

    then:
    def ex = thrown(JsonApiMappingException)
    ex.diagnostic() == MappingDiagnostic.RESOURCE_TYPE_MISMATCH
    ex.location().pointer() == "/type"
  }

  def "supplied non-bindable property fails before construction"() {
    given:
    def backend = backend()
    backend.nonBindable.add("title")
    def resource = new ResourceObject(
        "articles",
        "a1",
        null,
        Attributes.ofAttributes([title: "Hello"]),
        null,
        null,
        null,
        [:])

    when:
    new GenericDomainResourceBinder<Class<?>, String>(backend)
        .fromResource(resource, BoundArticle)

    then:
    def ex = thrown(JsonApiMappingException)
    ex.diagnostic() == MappingDiagnostic.NON_DESERIALIZABLE_PROPERTY
    ex.location().pointer() == "/attributes/title"
  }

  def "null identifier parse result has stable diagnostic"() {
    given:
    def backend = backend()
    backend.nullIdentifier = true

    when:
    new GenericDomainResourceBinder<Class<?>, String>(backend)
        .fromResource(ResourceObject.of("articles", "a1"), BoundArticle)

    then:
    def ex = thrown(JsonApiMappingException)
    ex.diagnostic() == MappingDiagnostic.IDENTIFIER_CONVERSION_FAILED
  }

  def "identifier parser failure has stable diagnostic"() {
    given:
    def backend = backend()
    backend.failIdentifier = true

    when:
    new GenericDomainResourceBinder<Class<?>, String>(backend)
        .fromResource(ResourceObject.of("articles", "a1"), BoundArticle)

    then:
    def ex = thrown(JsonApiMappingException)
    ex.diagnostic() == MappingDiagnostic.IDENTIFIER_CONVERSION_FAILED
  }

  private static GenericDomainResourceBinder<Class<?>, String> binder() {
    new GenericDomainResourceBinder<>(backend())
  }

  private static FakeBindingBackend backend() {
    new FakeBindingBackend([
      (BoundArticle): new BindingDefinition<>(
      "articles",
      BoundArticle,
      prop("id", MappingRole.ID, String),
      null,
      [
        prop("title", MappingRole.ATTRIBUTE, String)
      ],
      [
        prop("author", MappingRole.RELATIONSHIP, ResourceIdentifier),
        prop("comments", MappingRole.RELATIONSHIP, List)
      ]),
      (BoundDraft): new BindingDefinition<>(
      "drafts",
      BoundDraft,
      null,
      prop("localId", "lid", MappingRole.LOCAL_ID, String),
      [
        prop("title", MappingRole.ATTRIBUTE, String)
      ],
      [])
    ])
  }

  private static BindingPropertyDefinition<Class<?>, String> prop(
      String name, MappingRole role, Class<?> type) {
    prop(name, name, role, type)
  }

  private static BindingPropertyDefinition<Class<?>, String> prop(
      String externalName, String jsonapiName, MappingRole role, Class<?> type) {
    new BindingPropertyDefinition<>(
        externalName,
        externalName,
        externalName,
        jsonapiName,
        role,
        type,
        true)
  }

  private static final class FakeBindingBackend
  implements DomainBindingBackend<Class<?>, String> {

    private final Map<Class<?>, BindingDefinition<Class<?>, String>> definitions
    final Set<String> nonBindable = new HashSet<>()
    Map<String, Object> lastConstructed = [:]
    boolean nullIdentifier
    boolean failIdentifier

    FakeBindingBackend(Map<Class<?>, BindingDefinition<Class<?>, String>> definitions) {
      this.definitions = Map.copyOf(definitions)
    }

    @Override
    Class<?> constructType(Class<?> rawType) {
      rawType
    }

    @Override
    Class<?> rawClass(Class<?> type) {
      type
    }

    @Override
    BindingDefinition<Class<?>, String> bindingFor(Class<?> type) {
      def definition = definitions.get(type)
      if (nonBindable.isEmpty()) {
        return definition
      }
      new BindingDefinition<>(
          definition.resourceType(),
          definition.domainType(),
          rewrite(definition.idProperty()),
          rewrite(definition.localIdProperty()),
          definition.attributes().collect { rewrite(it) },
          definition.relationships().collect { rewrite(it) })
    }

    private BindingPropertyDefinition<Class<?>, String> rewrite(
        BindingPropertyDefinition<Class<?>, String> property) {
      if (property == null) {
        return null
      }
      new BindingPropertyDefinition<>(
          property.handle(),
          property.logicalName(),
          property.externalName(),
          property.jsonapiName(),
          property.role(),
          property.declaredType(),
          !nonBindable.contains(property.logicalName()))
    }

    @Override
    Object parseIdentifier(String wireIdentifier) {
      if (failIdentifier) {
        throw new IllegalArgumentException("boom")
      }
      nullIdentifier ? null : wireIdentifier
    }

    @Override
    Object convertRelationship(
        RelationshipData data, BindingPropertyDefinition<Class<?>, String> property) {
      if (data instanceof RelationshipData.NullLinkage) {
        return null
      }
      if (data instanceof RelationshipData.SingleLinkage) {
        return data.identifier()
      }
      if (data instanceof RelationshipData.IdentifierCollectionLinkage) {
        return data.identifiers()
      }
      throw new IllegalArgumentException(data.toString())
    }

    @Override
    Object construct(Map<String, Object> properties, Class<?> targetType) {
      lastConstructed = new LinkedHashMap<>(properties)
      if (targetType == BoundArticle) {
        return new BoundArticle(
            properties.id as String,
            properties.title as String,
            properties.author as ResourceIdentifier,
            properties.comments as List<ResourceIdentifier>)
      }
      if (targetType == BoundDraft) {
        return new BoundDraft(
            properties.localId as String,
            properties.title as String)
      }
      throw new IllegalArgumentException(targetType.name)
    }
  }

  private static final class BoundArticle {
    final String id
    final String title
    final ResourceIdentifier author
    final List<ResourceIdentifier> comments

    BoundArticle(
    String id,
    String title,
    ResourceIdentifier author,
    List<ResourceIdentifier> comments) {
      this.id = id
      this.title = title
      this.author = author
      this.comments = comments
    }
  }

  private static final class BoundDraft {
    final String localId
    final String title

    BoundDraft(String localId, String title) {
      this.localId = localId
      this.title = title
    }
  }
}
