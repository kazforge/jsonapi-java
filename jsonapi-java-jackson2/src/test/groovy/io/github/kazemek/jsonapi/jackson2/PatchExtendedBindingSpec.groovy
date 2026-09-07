package io.github.kazemek.jsonapi.jackson2

import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.json.JsonMapper
import io.github.kazemek.jsonapi.annotation.JsonApiAttribute
import io.github.kazemek.jsonapi.annotation.JsonApiId
import io.github.kazemek.jsonapi.annotation.JsonApiResource
import io.github.kazemek.jsonapi.core.model.Attributes
import io.github.kazemek.jsonapi.core.model.DocumentData
import io.github.kazemek.jsonapi.core.model.JsonApiDocument
import io.github.kazemek.jsonapi.core.model.Relationship
import io.github.kazemek.jsonapi.core.model.RelationshipData
import io.github.kazemek.jsonapi.core.model.Relationships
import io.github.kazemek.jsonapi.core.model.ResourceObject
import io.github.kazemek.jsonapi.core.validation.EndpointIdentity
import io.github.kazemek.jsonapi.core.validation.ValidationContext
import io.github.kazemek.jsonapi.core.validation.ValidationRuleCode
import io.github.kazemek.jsonapi.fixtures.TestFixtureResources
import io.github.kazemek.jsonapi.fixtures.domainpatch.ArticleWithDimensions
import io.github.kazemek.jsonapi.fixtures.domainpatch.ArticleWithMeta
import io.github.kazemek.jsonapi.fixtures.domainpatch.ArticleWithRelationshipLinkage
import io.github.kazemek.jsonapi.fixtures.domainpatch.AuthorIdMeta
import io.github.kazemek.jsonapi.fixtures.domainpatch.PatchPresenceAddressPatchArticle
import io.github.kazemek.jsonapi.fixtures.domainread.FlatArticle
import io.github.kazemek.jsonapi.fixtures.domainread.FlatArticleWithArray
import io.github.kazemek.jsonapi.fixtures.domainread.FlatArticleWithOptional
import io.github.kazemek.jsonapi.fixtures.domainread.FlatArticleWithSet
import io.github.kazemek.jsonapi.fixtures.domainread.FlatCountedThing
import io.github.kazemek.jsonapi.fixtures.domainread.FlatIntIdArticle
import io.github.kazemek.jsonapi.fixtures.domainpatch.WholeMetaTargetFixtures
import io.github.kazemek.jsonapi.fixtures.domainread.FlatUnregisteredRelationshipsArticle
import io.github.kazemek.jsonapi.jackson.diagnostic.JsonApiDocumentReadException
import io.github.kazemek.jsonapi.jackson.diagnostic.JsonApiMappingException
import io.github.kazemek.jsonapi.jackson.diagnostic.MappingDiagnostic
import io.github.kazemek.jsonapi.jackson.mapping.IdentifierConverter
import io.github.kazemek.jsonapi.jackson.mapping.RelationshipLinkage
import io.github.kazemek.jsonapi.jackson.patch.PatchChange
import io.github.kazemek.jsonapi.jackson.patch.PatchCommand
import io.github.kazemek.jsonapi.jackson.patch.StructuredMember
import io.github.kazemek.jsonapi.jackson.patch.StructuredMemberState
import io.github.kazemek.jsonapi.jackson.patch.StructuredPatch
import io.github.kazemek.jsonapi.core.model.Meta
import io.github.kazemek.jsonapi.core.model.ResourceIdentifier
import io.github.kazemek.jsonapi.jackson2.LinkageMapperFixtures.FlatAuthor
import io.github.kazemek.jsonapi.jackson2.LinkageMapperFixtures.FlatMappedArticle
import spock.lang.Specification
import spock.lang.Unroll

class PatchExtendedBindingSpec extends Specification {

