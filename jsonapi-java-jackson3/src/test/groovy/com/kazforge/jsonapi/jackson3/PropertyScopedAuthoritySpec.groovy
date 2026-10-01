package com.kazforge.jsonapi.jackson3

import com.kazforge.jsonapi.annotation.JsonApiAttribute
import com.kazforge.jsonapi.annotation.JsonApiId
import com.kazforge.jsonapi.annotation.JsonApiLocalId
import com.kazforge.jsonapi.annotation.JsonApiResource
import com.kazforge.jsonapi.core.model.Attributes
import com.kazforge.jsonapi.core.model.ResourceObject
import com.kazforge.jsonapi.diagnostic.MappingLocation
import com.kazforge.jsonapi.mapping.IdentifierConverter
import com.kazforge.jsonapi.diagnostic.JsonApiMappingException
import com.kazforge.jsonapi.diagnostic.MappingDiagnostic
import com.kazforge.jsonapi.patch.PatchPresence
import com.kazforge.jsonapi.core.aggregate.ValidationContext
import spock.lang.Specification
import tools.jackson.core.JsonParser
import tools.jackson.databind.DeserializationContext
import tools.jackson.databind.SerializationFeature
import tools.jackson.databind.annotation.JsonDeserialize
import tools.jackson.databind.deser.std.StdDeserializer
import tools.jackson.databind.exc.MismatchedInputException
import tools.jackson.databind.exc.ValueInstantiationException
import tools.jackson.databind.json.JsonMapper

class PropertyScopedAuthoritySpec extends Specification {

  def "custom identifier target is deserialized in property context without root coercion"() {
    given:
    def binder = JsonApiJackson3.resourceBinder(
        JsonMapper.builder().build(), identifierConverter())

    when:
    def article = binder.fromResource(resourceWithId("rich-articles", "1"), RichIdArticle)

    then:
    article.id == new PropertyIdentifier("parsed-1")
  }

  def "flat identifier construction failures retain the identifier diagnostic"() {
    given:
    def binder = JsonApiJackson3.resourceBinder(
        JsonMapper.builder().build(), identifierConverter())

    when:
    binder.fromResource(resourceWithId("articles", "1"), FailingIdArticle)

    then:
    def ex = thrown(JsonApiMappingException)
    ex.diagnostic() == MappingDiagnostic.IDENTIFIER_CONVERSION_FAILED
    ex.propertyPath() == "/id"
  }

  def "nested flat identifier construction failures retain the identifier diagnostic"() {
    given:
    def binder = JsonApiJackson3.resourceBinder(
        JsonMapper.builder().build(), nestedIdentifierConverter())

    when:
    binder.fromResource(resourceWithId("nested-articles", "1"), NestedFailingIdArticle)

    then:
    def ex = thrown(JsonApiMappingException)
    ex.diagnostic() == MappingDiagnostic.IDENTIFIER_CONVERSION_FAILED
    ex.propertyPath() == "/id"
  }

  def "nested attribute failures with an inner id are not identifier failures"() {
    given:
    def binder = JsonApiJackson3.resourceBinder(
        JsonMapper.builder().build(), identifierConverter())

    when:
    binder.fromResource(
        resourceWithAttributes("nested-attribute-articles", "1", [details: [id: "bad"]]),
        NestedAttributeIdArticle)

    then:
    def ex = thrown(JsonApiMappingException)
    ex.diagnostic() == MappingDiagnostic.UNSUPPORTED_ATTRIBUTE_VALUE
    // The nested failure keeps its nested wire location under the attribute; it is not rewritten
    // to the identifier pointer /id.
    ex.propertyPath() == "/attributes/details/id"
  }

