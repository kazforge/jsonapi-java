package com.kazforge.jsonapi.jackson3

import com.kazforge.jsonapi.annotation.JsonApiAttribute
import com.kazforge.jsonapi.annotation.JsonApiId
import com.kazforge.jsonapi.annotation.JsonApiLocalId
import com.kazforge.jsonapi.annotation.JsonApiRelationship
import com.kazforge.jsonapi.annotation.JsonApiResource
import com.kazforge.jsonapi.core.model.DocumentData
import com.kazforge.jsonapi.core.model.Meta
import com.kazforge.jsonapi.core.model.Relationship
import com.kazforge.jsonapi.core.model.RelationshipData
import com.kazforge.jsonapi.core.model.Relationships
import com.kazforge.jsonapi.core.model.ResourceIdentifier
import com.kazforge.jsonapi.core.model.ResourceObject
import com.kazforge.jsonapi.core.validation.DocumentUsage
import com.kazforge.jsonapi.core.validation.ValidationContext
import com.kazforge.jsonapi.jackson.diagnostic.JsonApiMappingException
import com.kazforge.jsonapi.jackson.diagnostic.MappingDiagnostic
import com.kazforge.jsonapi.jackson.mapping.IdentifierConverter
import com.kazforge.jsonapi.jackson.mapping.RelationshipLinkage
import com.kazforge.jsonapi.jackson.representation.IncludePath
import com.kazforge.jsonapi.jackson.representation.IncludePolicy
import com.kazforge.jsonapi.jackson.representation.RepresentationPolicy
import com.kazforge.jsonapi.jackson.representation.RepresentationSelection
import com.kazforge.jsonapi.fixtures.localid.IdentifiedComment
import com.kazforge.jsonapi.fixtures.localid.LocalIdOnlyComment
import com.kazforge.jsonapi.fixtures.localid.LocalIdentityArticle
import spock.lang.Shared
import spock.lang.Specification
import tools.jackson.databind.PropertyNamingStrategies
import tools.jackson.databind.json.JsonMapper

/**
 * Jackson 3 behavioral proof that {@code @JsonApiId} and {@code @JsonApiLocalId} are independent
 * identity roles: wire {@code id} and {@code lid} map only to their own role, neither direction
 * falls back to the other, linkage and inclusion preserve both members, and ambiguous declarations
 * fail deterministically.
 */
class LocalIdentifierMappingSpec extends Specification {

  private static final String ARTICLES = "articles"
  private static final String COMMENTS = "comments"
  private static final String PEOPLE = "people"

  @Shared
  JsonApiResourceMapper mapper = JsonApiJackson3.resourceMapper(JsonMapper.builder().build())

  @Shared
  JsonApiResourceBinder binder = JsonApiJackson3.resourceBinder(JsonMapper.builder().build())

  // ============================== WRITES ==============================

  def "writes id-only domain values as id with no lid"() {
    expect:
    def resource = mapper.toResource(new LocalIdentityArticle("123", null, "Title"))
    resource.type() == ARTICLES
    resource.id() == "123"
    resource.lid() == null
  }

  def "writes local-id-only domain values as lid and never promotes lid to id"() {
    expect:
    def resource = mapper.toResource(new LocalIdentityArticle(null, "local-1", "Title"))
    resource.type() == ARTICLES
    resource.id() == null
    resource.lid() == "local-1"
  }

  def "writes id and lid independently when both roles carry values"() {
    expect:
    def resource = mapper.toResource(new LocalIdentityArticle("123", "local-1", "Title"))
    resource.id() == "123"
    resource.lid() == "local-1"
  }

  def "no usable identity on either role fails at /id when an id role is mapped"() {
    when:
    mapper.toResource(new LocalIdentityArticle(null, null, "Title"))

    then:
    def ex = thrown(JsonApiMappingException)
    ex.diagnostic() == MappingDiagnostic.MISSING_IDENTIFIER
    ex.propertyPath() == "/id"
  }

