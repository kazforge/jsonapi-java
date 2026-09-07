package io.github.kazemek.jsonapi.jackson2

import io.github.kazemek.jsonapi.annotation.JsonApiAttribute
import io.github.kazemek.jsonapi.annotation.JsonApiRelationship
import io.github.kazemek.jsonapi.annotation.JsonApiResource
import io.github.kazemek.jsonapi.core.model.RelationshipData
import io.github.kazemek.jsonapi.core.model.ResourceIdentifier
import io.github.kazemek.jsonapi.jackson.diagnostic.JsonApiMappingException
import io.github.kazemek.jsonapi.jackson.diagnostic.MappingDiagnostic
import spock.lang.Specification
import com.fasterxml.jackson.databind.json.JsonMapper

// Adapter-specific regression coverage for the configured-Jackson resource metadata
// authority on the write direction: class-level @JsonApiResource metadata is resolved through the
// configured mapper's introspection (so class-level mix-ins provide or override it) in direct
// domain writes and declared to-many relationship target validation. Mix-in mechanics are
// Jackson-specific and stay local. Binding, PATCH, and registry sections belong to later Jackson 2
// capabilities.
class ConfiguredResourceMetadataAuthoritySpec extends Specification {

  JsonMapper mixinMapper() {
    JsonMapper.builder()
        .addMixIn(MixinOnlyArticle, ArticleTypeMixin)
        .addMixIn(MixinComment, CommentTypeMixin)
        .build()
  }

  // ---- fixtures ----------------------------------------------------------

  /** No direct annotation anywhere: only the configured mix-in supplies resource metadata. */
  static class MixinOnlyArticle {
    String id
    @JsonApiAttribute String title
  }

  @JsonApiResource(type = "mixin-articles")
  interface ArticleTypeMixin {}

  /** Direct annotation that a configured mix-in overrides (mix-in precedence). */
  @JsonApiResource(type = "direct-articles")
  static class DirectlyTypedArticle {
    String id
    @JsonApiAttribute String title
  }

  @JsonApiResource(type = "resource-bases")
  static class ResourceBase {
    String id
  }

  static class ResourceChild extends ResourceBase {
    @JsonApiAttribute String title
  }

  @JsonApiResource(type = "interface-resources")
  interface ResourceTypeInterface {}

  static class InterfaceResource implements ResourceTypeInterface {
    String id
  }

  @JsonApiResource(type = "override-articles")
  interface OverridingTypeMixin {}

  /** Declared to-many element type whose wire type exists only through its mix-in. */
  static class MixinComment {
    String id
  }

  @JsonApiResource(type = "mixin-comments")
  interface CommentTypeMixin {}

  @JsonApiResource(type = "blogs")
  static class BlogWithMixinComments {
    String id
    @JsonApiRelationship List<MixinComment> comments
  }

  // ---- 1. direct domain write ---------------------------------------------

  def "domain write uses the mix-in-provided resource type"() {
    given:
    def mapper = JsonApiJackson2.resourceMapper(mixinMapper())
    def article = new MixinOnlyArticle(id: "1", title: "Hello")

    when:
    def resource = mapper.toResource(article)

    then:
    resource.type() == "mixin-articles"
    resource.id() == "1"
    resource.attributes().attributes().title == "Hello"
  }

  def "configured mix-in overrides the directly declared resource type on write"() {
    given:
    def overrideMapper = JsonMapper.builder()
        .addMixIn(DirectlyTypedArticle, OverridingTypeMixin)
        .build()
    def mapper = JsonApiJackson2.resourceMapper(overrideMapper)

    when:
    def resource = mapper.toResource(new DirectlyTypedArticle(id: "2", title: "Hi"))

    then:
    resource.type() == "override-articles"
  }

  def "plain mapper without the mix-in still rejects the unannotated type"() {
    given:
    def mapper = JsonApiJackson2.resourceMapper(JsonMapper.builder().build())

    when:
    mapper.toResource(new MixinOnlyArticle(id: "1", title: "Hello"))

    then:
    def ex = thrown(JsonApiMappingException)
    ex.diagnostic() == MappingDiagnostic.MISSING_RESOURCE_ANNOTATION
    ex.resourceClass() == MixinOnlyArticle
  }

  def "resource metadata does not inherit from an annotated superclass"() {
    given:
    def mapper = JsonApiJackson2.resourceMapper(JsonMapper.builder().build())

    when:
    mapper.toResource(new ResourceChild(id: "1", title: "Child"))

    then:
    def ex = thrown(JsonApiMappingException)
    ex.diagnostic() == MappingDiagnostic.MISSING_RESOURCE_ANNOTATION
    ex.resourceClass() == ResourceChild
  }

  def "resource metadata does not inherit from an annotated interface"() {
    given:
    def mapper = JsonApiJackson2.resourceMapper(JsonMapper.builder().build())

    when:
    mapper.toResource(new InterfaceResource(id: "1"))

    then:
    def ex = thrown(JsonApiMappingException)
    ex.diagnostic() == MappingDiagnostic.MISSING_RESOURCE_ANNOTATION
    ex.resourceClass() == InterfaceResource
  }

  // ---- 2. declared to-many relationship target validation -------------------

  def "declared to-many linkage derives element types from configured metadata"() {
    given:
    def mapper = JsonApiJackson2.resourceMapper(mixinMapper())
    def blog = new BlogWithMixinComments(
        id: "b1",
        comments: [
          new MixinComment(id: "c1"),
          new MixinComment(id: "c2")
        ])

    when:
    def resource = mapper.toResource(blog)

    then:
    def linkage = (RelationshipData.IdentifierCollectionLinkage) resource.relationships()
        .relationships().get("comments").data()
    linkage.identifiers() == [
      ResourceIdentifier.of("mixin-comments", "c1"),
      ResourceIdentifier.of("mixin-comments", "c2")
    ]
  }

  def "declared to-many element type without configured metadata keeps its diagnostic"() {
    given:
    def mapper = JsonApiJackson2.resourceMapper(JsonMapper.builder().build())
    def blog = new BlogWithMixinComments(id: "b1", comments: [new MixinComment(id: "c1")])

    when:
    mapper.toResource(blog)

    then:
    def ex = thrown(JsonApiMappingException)
    ex.diagnostic() == MappingDiagnostic.UNSUPPORTED_RELATIONSHIP_COLLECTION_TYPE
    ex.resourceClass() == MixinComment
  }
}
