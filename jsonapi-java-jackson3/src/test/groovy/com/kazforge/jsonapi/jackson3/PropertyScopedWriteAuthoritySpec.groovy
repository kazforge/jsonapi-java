package com.kazforge.jsonapi.jackson3

import com.fasterxml.jackson.annotation.JsonInclude
import com.kazforge.jsonapi.annotation.JsonApiAttribute
import com.kazforge.jsonapi.annotation.JsonApiId
import com.kazforge.jsonapi.annotation.JsonApiMeta
import com.kazforge.jsonapi.annotation.JsonApiRelationship
import com.kazforge.jsonapi.annotation.JsonApiRelationshipMeta
import com.kazforge.jsonapi.annotation.JsonApiResource
import com.kazforge.jsonapi.core.model.ResourceIdentifier
import com.kazforge.jsonapi.diagnostic.JsonApiMappingException
import com.kazforge.jsonapi.diagnostic.MappingDiagnostic
import com.kazforge.jsonapi.fixtures.domainpatch.ArticleWithMapMeta
import spock.lang.Specification
import tools.jackson.core.JsonGenerator
import tools.jackson.databind.BeanDescription
import tools.jackson.databind.SerializationConfig
import tools.jackson.databind.SerializationContext
import tools.jackson.databind.SerializationFeature
import tools.jackson.databind.ValueSerializer
import tools.jackson.databind.annotation.JsonSerialize
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.module.SimpleModule
import tools.jackson.databind.ser.BeanPropertyWriter
import tools.jackson.databind.ser.ValueSerializerModifier

class PropertyScopedWriteAuthoritySpec extends Specification {

  def "attribute and both meta locations use direct property serializers"() {
    given:
    def article = new DirectPropertyArticle(
        "1", "title", new StructuredValue("detail"), new MetaValue("resource"),
        ResourceIdentifier.of("people", "p1"), new MetaValue("relationship"))

    when:
    def resource = JsonApiJackson3.resourceMapper(JsonMapper.builder().build()).toResource(article)

    then:
    resource.attributes().attributes() == [title: "property:title", details: [encoded: "detail"]]
    resource.meta().members() == [encoded: "resource"]
    resource.relationships().relationships().author.meta().members() == [encoded: "relationship"]
  }

  def "populated map meta converts resource and relationship members independently"() {
    given:
    def article = new ArticleWithMapMeta(
        "1", "T", ResourceIdentifier.of("people", "p1"),
        Map.of("source", "cms"), Map.of("displayName", "Alice"))

    when:
    def resource = JsonApiJackson3.resourceMapper(JsonMapper.builder().build()).toResource(article)

    then:
    resource.meta().members() == [source: "cms"]
    resource.relationships().relationships().author.meta().members() == [displayName: "Alice"]
  }

  def "property-scoped writes retain runtime subtype fields for concrete base values"() {
    given:
    def article = new RuntimeSubtypeArticle("1", new ConcreteSubtypeValue("base", "subclass"))

    when:
    def resource = JsonApiJackson3.resourceMapper(JsonMapper.builder().build()).toResource(article)

    then:
    resource.attributes().attributes() == [details: [base: "base", extra: "subclass"]]
  }

  def "ordinary null attributes use their contextual null serializer"() {
    given:
    def article = new NullSerializedArticle("1", null)

    when:
    def resource = JsonApiJackson3.resourceMapper(JsonMapper.builder().build()).toResource(article)

    then:
    resource.attributes().attributes() == [title: "property:null"]
  }

  def "ordinary null attributes use a module-assigned property null serializer"() {
    given:
    def mapper = JsonMapper.builder().addModule(new ModuleNullSerializerModule()).build()
    def article = new ModuleNullSerializedArticle("1", null)

    when:
    def resource = JsonApiJackson3.resourceMapper(mapper).toResource(article)

    then:
    resource.attributes().attributes() == [title: "module:null"]
  }

  def "property inclusion preserves omission separately from explicit null"() {
    given:
    def article = new IncludedPropertyArticle("1", "", null, null)

    when:
    def resource = JsonApiJackson3.resourceMapper(JsonMapper.builder().build()).toResource(article)

    then:
    resource.attributes().attributes() == [explicitNull: null]
  }

  def "property inclusion evaluates the already-read value once"() {
    given:
    def article = new SingleReadIncludedArticle("1")

    when:
    def resource = JsonApiJackson3.resourceMapper(JsonMapper.builder().build()).toResource(article)

    then:
    article.titleReads() == 1
    resource.attributes() == null
  }

  def "attribute and resource meta mix-in serializers remain property-scoped"() {
    given:
    def mapper = JsonMapper.builder().addMixIn(MixinPropertyArticle, PropertyCustomizationMixIn).build()
    def article = new MixinPropertyArticle("1", "title", new MetaValue("resource"))

    when:
    def resource = JsonApiJackson3.resourceMapper(mapper).toResource(article)

    then:
    resource.attributes().attributes() == [title: "mixin:title"]
    resource.meta().members() == [encoded: "resource"]
  }

