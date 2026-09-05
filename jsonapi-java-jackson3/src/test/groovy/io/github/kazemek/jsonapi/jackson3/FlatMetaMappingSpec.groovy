package io.github.kazemek.jsonapi.jackson3

import io.github.kazemek.jsonapi.core.model.Attributes
import io.github.kazemek.jsonapi.core.model.DocumentData
import io.github.kazemek.jsonapi.core.model.JsonApiDocument
import io.github.kazemek.jsonapi.core.model.Meta
import io.github.kazemek.jsonapi.core.model.Relationship
import io.github.kazemek.jsonapi.core.model.Relationships
import io.github.kazemek.jsonapi.core.model.ResourceObject
import io.github.kazemek.jsonapi.jackson.diagnostic.CodecFailureCategory
import io.github.kazemek.jsonapi.jackson.document.DocumentReadContext
import io.github.kazemek.jsonapi.jackson.diagnostic.JsonApiDocumentReadException
import io.github.kazemek.jsonapi.jackson.diagnostic.JsonApiMappingException
import io.github.kazemek.jsonapi.jackson.diagnostic.MappingDiagnostic
import io.github.kazemek.jsonapi.jackson.patch.PatchChange
import io.github.kazemek.jsonapi.jackson.patch.PatchPresence
import io.github.kazemek.jsonapi.jackson.patch.StructuredMember
import io.github.kazemek.jsonapi.jackson.patch.StructuredMemberState
import io.github.kazemek.jsonapi.jackson.patch.StructuredPatch
import io.github.kazemek.jsonapi.jackson3.MetaConversionProbeFixtures.ArticleWithBoxMeta
import io.github.kazemek.jsonapi.jackson3.MetaConversionProbeFixtures.ArticleWithBoxMetaPatch
import io.github.kazemek.jsonapi.jackson3.MetaConversionProbeFixtures.ArticleWithNullEmptyCityMeta
import io.github.kazemek.jsonapi.jackson3.MetaConversionProbeFixtures.ArticleWithTypedContactMeta
import io.github.kazemek.jsonapi.jackson3.MetaConversionProbeFixtures.MetaBox
import io.github.kazemek.jsonapi.jackson3.MetaConversionProbeFixtures.RenamedNestedMetaPatch
import io.github.kazemek.jsonapi.jackson3.MetaConversionProbeFixtures.ThrowingMetaPatchArticle
import io.github.kazemek.jsonapi.jackson3.MetaConversionProbeFixtures.ThrowingRelMetaPatchArticle
import io.github.kazemek.jsonapi.jackson3.StructuredRecursionFixtures.EmailContact
import io.github.kazemek.jsonapi.jackson3.WholeMetaTargetFixtures.ConcreteTypedMeta
import io.github.kazemek.jsonapi.jackson3.WholeMetaTargetFixtures.ConcreteTypedMetaArticle
import io.github.kazemek.jsonapi.jackson3.WholeMetaTargetFixtures.PolyMetaArticle
import io.github.kazemek.jsonapi.jackson3.WholeMetaTargetFixtures.PolyMetaArticlePatch
import io.github.kazemek.jsonapi.jackson3.WholeMetaTargetFixtures.SourceMeta
import io.github.kazemek.jsonapi.fixtures.domainpatch.ArticleWithMeta
import io.github.kazemek.jsonapi.fixtures.domainpatch.ArticleWithMetaPatch
import spock.lang.Specification
import tools.jackson.databind.json.JsonMapper

// Jackson 3 mechanism probes for whole-meta: TypeDeserializer / polymorphic conversion, JavaType
// MetaBox preservation, property null providers, renamed-wire construction pointers, codec
// rejection of wire-level meta null, and fromDocument data-less relationship meta. Major-neutral
// whole-meta write/read/PATCH/fieldset semantics are exercised by direct adapter-owned cases.
class FlatMetaMappingSpec extends Specification {

  static def mapper() {
    JsonApiJackson3.resourceMapper(JsonMapper.builder().build())
  }

  static def binder() {
    JsonApiJackson3.resourceBinder(JsonMapper.builder().build())
  }

  static def patchCommandReader() {
    JsonApiJackson3.patchCommandReader(JsonMapper.builder().build())
  }

  static def patchDtoReader() {
    JsonApiJackson3.patchDtoReader(JsonMapper.builder().build())
  }