  def "a lid-only type with a null local-id fails at /lid"() {
    when:
    mapper.toResource(new LocalIdOnlyComment(null, "Body"))

    then:
    def ex = thrown(JsonApiMappingException)
    ex.diagnostic() == MappingDiagnostic.MISSING_IDENTIFIER
    ex.propertyPath() == "/lid"
  }

  def "a local-id converter returning null fails with MISSING_IDENTIFIER at /lid"() {
    given:
    def converter = new IdentifierConverter() {
          @Override
          String convert(Object idValue) {
            "local-1" == idValue ? null : idValue.toString()
          }
        }
    def localMapper = JsonApiJackson3.resourceMapper(JsonMapper.builder().build(), converter)

    when:
    localMapper.toResource(new LocalIdentityArticle("123", "local-1", "Title"))

    then:
    def ex = thrown(JsonApiMappingException)
    ex.diagnostic() == MappingDiagnostic.MISSING_IDENTIFIER
    ex.propertyPath() == "/lid"
  }

  def "the default converter stringifies non-string local-id scalars"() {
    given:
    def localMapper = JsonApiJackson3.resourceMapper(JsonMapper.builder().build())

    when:
    def resource = localMapper.toResource(new LongLocalIdArticle(7L))

    then:
    resource.id() == "9"
    resource.lid() == "7"
  }

  // ============================== READS ==============================

  def "wire lid binds only to the local-id role and never the id role"() {
    expect:
    def bound = bind(null, "tmp-123")
    bound.id() == null
    bound.localId() == "tmp-123"
    bound.title() == null
  }

  def "wire id binds only to the id role"() {
    expect:
    def bound = bind("42", null)
    bound.id() == "42"
    bound.localId() == null
  }

  def "wire id and lid bind independently"() {
    expect:
    def bound = bind("42", "tmp-123")
    bound.id() == "42"
    bound.localId() == "tmp-123"
  }

  def "wire lid is ignored by a type with no local-id role instead of entering the id role"() {
    expect:
    def bound =
        binder.fromResource(typedResource("id-only", null, "tmp-123"), LocalIdFixtures.IdOnlyArticle)
    bound.id == null
    bound.title == null
  }

  def "local-id conversion failure fails with IDENTIFIER_CONVERSION_FAILED at /lid"() {
    given:
    def converter = new IdentifierConverter() {
          @Override
          String convert(Object idValue) {
            idValue.toString()
          }

          @Override
          Object parse(String wireIdentifier) {
            throw new IllegalArgumentException("bad lid")
          }
        }
    def localBinder = JsonApiJackson3.resourceBinder(JsonMapper.builder().build(), converter)

    when:
    localBinder.fromResource(resource(null, "tmp-123"), LocalIdentityArticle)

    then:
    def ex = thrown(JsonApiMappingException)
    ex.diagnostic() == MappingDiagnostic.IDENTIFIER_CONVERSION_FAILED
    ex.propertyPath() == "/lid"
  }

  // ============================== ROUND TRIP ==============================

  def "lid-only read then write stays lid-only and never invents an id"() {
    given:
    def bound = bind(null, "tmp-123")

    when:
    def resource = mapper.toResource(bound)

    then:
    resource.type() == ARTICLES
    resource.id() == null
    resource.lid() == "tmp-123"
  }

  def "lid-only round trip serializes as a lid-only resource in create-request usage"() {
    given:
    def bound = bind(null, "tmp-123")
    def document = mapper.toDocument(bound)
    // Document validation owns usage legality: a lid-only primary is a create-request state, so
    // the wire proof composes a create-request context rather than mapping inferring usage.
    def context = ValidationContext.defaults().withDocumentUsage(DocumentUsage.CREATE_REQUEST)
    def writer = JsonApiJackson3.writer(JsonMapper.builder().build(), context)

    when:
    def json = writer.writeValueAsString(document)

    then:
    json.contains('"type":"articles"')
    json.contains('"lid":"tmp-123"')
    !json.contains('"id"')
  }

  // ============================== RELATIONSHIP LINKAGE ==============================

