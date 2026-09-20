package com.kazforge.jsonapi.mapping.internal

import com.kazforge.jsonapi.core.model.ResourceIdentifier
import com.kazforge.jsonapi.core.model.ResourceObject
import com.kazforge.jsonapi.core.model.ResourceIdentity
import com.kazforge.jsonapi.jackson.diagnostic.JsonApiMappingException
import com.kazforge.jsonapi.jackson.diagnostic.MappingDiagnostic
import com.kazforge.jsonapi.mapping.internal.MappingRepresentation
import com.kazforge.jsonapi.jackson.representation.IncludePolicy
import com.kazforge.jsonapi.jackson.representation.RepresentationPolicy
import com.kazforge.jsonapi.jackson.representation.RepresentationSelection
import spock.lang.Specification

class GenericCompoundInclusionEngineSpec extends Specification {

  def "compound inclusion is independent of Jackson type tokens and introspection"() {
    given:
    def articleType = new FakeType("articles", Article)
    def personType = new FakeType("people", Person)
    def author = new Person("9")
    def article = new Article("1", author)

    def backend = new FakeBackend(["articles#author": personType])
    def engine = new GenericCompoundInclusionEngine<FakeType>(backend)
    def representation = new MappingRepresentation(
        RepresentationSelection.builder().include("author").build(),
        RepresentationPolicy.defaults().withIncludePolicy(IncludePolicy.allowAll())
        )

    when:
    def result = engine.collectIncluded(
        [article],
        [articleType],
        [
          ResourceObject.of("articles", "1")
        ],
        null,
        representation
        )

    then:
    result.included() == [
      ResourceObject.of("people", "9")
    ]
    result.sparseFieldsetLinkageExemptions().isEmpty()
  }

  def "no include request returns absent included and explicit empty request returns empty included"() {
    given:
    def articleType = new FakeType("articles", Article)
    def backend = new FakeBackend([:])
    def engine = new GenericCompoundInclusionEngine<FakeType>(backend)

    expect:
    engine.collectIncluded(
        [new Article("1", null)],
        [articleType],
        [
          ResourceObject.of("articles", "1")
        ],
        null,
        new MappingRepresentation(
        RepresentationSelection.none(),
        RepresentationPolicy.defaults())
        ).included() == null

    and:
    engine.collectIncluded(
        [new Article("1", null)],
        [articleType],
        [
          ResourceObject.of("articles", "1")
        ],
        null,
        new MappingRepresentation(
        RepresentationSelection.builder().includeRequested().build(),
        RepresentationPolicy.defaults())
        ).included() == []
  }

  def "mismatched primary snapshots are rejected before traversal"() {
    given:
    def engine = new GenericCompoundInclusionEngine<FakeType>(new FakeBackend([:]))

    when:
    engine.collectIncluded(
        [new Article("1", null)],
        [],
        [
          ResourceObject.of("articles", "1")
        ],
        null,
        new MappingRepresentation(
        RepresentationSelection.builder().include("author").build(),
        RepresentationPolicy.defaults().withIncludePolicy(IncludePolicy.allowAll()))
        )

    then:
    thrown(IllegalArgumentException)
  }

  def "unknown include relationship has stable mapping diagnostic"() {
    given:
    def type = new FakeType("articles", Article)
    def engine = new GenericCompoundInclusionEngine<FakeType>(new FakeBackend([:]))

    when:
    engine.collectIncluded(
        [new Article("1", null)],
        [type],
        [
          ResourceObject.of("articles", "1")
        ],
        null,
        new MappingRepresentation(
        RepresentationSelection.builder().include("missing").build(),
        RepresentationPolicy.defaults().withIncludePolicy(IncludePolicy.allowAll()))
        )

    then:
    def ex = thrown(JsonApiMappingException)
    ex.diagnostic() == MappingDiagnostic.INVALID_INCLUDE_PATH
  }

  def "include policy denial has stable mapping diagnostic"() {
    given:
    def articleType = new FakeType("articles", Article)
    def personType = new FakeType("people", Person)
    def engine = new GenericCompoundInclusionEngine<FakeType>(
        new FakeBackend(["articles#author": personType]))

    and:
    def representation = new MappingRepresentation(
        RepresentationSelection.builder().include("author").build(),
        RepresentationPolicy.defaults())

    when:
    engine.collectIncluded(
        [
          new Article("1", new Person("9"))
        ],
        [articleType],
        [
          ResourceObject.of("articles", "1")
        ],
        null,
        representation)

    then:
    def ex = thrown(JsonApiMappingException)
    ex.diagnostic() == MappingDiagnostic.DENIED_RELATIONSHIP_INCLUDE
  }