  def "wire-level resource meta null is rejected at the reader"() {
    when:
    documentFrom('{"data":{"type":"articles","id":"1","meta":null}}')

    then:
    def e = thrown(JsonApiDocumentReadException)
    e.category == CodecFailureCategory.UNEXPECTED_TOKEN
  }

  def "wire-level relationship meta null is rejected at the reader"() {
    when:
    documentFrom(
        '{"data":{"type":"articles","id":"1","relationships":{"author":{"data":{"type":"people","id":"p1"},"meta":null}}}}')

    then:
    def e = thrown(JsonApiDocumentReadException)
    e.category == CodecFailureCategory.UNEXPECTED_TOKEN
  }

  def "low-level recursive meta preserves generic JavaType binding"() {
    given:
    def json = '{"data":{"type":"articles","id":"1","meta":{"value":"42"}}}'

    when:
    def command = patchCommandReader().readValue(json, ArticleWithBoxMeta)

    then: // the nested value converts as Integer, not Object/String/raw type
    command.changes() == [
      new PatchChange.ResourceMetaChange(
      "meta", "meta",
      new StructuredPatch(
      [
        new StructuredMember("value", "value", new StructuredMemberState.Atomic(42))
      ]))
    ]
  }

  def "typed meta preserves generic JavaType binding"() {
    given:
    def json = '{"data":{"type":"articles","id":"1","meta":{"value":"42"}}}'

    when:
    def patch = patchDtoReader().readValue(json, ArticleWithBoxMetaPatch)

    then:
    patch.meta() == PatchPresence.present(new MetaBox<>(42))
  }

  def "low-level recursive meta routes nested explicit null through the property null provider"() {
    given:
    def json = '{"data":{"type":"articles","id":"1","meta":{"city":null}}}'

    when: // the city setter's @JsonSetter(nulls = Nulls.AS_EMPTY) null provider yields "" for null
    def command = patchCommandReader().readValue(json, ArticleWithNullEmptyCityMeta)

    then: // not the root String deserializer's null value (null)
    command.changes() == [
      new PatchChange.ResourceMetaChange(
      "meta", "meta",
      new StructuredPatch(
      [
        new StructuredMember("city", "city", new StructuredMemberState.Atomic(""))
      ]))
    ]
  }

  def "low-level recursive meta preserves a property-level TypeDeserializer for a polymorphic nested member"() {
    given:
    def json =
        '{"data":{"type":"articles","id":"1","meta":{"contact":{"kind":"email","email":"a@b.c"}}}}'

    when: // the polymorphic contact converts through the property's TypeDeserializer path
    def command = patchCommandReader().readValue(json, ArticleWithTypedContactMeta)

    then:
    command.changes() == [
      new PatchChange.ResourceMetaChange(
      "meta", "meta",
      new StructuredPatch(
      [
        new StructuredMember(
        "contact",
        "contact",
        new StructuredMemberState.Atomic(new EmailContact("a@b.c")))
      ]))
    ]
  }

  def "concrete root-polymorphic whole-meta POJO is a valid declaration and binds"() {
    given:
    def json =
        '{"data":{"type":"articles","id":"1","meta":{"kind":"concrete","value":"v"}}}'

    when: // the root TypeDeserializer decoration must not disqualify the decorated POJO
    def bound = binder().fromResource(mapper().toResource(new ConcreteTypedMetaArticle(
        "1", new ConcreteTypedMeta("v"))), ConcreteTypedMetaArticle)
    def command = patchCommandReader().readValue(json, ConcreteTypedMetaArticle)

    then:
    bound.meta() == new ConcreteTypedMeta("v")
    command.changes() == [
      new PatchChange.ResourceMetaChange("meta", "meta", new ConcreteTypedMeta("v"))
    ]
  }

  def "abstract polymorphic whole-meta base materializes the subtype on read and low-level patch"() {
    given:
    def json =
        '{"data":{"type":"articles","id":"1","meta":{"kind":"source","source":"cms","note":"n"}}}'

    when: // the TypeDeserializer selects the concrete subtype from the discriminator
    def bound = binder().fromResource(mapper().toResource(new PolyMetaArticle(
        "1", new SourceMeta("cms", "n"))), PolyMetaArticle)
    def command = patchCommandReader().readValue(json, PolyMetaArticle)

    then:
    bound.meta() == new SourceMeta("cms", "n")
    command.changes() == [
      new PatchChange.ResourceMetaChange("meta", "meta", new SourceMeta("cms", "n"))
    ]
  }

