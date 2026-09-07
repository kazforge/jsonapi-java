package io.github.kazemek.jsonapi.jackson2

import io.github.kazemek.jsonapi.annotation.JsonApiAttribute
import io.github.kazemek.jsonapi.annotation.JsonApiId
import io.github.kazemek.jsonapi.annotation.JsonApiMeta
import io.github.kazemek.jsonapi.annotation.JsonApiRelationship
import io.github.kazemek.jsonapi.annotation.JsonApiResource
import io.github.kazemek.jsonapi.annotation.JsonApiRelationshipMeta
import io.github.kazemek.jsonapi.core.model.ResourceIdentifier
import io.github.kazemek.jsonapi.jackson.diagnostic.JsonApiMappingException
import io.github.kazemek.jsonapi.jackson.diagnostic.MappingDiagnostic
import spock.lang.Specification
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.BeanDescription
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.SerializationConfig
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter
import com.fasterxml.jackson.databind.ser.BeanSerializerModifier
import java.io.IOException

// Jackson 2 mechanism probes for property-scoped serialization authority: property serializers,
// null serializers (property- and module-assigned), inclusion, one accessor read per local member
// render, mix-in serializers, and root-wrapping isolation. Identifier conversion and deserialization
// authorities belong to the flat-read binder, a later Jackson 2 capability.
class PropertyScopedAuthoritySpec extends Specification {

  def "attribute and both meta locations use direct property serializers"() {
    given:
    def article = new DirectPropertyArticle(
        "1",
        "title",
        new StructuredValue("detail"),
        new MetaValue("resource"),
        ResourceIdentifier.of("people", "p1"),
        new MetaValue("relationship"))

    when:
    def resource = JsonApiJackson2.resourceMapper(JsonMapper.builder().build()).toResource(article)

    then:
    resource.attributes().attributes() == [
      title: "property:title",
      details: [encoded: "detail"]
    ]
    resource.meta().members() == [encoded: "resource"]
    resource.relationships().relationships().author.meta().members() == [encoded: "relationship"]
  }

  def "ordinary uncustomized scalar attributes retain their value"() {
    given:
    def article = new OrdinaryPropertyArticle("1", "title")

    when:
    def resource = JsonApiJackson2.resourceMapper(JsonMapper.builder().build()).toResource(article)

    then:
    resource.attributes().attributes() == [title: "title"]
  }

  def "property-scoped writes retain runtime subtype fields for concrete base values"() {
    given:
    def article = new RuntimeSubtypeArticle(
        "1", new ConcreteSubtypeValue("base", "subclass"))

    when:
    def resource = JsonApiJackson2.resourceMapper(JsonMapper.builder().build()).toResource(article)

    then:
    resource.attributes().attributes() == [details: [base: "base", extra: "subclass"]]
  }

  def "ordinary null attributes use their contextual null serializer"() {
    given:
    def article = new NullSerializedArticle("1", null)

    when:
    def resource = JsonApiJackson2.resourceMapper(JsonMapper.builder().build()).toResource(article)

    then:
    resource.attributes().attributes() == [title: "property:null"]
  }

  def "ordinary null attributes use a module-assigned property null serializer"() {
    given:
    def mapper = JsonMapper.builder()
        .addModule(new ModuleNullSerializerModule())
        .build()
    def article = new ModuleNullSerializedArticle("1", null)

    when:
    def resource = JsonApiJackson2.resourceMapper(mapper).toResource(article)

    then:
    resource.attributes().attributes() == [title: "module:null"]
  }

  def "property inclusion preserves omission separately from explicit null"() {
    given:
    def article = new IncludedPropertyArticle("1", "", null, null)

    when:
    def resource = JsonApiJackson2.resourceMapper(JsonMapper.builder().build()).toResource(article)

    then:
    resource.attributes().attributes() == [explicitNull: null]
  }

  def "property inclusion evaluates the already-read value once"() {
    given:
    def article = new SingleReadIncludedArticle("1")

    when:
    def resource = JsonApiJackson2.resourceMapper(JsonMapper.builder().build()).toResource(article)

    then:
    article.titleReads() == 1
    resource.attributes() == null
  }

  def "attribute and resource meta mix-in serializers remain property-scoped"() {
    given:
    def mapper = JsonMapper.builder()
        .addMixIn(MixinPropertyArticle, PropertyCustomizationMixIn)
        .build()
    def article = new MixinPropertyArticle("1", "title", new MetaValue("resource"))

    when:
    def resource = JsonApiJackson2.resourceMapper(mapper).toResource(article)

    then:
    resource.attributes().attributes() == [title: "mixin:title"]
    resource.meta().members() == [encoded: "resource"]
  }

