package com.kazforge.jsonapi.jackson2

import com.kazforge.jsonapi.jackson2.mapping.RelationshipLinkageMapper

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JavaType
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.databind.module.SimpleModule
import com.kazforge.jsonapi.annotation.JsonApiAttribute
import com.kazforge.jsonapi.annotation.JsonApiId
import com.kazforge.jsonapi.annotation.JsonApiRelationship
import com.kazforge.jsonapi.annotation.JsonApiResource
import com.kazforge.jsonapi.core.model.JsonApiDocument
import com.kazforge.jsonapi.core.model.Meta
import com.kazforge.jsonapi.core.model.RelationshipData
import com.kazforge.jsonapi.core.model.ResourceIdentifier
import com.kazforge.jsonapi.core.validation.DocumentUsage
import com.kazforge.jsonapi.core.aggregate.ValidationContext
import com.kazforge.jsonapi.fixtures.TestFixtureResources
import com.kazforge.jsonapi.fixtures.domainpatch.ArticleMeta
import com.kazforge.jsonapi.fixtures.domainpatch.ArticleMetaPatch
import com.kazforge.jsonapi.fixtures.domainpatch.ArticlePatch
import com.kazforge.jsonapi.fixtures.domainpatch.ArticleWithAddressPatch
import com.kazforge.jsonapi.fixtures.domainpatch.ArticleWithMetaPatch
import com.kazforge.jsonapi.fixtures.domainpatch.ArticleWithOptionalMetaPatch
import com.kazforge.jsonapi.fixtures.domainpatch.AuthorMeta
import com.kazforge.jsonapi.fixtures.domainpatch.WholeMetaTargetFixtures
import com.kazforge.jsonapi.diagnostic.JsonApiMappingException
import com.kazforge.jsonapi.diagnostic.MappingDiagnostic
import com.kazforge.jsonapi.document.DocumentReadContext
import com.kazforge.jsonapi.document.PrimaryDataKind
import com.kazforge.jsonapi.mapping.IdentifierConverter
import com.kazforge.jsonapi.patch.PatchPresence
import com.kazforge.jsonapi.jackson2.ParameterizedBindingFixtures.GenericPatch
import spock.lang.Specification
import spock.lang.Unroll

class PatchDtoBindingSpec extends Specification {

  def "binds omitted, present, and present-null distinctly"() {
    given:
    def reader = JsonApiJackson2.patchDtoReader(JsonMapper.builder().build())
    def json = '{"data":{"type":"articles","id":"1","attributes":{"title":"New title"}}}'

    when:
    def patch = reader.readValue(json, ArticlePatch)

    then:
    patch.id() == "1"
    patch.title() == PatchPresence.present("New title")
    patch.body().isOmitted()
    patch.author().isOmitted()
    patch.comments().isOmitted()
  }

  def "keeps explicit null distinct from omitted"() {
    given:
    def reader = JsonApiJackson2.patchDtoReader(JsonMapper.builder().build())
    def json = '{"data":{"type":"articles","id":"1","attributes":{"title":null},"relationships":{"author":{"data":null}}}}'

    when:
    def patch = reader.readValue(json, ArticlePatch)

    then:
    patch.title() == PatchPresence.present(null)
    patch.author() == PatchPresence.present(null)
    patch.body().isOmitted()
  }

  def "supplied members unknown to the PATCH DTO fail"() {
    given:
    def reader = JsonApiJackson2.patchDtoReader(JsonMapper.builder().build())
    def json = '{"data":{"type":"articles","id":"1","attributes":{"title":"T","bogus":"x"}}}'

    when:
    reader.readValue(json, ArticlePatch)

    then:
    def ex = thrown(JsonApiMappingException)
    ex.diagnostic() == MappingDiagnostic.UNKNOWN_PATCH_MEMBER
    ex.location().pointer() == "/attributes/bogus"
  }

