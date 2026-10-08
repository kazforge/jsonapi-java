package com.kazforge.jsonapi.jackson2

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JavaType
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.fasterxml.jackson.databind.json.JsonMapper
import com.kazforge.jsonapi.annotation.JsonApiAttribute
import com.kazforge.jsonapi.annotation.JsonApiId
import com.kazforge.jsonapi.annotation.JsonApiResource
import com.kazforge.jsonapi.core.aggregate.ValidationContext
import com.kazforge.jsonapi.core.model.Attributes
import com.kazforge.jsonapi.core.model.DocumentData
import com.kazforge.jsonapi.core.model.JsonApiDocument
import com.kazforge.jsonapi.core.model.Meta
import com.kazforge.jsonapi.core.model.Relationship
import com.kazforge.jsonapi.core.model.RelationshipData
import com.kazforge.jsonapi.core.model.Relationships
import com.kazforge.jsonapi.core.model.ResourceIdentifier
import com.kazforge.jsonapi.core.model.ResourceObject
import com.kazforge.jsonapi.core.validation.DocumentUsage
import com.kazforge.jsonapi.core.validation.EndpointIdentity
import com.kazforge.jsonapi.core.validation.ValidationRuleCode
import com.kazforge.jsonapi.diagnostic.JsonApiDocumentReadException
import com.kazforge.jsonapi.diagnostic.JsonApiMappingException
import com.kazforge.jsonapi.diagnostic.MappingDiagnostic
import com.kazforge.jsonapi.document.DocumentReadContext
import com.kazforge.jsonapi.document.PrimaryDataKind
import com.kazforge.jsonapi.fixtures.TestFixtureResources
import com.kazforge.jsonapi.fixtures.domainpatch.Article
import com.kazforge.jsonapi.fixtures.domainpatch.ArticleWithDimensions
import com.kazforge.jsonapi.fixtures.domainpatch.ArticleWithMeta
import com.kazforge.jsonapi.fixtures.domainpatch.ArticleWithRelationshipLinkage
import com.kazforge.jsonapi.fixtures.domainpatch.AuthorIdMeta
import com.kazforge.jsonapi.fixtures.domainpatch.PatchPresenceAddressPatchArticle
import com.kazforge.jsonapi.fixtures.domainpatch.WholeMetaTargetFixtures
import com.kazforge.jsonapi.fixtures.domainread.FlatArticle
import com.kazforge.jsonapi.fixtures.domainread.FlatArticleWithArray
import com.kazforge.jsonapi.fixtures.domainread.FlatArticleWithOptional
import com.kazforge.jsonapi.fixtures.domainread.FlatArticleWithSet
import com.kazforge.jsonapi.fixtures.domainread.FlatCountedThing
import com.kazforge.jsonapi.fixtures.domainread.FlatIntIdArticle
import com.kazforge.jsonapi.fixtures.domainread.FlatThingWithIgnored
import com.kazforge.jsonapi.fixtures.domainread.FlatUnregisteredRelationshipsArticle
import com.kazforge.jsonapi.fixtures.generic.ParameterizedBindingFixtures
import com.kazforge.jsonapi.jackson2.LinkageMapperFixtures.FlatAuthor
import com.kazforge.jsonapi.jackson2.LinkageMapperFixtures.FlatMappedArticle
import com.kazforge.jsonapi.jackson2.mapping.RelationshipLinkageMapper
import com.kazforge.jsonapi.mapping.IdentifierConverter
import com.kazforge.jsonapi.mapping.RelationshipLinkage
import com.kazforge.jsonapi.patch.PatchChange
import com.kazforge.jsonapi.patch.PatchCommand
import com.kazforge.jsonapi.patch.StructuredMember
import com.kazforge.jsonapi.patch.StructuredMemberState
import com.kazforge.jsonapi.patch.StructuredPatch
import spock.lang.Specification
import spock.lang.Unroll