  def "a lid-only related target produces lid-only linkage"() {
    given:
    def article =
        new LidArticle("1", [
          new LocalIdOnlyComment("local-comment-1", "Nice")
        ], Optional.empty())

    when:
    def resource = mapper.toResource(article)

    then:
    resource.relationships().relationships().comments.data() ==
        new RelationshipData.IdentifierCollectionLinkage([
          new ResourceIdentifier(COMMENTS, null, "local-comment-1", null, Map.of())
        ])
  }

  def "a lid-only to-one related target produces a single lid linkage"() {
    given:
    def article =
        new LidArticle("1", [], Optional.of(new LocalIdOnlyComment("local-comment-1", "Nice")))

    when:
    def resource = mapper.toResource(article)

    then:
    resource.relationships().relationships().featured.data() ==
        new RelationshipData.SingleLinkage(
        new ResourceIdentifier(COMMENTS, null, "local-comment-1", null, Map.of()))
  }

  def "a related target with id and lid preserves both members"() {
    given:
    def article =
        new DualIdentityArticle("1", [
          new IdentifiedComment("99", "local-comment-1", "Nice")
        ])

    when:
    def resource = mapper.toResource(article)

    then:
    resource.relationships().relationships().comments.data() ==
        new RelationshipData.IdentifierCollectionLinkage([
          new ResourceIdentifier(COMMENTS, "99", "local-comment-1", null, Map.of())
        ])
  }

  def "identifier meta overlay on a lid-only linkage preserves the lid"() {
    given:
    def article =
        new LidLinkageArticle(
        "1",
        new RelationshipLinkage<>(
        new ResourceIdentifier(PEOPLE, null, "local-person-1", null, Map.of()),
        new AuthorMeta("editor")))

    when:
    def resource = mapper.toResource(article)

    then:
    resource.relationships().relationships().author.data() ==
        new RelationshipData.SingleLinkage(
        new ResourceIdentifier(
        PEOPLE,
        null,
        "local-person-1",
        Meta.of(Map.of("role", "editor")),
        Map.of()))
  }

  def "built-in read linkage conversion preserves ResourceIdentifier lid"() {
    expect:
    def bound = binder.fromResource(resourceWithLinkage(), LidAuthorArticle)
    bound.author == new ResourceIdentifier(PEOPLE, "42", "tmp-123", null, Map.of())
  }

  // ============================== COMPOUND INCLUSION ==============================

  def "included resources keep their lid through compound traversal"() {
    given:
    def article =
        new LidArticle("1", [
          new LocalIdOnlyComment("local-comment-1", "Nice")
        ], Optional.empty())
    def selection = RepresentationSelection.builder().include(IncludePath.of(COMMENTS)).build()
    def policy = RepresentationPolicy.defaults().withIncludePolicy(IncludePolicy.allowAll())

    when:
    def document = mapper.toDocument(article, null, selection, policy)

    then:
    document.included() == [
      mapper.toResource(new LocalIdOnlyComment("local-comment-1", "Nice"))
    ]
  }

  def "included lid-only resources deduplicate by shared lid identity"() {
    given:
    def shared = new LocalIdOnlyComment("local-comment-1", "Nice")
    def article =
        new LidArticle("1", [
          shared,
          new LocalIdOnlyComment("local-comment-2", "More")
        ], Optional.empty())
    def selection = RepresentationSelection.builder().include(IncludePath.of(COMMENTS)).build()
    def policy = RepresentationPolicy.defaults().withIncludePolicy(IncludePolicy.allowAll())

    when:
    def document = mapper.toDocument(article, null, selection, policy)

    then:
    document.included()*.lid() == [
      "local-comment-1",
      "local-comment-2"
    ]
    document.included()*.id() == [null, null]
  }

  def "a lid-only related resource matching the primary lid identity is not re-included"() {
    given:
    def primary = new LidPrimaryComment("local-comment-1")
    def selection = RepresentationSelection.builder().include(IncludePath.of("self")).build()
    def policy = RepresentationPolicy.defaults().withIncludePolicy(IncludePolicy.allowAll())

    when:
    def document = mapper.toDocument(primary, null, selection, policy)

    then:
    document.included() == []
  }