  def "root wrapping does not leak into property-scoped writes"() {
    given:
    def mapper = JsonMapper.builder()
        .enable(SerializationFeature.WRAP_ROOT_VALUE)
        .build()
    def article = new DirectPropertyArticle(
        "1",
        "title",
        new StructuredValue("detail"),
        new MetaValue("resource"),
        ResourceIdentifier.of("people", "p1"),
        new MetaValue("relationship"))

    when:
    def resource = JsonApiJackson2.resourceMapper(mapper).toResource(article)

    then:
    resource.attributes().attributes() == [title: "property:title", details: [encoded: "detail"]]
    resource.meta().members() == [encoded: "resource"]
  }

  def "identifier wire semantics remain JSON:API-owned"() {
    given:
    def article = new JsonApiOwnedIdentifier("1")

    when:
    def resource = JsonApiJackson2.resourceMapper(JsonMapper.builder().build()).toResource(article)

    then:
    resource.id() == "1"
  }

  def "property serialization cannot bypass object-shaped meta validation"() {
    given:
    def article = new ScalarSerializedMetaArticle("1", new MetaValue("resource"))

    when:
    JsonApiJackson2.resourceMapper(JsonMapper.builder().build()).toResource(article)

    then:
    def ex = thrown(JsonApiMappingException)
    ex.diagnostic() == MappingDiagnostic.INVALID_META_TARGET
    ex.propertyPath() == "/meta"
  }

  static class PropertySerializer extends JsonSerializer<Object> {
    @Override
    void serialize(Object value, JsonGenerator generator, SerializerProvider context)
    throws IOException {
      generator.writeString("property:" + value)
    }
  }

  static class NullPropertySerializer extends JsonSerializer<Object> {
    @Override
    void serialize(Object value, JsonGenerator generator, SerializerProvider context)
    throws IOException {
      generator.writeString("property:null")
    }
  }

  static class ModuleNullPropertySerializer extends JsonSerializer<Object> {
    @Override
    void serialize(Object value, JsonGenerator generator, SerializerProvider context)
    throws IOException {
      generator.writeString("module:null")
    }
  }

  static class MixinPropertySerializer extends JsonSerializer<Object> {
    @Override
    void serialize(Object value, JsonGenerator generator, SerializerProvider context)
    throws IOException {
      generator.writeString("mixin:" + value)
    }
  }

  static class StructuredSerializer extends JsonSerializer<StructuredValue> {
    @Override
    void serialize(StructuredValue value, JsonGenerator generator, SerializerProvider context)
    throws IOException {
      generator.writeStartObject()
      generator.writeFieldName("encoded")
      generator.writeString(value.value)
      generator.writeEndObject()
    }
  }

  static class MetaSerializer extends JsonSerializer<MetaValue> {
    @Override
    void serialize(MetaValue value, JsonGenerator generator, SerializerProvider context)
    throws IOException {
      generator.writeStartObject()
      generator.writeFieldName("encoded")
      generator.writeString(value.value)
      generator.writeEndObject()
    }
  }

  static class ScalarMetaSerializer extends JsonSerializer<MetaValue> {
    @Override
    void serialize(MetaValue value, JsonGenerator generator, SerializerProvider context)
    throws IOException {
      generator.writeString(value.value)
    }
  }

  static class ModuleNullSerializerModule extends SimpleModule {
    ModuleNullSerializerModule() {
      super("property-null-test")
      setSerializerModifier(new ModuleNullSerializerModifier())
    }
  }

  static class ModuleNullSerializerModifier extends BeanSerializerModifier {
    @Override
    List<BeanPropertyWriter> changeProperties(
        SerializationConfig config,
        BeanDescription beanDesc,
        List<BeanPropertyWriter> properties) {
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
    String id,
    @JsonApiAttribute String title,
    StructuredValue details,
    MetaValue meta,
    ResourceIdentifier author,
    MetaValue authorMeta) {
      this.id = id
      this.title = title
      this.details = details
      this.meta = meta
      this.author = author
      this.authorMeta = authorMeta
    }
  }

  @JsonApiResource(type = "ordinary-articles")
  static class OrdinaryPropertyArticle {
    @JsonApiId String id
    @JsonApiAttribute String title

    OrdinaryPropertyArticle(String id, String title) {
      this.id = id
      this.title = title
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
  static class JsonApiOwnedIdentifier {
    @JsonApiId @JsonSerialize(using = PropertySerializer) String id

    JsonApiOwnedIdentifier(String id) {
      this.id = id
    }
  }
}