  @Unroll
  def "rejects command patch #id with a mapping diagnostic"() {
    given:
    def reader = JsonApiJackson2.patchCommandReader(JsonMapper.builder().build())
    def json = TestFixtureResources.readCorpusUtf8("patch/${resource}.json")

    when:
    reader.readValue(json, targetType)

    then:
    def ex = thrown(JsonApiMappingException)
    ex.diagnostic() == expectedDiagnostic
    ex.location().pointer() == expectedPath

    where:
    id | resource | targetType | expectedDiagnostic | expectedPath
    "cardinality-mismatch" | "relationship-cardinality-mismatch" | FlatArticle.class | MappingDiagnostic.RELATIONSHIP_CARDINALITY_MISMATCH | "/relationships/author/data"
    "resource-type-mismatch" | "resource-type-mismatch" | FlatArticle.class | MappingDiagnostic.RESOURCE_TYPE_MISMATCH | "/type"
    "identifier-conversion-failure" | "identifier-not-an-integer" | FlatIntIdArticle.class | MappingDiagnostic.IDENTIFIER_CONVERSION_FAILED | "/id"
    "attribute-conversion-failure" | "attribute-conversion-failure" | FlatCountedThing.class | MappingDiagnostic.UNSUPPORTED_ATTRIBUTE_VALUE | "/attributes/count"
    "unsupported-relationship-target" | "relationship-single-linkage" | FlatUnregisteredRelationshipsArticle.class | MappingDiagnostic.UNSUPPORTED_RELATIONSHIP_TARGET | "/relationships/author/data"
    "nested-primitive-null" | "dimensions-width-null" | ArticleWithDimensions.class | MappingDiagnostic.UNSUPPORTED_ATTRIBUTE_VALUE | "/attributes/dimensions/width"
    "presence-shape-rejected" | "address-street" | PatchPresenceAddressPatchArticle.class | MappingDiagnostic.INVALID_PATCH_PROPERTY_TYPE | "/attributes/address"
    "scalar-meta-target" | "identity-only" | WholeMetaTargetFixtures.ScalarMetaArticle.class | MappingDiagnostic.INVALID_META_TARGET | "/meta"
  }

  @Unroll
  def "rejects command patch #id during document validation"() {
    given:
    def context = endpointIdentity == null
        ? ValidationContext.defaults()
        : ValidationContext.defaults().withExpectedEndpointIdentity(endpointIdentity)
    def reader = JsonApiJackson2.patchCommandReader(JsonMapper.builder().build(), context)
    def json = TestFixtureResources.readCorpusUtf8("patch/${resource}.json")

    when:
    reader.readValue(json, targetType)

    then:
    def ex = thrown(JsonApiDocumentReadException)
    ex.ruleCode() == expectedRule
    ex.jsonPointer() == expectedPointer

    where:
    id | resource | targetType | endpointIdentity | expectedRule | expectedPointer
    "endpoint-identity-mismatch" | "title-only" | FlatArticle.class | new EndpointIdentity("articles", "99") | ValidationRuleCode.ENDPOINT_IDENTITY_MISMATCH | "/data/id"
    "missing-relationship-data" | "missing-relationship-data" | FlatArticle.class | null | ValidationRuleCode.RELATIONSHIP_DATA_REQUIRED | "/data/relationships/author/data"
    "wrong-primary-shape" | "wrong-primary-shape" | FlatArticle.class | null | ValidationRuleCode.UPDATE_REQUIRES_SINGLE_RESOURCE | "/data"
  }

  @Unroll
  def "binds linkage meta command #id"() {
    given:
    def reader = JsonApiJackson2.patchCommandReader(JsonMapper.builder().build())
    def json = TestFixtureResources.readCorpusUtf8("patch/${resource}.json")

    when:
    def actual = reader.readValue(json, targetType)

    then:
    actual == expected

    where:
    id | resource | targetType | expected
    "to-one-identifier-meta" | "author-identifier-meta" | FlatArticle.class | patch(FlatArticle.class, "1", new PatchChange.RelationshipChange("author", "author", identifier("people", "p1", [role: "editor"])))
    "to-many-identifier-meta" | "comments-identifier-meta" | FlatArticle.class | patch(FlatArticle.class, "1", new PatchChange.RelationshipChange("comments", "comments", [
      identifier("comments", "c1", [pinned: true]),
      ResourceIdentifier.of("comments", "c2")
    ]))
    "wrapper-linkage" | "author-identifier-meta" | ArticleWithRelationshipLinkage.class | patch(ArticleWithRelationshipLinkage.class, "1", new PatchChange.RelationshipChange("author", "author", new RelationshipLinkage(identifier("people", "p1", [role: "editor"]), new AuthorIdMeta("editor"))))
    "set-linkage" | "tags-identifier-meta" | FlatArticleWithSet.class | patch(FlatArticleWithSet.class, "1", new PatchChange.RelationshipChange("tags", "tags", [
      identifier("tags", "t1", [pinned: true])
    ] as Set))
    "optional-linkage" | "author-identifier-meta" | FlatArticleWithOptional.class | patch(FlatArticleWithOptional.class, "1", new PatchChange.RelationshipChange("author", "author", Optional.of(identifier("people", "p1", [role: "editor"]))))
  }

  def "binds array-valued relationship with identifier meta"() {
    given:
    def reader = JsonApiJackson2.patchCommandReader(JsonMapper.builder().build())
    def json = TestFixtureResources.readCorpusUtf8("patch/comments-identifier-meta.json")

    when:
    def command = reader.readValue(json, FlatArticleWithArray)

    then:
    command.resourceType() == FlatArticleWithArray
    Arrays.equals(command.changes()[0].value() as ResourceIdentifier[], ([
      identifier("comments", "c1", [pinned: true]),
      ResourceIdentifier.of("comments", "c2")
    ] as ResourceIdentifier[]))
  }