  def "root wrapping does not leak into property-scoped writes"() {
    given:
    def mapper = JsonMapper.builder().enable(SerializationFeature.WRAP_ROOT_VALUE).build()
    def article = new DirectPropertyArticle(
        "1", "title", new StructuredValue("detail"), new MetaValue("resource"),
        ResourceIdentifier.of("people", "p1"), new MetaValue("relationship"))

    when:
    def resource = JsonApiJackson3.resourceMapper(mapper).toResource(article)

    then:
    resource.attributes().attributes() == [title: "property:title", details: [encoded: "detail"]]
    resource.meta().members() == [encoded: "resource"]
  }

  def "identifier wire semantics remain JSON:API-owned"() {
    given:
    def article = new JsonApiOwnedIdentifier("1")

    when:
    def resource = JsonApiJackson3.resourceMapper(JsonMapper.builder().build()).toResource(article)

    then:
    resource.id() == "1"
  }

  def "property serialization cannot bypass object-shaped meta validation"() {
    given:
    def article = new ScalarSerializedMetaArticle("1", new MetaValue("resource"))

    when:
    JsonApiJackson3.resourceMapper(JsonMapper.builder().build()).toResource(article)

    then:
    def ex = thrown(JsonApiMappingException)
    ex.diagnostic() == MappingDiagnostic.INVALID_META_TARGET
    ex.propertyPath() == "/meta"
  }

  def "a meta serializer emitting JSON null is a non-object meta target failure"() {
    given:
    def article = new JsonNullSerializedMetaArticle("1", new MetaValue("resource"))

    when:
    JsonApiJackson3.resourceMapper(JsonMapper.builder().build()).toResource(article)

    then:
    def ex = thrown(JsonApiMappingException)
    ex.diagnostic() == MappingDiagnostic.INVALID_META_TARGET
    ex.propertyPath() == "/meta"
    ex.message == "Converted meta value is not an object (expected a JSON object, got null)"
  }

  static class PropertySerializer extends ValueSerializer<Object> {
    @Override
    void serialize(Object value, JsonGenerator generator, SerializationContext context) {
      generator.writeString("property:" + value)
    }
  }

  static class NullPropertySerializer extends ValueSerializer<Object> {
    @Override
    void serialize(Object value, JsonGenerator generator, SerializationContext context) {
      generator.writeString("property:null")
    }
  }

  static class ModuleNullPropertySerializer extends ValueSerializer<Object> {
    @Override
    void serialize(Object value, JsonGenerator generator, SerializationContext context) {
      generator.writeString("module:null")
    }
  }

  static class MixinPropertySerializer extends ValueSerializer<Object> {
    @Override
    void serialize(Object value, JsonGenerator generator, SerializationContext context) {
      generator.writeString("mixin:" + value)
    }
  }

  static class StructuredSerializer extends ValueSerializer<StructuredValue> {
    @Override
    void serialize(StructuredValue value, JsonGenerator generator, SerializationContext context) {
      generator.writeStartObject()
      generator.writeName("encoded")
      generator.writeString(value.value)
      generator.writeEndObject()
    }
  }

  static class MetaSerializer extends ValueSerializer<MetaValue> {
    @Override
    void serialize(MetaValue value, JsonGenerator generator, SerializationContext context) {
      generator.writeStartObject()
      generator.writeName("encoded")
      generator.writeString(value.value)
      generator.writeEndObject()
    }
  }

  static class ScalarMetaSerializer extends ValueSerializer<MetaValue> {
    @Override
    void serialize(MetaValue value, JsonGenerator generator, SerializationContext context) {
      generator.writeString(value.value)
    }
  }

  static class JsonNullMetaSerializer extends ValueSerializer<MetaValue> {
    @Override
    void serialize(MetaValue value, JsonGenerator generator, SerializationContext context) {
      generator.writeNull()
    }
  }

  static class ModuleNullSerializerModule extends SimpleModule {
    ModuleNullSerializerModule() {
      super("property-null-test")
      setSerializerModifier(new ModuleNullSerializerModifier())
    }
  }

  static class ModuleNullSerializerModifier extends ValueSerializerModifier {
    @Override
    List<BeanPropertyWriter> changeProperties(
        SerializationConfig config, BeanDescription.Supplier beanDesc, List<BeanPropertyWriter> properties) {
      def title = properties.find { it.name == "title" }
      if (title != null) {
        title.assignNullSerializer(new ModuleNullPropertySerializer())
      }
      properties
    }
  }

