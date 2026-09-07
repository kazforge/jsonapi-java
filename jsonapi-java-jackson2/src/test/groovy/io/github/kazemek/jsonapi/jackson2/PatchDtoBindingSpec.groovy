package io.github.kazemek.jsonapi.jackson2

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JavaType
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.databind.module.SimpleModule
import io.github.kazemek.jsonapi.annotation.JsonApiAttribute
import io.github.kazemek.jsonapi.annotation.JsonApiId
import io.github.kazemek.jsonapi.annotation.JsonApiRelationship
import io.github.kazemek.jsonapi.annotation.JsonApiResource
import io.github.kazemek.jsonapi.core.model.JsonApiDocument
import io.github.kazemek.jsonapi.core.model.ResourceIdentifier
import io.github.kazemek.jsonapi.core.validation.DocumentUsage
import io.github.kazemek.jsonapi.core.validation.ValidationContext
import io.github.kazemek.jsonapi.fixtures.TestFixtureResources
import io.github.kazemek.jsonapi.fixtures.domainpatch.ArticlePatch
import io.github.kazemek.jsonapi.fixtures.domainread.FlatArticle
import io.github.kazemek.jsonapi.jackson.diagnostic.JsonApiMappingException
import io.github.kazemek.jsonapi.jackson.diagnostic.MappingDiagnostic
import io.github.kazemek.jsonapi.jackson.document.DocumentReadContext
import io.github.kazemek.jsonapi.jackson.document.PrimaryDataKind
import io.github.kazemek.jsonapi.jackson.mapping.IdentifierConverter
import io.github.kazemek.jsonapi.jackson.patch.PatchPresence
import io.github.kazemek.jsonapi.jackson2.ParameterizedBindingFixtures.GenericPatch
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

  @Unroll
  def "binds patch dto #id"() {
    given:
    def reader = JsonApiJackson2.patchDtoReader(JsonMapper.builder().build())
    def json = TestFixtureResources.readCorpusUtf8("patch/${resource}.json")

    when:
    def actual = reader.readValue(json, ArticlePatch)

    then:
    actual == expected

    where:
    id | resource | expected
    "patch-title-only" | "title-only" | new ArticlePatch("1", PatchPresence.present("T"), PatchPresence.omitted(), PatchPresence.omitted(), PatchPresence.omitted())
    "patch-explicit-null-title" | "explicit-null-attribute" | new ArticlePatch("1", PatchPresence.present(null), PatchPresence.omitted(), PatchPresence.omitted(), PatchPresence.omitted())
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

  @JsonApiResource(type = "int-articles")
  static class IntIdPatch {
    @JsonApiId int id
    @JsonApiAttribute PatchPresence<String> title
  }
}