  def "an id+lid primary is not re-included through a lid-only alias occurrence"() {
    given:
    def primary =
        new AliasArticle("1", "local-1", "Primary", new AliasArticle(null, "local-1", "Alias", null))
    def selection = RepresentationSelection.builder().include(IncludePath.of("related")).build()
    def policy = RepresentationPolicy.defaults().withIncludePolicy(IncludePolicy.allowAll())

    when:
    def document = mapper.toDocument(primary, null, selection, policy)

    then:
    // The alias occurrence is the primary resource itself (core binds id and lid as alias
    // partners), so it must not enter included; the linkage still points at the primary's lid.
    document.included() == []
    def primaryResource = ((DocumentData.SingleResource) document.data()).resource()
    primaryResource.relationships().relationships().related.data() ==
        new RelationshipData.SingleLinkage(
        new ResourceIdentifier("alias-articles", null, "local-1", null, Map.of()))
  }

  def "included occurrences of one id+lid resource deduplicate across alias identities"() {
    given:
    def shared = new IdentifiedComment("99", "local-comment-1", "Nice")
    def article = new DualRefArticle("1", shared, [shared])
    def selection = RepresentationSelection.builder()
        .include(IncludePath.of("featured"))
        .include(IncludePath.of("comments"))
        .build()
    def policy = RepresentationPolicy.defaults().withIncludePolicy(IncludePolicy.allowAll())

    when:
    def document = mapper.toDocument(article, null, selection, policy)

    then:
    document.included() == [mapper.toResource(shared)]
  }

  def "included occurrences sharing a lid with unequal representations conflict"() {
    given:
    def article =
        new DualRefArticle(
        "1",
        new IdentifiedComment("99", "local-comment-1", "Nice"),
        [
          new IdentifiedComment(null, "local-comment-1", "Nice")
        ])
    def selection = RepresentationSelection.builder()
        .include(IncludePath.of("featured"))
        .include(IncludePath.of("comments"))
        .build()
    def policy = RepresentationPolicy.defaults().withIncludePolicy(IncludePolicy.allowAll())

    when:
    mapper.toDocument(article, null, selection, policy)

    then:
    def ex = thrown(JsonApiMappingException)
    ex.diagnostic() == MappingDiagnostic.CONFLICTING_INCLUDED_REPRESENTATION
  }

  // ============================== DECLARATIONS ==============================

  def "duplicate id roles fail with DUPLICATE_ROLE"() {
    when:
    mapper.toResource(new DuplicateIdArticle("1", "2", "Title"))

    then:
    def ex = thrown(JsonApiMappingException)
    ex.diagnostic() == MappingDiagnostic.DUPLICATE_ROLE
  }

  def "duplicate local-id roles fail with DUPLICATE_ROLE"() {
    when:
    mapper.toResource(new DuplicateLocalIdArticle("1", "2", "Title"))

    then:
    def ex = thrown(JsonApiMappingException)
    ex.diagnostic() == MappingDiagnostic.DUPLICATE_ROLE
  }

  def "one property claiming both identity roles fails with DUPLICATE_ROLE"() {
    when:
    mapper.toResource(new BothRolesArticle("1", "Title"))

    then:
    def ex = thrown(JsonApiMappingException)
    ex.diagnostic() == MappingDiagnostic.DUPLICATE_ROLE
  }

  def "a type with neither identity role fails with MISSING_IDENTIFIER"() {
    when:
    mapper.toResource(new NoIdentityArticle("Title"))

    then:
    def ex = thrown(JsonApiMappingException)
    ex.diagnostic() == MappingDiagnostic.MISSING_IDENTIFIER
  }

  // ============================== CONFIGURED JACKSON ==============================