  def "typed patch preserves root polymorphic conversion for an abstract whole-meta target"() {
    given:
    def json =
        '{"data":{"type":"articles","id":"1","meta":{"kind":"source","source":"cms","note":"n"}}}'

    when:
    def patch = patchDtoReader().readValue(json, PolyMetaArticlePatch)

    then:
    patch.meta() == PatchPresence.present(new SourceMeta("cms", "n"))
  }

  def "typed patch construction failure inside meta reports the renamed nested wire name"() {
    given:
    def json = '{"data":{"type":"articles","id":"1","meta":{"w_source":{"a":1}}}}'

    when:
    patchDtoReader().readValue(json, RenamedNestedMetaPatch)

    then:
    def e = thrown(JsonApiMappingException)
    e.diagnostic == MappingDiagnostic.UNSUPPORTED_ATTRIBUTE_VALUE
    e.propertyPath() == "/meta/w_source"
  }

  def "typed patch final meta construction failure reports the renamed nested wire pointer"() {
    given:
    def json = '{"data":{"type":"articles","id":"1","meta":{"w_source":"cms","note":"n"}}}'

    when:
    patchDtoReader().readValue(json, ThrowingMetaPatchArticle)

    then:
    def e = thrown(JsonApiMappingException)
    e.diagnostic == MappingDiagnostic.UNSUPPORTED_ATTRIBUTE_VALUE
    e.propertyPath() == "/meta/w_source"
  }

  def "typed patch final relationship-meta construction failure reports the renamed nested wire pointer"() {
    given:
    def json =
        '{"data":{"type":"articles","id":"1","relationships":' +
        '{"author":{"data":{"type":"people","id":"p1"},"meta":{"w_source":"cms","note":"n"}}}}}'

    when:
    patchDtoReader().readValue(json, ThrowingRelMetaPatchArticle)

    then:
    def e = thrown(JsonApiMappingException)
    e.diagnostic == MappingDiagnostic.UNSUPPORTED_ATTRIBUTE_VALUE
    e.propertyPath() == "/relationships/author/meta/w_source"
  }

  def "fromDocument skips a data-less relationship with meta on the low-level path"() {
    given:
    def document = dataLessMetaDocument(null)
    def command = patchCommandReader().fromDocument(document, ArticleWithMeta)

    expect:
    command.identity() == "1"
    command.changes().isEmpty()
  }

  def "fromDocument skips a data-less relationship with meta but keeps other supplied changes on the low-level path"() {
    given:
    def document = dataLessMetaDocument([title: "T"])
    def command = patchCommandReader().fromDocument(document, ArticleWithMeta)

    expect:
    command.changes() == [
      new PatchChange.AttributeChange("title", "title", "T")
    ]
  }

  def "fromDocument binds a data-less relationship with meta as Omitted on the typed path"() {
    given:
    def document = dataLessMetaDocument(null)
    def dto = patchDtoReader().fromDocument(document, ArticleWithMetaPatch)

    expect:
    dto.author().isOmitted()
    dto.authorMeta().isOmitted()
  }

  def "fromDocument binds a data-less relationship with meta as Omitted but keeps other supplied members on the typed path"() {
    given:
    def document = dataLessMetaDocument([title: "T"])
    def dto = patchDtoReader().fromDocument(document, ArticleWithMetaPatch)

    expect:
    dto.title() == PatchPresence.present("T")
    dto.author().isOmitted()
    dto.authorMeta().isOmitted()
  }

  private static JsonApiDocument dataLessMetaDocument(Map suppliedAttrs) {
    def resource = new ResourceObject(
        "articles",
        "1",
        null,
        suppliedAttrs == null ? null : Attributes.ofAttributes(suppliedAttrs),
        Relationships.ofRelationships(
        [author: Relationship.metaOnly(Meta.of([displayName: "Alice"]))]),
        null,
        null,
        [:])
    return new JsonApiDocument(
        new DocumentData.SingleResource(resource), null, null, null, null, null, [:])
  }

  private static JsonApiDocument documentFrom(String json) {
    JsonApiJackson3.reader(JsonMapper.builder().build(),
        DocumentReadContext.resourceDefaults()).readValue(json)
  }
}