  def "flat lid-only identifier construction failures retain the lid diagnostic pointer"() {
    given:
    def binder = JsonApiJackson3.resourceBinder(
        JsonMapper.builder().build(), identifierConverter())

    when:
    binder.fromResource(resourceWithLid("articles", "local-1"), FailingLocalIdArticle)

    then:
    def ex = thrown(JsonApiMappingException)
    ex.diagnostic() == MappingDiagnostic.IDENTIFIER_CONVERSION_FAILED
    ex.propertyPath() == "/lid"
  }

  def "flat read applies the property deserializer after identifier parsing"() {
    given:
    def readerMapper = JsonMapper.builder().build()
    def binder = JsonApiJackson3.resourceBinder(readerMapper, identifierConverter())
    def resource = resourceWithId("articles", "1")

    when:
    def article = binder.fromResource(resource, DirectIdArticle)

    then:
    article.id == "property:parsed-1"
  }

  def "flat read honors an identifier deserializer supplied by a mix-in"() {
    given:
    def mapper = JsonMapper.builder()
        .addMixIn(MixinIdArticle, IdDeserializerMixIn)
        .build()
    def binder = JsonApiJackson3.resourceBinder(mapper, identifierConverter())

    when:
    def article = binder.fromResource(resourceWithId("articles", "1"), MixinIdArticle)

    then:
    article.id == "property:parsed-1"
  }

  def "low-level PATCH applies the property deserializer after identifier parsing"() {
    given:
    def reader = JsonApiJackson3.patchCommandReader(
        JsonMapper.builder().build(),
        ValidationContext.defaults(),
        identifierConverter())

    when:
    def command = reader.readValue(
        '{"data":{"type":"articles","id":"1"}}', DirectIdArticle)

    then:
    command.identity() == "property:parsed-1"
    command.changes().isEmpty()
  }

  def "low-level property conversion preserves convertValue root-unwrapped semantics"() {
    given:
    def mapper = JsonMapper.builder()
        .enable(SerializationFeature.WRAP_ROOT_VALUE)
        .build()
    def reader = JsonApiJackson3.patchCommandReader(mapper, ValidationContext.defaults(), identifierConverter())

    when:
    def command = reader.readValue(
        '{"data":{"type":"articles","id":"1","attributes":{"title":"title"}}}',
        DirectIdArticle)

    then:
    command.identity() == "property:parsed-1"
    command.changes()[0].value() == "property:title"
  }

  def "low-level PATCH honors an identifier deserializer supplied by a mix-in"() {
    given:
    def mapper = JsonMapper.builder()
        .addMixIn(MixinIdArticle, IdDeserializerMixIn)
        .build()
    def reader = JsonApiJackson3.patchCommandReader(
        mapper, ValidationContext.defaults(), identifierConverter())

    when:
    def command = reader.readValue(
        '{"data":{"type":"articles","id":"1"}}', MixinIdArticle)

    then:
    command.identity() == "property:parsed-1"
  }

  def "low-level PATCH normalizes identifier converter mapping exceptions"() {
    given:
    def reader = JsonApiJackson3.patchCommandReader(
        JsonMapper.builder().build(), ValidationContext.defaults(), mappingThrowingConverter())

    when:
    reader.readValue('{"data":{"type":"articles","id":"1"}}', DirectIdArticle)

    then:
    def ex = thrown(JsonApiMappingException)
    ex.diagnostic() == MappingDiagnostic.IDENTIFIER_CONVERSION_FAILED
    ex.propertyPath() == "/id"
  }

  def "typed PATCH applies the property deserializer after identifier parsing"() {
    given:
    def reader = JsonApiJackson3.patchDtoReader(
        JsonMapper.builder().build(),
        ValidationContext.defaults(),
        identifierConverter())

    when:
    def patch = reader.readValue(
        '{"data":{"type":"articles","id":"1"}}', DirectIdPatch)

    then:
    patch.id == "property:parsed-1"
    patch.title == PatchPresence.omitted()
  }