  def "include depth is enforced before traversal"() {
    given:
    def articleType = new FakeType("articles", Article)
    def personType = new FakeType("people", Person)
    def engine = new GenericCompoundInclusionEngine<FakeType>(
        new FakeBackend(["articles#author": personType]))

    when:
    engine.collectIncluded(
        [
          new Article("1", new Person("9"))
        ],
        [articleType],
        [
          ResourceObject.of("articles", "1")
        ],
        null,
        new MappingRepresentation(
        RepresentationSelection.builder().include("author").build(),
        RepresentationPolicy.defaults()
        .withIncludePolicy(IncludePolicy.allowAll())
        .withMaxIncludeDepth(0))
        )

    then:
    def ex = thrown(JsonApiMappingException)
    ex.diagnostic() == MappingDiagnostic.INCLUDE_DEPTH_EXCEEDED
  }

  def "fieldset-omitted include edge records linkage exemption"() {
    given:
    def articleType = new FakeType("articles", Article)
    def personType = new FakeType("people", Person)
    def engine = new GenericCompoundInclusionEngine<FakeType>(
        new FakeBackend(["articles#author": personType]))
    def representation = new MappingRepresentation(
        RepresentationSelection.builder()
        .include("author")
        .fields("articles", ["title"])
        .build(),
        RepresentationPolicy.defaults().withIncludePolicy(IncludePolicy.allowAll()))

    when:
    def result = engine.collectIncluded(
        [
          new Article("1", new Person("9"))
        ],
        [articleType],
        [
          ResourceObject.of("articles", "1")
        ],
        null,
        representation)

    then:
    result.included() == [
      ResourceObject.of("people", "9")
    ]
    result.sparseFieldsetLinkageExemptions() == [
      ResourceIdentity.ofId("people", "9")
    ] as Set
  }

  def "max included resource count is enforced during traversal"() {
    given:
    def articleType = new FakeType("articles", Article)
    def personType = new FakeType("people", Person)
    def engine = new GenericCompoundInclusionEngine<FakeType>(
        new FakeBackend(["articles#author": personType]))

    when:
    engine.collectIncluded(
        [
          new Article("1", new Person("9"))
        ],
        [articleType],
        [
          ResourceObject.of("articles", "1")
        ],
        null,
        new MappingRepresentation(
        RepresentationSelection.builder().include("author").build(),
        RepresentationPolicy.defaults()
        .withIncludePolicy(IncludePolicy.allowAll())
        .withMaxIncludedResources(0))
        )

    then:
    def ex = thrown(JsonApiMappingException)
    ex.diagnostic() == MappingDiagnostic.INCLUDE_COUNT_EXCEEDED
  }

  private static final class FakeType {
    private final String resourceType
    private final Class<?> rawClass

    FakeType(String resourceType, Class<?> rawClass) {
      this.resourceType = resourceType
      this.rawClass = rawClass
    }

    String resourceType() {
      resourceType
    }

    Class<?> rawClass() {
      rawClass
    }
  }

  private static final class Article {
    private final String id
    private final Person author

    Article(String id, Person author) {
      this.id = id
      this.author = author
    }

    String id() {
      id
    }

    Person author() {
      author
    }
  }

  private static final class Person {
    private final String id

    Person(String id) {
      this.id = id
    }

    String id() {
      id
    }
  }

  private static final class FakeBackend implements InclusionMappingBackend<FakeType> {
    private final Map<String, FakeType> relationships

    FakeBackend(Map<String, FakeType> relationships) {
      this.relationships = Map.copyOf(relationships)
    }

    @Override
    Class<?> rawClass(FakeType type) {
      type.rawClass()
    }

    @Override
    String resourceType(FakeType type) {
      type.resourceType()
    }

    @Override
    Optional<FakeType> relatedType(
        FakeType ownerType, String relationshipName, String dottedPath) {
      Optional.ofNullable(relationships.get(key(ownerType, relationshipName)))
    }

    @Override
    List<Object> relatedValues(
        Object domain, FakeType ownerType, String relationshipName) {
      if (domain instanceof Article && relationshipName == "author") {
        return [((Article) domain).author()]
      }
      []
    }

    @Override
    FakeType effectiveType(Object domain, FakeType declaredType) {
      declaredType
    }

    @Override
    ResourceIdentifier identifier(Object domain, FakeType type) {
      ResourceIdentifier.of(type.resourceType(), idOf(domain))
    }

    @Override
    ResourceObject render(
        Object domain, FakeType type, MappingRepresentation representation) {
      ResourceObject.of(type.resourceType(), idOf(domain))
    }

    @Override
    boolean hasIdentity(Object domain, FakeType type) {
      idOf(domain) != null
    }

    private static String key(FakeType type, String relationshipName) {
      type.resourceType() + "#" + relationshipName
    }

    private static String idOf(Object domain) {
      if (domain instanceof Article) {
        return ((Article) domain).id()
      }
      if (domain instanceof Person) {
        return ((Person) domain).id()
      }
      throw new IllegalArgumentException("Unsupported fake domain type: " + domain.getClass())
    }
  }
}