  @JsonApiResource(type = "articles")
  static class DirectPropertyArticle {
    @JsonApiId String id
    @JsonApiAttribute @JsonSerialize(using = PropertySerializer) String title
    @JsonApiAttribute @JsonSerialize(using = StructuredSerializer) StructuredValue details
    @JsonApiMeta @JsonSerialize(using = MetaSerializer) MetaValue meta
    @JsonApiRelationship ResourceIdentifier author
    @JsonApiRelationshipMeta(relationship = "author") @JsonSerialize(using = MetaSerializer) MetaValue authorMeta

    DirectPropertyArticle(
    String id, @JsonApiAttribute String title, StructuredValue details, MetaValue meta,
    ResourceIdentifier author, MetaValue authorMeta) {
      this.id = id
      this.title = title
      this.details = details
      this.meta = meta
      this.author = author
      this.authorMeta = authorMeta
    }
  }

  @JsonApiResource(type = "runtime-articles")
  static class RuntimeSubtypeArticle {
    @JsonApiId String id
    @JsonApiAttribute ConcreteBaseValue details

    RuntimeSubtypeArticle(String id, ConcreteBaseValue details) {
      this.id = id
      this.details = details
    }
  }

  static class ConcreteBaseValue {
    String base

    ConcreteBaseValue(String base) {
      this.base = base
    }
  }

  static class ConcreteSubtypeValue extends ConcreteBaseValue {
    String extra

    ConcreteSubtypeValue(String base, String extra) {
      super(base)
      this.extra = extra
    }
  }

  @JsonApiResource(type = "articles")
  static class NullSerializedArticle {
    @JsonApiId String id
    @JsonApiAttribute @JsonSerialize(nullsUsing = NullPropertySerializer) String title

    NullSerializedArticle(String id, String title) {
      this.id = id
      this.title = title
    }
  }

  @JsonApiResource(type = "articles")
  static class ModuleNullSerializedArticle {
    @JsonApiId String id
    @JsonApiAttribute String title

    ModuleNullSerializedArticle(String id, String title) {
      this.id = id
      this.title = title
    }
  }

  @JsonApiResource(type = "included-articles")
  static class IncludedPropertyArticle {
    @JsonApiId String id
    @JsonApiAttribute @JsonInclude(JsonInclude.Include.NON_EMPTY) String empty
    @JsonApiAttribute @JsonInclude(JsonInclude.Include.NON_NULL) String missing
    @JsonApiAttribute String explicitNull

    IncludedPropertyArticle(String id, String empty, String missing, String explicitNull) {
      this.id = id
      this.empty = empty
      this.missing = missing
      this.explicitNull = explicitNull
    }
  }

  @JsonApiResource(type = "single-read-articles")
  static class SingleReadIncludedArticle {
    @JsonApiId String id
    private int titleReadCount

    SingleReadIncludedArticle(String id) {
      this.id = id
    }

    @JsonApiAttribute @JsonInclude(JsonInclude.Include.NON_EMPTY)
    String getTitle() {
      titleReadCount += 1
      titleReadCount == 1 ? "" : "second-read-value"
    }

    int titleReads() {
      titleReadCount
    }
  }

  static class StructuredValue {
    String value

    StructuredValue(String value) {
      this.value = value
    }
  }

  static class MetaValue {
    String value

    MetaValue(String value) {
      this.value = value
    }
  }

  @JsonApiResource(type = "mixin-articles")
  static class MixinPropertyArticle {
    @JsonApiId String id
    @JsonApiAttribute String title
    @JsonApiMeta MetaValue meta

    MixinPropertyArticle(String id, String title, MetaValue meta) {
      this.id = id
      this.title = title
      this.meta = meta
    }
  }

  static abstract class PropertyCustomizationMixIn {
    @JsonSerialize(using = MixinPropertySerializer)
    abstract String getTitle()

    @JsonSerialize(using = MetaSerializer)
    abstract MetaValue getMeta()
  }

  @JsonApiResource(type = "articles")
  static class ScalarSerializedMetaArticle {
    @JsonApiId String id
    @JsonApiMeta @JsonSerialize(using = ScalarMetaSerializer) MetaValue meta

    ScalarSerializedMetaArticle(String id, MetaValue meta) {
      this.id = id
      this.meta = meta
    }
  }

  @JsonApiResource(type = "articles")
  static class JsonNullSerializedMetaArticle {
    @JsonApiId String id
    @JsonApiMeta @JsonSerialize(using = JsonNullMetaSerializer) MetaValue meta

    JsonNullSerializedMetaArticle(String id, MetaValue meta) {
      this.id = id
      this.meta = meta
    }
  }

  @JsonApiResource(type = "articles")
  static class JsonApiOwnedIdentifier {
    @JsonApiId @JsonSerialize(using = PropertySerializer) String id

    JsonApiOwnedIdentifier(String id) {
      this.id = id
    }
  }
}