class PatchCommandBindingSpec extends Specification {

  @Unroll
  def "binds patch #id into an explicit command"() {
    given:
    def reader = JsonApiJackson2.patchCommandReader(JsonMapper.builder().build())
    def json = TestFixtureResources.readCorpusUtf8("patch/${resource}.json")

    when:
    def actual = reader.readValue(json, targetType)

    then:
    actual == expected

    where:
    id | resource | targetType | expected
    "patch-ignored-unmapped-omitted-from-changes" | "ignored-unmapped-attributes" | FlatThingWithIgnored.class | patch(FlatThingWithIgnored.class, "1", new PatchChange.AttributeChange("name", "name", "visible"))
    "patch-ordinary-domain-nested-partial" | "address-street-new-street" | Article.class | patch(Article.class, "1", new PatchChange.AttributeChange("address", "address", structured(atomic("street", "New Street"))))
    "patch-ordinary-domain-unknown-nested-skip" | "address-bogus-and-street" | Article.class | patch(Article.class, "1", new PatchChange.AttributeChange("address", "address", structured(atomic("street", "S"))))
    "patch-resource-meta-supplied-unmapped-skipped" | "title-with-meta-source" | FlatArticle.class | patch(FlatArticle.class, "1", new PatchChange.AttributeChange("title", "title", "T"))
  }

  def "supplied members unknown to the DTO are skipped on the low-level path"() {
    given:
    def reader = JsonApiJackson2.patchCommandReader(JsonMapper.builder().build())
    def json = '{"data":{"type":"articles","id":"1","attributes":{"title":"T","bogus":"x"}}}'

    when:
    def command = reader.readValue(json, FlatArticle)

    then:
    command.changes() == [
      new PatchChange.AttributeChange("title", "title", "T")
    ]
  }

  def "compound included is never read"() {
    given:
    def reader = JsonApiJackson2.patchCommandReader(JsonMapper.builder().build())
    def json = TestFixtureResources.readCorpusUtf8("patch/compound-included-ignored.json")

    when:
    def command = reader.readValue(json, FlatArticle)

    then:
    command == patch(FlatArticle.class, "1", new PatchChange.AttributeChange("title", "title", "T"), new PatchChange.RelationshipChange("author", "author", ResourceIdentifier.of("people", "p1")))
  }

  def "identity comes from resource id only"() {
    given:
    def reader = JsonApiJackson2.patchCommandReader(JsonMapper.builder().build())
    def json = '{"data":{"type":"articles","id":"9","attributes":{"title":"T"}}}'

    when:
    def command = reader.readValue(json, FlatArticle)

    then:
    command.identity() == "9"
    command.resourceType() == FlatArticle
  }

  def "missing id fails with identifier conversion diagnostic"() {
    given:
    def reader = JsonApiJackson2.patchCommandReader(JsonMapper.builder().build())
    def document = new JsonApiDocument(
        new DocumentData.SingleResource(ResourceObject.ofType("articles")),
        null, null, null, null, null, Map.of())

    when:
    reader.fromDocument(document, FlatArticle)

    then:
    def ex = thrown(JsonApiMappingException)
    ex.diagnostic() == MappingDiagnostic.IDENTIFIER_CONVERSION_FAILED
    ex.location().pointer() == "/id"
  }

  def "forces update-request usage even with response defaults"() {
    given:
    def reader = JsonApiJackson2.patchCommandReader(
        JsonMapper.builder().build(), ValidationContext.defaults())
    def json = '{"data":{"type":"articles","id":"1","attributes":{"title":"T"}}}'

    when:
    def command = reader.readValue(json, FlatArticle)

    then:
    command.changes().size() == 1
  }

  def "endpoint identity is preserved through the forced update usage"() {
    given:
    def context = ValidationContext.defaults().withExpectedEndpointIdentity(new EndpointIdentity("articles", "1"))
    def reader = JsonApiJackson2.patchCommandReader(JsonMapper.builder().build(), context)
    def json = '{"data":{"type":"articles","id":"1","attributes":{"title":"T"}}}'

    when:
    def command = reader.readValue(json, FlatArticle)

    then:
    command.identity() == "1"
  }

