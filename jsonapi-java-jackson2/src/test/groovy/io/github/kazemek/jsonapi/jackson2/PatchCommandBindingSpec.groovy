package io.github.kazemek.jsonapi.jackson2

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JavaType
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.fasterxml.jackson.databind.json.JsonMapper
import io.github.kazemek.jsonapi.annotation.JsonApiAttribute
import io.github.kazemek.jsonapi.annotation.JsonApiId
import io.github.kazemek.jsonapi.annotation.JsonApiResource
import io.github.kazemek.jsonapi.core.model.DocumentData
import io.github.kazemek.jsonapi.core.model.JsonApiDocument
import io.github.kazemek.jsonapi.core.model.Meta
import io.github.kazemek.jsonapi.core.model.ResourceIdentifier
import io.github.kazemek.jsonapi.core.model.ResourceObject
import io.github.kazemek.jsonapi.core.validation.DocumentUsage
import io.github.kazemek.jsonapi.core.validation.EndpointIdentity
import io.github.kazemek.jsonapi.core.validation.ValidationContext
import io.github.kazemek.jsonapi.core.validation.ValidationRuleCode
import io.github.kazemek.jsonapi.fixtures.TestFixtureResources
import io.github.kazemek.jsonapi.fixtures.domainpatch.Article
import io.github.kazemek.jsonapi.fixtures.domainpatch.ArticleWithMeta
import io.github.kazemek.jsonapi.fixtures.domainread.FlatArticle
import io.github.kazemek.jsonapi.fixtures.domainread.FlatCountedThing
import io.github.kazemek.jsonapi.fixtures.domainread.FlatThingWithIgnored
import io.github.kazemek.jsonapi.jackson.diagnostic.JsonApiDocumentReadException
import io.github.kazemek.jsonapi.jackson.diagnostic.JsonApiMappingException
import io.github.kazemek.jsonapi.jackson.diagnostic.MappingDiagnostic
import io.github.kazemek.jsonapi.jackson.document.DocumentReadContext
import io.github.kazemek.jsonapi.jackson.document.PrimaryDataKind
import io.github.kazemek.jsonapi.jackson.mapping.IdentifierConverter
import io.github.kazemek.jsonapi.jackson.patch.PatchChange
import io.github.kazemek.jsonapi.jackson.patch.PatchCommand
import io.github.kazemek.jsonapi.jackson.patch.StructuredMember
import io.github.kazemek.jsonapi.jackson.patch.StructuredMemberState
import io.github.kazemek.jsonapi.jackson.patch.StructuredPatch
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
    "patch-omitted-and-supplied-attributes" | "omitted-and-supplied-attributes" | FlatArticle.class | patch(FlatArticle.class, "1", new PatchChange.AttributeChange("title", "title", "Hello"))
    "patch-explicit-null-attribute" | "explicit-null-attribute" | FlatArticle.class | patch(FlatArticle.class, "1", new PatchChange.AttributeChange("title", "title", null))
    "patch-attribute-rename" | "attribute-rename" | FlatArticle.class | patch(FlatArticle.class, "1", new PatchChange.AttributeChange("body-text", "body", "Content"))
    "patch-ignored-unmapped-omitted-from-changes" | "ignored-unmapped-attributes" | FlatThingWithIgnored.class | patch(FlatThingWithIgnored.class, "1", new PatchChange.AttributeChange("name", "name", "visible"))
    "patch-relationship-null-linkage" | "relationship-null-linkage" | FlatArticle.class | patch(FlatArticle.class, "1", new PatchChange.RelationshipChange("author", "author", null))
    "patch-relationship-single-linkage" | "relationship-single-linkage" | FlatArticle.class | patch(FlatArticle.class, "1", new PatchChange.RelationshipChange("author", "author", ResourceIdentifier.of("people", "p1")))
    "patch-relationship-empty-collection" | "relationship-empty-collection" | FlatArticle.class | patch(FlatArticle.class, "1", new PatchChange.RelationshipChange("comments", "comments", []))
    "patch-ordinary-domain-nested-partial" | "address-street-new-street" | Article.class | patch(Article.class, "1", new PatchChange.AttributeChange("address", "address", structured(atomic("street", "New Street"))))
    "patch-ordinary-domain-unknown-nested-skip" | "address-bogus-and-street" | Article.class | patch(Article.class, "1", new PatchChange.AttributeChange("address", "address", structured(atomic("street", "S"))))
    "patch-resource-meta-supplied-unmapped-skipped" | "title-with-meta-source" | FlatArticle.class | patch(FlatArticle.class, "1", new PatchChange.AttributeChange("title", "title", "T"))
  }

  def "identity-only update binds no changes"() {
    given:
    def reader = JsonApiJackson2.patchCommandReader(JsonMapper.builder().build())
    def json = TestFixtureResources.readCorpusUtf8("patch/identity-only.json")

    when:
    def command = reader.readValue(json, FlatArticle)

    then:
    command == patch(FlatArticle.class, "1")
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

  def "type mismatch fails without guessing"() {
    given:
    def reader = JsonApiJackson2.patchCommandReader(JsonMapper.builder().build())
    def json = '{"data":{"type":"people","id":"1","attributes":{"title":"T"}}}'

    when:
    reader.readValue(json, FlatArticle)

    then:
    def ex = thrown(JsonApiMappingException)
    ex.diagnostic() == MappingDiagnostic.RESOURCE_TYPE_MISMATCH
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

  private static PatchCommand patch(Class targetType, Object identity, PatchChange... changes) {
    return new PatchCommand(targetType, identity, Arrays.asList(changes))
  }

  private static StructuredPatch structured(StructuredMember... members) {
    return new StructuredPatch(Arrays.asList(members))
  }

  private static StructuredMember atomic(String name, Object value) {
    return new StructuredMember(name, name, new StructuredMemberState.Atomic(value))
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
}