  def "identifier must not be PatchPresence"() {
    given:
    def reader = JsonApiJackson2.patchDtoReader(JsonMapper.builder().build())
    def json = '{"data":{"type":"articles","id":"1","attributes":{"title":"T"}}}'

    when:
    reader.readValue(json, BadIdPatch)

    then:
    def ex = thrown(JsonApiMappingException)
    ex.diagnostic() == MappingDiagnostic.INVALID_PATCH_PROPERTY_TYPE
    ex.location().pointer() == "/id"
  }

  def "non-presence patchable members fail declaration validation"() {
    given:
    def reader = JsonApiJackson2.patchDtoReader(JsonMapper.builder().build())
    def json = '{"data":{"type":"articles","id":"1","attributes":{"title":"T"}}}'

    when:
    reader.readValue(json, PlainTitlePatch)

    then:
    def ex = thrown(JsonApiMappingException)
    ex.diagnostic() == MappingDiagnostic.INVALID_PATCH_PROPERTY_TYPE
    ex.location().pointer() == "/attributes/title"
  }

  def "wrapper-level customization on the presence member fails"() {
    given:
    def reader = JsonApiJackson2.patchDtoReader(JsonMapper.builder().build())
    def json = '{"data":{"type":"articles","id":"1","attributes":{"title":"T"}}}'

    when:
    reader.readValue(json, CustomizedPatch)

    then:
    def ex = thrown(JsonApiMappingException)
    ex.diagnostic() == MappingDiagnostic.INVALID_PATCH_PROPERTY_TYPE
  }

  def "generic JavaType overload preserves parameterization"() {
    given:
    def reader = JsonApiJackson2.patchDtoReader(JsonMapper.builder().build())
    def type = new TypeReference<GenericPatch<String>>() {}.getType()
    JavaType javaType = JsonMapper.builder().build().constructType(type)
    def json = '{"data":{"type":"articles","id":"1","attributes":{"title":"New title"}}}'

    when:
    def patch = reader.readValue(json, javaType)

    then:
    patch == new GenericPatch("1", PatchPresence.present("New title"))
  }

  def "fromDocument binds without revalidation"() {
    given:
    def reader = JsonApiJackson2.patchDtoReader(JsonMapper.builder().build())
    def json = '{"data":{"type":"articles","id":"1","attributes":{"title":"T"}}}'
    JsonApiDocument document = JsonApiJackson2.reader(
        JsonMapper.builder().build(),
        DocumentReadContext.of(
        ValidationContext.defaults().withDocumentUsage(DocumentUsage.UPDATE_REQUEST),
        PrimaryDataKind.RESOURCE)).readValue(json)

    when:
    def patch = reader.fromDocument(document, ArticlePatch)

    then:
    patch == reader.readValue(json, ArticlePatch)
  }

  def "fromDocument requires single-resource primary data"() {
    given:
    def reader = JsonApiJackson2.patchDtoReader(JsonMapper.builder().build())
    def document = JsonApiJackson2.reader(
        JsonMapper.builder().build(), DocumentReadContext.resourceDefaults()).readValue('{"data":[]}')

    when:
    reader.fromDocument(document, ArticlePatch)

    then:
    thrown(IllegalArgumentException)
  }

  def "byte array, stream, and parser sources bind the same DTO"() {
    given:
    def reader = JsonApiJackson2.patchDtoReader(JsonMapper.builder().build())
    def json = '{"data":{"type":"articles","id":"1","attributes":{"title":"T"}}}'
    def expected = reader.readValue(json, ArticlePatch)

    expect:
    reader.readValue(json.bytes, ArticlePatch) == expected
    reader.readValue(new ByteArrayInputStream(json.bytes), ArticlePatch) == expected

    when:
    def parser = JsonMapper.builder().build().createParser(json)
    def fromParser = reader.readValue(parser, ArticlePatch)
    parser.close()

    then:
    fromParser == expected
  }