  def "a renamed local-id property still maps to the lid member"() {
    given:
    def localMapper = JsonApiJackson3.resourceMapper(JsonMapper.builder().build())
    def localBinder = JsonApiJackson3.resourceBinder(JsonMapper.builder().build())
    def mapped =
        localMapper.toResource(new LocalIdFixtures.RenamedLocalIdArticle("1", "local-1", "Title"))

    expect:
    mapped.lid() == "local-1"
    mapped.id() == "1"

    and:
    def bound =
        localBinder.fromResource(
        typedResource("renamed-lids", null, "tmp-123"), LocalIdFixtures.RenamedLocalIdArticle)
    bound.localId() == "tmp-123"
    bound.id() == null
  }

  def "the configured naming strategy does not move the local-id wire member"() {
    given:
    def jacksonMapper = JsonMapper.builder()
        .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
        .build()
    def localMapper = JsonApiJackson3.resourceMapper(jacksonMapper)

    when:
    def resource =
        localMapper.toResource(new LocalIdFixtures.SnakeCaseLocalIdArticle("1", "local-1", "Title"))

    then:
    resource.lid() == "local-1"
    resource.id() == "1"
  }

  def "a class-level mix-in can supply the local-id role"() {
    given:
    def jacksonMapper = JsonMapper.builder()
        .addMixIn(LocalIdFixtures.MixinLocalIdArticle, LocalIdFixtures.LocalIdMixIn)
        .build()
    def localMapper = JsonApiJackson3.resourceMapper(jacksonMapper)
    def localBinder = JsonApiJackson3.resourceBinder(jacksonMapper)

    when:
    def resource = localMapper.toResource(new LocalIdFixtures.MixinLocalIdArticle("1", "local-1"))

    then:
    resource.lid() == "local-1"
    resource.id() == "1"

    and:
    def bound =
        localBinder.fromResource(
        typedResource("mixin-lids", null, "tmp-123"), LocalIdFixtures.MixinLocalIdArticle)
    bound.localId == "tmp-123"
    bound.id == null
  }

  // ============================== GENERIC PATH ==============================

  def "an unparameterized generic root with a local-id role fails at /lid rather than losing the effective type"() {
    when:
    mapper.toResource(new GenericLocalIdResource<>("9", null, "Title"))

    then:
    def ex = thrown(JsonApiMappingException)
    ex.diagnostic() == MappingDiagnostic.UNRESOLVED_GENERIC_TYPE
    ex.propertyPath() == "/lid"
  }

  def "a parameterized generic root maps its local-id role"() {
    given:
    def javaType = JsonMapper.builder().build()
        .typeFactory.constructParametricType(GenericLocalIdResource, UUID)
    def localMapper = JsonApiJackson3.resourceMapper(JsonMapper.builder().build())
    def value =
        new GenericLocalIdResource<UUID>(
        "9", UUID.fromString("00000000-0000-0000-0000-000000000001"), "Title")

    when:
    def resource = localMapper.toResource(value, javaType)

    then:
    resource.id() == "9"
    resource.lid() == "00000000-0000-0000-0000-000000000001"
  }

  // ============================== HELPERS ==============================

  private LocalIdentityArticle bind(String id, String lid) {
    (LocalIdentityArticle) binder.fromResource(resource(id, lid), LocalIdentityArticle)
  }

  private static ResourceObject resource(String id, String lid) {
    typedResource(ARTICLES, id, lid)
  }

  private static ResourceObject typedResource(String type, String id, String lid) {
    new ResourceObject(type, id, lid, null, null, null, null, Map.of())
  }

  private static ResourceObject resourceWithLinkage() {
    new ResourceObject(
        ARTICLES,
        null,
        "tmp-123",
        null,
        Relationships.ofRelationships(Map.of(
        "author",
        new Relationship(
        new RelationshipData.SingleLinkage(
        new ResourceIdentifier(PEOPLE, "42", "tmp-123", null, Map.of())),
        null,
        null,
        Map.of()))),
        null,
        null,
        Map.of())
  }

  @JsonApiResource(type = "long-lids")
  static class LongLocalIdArticle {
    @JsonApiId String id
    @JsonApiLocalId Long localId

    LongLocalIdArticle(Long localId) {
      this.id = "9"
      this.localId = localId
    }
  }

