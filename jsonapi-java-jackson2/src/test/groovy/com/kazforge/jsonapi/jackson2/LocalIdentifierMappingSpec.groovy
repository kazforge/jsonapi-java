package com.kazforge.jsonapi.jackson2

import com.kazforge.jsonapi.annotation.JsonApiAttribute
import com.kazforge.jsonapi.annotation.JsonApiId
import com.kazforge.jsonapi.annotation.JsonApiLocalId
import com.kazforge.jsonapi.annotation.JsonApiRelationship
import com.kazforge.jsonapi.annotation.JsonApiResource
import com.kazforge.jsonapi.core.model.Meta
import com.kazforge.jsonapi.core.model.RelationshipData
import com.kazforge.jsonapi.core.model.ResourceIdentifier
import com.kazforge.jsonapi.core.model.ResourceObject
import com.kazforge.jsonapi.diagnostic.JsonApiMappingException
import com.kazforge.jsonapi.diagnostic.MappingDiagnostic
import com.kazforge.jsonapi.mapping.RelationshipLinkage
import com.kazforge.jsonapi.fixtures.localid.LocalIdentityArticle
import com.kazforge.jsonapi.jackson2.LocalIdFixtures.LocalIdMixIn
import com.kazforge.jsonapi.jackson2.LocalIdFixtures.MixinLocalIdArticle
import com.kazforge.jsonapi.jackson2.LocalIdFixtures.RenamedLocalIdArticle
import com.kazforge.jsonapi.jackson2.LocalIdFixtures.SnakeCaseLocalIdArticle
import spock.lang.Shared
import spock.lang.Specification
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.json.JsonMapper

class LocalIdentifierMappingSpec extends Specification {

  private static final String ARTICLES = "articles"
  private static final String PEOPLE = "people"

  @Shared
  JsonApiResourceMapper mapper = JsonApiJackson2.resourceMapper(JsonMapper.builder().build())

  @Shared
  JsonApiResourceBinder binder = JsonApiJackson2.resourceBinder(JsonMapper.builder().build())

  def "the default converter stringifies non-string local-id scalars"() {
    when:
    def resource = mapper.toResource(new LongLocalIdArticle(7L))

    then:
    resource.id() == "9"
    resource.lid() == "7"
  }

  def "direct binding of a lid-only resource populates only the local-id role"() {
    given:
    def resource = new ResourceObject(ARTICLES, null, "tmp-123", null, null, null, null, Map.of())

    when:
    def bound = binder.fromResource(resource, LocalIdentityArticle)

    then:
    bound.id() == null
    bound.localId() == "tmp-123"
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

  def "one property claiming both identity roles fails with DUPLICATE_ROLE"() {
    when:
    mapper.toResource(new BothRolesArticle("1", "Title"))

    then:
    def ex = thrown(JsonApiMappingException)
    ex.diagnostic() == MappingDiagnostic.DUPLICATE_ROLE
  }

  def "a renamed local-id property still maps to the lid member"() {
    given:
    def localMapper = JsonApiJackson2.resourceMapper(JsonMapper.builder().build())
    def mapped =
        localMapper.toResource(new RenamedLocalIdArticle("1", "local-1", "Title"))

    expect:
    mapped.lid() == "local-1"
    mapped.id() == "1"
  }

  def "the configured naming strategy does not move the local-id wire member"() {
    given:
    def jacksonMapper = JsonMapper.builder()
        .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
        .build()
    def localMapper = JsonApiJackson2.resourceMapper(jacksonMapper)

    when:
    def resource =
        localMapper.toResource(new SnakeCaseLocalIdArticle("1", "local-1", "Title"))

    then:
    resource.lid() == "local-1"
    resource.id() == "1"
  }

  def "a class-level mix-in can supply the local-id role"() {
    given:
    def jacksonMapper = JsonMapper.builder()
        .addMixIn(MixinLocalIdArticle, LocalIdMixIn)
        .build()
    def localMapper = JsonApiJackson2.resourceMapper(jacksonMapper)

    when:
    def resource = localMapper.toResource(new MixinLocalIdArticle("1", "local-1"))

    then:
    resource.lid() == "local-1"
    resource.id() == "1"
  }

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
    def localMapper = JsonApiJackson2.resourceMapper(JsonMapper.builder().build())
    def value =
        new GenericLocalIdResource<UUID>(
        "9", UUID.fromString("00000000-0000-0000-0000-000000000001"), "Title")

    when:
    def resource = localMapper.toResource(value, javaType)

    then:
    resource.id() == "9"
    resource.lid() == "00000000-0000-0000-0000-000000000001"
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

  @JsonApiResource(type = "lid-linkage-articles")
  static class LidLinkageArticle {
    @JsonApiId String id
    @JsonApiRelationship RelationshipLinkage<ResourceIdentifier, AuthorMeta> author

    LidLinkageArticle(String id, RelationshipLinkage<ResourceIdentifier, AuthorMeta> author) {
      this.id = id
      this.author = author
    }
  }

  static class AuthorMeta {
    String role

    AuthorMeta(String role) {
      this.role = role
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