  def "caller-owned streams and parsers stay open"() {
    given:
    def reader = JsonApiJackson2.patchDtoReader(JsonMapper.builder().build())
    def json = '{"data":{"type":"articles","id":"1","attributes":{"title":"T"}}}'
    def stream = new CloseTrackingInputStream(new ByteArrayInputStream(json.bytes))

    when:
    reader.readValue(stream, ArticlePatch)

    then:
    !stream.closed

    when:
    def parser = JsonMapper.builder().build().createParser(json)
    reader.readValue(parser, ArticlePatch)

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
    JsonApiJackson2.patchDtoReader(caller)

    then:
    caller.registeredModuleIds == before
  }

  def "inner-type customization stays supported"() {
    given:
    def module = new SimpleModule()
    module.addDeserializer(String, new UppercaseDeserializer())
    def caller = JsonMapper.builder().addModule(module).build()
    def reader = JsonApiJackson2.patchDtoReader(caller)
    def json = '{"data":{"type":"things","id":"1","attributes":{"title":"hello"}}}'

    when:
    def patch = reader.readValue(json, InnerCustomizedPatch)

    then:
    patch.title == PatchPresence.present("HELLO")
  }

  def "deserializes an atomic inner type once during DTO construction"() {
    given:
    def reader = JsonApiJackson2.patchDtoReader(JsonMapper.builder().build())
    def json = '{"data":{"type":"single-pass-things","id":"1","attributes":{"title":"hello"}}}'

    when:
    def patch = reader.readValue(json, SinglePassPatch)

    then:
    patch.title == PatchPresence.present(new SinglePassValue("hello!"))
  }

  def "deserializes an optional relationship inner type once during DTO construction"() {
    given:
    def linkageMapper = { RelationshipData data, JavaType target ->
      new SinglePassValue("hello")
    } as RelationshipLinkageMapper
    def reader = JsonApiJackson2.patchDtoReader(
        JsonMapper.builder().build(),
        ValidationContext.defaults(),
        IdentifierConverter.defaults(),
        [(SinglePassValue): linkageMapper])
    def json = '{"data":{"type":"single-pass-things","id":"1","relationships":{"author":{"data":{"type":"people","id":"p1"}}}}}'

    when:
    def patch = reader.readValue(json, SinglePassRelationshipPatch)

    then:
    patch.author == PatchPresence.present(Optional.of(new SinglePassValue("hello!")))
  }

  def "typed PATCH DTO linkage mapper is not invoked for empty collection linkage"() {
    given:
    def invoked = false
    def mapper = { RelationshipData data, JavaType target ->
      invoked = true
      []
    } as RelationshipLinkageMapper
    def reader = JsonApiJackson2.patchDtoReader(
        JsonMapper.builder().build(),
        ValidationContext.defaults(),
        IdentifierConverter.defaults(),
        [(AuthorId): mapper])
    def json = '{"data":{"type":"articles","id":"1","relationships":{"contributors":{"data":[]}}}}'

    when:
    def patch = reader.readValue(json, AuthorListPatch)

    then:
    patch.contributors == PatchPresence.present([])
    !invoked
  }

  def "typed PATCH DTO linkage mapper failure is LINKAGE_MAPPING_FAILED"() {
    given:
    def mapper = { RelationshipData data, JavaType target ->
      throw new IllegalStateException("boom")
    } as RelationshipLinkageMapper
    def reader = JsonApiJackson2.patchDtoReader(
        JsonMapper.builder().build(),
        ValidationContext.defaults(),
        IdentifierConverter.defaults(),
        [(AuthorId): mapper])
    def json = '{"data":{"type":"articles","id":"1","relationships":{"contributors":{"data":[{"type":"authors","id":"a1"}]}}}}'

    when:
    reader.readValue(json, AuthorListPatch)

    then:
    def ex = thrown(JsonApiMappingException)
    ex.diagnostic() == MappingDiagnostic.LINKAGE_MAPPING_FAILED
    ex.location().pointer() == "/relationships/contributors/data"
  }