  def "typed PATCH honors an identifier deserializer supplied by a mix-in"() {
    given:
    def mapper = JsonMapper.builder()
        .addMixIn(MixinIdPatch, IdDeserializerMixIn)
        .build()
    def reader = JsonApiJackson3.patchDtoReader(
        mapper, ValidationContext.defaults(), identifierConverter())

    when:
    def patch = reader.readValue(
        '{"data":{"type":"articles","id":"1"}}', MixinIdPatch)

    then:
    patch.id == "property:parsed-1"
    patch.title == PatchPresence.omitted()
  }

  def "typed PATCH retains the identifier diagnostic for target construction failures"() {
    given:
    def reader = JsonApiJackson3.patchDtoReader(
        JsonMapper.builder().build(), ValidationContext.defaults(), identifierConverter())

    when:
    reader.readValue('{"data":{"type":"articles","id":"1"}}', FailingIdPatch)

    then:
    def ex = thrown(JsonApiMappingException)
    ex.diagnostic() == MappingDiagnostic.IDENTIFIER_CONVERSION_FAILED
    ex.propertyPath() == "/id"
  }

  def "nested typed PATCH identifier construction failures retain the identifier diagnostic"() {
    given:
    def reader = JsonApiJackson3.patchDtoReader(
        JsonMapper.builder().build(),
        ValidationContext.defaults(),
        nestedIdentifierConverter())

    when:
    reader.readValue('{"data":{"type":"nested-articles","id":"1"}}', NestedFailingIdPatch)

    then:
    def ex = thrown(JsonApiMappingException)
    ex.diagnostic() == MappingDiagnostic.IDENTIFIER_CONVERSION_FAILED
    ex.propertyPath() == "/id"
  }

  def "typed PATCH normalizes identifier converter mapping exceptions"() {
    given:
    def reader = JsonApiJackson3.patchDtoReader(
        JsonMapper.builder().build(), ValidationContext.defaults(), mappingThrowingConverter())

    when:
    reader.readValue('{"data":{"type":"articles","id":"1"}}', DirectIdPatch)

    then:
    def ex = thrown(JsonApiMappingException)
    ex.diagnostic() == MappingDiagnostic.IDENTIFIER_CONVERSION_FAILED
    ex.propertyPath() == "/id"
  }

  private static IdentifierConverter identifierConverter() {
    new IdentifierConverter() {
          @Override
          String convert(Object value) {
            value.toString()
          }

          @Override
          Object parse(String wire) {
            "parsed-" + wire
          }
        }
  }

  private static IdentifierConverter nestedIdentifierConverter() {
    new IdentifierConverter() {
          @Override
          String convert(Object value) {
            value.toString()
          }

          @Override
          Object parse(String wire) {
            [part: "parsed-" + wire]
          }
        }
  }

  private static IdentifierConverter mappingThrowingConverter() {
    new IdentifierConverter() {
          @Override
          String convert(Object value) {
            value.toString()
          }

          @Override
          Object parse(String wire) {
            throw new JsonApiMappingException(
            MappingDiagnostic.UNSUPPORTED_ATTRIBUTE_VALUE,
            DirectIdArticle,
            MappingLocation.of("wrong"),
            "converter failure")
          }
        }
  }

  private static ResourceObject resourceWithId(String type, String id) {
    new ResourceObject(type, id, null, null, null, null, null, [:])
  }

  private static ResourceObject resourceWithLid(String type, String lid) {
    new ResourceObject(type, null, lid, null, null, null, null, [:])
  }

  private static ResourceObject resourceWithAttributes(String type, String id, Map attributes) {
    new ResourceObject(type, id, null, Attributes.ofAttributes(attributes), null, null, null, [:])
  }

  static class IdentifierDeserializer extends StdDeserializer<String> {
    IdentifierDeserializer() {
      super(String)
    }

    @Override
    String deserialize(JsonParser parser, DeserializationContext context) {
      "property:" + parser.getValueAsString()
    }
  }