  @JsonApiResource(type = "lid-articles")
  static class LidArticle {
    @JsonApiId String id
    @JsonApiRelationship List<LocalIdOnlyComment> comments
    @JsonApiRelationship Optional<LocalIdOnlyComment> featured

    LidArticle(String id, List<LocalIdOnlyComment> comments, Optional<LocalIdOnlyComment> featured) {
      this.id = id
      this.comments = comments
      this.featured = featured
    }
  }

  @JsonApiResource(type = "lid-comment-primaries")
  static class LidPrimaryComment {
    @JsonApiLocalId String localId
    @JsonApiRelationship LidPrimaryComment self

    LidPrimaryComment(String localId) {
      this.localId = localId
      this.self = this
    }
  }

  @JsonApiResource(type = "dual-identity-articles")
  static class DualIdentityArticle {
    @JsonApiId String id
    @JsonApiRelationship List<IdentifiedComment> comments

    DualIdentityArticle(String id, List<IdentifiedComment> comments) {
      this.id = id
      this.comments = comments
    }
  }

  @JsonApiResource(type = "alias-articles")
  static class AliasArticle {
    @JsonApiId String id
    @JsonApiLocalId String localId
    @JsonApiAttribute String title
    @JsonApiRelationship AliasArticle related

    AliasArticle(String id, String localId, String title, AliasArticle related) {
      this.id = id
      this.localId = localId
      this.title = title
      this.related = related
    }
  }

  @JsonApiResource(type = "dual-ref-articles")
  static class DualRefArticle {
    @JsonApiId String id
    @JsonApiRelationship IdentifiedComment featured
    @JsonApiRelationship List<IdentifiedComment> comments

    DualRefArticle(String id, IdentifiedComment featured, List<IdentifiedComment> comments) {
      this.id = id
      this.featured = featured
      this.comments = comments
    }
  }

  @JsonApiResource(type = "lid-linkage-articles")
  static class LidLinkageArticle {
    @JsonApiId String id
    @JsonApiRelationship RelationshipLinkage<ResourceIdentifier, AuthorMeta> author

    LidLinkageArticle(String id, RelationshipLinkage<ResourceIdentifier, AuthorMeta> author) {
      this.id = id
      this.author = author
    }
  }

  @JsonApiResource(type = "articles")
  static class LidAuthorArticle {
    @JsonApiId String id
    @JsonApiLocalId String localId
    @JsonApiRelationship ResourceIdentifier author
  }

  static class AuthorMeta {
    String role

    AuthorMeta(String role) {
      this.role = role
    }
  }

  @JsonApiResource(type = "duplicate-ids")
  static class DuplicateIdArticle {
    @JsonApiId String firstId
    @JsonApiId String secondId
    @JsonApiAttribute String title

    DuplicateIdArticle(String firstId, String secondId, String title) {
      this.firstId = firstId
      this.secondId = secondId
      this.title = title
    }
  }

  @JsonApiResource(type = "duplicate-lids")
  static class DuplicateLocalIdArticle {
    @JsonApiLocalId String firstLocalId
    @JsonApiLocalId String secondLocalId
    @JsonApiAttribute String title

    DuplicateLocalIdArticle(String firstLocalId, String secondLocalId, String title) {
      this.firstLocalId = firstLocalId
      this.secondLocalId = secondLocalId
      this.title = title
    }
  }

  @JsonApiResource(type = "both-roles")
  static class BothRolesArticle {
    @JsonApiId @JsonApiLocalId String id
    @JsonApiAttribute String title

    BothRolesArticle(String id, String title) {
      this.id = id
      this.title = title
    }
  }

  @JsonApiResource(type = "no-identity")
  static class NoIdentityArticle {
    @JsonApiAttribute String title

    NoIdentityArticle(String title) {
      this.title = title
    }
  }

  @JsonApiResource(type = "generic-lids")
  static class GenericLocalIdResource<T> {
    @JsonApiId String id
    @JsonApiLocalId T localId
    @JsonApiAttribute String title

    GenericLocalIdResource(String id, T localId, String title) {
      this.id = id
      this.localId = localId
      this.title = title
    }
  }
}