  def "identifier conversion inverts the wire identifier"() {
    given:
    def converter = new IdentifierConverter() {
          @Override
          String convert(Object value) {
            return String.valueOf(value)
          }
          @Override
          Object parse(String wire) {
            return Integer.parseInt(wire)
          }
        }
    def reader = JsonApiJackson2.patchDtoReader(
        JsonMapper.builder().build(), ValidationContext.defaults(), converter)
    def json = '{"data":{"type":"int-articles","id":"9","attributes":{"title":"T"}}}'

    when:
    def patch = reader.readValue(json, IntIdPatch)

    then:
    patch.id == 9
  }

  @Unroll
  def "binds typed DTO #id"() {
    given:
    def reader = JsonApiJackson2.patchDtoReader(JsonMapper.builder().build())
    def json = TestFixtureResources.readCorpusUtf8("patch/${resource}.json")

    when:
    def actual = reader.readValue(json, ArticlePatch)

    then:
    actual == expected

    where:
    id | resource | expected
    "relationship-single" | "relationship-single-linkage" | new ArticlePatch("1", PatchPresence.omitted(), PatchPresence.omitted(), PatchPresence.present(ResourceIdentifier.of("people", "p1")), PatchPresence.omitted())
    "relationship-null" | "relationship-null-linkage" | new ArticlePatch("1", PatchPresence.omitted(), PatchPresence.omitted(), PatchPresence.present(null), PatchPresence.omitted())
    "relationship-empty-collection" | "relationship-empty-collection" | new ArticlePatch("1", PatchPresence.omitted(), PatchPresence.omitted(), PatchPresence.omitted(), PatchPresence.present([]))
    "identity-only" | "identity-other-id" | new ArticlePatch("7", PatchPresence.omitted(), PatchPresence.omitted(), PatchPresence.omitted(), PatchPresence.omitted())
  }

  @Unroll
  def "rejects typed DTO #id with a mapping diagnostic"() {
    given:
    def reader = JsonApiJackson2.patchDtoReader(JsonMapper.builder().build())
    def json = TestFixtureResources.readCorpusUtf8("patch/${resource}.json")

    when:
    reader.readValue(json, targetType)

    then:
    def ex = thrown(JsonApiMappingException)
    ex.diagnostic() == expectedDiagnostic
    ex.location().pointer() == expectedPath

    where:
    id | resource | targetType | expectedDiagnostic | expectedPath
    "unknown-attribute" | "attribute-unknown-member" | ArticlePatch.class | MappingDiagnostic.UNKNOWN_PATCH_MEMBER | "/attributes/bogus"
    "unknown-relationship" | "relationship-unknown-member" | ArticlePatch.class | MappingDiagnostic.UNKNOWN_PATCH_MEMBER | "/relationships/bogus"
    "cardinality-mismatch" | "relationship-cardinality-mismatch" | ArticlePatch.class | MappingDiagnostic.RELATIONSHIP_CARDINALITY_MISMATCH | "/relationships/author/data"
    "resource-type-mismatch" | "resource-type-mismatch" | ArticlePatch.class | MappingDiagnostic.RESOURCE_TYPE_MISMATCH | "/type"
    "unknown-resource-meta" | "title-with-meta-source" | WholeMetaTargetFixtures.NoMetaPatch.class | MappingDiagnostic.UNKNOWN_PATCH_MEMBER | "/meta"
    "unknown-relationship-meta" | "author-meta-with-data" | WholeMetaTargetFixtures.NoRelMetaPatch.class | MappingDiagnostic.UNKNOWN_PATCH_MEMBER | "/relationships/author/meta"
    "scalar-meta-target" | "title-with-meta-source" | WholeMetaTargetFixtures.ScalarMetaPatch.class | MappingDiagnostic.INVALID_META_TARGET | "/meta"
    "nested-unknown-member" | "address-unknown-member" | ArticleWithAddressPatch.class | MappingDiagnostic.UNKNOWN_PATCH_MEMBER | "/attributes/address/bogus"
    "meta-conversion-failure" | "meta-source-object" | ArticleWithMetaPatch.class | MappingDiagnostic.UNSUPPORTED_ATTRIBUTE_VALUE | "/meta/source"
  }