  static class PropertyIdentifierDeserializer extends StdDeserializer<PropertyIdentifier> {
    PropertyIdentifierDeserializer() {
      super(PropertyIdentifier)
    }

    @Override
    PropertyIdentifier deserialize(JsonParser parser, DeserializationContext context) {
      new PropertyIdentifier(parser.getValueAsString())
    }
  }

  static class FailingIdentifierDeserializer extends StdDeserializer<String> {
    FailingIdentifierDeserializer() {
      super(String)
    }

    @Override
    String deserialize(JsonParser parser, DeserializationContext context) {
      throw ValueInstantiationException.from(
      parser, "identifier construction failed", context.constructType(String))
    }
  }

  static class FailingAttributeDeserializer extends StdDeserializer<String> {
    FailingAttributeDeserializer() {
      super(String)
    }

    @Override
    String deserialize(JsonParser parser, DeserializationContext context) {
      throw MismatchedInputException.from(parser, String, "attribute conversion failed")
    }
  }

  static class PropertyIdentifier {
    String value

    PropertyIdentifier(String value) {
      this.value = value
    }

    boolean equals(Object other) {
      other instanceof PropertyIdentifier && value == other.value
    }

    int hashCode() {
      value.hashCode()
    }
  }

  @JsonApiResource(type = "articles")
  static class DirectIdArticle {
    @JsonApiId @JsonDeserialize(using = IdentifierDeserializer) String id
    @JsonDeserialize(using = IdentifierDeserializer) @JsonApiAttribute String title
  }

  @JsonApiResource(type = "articles")
  static class MixinIdArticle {
    @JsonApiId String id
    @JsonApiAttribute String title
  }

  static abstract class IdDeserializerMixIn {
    @JsonDeserialize(using = IdentifierDeserializer)
    abstract String getId()
  }

  @JsonApiResource(type = "rich-articles")
  static class RichIdArticle {
    @JsonApiId @JsonDeserialize(using = PropertyIdentifierDeserializer) PropertyIdentifier id

    RichIdArticle() {}
  }

  @JsonApiResource(type = "articles")
  static class FailingIdArticle {
    @JsonApiId @JsonDeserialize(using = FailingIdentifierDeserializer) String id
    @JsonApiAttribute String title
  }

  @JsonApiResource(type = "articles")
  static class FailingLocalIdArticle {
    @JsonApiId String id
    @JsonApiLocalId @JsonDeserialize(using = FailingIdentifierDeserializer) String localId
    @JsonApiAttribute String title
  }

  static class NestedFailingIdentifier {
    @JsonDeserialize(using = FailingIdentifierDeserializer) String part
  }

  static class NestedAttributeValue {
    @JsonDeserialize(using = FailingAttributeDeserializer) String id
  }

  @JsonApiResource(type = "nested-articles")
  static class NestedFailingIdArticle {
    @JsonApiId NestedFailingIdentifier id
  }

  @JsonApiResource(type = "nested-attribute-articles")
  static class NestedAttributeIdArticle {
    @JsonApiId String id
    @JsonApiAttribute NestedAttributeValue details
  }

  @JsonApiResource(type = "articles")
  static class DirectIdPatch {
    @JsonApiId @JsonDeserialize(using = IdentifierDeserializer) String id
    @JsonApiAttribute PatchPresence<String> title
  }

  @JsonApiResource(type = "articles")
  static class MixinIdPatch {
    @JsonApiId String id
    @JsonApiAttribute PatchPresence<String> title
  }

  @JsonApiResource(type = "articles")
  static class FailingIdPatch {
    @JsonApiId @JsonDeserialize(using = FailingIdentifierDeserializer) String id
    @JsonApiAttribute PatchPresence<String> title
  }

  @JsonApiResource(type = "nested-articles")
  static class NestedFailingIdPatch {
    @JsonApiId NestedFailingIdentifier id
    @JsonApiAttribute PatchPresence<String> title
  }
}