  def "fromDocument binds without revalidation and skips data-less relationships"() {
    given:
    def reader = JsonApiJackson2.patchCommandReader(JsonMapper.builder().build())
    def document = decodeUpdateDocument('{"data":{"type":"articles","id":"1","attributes":{"title":"T"}}}')

    when:
    def command = reader.fromDocument(document, FlatArticle)

    then:
    command == patch(FlatArticle.class, "1", new PatchChange.AttributeChange("title", "title", "T"))
  }

  def "fromDocument requires single-resource primary data"() {
    given:
    def reader = JsonApiJackson2.patchCommandReader(JsonMapper.builder().build())
    def json = '{"data":[]}'
    def document = JsonApiJackson2.reader(
        JsonMapper.builder().build(), DocumentReadContext.resourceDefaults()).readValue(json)

    when:
    reader.fromDocument(document, FlatArticle)

    then:
    thrown(IllegalArgumentException)
  }

  def "JavaType overload preserves full parameterization"() {
    given:
    def reader = JsonApiJackson2.patchCommandReader(JsonMapper.builder().build())
    JavaType type = JsonMapper.builder().build().constructType(FlatArticle)

    when:
    def command = reader.readValue('{"data":{"type":"articles","id":"1","attributes":{"title":"T"}}}', type)

    then:
    command.resourceType() == FlatArticle
    command.changes().size() == 1
  }

  def "byte array, stream, and parser sources bind the same command"() {
    given:
    def reader = JsonApiJackson2.patchCommandReader(JsonMapper.builder().build())
    def json = '{"data":{"type":"articles","id":"1","attributes":{"title":"T"}}}'
    def expected = reader.readValue(json, FlatArticle)

    expect:
    reader.readValue(json.bytes, FlatArticle) == expected
    reader.readValue(new ByteArrayInputStream(json.bytes), FlatArticle) == expected

    when:
    def parser = JsonMapper.builder().build().createParser(json)
    def fromParser = reader.readValue(parser, FlatArticle)
    parser.close()

    then:
    fromParser == expected
  }

  def "caller-owned streams and parsers stay open"() {
    given:
    def reader = JsonApiJackson2.patchCommandReader(JsonMapper.builder().build())
    def json = '{"data":{"type":"articles","id":"1","attributes":{"title":"T"}}}'
    def stream = new CloseTrackingInputStream(new ByteArrayInputStream(json.bytes))

    when:
    reader.readValue(stream, FlatArticle)

    then:
    !stream.closed

    when:
    def parser = JsonMapper.builder().build().createParser(json)
    reader.readValue(parser, FlatArticle)

    then:
    !parser.isClosed()

    cleanup:
    parser?.close()
  }

  def "mapper isolation leaves the caller mapper unmutated"() {
    given:
    def caller = JsonMapper.builder().build()
    def before = caller.registeredModuleIds

    when:
    JsonApiJackson2.patchCommandReader(caller)

    then:
    caller.registeredModuleIds == before
  }

  def "property-scoped deserialization applies on the low-level path"() {
    given:
    def reader = JsonApiJackson2.patchCommandReader(JsonMapper.builder().build())
    def json = '{"data":{"type":"things","id":"1","attributes":{"title":"hello"}}}'

    when:
    def command = reader.readValue(json, FlatLoudThing)

    then:
    command.changes() == [
      new PatchChange.AttributeChange("title", "title", "HELLO")
    ]
  }