  def "binds meta on the typed path"() {
    given:
    def reader = JsonApiJackson2.patchDtoReader(JsonMapper.builder().build())
    def json = TestFixtureResources.readCorpusUtf8("patch/meta-source-note-author-meta.json")

    when:
    def patch = reader.readValue(json, ArticleWithMetaPatch)

    then:
    patch == new ArticleWithMetaPatch("1", PatchPresence.present("T"), PatchPresence.present(ResourceIdentifier.of("people", "p1")), PatchPresence.present(new ArticleMetaPatch(PatchPresence.present("cms"), PatchPresence.present("n"))), PatchPresence.present(new AuthorMeta("Alice")))
  }

  def "binds relationship meta on the typed path"() {
    given:
    def reader = JsonApiJackson2.patchDtoReader(JsonMapper.builder().build())
    def json = TestFixtureResources.readCorpusUtf8("patch/author-meta-with-data.json")

    when:
    def patch = reader.readValue(json, ArticleWithMetaPatch)

    then:
    patch.authorMeta() == PatchPresence.present(new AuthorMeta("Alice"))
  }

  def "binds optional typed resource meta and omits it"() {
    given:
    def reader = JsonApiJackson2.patchDtoReader(JsonMapper.builder().build())

    when:
    def supplied = reader.readValue(
        '{"data":{"type":"articles","id":"1","meta":{"source":"cms","note":"n"}}}',
        ArticleWithOptionalMetaPatch)
    def omitted = reader.readValue('{"data":{"type":"articles","id":"1"}}', ArticleWithOptionalMetaPatch)

    then:
    supplied.meta() == PatchPresence.present(Optional.of(new ArticleMeta("cms", "n")))
    omitted.meta() == PatchPresence.omitted()
  }

  def "rejects a nested-presence meta declaration at the meta pointer"() {
    given:
    def reader = JsonApiJackson2.patchDtoReader(JsonMapper.builder().build())

    when:
    reader.readValue(
        '{"data":{"type":"articles","id":"1","meta":{"source":"cms"}}}',
        WholeMetaTargetFixtures.NestedPresenceMetaPatch)

    then:
    def ex = thrown(JsonApiMappingException)
    ex.diagnostic() == MappingDiagnostic.INVALID_META_TARGET
    ex.propertyPath() == "/meta"
  }

  def "binds whole-linkage identifier meta on the typed path"() {
    given:
    def reader = JsonApiJackson2.patchDtoReader(JsonMapper.builder().build())
    def json = TestFixtureResources.readCorpusUtf8("patch/author-identifier-meta.json")

    when:
    def patch = reader.readValue(json, ArticlePatch)

    then:
    patch.author() == PatchPresence.present(new ResourceIdentifier("people", "p1", null, Meta.of([role: "editor"]), Map.of()))

    when:
    def commentsPatch = reader.readValue(TestFixtureResources.readCorpusUtf8("patch/comments-identifier-meta.json"), ArticlePatch)

    then:
    commentsPatch.comments() == PatchPresence.present([
      new ResourceIdentifier("comments", "c1", null, Meta.of([pinned: true]), Map.of()),
      ResourceIdentifier.of("comments", "c2")
    ])
  }

  def "optional inner null binds to present empty"() {
    given:
    def reader = JsonApiJackson2.patchDtoReader(JsonMapper.builder().build())
    def json = TestFixtureResources.readCorpusUtf8("patch/subtitle-explicit-null.json")

    when:
    def patch = reader.readValue(json, OptionalSubtitlePatch)

    then:
    patch.subtitle == PatchPresence.present(Optional.empty())
  }

  def "escaped member names produce escaped locations"() {
    given:
    def reader = JsonApiJackson2.patchDtoReader(JsonMapper.builder().build())

    when:
    reader.readValue(
        '{"data":{"type":"articles","id":"1","attributes":{"address":{"external/name":"x"}}}}',
        ArticleWithAddressPatch)

    then:
    def ex = thrown(JsonApiMappingException)
    ex.diagnostic() == MappingDiagnostic.UNKNOWN_PATCH_MEMBER
    ex.location().pointer() == "/attributes/address/external~1name"
  }

