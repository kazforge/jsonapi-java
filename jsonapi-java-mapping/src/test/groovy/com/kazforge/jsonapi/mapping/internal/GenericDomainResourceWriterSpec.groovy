package com.kazforge.jsonapi.mapping.internal

import com.kazforge.jsonapi.core.model.RelationshipData
import com.kazforge.jsonapi.core.model.ResourceIdentifier
import com.kazforge.jsonapi.jackson.diagnostic.JsonApiMappingException
import com.kazforge.jsonapi.jackson.diagnostic.MappingDiagnostic
import com.kazforge.jsonapi.mapping.internal.MappingRepresentation
import com.kazforge.jsonapi.jackson.representation.FieldPolicy
import com.kazforge.jsonapi.jackson.representation.IncludePolicy
import com.kazforge.jsonapi.jackson.representation.RepresentationPolicy
import com.kazforge.jsonapi.jackson.representation.RepresentationSelection
import spock.lang.Specification

class GenericDomainResourceWriterSpec extends Specification {

  private static final FakeType PEOPLE = new FakeType("people", Person)
  private static final FakeType COMMENTS = new FakeType("comments", Comment)
  private static final FakeType ARTICLES = new FakeType("articles", Article)
  private static final FakeType DRAFTS = new FakeType("drafts", Draft)

  def "maps identity attributes and relationship linkage without a JSON library"() {
    given:
    def writer = writer()
    def article = new Article(
        "a1",
        "Hello",
        new Person("p1", "Alice"),
        [
          new Comment("c1", "First"),
          new Comment("c2", "Second")
        ])

    when:
    def resource = writer.toResource(article)

    then:
    resource.type() == "articles"
    resource.id() == "a1"
    resource.lid() == null
    resource.attributes().attributes() == [title: "Hello"]
    resource.relationships().relationships().author.data() ==
        new RelationshipData.SingleLinkage(ResourceIdentifier.of("people", "p1"))
    resource.relationships().relationships().comments.data() ==
        new RelationshipData.IdentifierCollectionLinkage([
          ResourceIdentifier.of("comments", "c1"),
          ResourceIdentifier.of("comments", "c2")
        ])
  }

  def "maps local identifier independently of id"() {
    when:
    def resource = writer().toResource(new Draft("draft-1", "Draft"))

    then:
    resource.type() == "drafts"
    resource.id() == null
    resource.lid() == "draft-1"
    resource.attributes().attributes() == [title: "Draft"]
  }

  def "maps null to-one relationship as explicit null linkage"() {
    when:
    def resource = writer().toResource(new Article("a1", "Hello", null, []))

    then:
    resource.relationships().relationships().author.data() == RelationshipData.NullLinkage.INSTANCE
    resource.relationships().relationships().comments.data() ==
        RelationshipData.IdentifierCollectionLinkage.empty()
  }

  def "attribute backend can intentionally omit a mapped member"() {
    given:
    def backend = backend()
    backend.omittedAttributes.add("title")
    def writer = new GenericDomainResourceWriter<FakeType, String>(backend)

    when:
    def resource = writer.toResource(new Article("a1", "Hello", null, []))

    then:
    resource.attributes() == null
  }

  def "sparse fieldset is shared mapping semantics and inclusion still traverses omitted edge"() {
    given:
    def writer = writer()
    def article = new Article("a1", "Hello", new Person("p1", "Alice"), [])
    def representation = representation(
        RepresentationSelection.builder()
        .include("author")
        .fields("articles", ["title"])
        .build(),
        RepresentationPolicy.defaults().withIncludePolicy(IncludePolicy.allowAll()))

    when:
    def primary = writer.toResource(article, ARTICLES, representation)
    def included = writer.collectIncluded(article, ARTICLES, primary, representation)

    then:
    primary.attributes().attributes() == [title: "Hello"]
    primary.relationships() == null
    included.included()*.type() == ["people"]
    included.included()*.id() == ["p1"]
    included.sparseFieldsetLinkageExemptions() as List ==
        [
          com.kazforge.jsonapi.core.model.ResourceIdentity.ofId("people", "p1")
        ]
  }

  def "unknown sparse field fails in mapping diagnostic family"() {
    given:
    def representation = representation(
        RepresentationSelection.builder().fields("articles", ["missing"]).build(),
        RepresentationPolicy.defaults())

    when:
    writer().toResource(new Article("a1", "Hello", null, []), ARTICLES, representation)

    then:
    def ex = thrown(JsonApiMappingException)
    ex.diagnostic() == MappingDiagnostic.INVALID_FIELDSET_FIELD
  }

  def "denied sparse field fails in mapping diagnostic family"() {
    given:
    def representation = representation(
        RepresentationSelection.builder().fields("articles", ["title"]).build(),
        RepresentationPolicy.defaults().withFieldPolicy(FieldPolicy.denyAll()))

    when:
    writer().toResource(new Article("a1", "Hello", null, []), ARTICLES, representation)

    then:
    def ex = thrown(JsonApiMappingException)
    ex.diagnostic() == MappingDiagnostic.DENIED_FIELDSET_FIELD
  }

  def "resource without id or lid is rejected by shared writer"() {
    when:
    writer().toResource(new Person(null, "Alice"))

    then:
    thrown(IllegalArgumentException)
  }