  def "explicit null for primitive fails independently of null coercion"() {
    given:
    def permissive = JsonMapper.builder().build()
    def reader = JsonApiJackson2.patchCommandReader(permissive)
    def json = '{"data":{"type":"things","id":"1","attributes":{"count":null}}}'

    when:
    reader.readValue(json, FlatCountedThing)

    then:
    def ex = thrown(JsonApiMappingException)
    ex.diagnostic() == MappingDiagnostic.UNSUPPORTED_ATTRIBUTE_VALUE
    ex.location().pointer() == "/attributes/count"
  }

  def "attribute conversion failure wraps as an unsupported-attribute diagnostic"() {
    given:
    def reader = JsonApiJackson2.patchCommandReader(JsonMapper.builder().build())
    def json = '{"data":{"type":"things","id":"1","attributes":{"count":"not-a-number"}}}'

    when:
    reader.readValue(json, FlatCountedThing)

    then:
    def ex = thrown(JsonApiMappingException)
    ex.diagnostic() == MappingDiagnostic.UNSUPPORTED_ATTRIBUTE_VALUE
    ex.location().pointer() == "/attributes/count"
  }

  def "duplicate mapping definitions fail with a name-collision diagnostic"() {
    given:
    def reader = JsonApiJackson2.patchCommandReader(JsonMapper.builder().build())
    def json = '{"data":{"type":"articles","id":"1","attributes":{"title":"T"}}}'

    when:
    reader.readValue(json, FlatDuplicateAttributeArticle)

    then:
    def failure = thrown(JsonApiMappingException)
    failure.diagnostic() == MappingDiagnostic.NAME_COLLISION
  }

  def "identifier conversion applies through the property deserializer"() {
    given:
    def converter = new IdentifierConverter() {
          @Override
          String convert(Object value) {
            return String.valueOf(value)
          }
          @Override
          Object parse(String wire) {
            return "b-" + wire
          }
        }
    def reader = JsonApiJackson2.patchCommandReader(
        JsonMapper.builder().build(), ValidationContext.defaults(), converter)
    def json = '{"data":{"type":"articles","id":"9","attributes":{"title":"T"}}}'

    when:
    def command = reader.readValue(json, FlatArticle)

    then:
    command.identity() == "b-9"
  }

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
    command.changes()[0].value() instanceof ResourceIdentifier[]
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
    def javaType = base.typeFactory.constructParametricType(ParameterizedBindingFixtures.GenericValue, Integer)
    def json = '{"data":{"type":"things","id":"1","attributes":{"value":42}}}'

    when:
    def command = reader.readValue(json, javaType)

    then:
    command.resourceType() == ParameterizedBindingFixtures.GenericValue
    command.changes() == [
      new PatchChange.AttributeChange("value", "value", 42)
    ]
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

  private static JsonApiDocument decodeUpdateDocument(String json) {
    return JsonApiJackson2.reader(
        JsonMapper.builder().build(),
        DocumentReadContext.of(
        ValidationContext.defaults().withDocumentUsage(DocumentUsage.UPDATE_REQUEST),
        PrimaryDataKind.RESOURCE))
        .readValue(json)
  }

  static class CloseTrackingInputStream extends FilterInputStream {
    boolean closed

    CloseTrackingInputStream(InputStream delegate) {
      super(delegate)
    }

    @Override
    void close() {
      closed = true
      super.close()
    }
  }

  static class UppercaseDeserializer extends StdDeserializer<String> {
    UppercaseDeserializer() {
      super(String.class)
    }

    @Override
    String deserialize(JsonParser parser, DeserializationContext context) {
      return parser.getValueAsString().toUpperCase()
    }
  }

  @JsonApiResource(type = "things")
  static class FlatLoudThing {
    @JsonApiId String id
    @JsonDeserialize(using = UppercaseDeserializer)
    @JsonApiAttribute
    String title
  }

  @JsonApiResource(type = "articles")
  static class FlatDuplicateAttributeArticle {
    @JsonApiId String id
    @JsonApiAttribute @JsonProperty("title")
    String title
    @JsonApiAttribute @JsonProperty("title")
    String alsoTitle
  }
}