  def "identifier construction failure reclassifies to identifier diagnostic"() {
    given:
    def reader = JsonApiJackson2.patchDtoReader(JsonMapper.builder().build())
    def json = TestFixtureResources.readCorpusUtf8("patch/identifier-not-an-integer.json")

    when:
    reader.readValue(json, IntArticlePatch)

    then:
    def ex = thrown(JsonApiMappingException)
    ex.diagnostic() == MappingDiagnostic.IDENTIFIER_CONVERSION_FAILED
    ex.location().pointer() == "/id"
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

  @JsonApiResource(type = "articles")
  static class BadIdPatch {
    @JsonApiId PatchPresence<String> id
    @JsonApiAttribute PatchPresence<String> title
  }

  @JsonApiResource(type = "articles")
  static class PlainTitlePatch {
    @JsonApiId String id
    @JsonApiAttribute String title
  }

  @JsonApiResource(type = "articles")
  static class CustomizedPatch {
    @JsonApiId String id
    @JsonDeserialize(using = UppercaseDeserializer)
    @JsonApiAttribute PatchPresence<String> title
  }

  @JsonApiResource(type = "things")
  static class InnerCustomizedPatch {
    @JsonApiId String id
    @JsonApiAttribute PatchPresence<String> title
  }

  @JsonApiResource(type = "single-pass-things")
  static class SinglePassPatch {
    @JsonApiId String id
    @JsonApiAttribute PatchPresence<SinglePassValue> title
  }

  @JsonApiResource(type = "single-pass-things")
  static class SinglePassRelationshipPatch {
    @JsonApiId String id
    @JsonApiRelationship PatchPresence<Optional<SinglePassValue>> author
  }

  @JsonDeserialize(using = SinglePassDeserializer)
  @JsonSerialize(using = SinglePassSerializer)
  static class SinglePassValue {
    String value

    SinglePassValue(String value) {
      this.value = value
    }

    boolean equals(Object other) {
      other instanceof SinglePassValue && value == other.value
    }

    int hashCode() {
      Objects.hash(value)
    }
  }

  static class SinglePassDeserializer extends StdDeserializer<SinglePassValue> {
    SinglePassDeserializer() {
      super(SinglePassValue)
    }

    @Override
    SinglePassValue deserialize(JsonParser parser, DeserializationContext context) {
      new SinglePassValue(parser.getValueAsString() + "!")
    }
  }

  static class SinglePassSerializer extends JsonSerializer<SinglePassValue> {
    @Override
    void serialize(SinglePassValue value, JsonGenerator generator, SerializerProvider provider) {
      generator.writeString(value.value)
    }
  }

  @JsonApiResource(type = "int-articles")
  static class IntIdPatch {
    @JsonApiId int id
    @JsonApiAttribute PatchPresence<String> title
  }

  @JsonApiResource(type = "articles")
  static class IntArticlePatch {
    @JsonApiId Integer id
    @JsonApiAttribute PatchPresence<String> title
  }

  @JsonApiResource(type = "articles")
  static class OptionalTitlePatch {
    @JsonApiId String id
    @JsonApiAttribute PatchPresence<Optional<String>> title
  }

  @JsonApiResource(type = "articles")
  static class OptionalSubtitlePatch {
    @JsonApiId String id
    @JsonApiAttribute PatchPresence<Optional<String>> subtitle
  }

  @JsonApiResource(type = "articles")
  static class AuthorListPatch {
    @JsonApiId String id
    @JsonApiRelationship PatchPresence<List<AuthorId>> contributors
  }

  static class AuthorId {
    String type
    String id

    AuthorId() {}

    AuthorId(String type, String id) {
      this.type = type
      this.id = id
    }
  }
}