  def "custom linkage mapper applies on the command path"() {
    given:
    def mapper = { data, target ->
      def identifier = ((RelationshipData.SingleLinkage) data).identifier()
      return new FlatAuthor(identifier.type(), identifier.id())
    } as RelationshipLinkageMapper
    def reader = JsonApiJackson2.patchCommandReader(
        JsonMapper.builder().build(),
        ValidationContext.defaults(),
        IdentifierConverter.defaults(),
        [(FlatAuthor): mapper])
    def json = '{"data":{"type":"articles","id":"1","relationships":{"author":{"data":{"type":"people","id":"p1"}}}}}'

    when:
    def command = reader.readValue(json, FlatMappedArticle)

    then:
    command.changes() == [
      new PatchChange.RelationshipChange("author", "author", new FlatAuthor("people", "p1"))
    ]
  }

  def "binds resource and relationship meta on the command path"() {
    given:
    def reader = JsonApiJackson2.patchCommandReader(JsonMapper.builder().build())
    def json = TestFixtureResources.readCorpusUtf8("patch/meta-source-note-author-meta.json")

    when:
    def command = reader.readValue(json, ArticleWithMeta)

    then:
    command == patch(ArticleWithMeta.class, "1", new PatchChange.ResourceMetaChange("meta", "meta", structured(atomic("source", "cms"), atomic("note", "n"))), new PatchChange.AttributeChange("title", "title", "T"), new PatchChange.RelationshipChange("author", "author", ResourceIdentifier.of("people", "p1")), new PatchChange.RelationshipMetaChange("author", "authorMeta", structured(atomic("displayName", "Alice"))))
  }

  def "generic JavaType command preserves parameterization"() {
    given:
    def base = JsonMapper.builder().build()
    def reader = JsonApiJackson2.patchCommandReader(base)
    def javaType = base.typeFactory.constructParametricType(ParameterizedBindingFixtures.GenericValue, String)
    def json = '{"data":{"type":"things","id":"1","attributes":{"value":"v"}}}'

    when:
    def command = reader.readValue(json, javaType)

    then:
    command.resourceType() == ParameterizedBindingFixtures.GenericValue
    command.changes().size() == 1
  }

  def "identifier converter returning null fails with identifier diagnostic"() {
    given:
    def converter = new IdentifierConverter() {
          @Override
          String convert(Object value) {
            return "x"
          }
          @Override
          Object parse(String wire) {
            return null
          }
        }
    def reader = JsonApiJackson2.patchCommandReader(
        JsonMapper.builder().build(), ValidationContext.defaults(), converter)
    def json = '{"data":{"type":"articles","id":"1","attributes":{"title":"T"}}}'

    when:
    reader.readValue(json, FlatArticle)

    then:
    def ex = thrown(JsonApiMappingException)
    ex.diagnostic() == MappingDiagnostic.IDENTIFIER_CONVERSION_FAILED
  }

  def "fromDocument skips mapped relationships without data"() {
    given:
    def reader = JsonApiJackson2.patchCommandReader(JsonMapper.builder().build())
    def resource = new ResourceObject(
        "articles", "1", null,
        Attributes.ofAttributes([title: "T"]),
        Relationships.ofRelationships([author: Relationship.metaOnly(Meta.of([a: 1]))]),
        null, null, Map.of())
    def document = new JsonApiDocument(
        new DocumentData.SingleResource(resource), null, null, null, null, null, Map.of())

    when:
    def command = reader.fromDocument(document, FlatArticle)

    then:
    command.changes() == [
      new PatchChange.AttributeChange("title", "title", "T")
    ]
  }

  def "configured naming strategy applies to PATCH lookup"() {
    given:
    def caller = JsonMapper.builder().propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE).build()
    def reader = JsonApiJackson2.patchCommandReader(caller)
    def json = '{"data":{"type":"articles","id":"1","attributes":{"blog_title":"Content"}}}'

    when:
    def command = reader.readValue(json, SnakeArticle)

    then:
    command.changes() == [
      new PatchChange.AttributeChange("blog_title", "blogTitle", "Content")
    ]
  }

  @JsonApiResource(type = "articles")
  static class SnakeArticle {
    @JsonApiId String id
    @JsonApiAttribute String blogTitle
  }

  private static PatchCommand patch(Class targetType, Object identity, PatchChange... changes) {
    return new PatchCommand(targetType, identity, Arrays.asList(changes))
  }

  private static StructuredPatch structured(StructuredMember... members) {
    return new StructuredPatch(Arrays.asList(members))
  }

  private static StructuredMember atomic(String name, Object value) {
    return new StructuredMember(name, name, new StructuredMemberState.Atomic(value))
  }

  private static ResourceIdentifier identifier(String type, String id, Map<String, Object> meta) {
    return new ResourceIdentifier(type, id, null, Meta.of(meta), Map.of())
  }
}