  private static GenericDomainResourceWriter<FakeType, String> writer() {
    new GenericDomainResourceWriter<>(backend())
  }

  private static FakeBackend backend() {
    new FakeBackend([
      (ARTICLES): new MappingDefinition<>(
      "articles",
      ARTICLES,
      prop("id", MappingRole.ID, ARTICLES, false),
      null,
      [
        prop("title", MappingRole.ATTRIBUTE, ARTICLES, false)
      ],
      [
        prop("author", MappingRole.RELATIONSHIP, PEOPLE, false),
        prop("comments", MappingRole.RELATIONSHIP, COMMENTS, true)
      ]),
      (PEOPLE): new MappingDefinition<>(
      "people",
      PEOPLE,
      prop("id", MappingRole.ID, PEOPLE, false),
      null,
      [
        prop("name", MappingRole.ATTRIBUTE, PEOPLE, false)
      ],
      []),
      (COMMENTS): new MappingDefinition<>(
      "comments",
      COMMENTS,
      prop("id", MappingRole.ID, COMMENTS, false),
      null,
      [
        prop("body", MappingRole.ATTRIBUTE, COMMENTS, false)
      ],
      []),
      (DRAFTS): new MappingDefinition<>(
      "drafts",
      DRAFTS,
      null,
      prop("lid", MappingRole.LOCAL_ID, DRAFTS, false),
      [
        prop("title", MappingRole.ATTRIBUTE, DRAFTS, false)
      ],
      [])
    ])
  }

  private static MappingPropertyDefinition<FakeType, String> prop(
      String name, MappingRole role, FakeType type, boolean toMany) {
    new MappingPropertyDefinition<>(name, name, name, name, role, type, toMany)
  }

  private static MappingRepresentation representation(
      RepresentationSelection selection, RepresentationPolicy policy) {
    new MappingRepresentation(selection, policy)
  }

  private static final class FakeBackend implements DomainMappingBackend<FakeType, String> {
    private final Map<FakeType, MappingDefinition<FakeType, String>> mappings
    final Set<String> omittedAttributes = new HashSet<>()

    FakeBackend(Map<FakeType, MappingDefinition<FakeType, String>> mappings) {
      this.mappings = Map.copyOf(mappings)
    }

    @Override
    FakeType inferredType(Object domain) {
      if (domain instanceof Article) {
        return ARTICLES
      }
      if (domain instanceof Person) {
        return PEOPLE
      }
      if (domain instanceof Comment) {
        return COMMENTS
      }
      if (domain instanceof Draft) {
        return DRAFTS
      }
      throw new IllegalArgumentException("Unsupported domain " + domain.getClass())
    }

    @Override
    FakeType effectiveType(Object domain, FakeType declaredType) {
      inferredType(domain)
    }

    @Override
    Class<?> rawClass(FakeType type) {
      type.rawClass
    }

    @Override
    MappingDefinition<FakeType, String> mappingFor(FakeType type) {
      mappings.get(type)
    }

    @Override
    Object read(Object domain, MappingPropertyDefinition<FakeType, String> property) {
      def handle = property.handle()
      if (handle == "id") {
        return domain.id
      }
      if (handle == "lid") {
        return domain.lid
      }
      if (handle == "title") {
        return domain.title
      }
      if (handle == "name") {
        return domain.name
      }
      if (handle == "body") {
        return domain.body
      }
      if (handle == "author") {
        return domain.author
      }
      if (handle == "comments") {
        return domain.comments
      }
      throw new IllegalArgumentException(handle)
    }

    @Override
    MappingValue convertAttribute(
        Object domain,
        MappingDefinition<FakeType, String> mapping,
        MappingPropertyDefinition<FakeType, String> property) {
      omittedAttributes.contains(property.handle())
          ? MappingValue.omitted()
          : MappingValue.emitted(read(domain, property))
    }

    @Override
    String convertIdentifier(Object value) {
      value == null ? null : value.toString()
    }

    @Override
    FakeType relationshipTargetType(MappingPropertyDefinition<FakeType, String> property) {
      property.declaredType()
    }

    @Override
    List<Object> relationshipValues(
        Object rawValue, MappingPropertyDefinition<FakeType, String> property) {
      if (rawValue == null) {
        return []
      }
      property.toMany() ? new ArrayList<>((Collection<?>) rawValue) : [rawValue]
    }
  }

  private static final class FakeType {
    final String resourceType
    final Class<?> rawClass

    FakeType(String resourceType, Class<?> rawClass) {
      this.resourceType = resourceType
      this.rawClass = rawClass
    }
  }

  private static final class Article {
    final String id
    final String title
    final Person author
    final List<Comment> comments

    Article(String id, String title, Person author, List<Comment> comments) {
      this.id = id
      this.title = title
      this.author = author
      this.comments = comments
    }
  }

  private static final class Person {
    final String id
    final String name

    Person(String id, String name) {
      this.id = id
      this.name = name
    }
  }

  private static final class Comment {
    final String id
    final String body

    Comment(String id, String body) {
      this.id = id
      this.body = body
    }
  }

  private static final class Draft {
    final String lid
    final String title

    Draft(String lid, String title) {
      this.lid = lid
      this.title = title
    }
  }
}
