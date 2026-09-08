package io.github.kazemek.jsonapi.jackson3

import com.fasterxml.jackson.annotation.JsonInclude
import io.github.kazemek.jsonapi.annotation.JsonApiAttribute
import io.github.kazemek.jsonapi.annotation.JsonApiId
import io.github.kazemek.jsonapi.annotation.JsonApiRelationship
import io.github.kazemek.jsonapi.annotation.JsonApiResource
import io.github.kazemek.jsonapi.core.model.DocumentData
import io.github.kazemek.jsonapi.core.model.JsonApiDocument
import io.github.kazemek.jsonapi.core.model.Meta
import io.github.kazemek.jsonapi.core.model.Relationship
import io.github.kazemek.jsonapi.core.model.RelationshipData
import io.github.kazemek.jsonapi.core.model.Relationships
import io.github.kazemek.jsonapi.core.model.ResourceIdentifier
import io.github.kazemek.jsonapi.core.model.ResourceObject
import io.github.kazemek.jsonapi.core.validation.DocumentUsage
import io.github.kazemek.jsonapi.core.validation.EndpointIdentity
import io.github.kazemek.jsonapi.core.validation.ValidationContext
import io.github.kazemek.jsonapi.core.validation.ValidationRuleCode
import io.github.kazemek.jsonapi.jackson.document.DocumentReadContext
import io.github.kazemek.jsonapi.jackson.diagnostic.JsonApiDocumentReadException
import io.github.kazemek.jsonapi.jackson.diagnostic.JsonApiMappingException
import io.github.kazemek.jsonapi.jackson.diagnostic.MappingDiagnostic
import io.github.kazemek.jsonapi.jackson.mapping.IdentifierConverter
import io.github.kazemek.jsonapi.jackson.mapping.RelationshipLinkage
import io.github.kazemek.jsonapi.jackson.patch.PatchPresence
import io.github.kazemek.jsonapi.jackson.document.PrimaryDataKind
import io.github.kazemek.jsonapi.jackson3.ParameterizedBindingFixtures.GenericPatch
import io.github.kazemek.jsonapi.fixtures.domainpatch.AddressPatch
import io.github.kazemek.jsonapi.fixtures.domainpatch.AddressWithContainersPatch
import io.github.kazemek.jsonapi.fixtures.domainpatch.AddressWithGeoPatch
import io.github.kazemek.jsonapi.fixtures.domainpatch.AddressWithOptionalCityPatch
import io.github.kazemek.jsonapi.fixtures.domainpatch.AddressWithTagsPatch
import io.github.kazemek.jsonapi.fixtures.domainpatch.ArticlePatch
import io.github.kazemek.jsonapi.fixtures.domainpatch.ArticleMeta
import io.github.kazemek.jsonapi.fixtures.domainpatch.ArticleMetaPatch
import io.github.kazemek.jsonapi.fixtures.domainpatch.ArticleWithAddressPatch
import io.github.kazemek.jsonapi.fixtures.domainpatch.ArticleWithAddressTagsPatch
import io.github.kazemek.jsonapi.fixtures.domainpatch.ArticleWithBoxPatch
import io.github.kazemek.jsonapi.fixtures.domainpatch.ArticleWithContainerAddressPatch
import io.github.kazemek.jsonapi.fixtures.domainpatch.ArticleWithDirectPresentAddressPatch
import io.github.kazemek.jsonapi.fixtures.domainpatch.ArticleWithGeoPatch
import io.github.kazemek.jsonapi.fixtures.domainpatch.ArticleWithMapMetaPatch
import io.github.kazemek.jsonapi.fixtures.domainpatch.ArticleWithMetaPatch
import io.github.kazemek.jsonapi.fixtures.domainpatch.ArticleWithMixedAddressPatch
import io.github.kazemek.jsonapi.fixtures.domainpatch.ArticleWithOptionalAddressPatch
import io.github.kazemek.jsonapi.fixtures.domainpatch.ArticleWithOptionalCityPatch
import io.github.kazemek.jsonapi.fixtures.domainpatch.ArticleWithOptionalMetaPatch
import io.github.kazemek.jsonapi.fixtures.domainpatch.ArticleWithRawAddressPatch
import io.github.kazemek.jsonapi.fixtures.domainpatch.ArticleWithRelationshipLinkagePatch
import io.github.kazemek.jsonapi.fixtures.domainpatch.AuthorIdMeta
import io.github.kazemek.jsonapi.fixtures.domainpatch.AuthorMeta
import io.github.kazemek.jsonapi.fixtures.domainpatch.BoxPatch
import io.github.kazemek.jsonapi.fixtures.domainpatch.CommentIdMeta
import io.github.kazemek.jsonapi.fixtures.domainpatch.ConventionalIdPatch
import io.github.kazemek.jsonapi.fixtures.domainpatch.DirectPresentPatch
import io.github.kazemek.jsonapi.fixtures.domainpatch.GeoPatch
import io.github.kazemek.jsonapi.fixtures.domainpatch.IntIdPatch
import io.github.kazemek.jsonapi.fixtures.domainpatch.MutableAddressPatch
import io.github.kazemek.jsonapi.fixtures.domainpatch.MutableArticleWithAddressPatch
import io.github.kazemek.jsonapi.fixtures.domainpatch.NonPatchPresencePatch
import io.github.kazemek.jsonapi.fixtures.domainpatch.OptionalPatch
import io.github.kazemek.jsonapi.fixtures.domainpatch.PresenceIdPatch
import io.github.kazemek.jsonapi.fixtures.domainpatch.RawPatchPresencePatch
import io.github.kazemek.jsonapi.fixtures.domainpatch.UnannotatedPatch
import io.github.kazemek.jsonapi.fixtures.TestFixtureResources
import spock.lang.Specification
import spock.lang.Unroll
import tools.jackson.core.JsonParser
import tools.jackson.core.JsonGenerator
import tools.jackson.databind.DeserializationContext
import tools.jackson.databind.JavaType
import tools.jackson.databind.PropertyNamingStrategies
import tools.jackson.databind.SerializationContext
import tools.jackson.databind.ValueSerializer
import tools.jackson.databind.annotation.JsonDeserialize
import tools.jackson.databind.annotation.JsonSerialize
import tools.jackson.databind.deser.std.StdDeserializer
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.type.TypeFactory
import tools.jackson.databind.util.Converter

class PatchDtoBindingSpec extends Specification {

  @Unroll
  def "binds typed patch dto #id into an explicit DTO"() {
    given:
    def json = TestFixtureResources.readCorpusUtf8("patch/${resource}.json")
    def reader = JsonApiJackson3.patchDtoReader(JsonMapper.builder().build())

    when:
    def actual = reader.readValue(json, targetType)

    then:
    actual == expected

    where:
    id | resource | targetType | expected
    "patch-dto-omitted-and-supplied-attributes" | "omitted-and-supplied-attributes" | ArticlePatch.class | new ArticlePatch("1", PatchPresence.present("Hello"), PatchPresence.omitted(), PatchPresence.omitted(), PatchPresence.omitted())
    "patch-dto-explicit-null-attribute" | "explicit-null-attribute" | ArticlePatch.class | new ArticlePatch("1", PatchPresence.present(null), PatchPresence.omitted(), PatchPresence.omitted(), PatchPresence.omitted())
    "patch-dto-explicit-null-optional-inner" | "subtitle-explicit-null" | OptionalPatch.class | new OptionalPatch("1", PatchPresence.present(Optional.empty()))
    "patch-dto-attribute-rename" | "attribute-rename" | ArticlePatch.class | new ArticlePatch("1", PatchPresence.omitted(), PatchPresence.present("Content"), PatchPresence.omitted(), PatchPresence.omitted())
    "patch-dto-relationship-null-linkage" | "relationship-null-linkage" | ArticlePatch.class | new ArticlePatch("1", PatchPresence.omitted(), PatchPresence.omitted(), PatchPresence.present(null), PatchPresence.omitted())
    "patch-dto-relationship-single-linkage" | "relationship-single-linkage" | ArticlePatch.class | new ArticlePatch("1", PatchPresence.omitted(), PatchPresence.omitted(), PatchPresence.present(ResourceIdentifier.of("people", "p1")), PatchPresence.omitted())
    "patch-dto-relationship-empty-collection" | "relationship-empty-collection" | ArticlePatch.class | new ArticlePatch("1", PatchPresence.omitted(), PatchPresence.omitted(), PatchPresence.omitted(), PatchPresence.present([]))
    "patch-dto-relationship-non-empty-collection" | "relationship-non-empty-collection" | ArticlePatch.class | new ArticlePatch("1", PatchPresence.omitted(), PatchPresence.omitted(), PatchPresence.omitted(), PatchPresence.present([
      ResourceIdentifier.of("comments", "c1"),
      ResourceIdentifier.of("comments", "c2")
    ]))
    "patch-dto-identity-only" | "identity-other-id" | ArticlePatch.class | new ArticlePatch("7", PatchPresence.omitted(), PatchPresence.omitted(), PatchPresence.omitted(), PatchPresence.omitted())
    "patch-dto-conventional-id" | "title-only" | ConventionalIdPatch.class | new ConventionalIdPatch("1", PatchPresence.present("T"))
    "patch-dto-nested-partial-structured-object" | "address-street-new-street" | ArticleWithAddressPatch.class | new ArticleWithAddressPatch("1", PatchPresence.present(new AddressPatch(PatchPresence.present("New Street"), PatchPresence.omitted())))
    "patch-dto-nested-empty-structured-object" | "address-empty-object" | ArticleWithAddressPatch.class | new ArticleWithAddressPatch("1", PatchPresence.present(new AddressPatch(PatchPresence.omitted(), PatchPresence.omitted())))
    "patch-dto-nested-explicit-null" | "address-explicit-null" | ArticleWithAddressPatch.class | new ArticleWithAddressPatch("1", PatchPresence.present(null))
    "patch-dto-nested-omitted" | "identity-only" | ArticleWithAddressPatch.class | new ArticleWithAddressPatch("1", PatchPresence.omitted())
    "patch-dto-nested-multi-level" | "address-street-and-geo-lat" | ArticleWithGeoPatch.class | new ArticleWithGeoPatch("1", PatchPresence.present(new AddressWithGeoPatch(PatchPresence.present("S"), PatchPresence.present(new GeoPatch(PatchPresence.present("1"), PatchPresence.omitted())))))
    "patch-dto-nested-optional-object" | "address-street" | ArticleWithOptionalAddressPatch.class | new ArticleWithOptionalAddressPatch("1", PatchPresence.present(Optional.of(new AddressPatch(PatchPresence.present("S"), PatchPresence.omitted()))))
    "patch-dto-nested-optional-null" | "address-explicit-null" | ArticleWithOptionalAddressPatch.class | new ArticleWithOptionalAddressPatch("1", PatchPresence.present(Optional.empty()))
    "patch-dto-nested-optional-member-null" | "address-street-city-null" | ArticleWithOptionalCityPatch.class | new ArticleWithOptionalCityPatch("1", PatchPresence.present(new AddressWithOptionalCityPatch(PatchPresence.present("S"), PatchPresence.present(Optional.empty()))))
    "patch-dto-nested-container-atomic" | "address-street-tags" | ArticleWithAddressTagsPatch.class | new ArticleWithAddressTagsPatch("1", PatchPresence.present(new AddressWithTagsPatch(PatchPresence.present("S"), PatchPresence.present(["a", "b"]))))
    "patch-dto-nested-generic-javatype" | "box-numbers" | ArticleWithBoxPatch.class | new ArticleWithBoxPatch("1", PatchPresence.present(new BoxPatch<Integer>(PatchPresence.present([1, 2]))))
    "patch-dto-nested-container-atomic-set" | "address-street-aliases" | ArticleWithContainerAddressPatch.class | new ArticleWithContainerAddressPatch("1", PatchPresence.present(new AddressWithContainersPatch(PatchPresence.present("S"), PatchPresence.present(["a", "b"] as Set), PatchPresence.omitted(), PatchPresence.omitted())))
    "patch-dto-nested-container-atomic-map" | "address-street-scores" | ArticleWithContainerAddressPatch.class | new ArticleWithContainerAddressPatch("1", PatchPresence.present(new AddressWithContainersPatch(PatchPresence.present("S"), PatchPresence.omitted(), PatchPresence.omitted(), PatchPresence.present([x: 1, y: 2]))))
    "patch-dto-nested-invalid-shape-omitted" | "identity-only" | ArticleWithMixedAddressPatch.class | new ArticleWithMixedAddressPatch("1", PatchPresence.omitted())
    "patch-dto-javabean-nested-partial" | "address-street" | MutableArticleWithAddressPatch.class | new MutableArticleWithAddressPatch("1", PatchPresence.present(new MutableAddressPatch(PatchPresence.present("S"), PatchPresence.omitted())))
    "patch-dto-meta-recursive-resource-and-relationship" | "meta-source-note-author-meta" | ArticleWithMetaPatch.class | new ArticleWithMetaPatch("1", PatchPresence.present("T"), PatchPresence.present(ResourceIdentifier.of("people", "p1")), PatchPresence.present(new ArticleMetaPatch(PatchPresence.present("cms"), PatchPresence.present("n"))), PatchPresence.present(new AuthorMeta("Alice")))
    "patch-dto-meta-atomic-map" | "meta-map-with-relationship" | ArticleWithMapMetaPatch.class | new ArticleWithMapMetaPatch("1", PatchPresence.present("T"), identifierPresence(ResourceIdentifier.of("people", "p1")), mapPresence(objectMap("source", "cms")), mapPresence(objectMap("displayName", "Alice")))
    "patch-dto-meta-optional-object" | "title-with-meta-source-note" | ArticleWithOptionalMetaPatch.class | new ArticleWithOptionalMetaPatch("1", PatchPresence.present("T"), PatchPresence.omitted(), PatchPresence.present(Optional.of(new ArticleMeta("cms", "n"))))
    "patch-dto-meta-empty-object" | "meta-empty-object" | ArticleWithMetaPatch.class | new ArticleWithMetaPatch("1", PatchPresence.present("T"), PatchPresence.omitted(), PatchPresence.present(new ArticleMetaPatch(PatchPresence.omitted(), PatchPresence.omitted())), PatchPresence.omitted())
    "patch-dto-meta-omitted" | "identity-only" | ArticleWithMetaPatch.class | new ArticleWithMetaPatch("1", PatchPresence.omitted(), PatchPresence.omitted(), PatchPresence.omitted(), PatchPresence.omitted())
    "patch-dto-whole-linkage-to-one-identifier-meta" | "author-identifier-meta" | ArticlePatch.class | expectedArticleWithAuthorIdentifierMeta()
    "patch-dto-whole-linkage-to-many-identifier-meta" | "comments-identifier-meta" | ArticlePatch.class | new ArticlePatch("1", PatchPresence.omitted(), PatchPresence.omitted(), PatchPresence.omitted(), PatchPresence.present([
      identifier("comments", "c1", objectMap("pinned", true)),
      ResourceIdentifier.of("comments", "c2")
    ]))
    "patch-dto-wrapper-whole-linkage-to-one-identifier-meta" | "author-identifier-meta" | ArticleWithRelationshipLinkagePatch.class | new ArticleWithRelationshipLinkagePatch("1", PatchPresence.omitted(), authorLinkagePresence(new RelationshipLinkage(identifier("people", "p1", objectMap("role", "editor")), new AuthorIdMeta("editor"))), PatchPresence.omitted())
    "patch-dto-wrapper-whole-linkage-to-many-identifier-meta" | "comments-identifier-meta" | ArticleWithRelationshipLinkagePatch.class | expectedWrapperWithCommentsIdentifierMeta()
    "patch-dto-object-meta-atomic" | "meta-source-only" | io.github.kazemek.jsonapi.fixtures.domainpatch.WholeMetaTargetFixtures.ObjectMetaPatch.class | new io.github.kazemek.jsonapi.fixtures.domainpatch.WholeMetaTargetFixtures.ObjectMetaPatch("1", PatchPresence.present([source: "cms"]))
  }

  @Unroll
  def "rejects typed patch dto #id with a mapping diagnostic"() {
    given:
    def json = TestFixtureResources.readCorpusUtf8("patch/${resource}.json")
    def reader = JsonApiJackson3.patchDtoReader(JsonMapper.builder().build())

    when:
    reader.readValue(json, targetType)

    then:
    def ex = thrown(JsonApiMappingException)
    ex.diagnostic() == expectedDiagnostic
    ex.propertyPath() == expectedPath

    where:
    id | resource | targetType | expectedDiagnostic | expectedPath
    "patch-dto-resource-type-mismatch" | "resource-type-mismatch" | ArticlePatch.class | MappingDiagnostic.RESOURCE_TYPE_MISMATCH | "/type"
    "patch-dto-unknown-attribute" | "attribute-unknown-member" | ArticlePatch.class | MappingDiagnostic.UNKNOWN_PATCH_MEMBER | "/attributes/bogus"
    "patch-dto-unknown-relationship" | "relationship-unknown-member" | ArticlePatch.class | MappingDiagnostic.UNKNOWN_PATCH_MEMBER | "/relationships/bogus"
    "patch-dto-identifier-conversion-failure" | "things-identifier-not-an-integer" | IntIdPatch.class | MappingDiagnostic.IDENTIFIER_CONVERSION_FAILED | "/id"
    "patch-dto-relationship-cardinality-mismatch" | "relationship-cardinality-mismatch" | ArticlePatch.class | MappingDiagnostic.RELATIONSHIP_CARDINALITY_MISMATCH | "/relationships/author/data"
    "patch-dto-declaration-non-patch-presence" | "title-only" | NonPatchPresencePatch.class | MappingDiagnostic.INVALID_PATCH_PROPERTY_TYPE | "/attributes/title"
    "patch-dto-declaration-raw-patch-presence" | "title-only" | RawPatchPresencePatch.class | MappingDiagnostic.INVALID_PATCH_PROPERTY_TYPE | "/attributes/title"
    "patch-dto-declaration-direct-present" | "title-only" | DirectPresentPatch.class | MappingDiagnostic.INVALID_PATCH_PROPERTY_TYPE | "/attributes/title"
    "patch-dto-declaration-unannotated-member" | "note-attribute" | UnannotatedPatch.class | MappingDiagnostic.UNKNOWN_PATCH_MEMBER | "/attributes/note"
    "patch-dto-declaration-presence-id" | "identity-only" | PresenceIdPatch.class | MappingDiagnostic.INVALID_PATCH_PROPERTY_TYPE | "/id"
    "patch-dto-nested-non-object-wire" | "address-scalar-wire" | ArticleWithAddressPatch.class | MappingDiagnostic.UNSUPPORTED_ATTRIBUTE_VALUE | "/attributes/address"
    "patch-dto-nested-unknown-member" | "address-unknown-member" | ArticleWithAddressPatch.class | MappingDiagnostic.UNKNOWN_PATCH_MEMBER | "/attributes/address/bogus"
    "patch-dto-nested-declaration-mixed" | "address-street-city" | ArticleWithMixedAddressPatch.class | MappingDiagnostic.INVALID_PATCH_PROPERTY_TYPE | "/attributes/address"
    "patch-dto-nested-declaration-raw" | "address-street-city" | ArticleWithRawAddressPatch.class | MappingDiagnostic.INVALID_PATCH_PROPERTY_TYPE | "/attributes/address"
    "patch-dto-nested-declaration-direct-present" | "address-street-city" | ArticleWithDirectPresentAddressPatch.class | MappingDiagnostic.INVALID_PATCH_PROPERTY_TYPE | "/attributes/address"
    "patch-dto-unknown-resource-meta" | "title-with-meta-source" | io.github.kazemek.jsonapi.fixtures.domainpatch.WholeMetaTargetFixtures.NoMetaPatch.class | MappingDiagnostic.UNKNOWN_PATCH_MEMBER | "/meta"
    "patch-dto-unknown-relationship-meta" | "author-meta-with-data" | io.github.kazemek.jsonapi.fixtures.domainpatch.WholeMetaTargetFixtures.NoRelMetaPatch.class | MappingDiagnostic.UNKNOWN_PATCH_MEMBER | "/relationships/author/meta"
    "patch-dto-scalar-meta-target" | "title-with-meta-source" | io.github.kazemek.jsonapi.fixtures.domainpatch.WholeMetaTargetFixtures.ScalarMetaPatch.class | MappingDiagnostic.INVALID_META_TARGET | "/meta"
    "patch-dto-nested-presence-meta-target" | "title-with-meta-source" | io.github.kazemek.jsonapi.fixtures.domainpatch.WholeMetaTargetFixtures.NestedPresenceMetaPatch.class | MappingDiagnostic.INVALID_META_TARGET | "/meta"
    "patch-dto-uuid-meta-target" | "identity-only" | io.github.kazemek.jsonapi.fixtures.domainpatch.WholeMetaTargetFixtures.UuidMetaPatch.class | MappingDiagnostic.INVALID_META_TARGET | "/meta"
    "patch-dto-meta-conversion-failure" | "meta-source-object" | ArticleWithMetaPatch.class | MappingDiagnostic.UNSUPPORTED_ATTRIBUTE_VALUE | "/meta/source"
  }

  @Unroll
  def "rejects typed patch dto #id during document validation"() {
    given:
    def reader = JsonApiJackson3.patchDtoReader(JsonMapper.builder().build())
    def json = TestFixtureResources.readCorpusUtf8("patch/${resource}.json")

    when:
    reader.readValue(json, targetType)

    then:
    def ex = thrown(JsonApiDocumentReadException)
    ex.ruleCode() == expectedRule
    ex.jsonPointer() == expectedPointer

    where:
    id | resource | targetType | expectedRule | expectedPointer
    "patch-dto-wrong-primary-shape" | "wrong-primary-shape" | ArticlePatch.class | ValidationRuleCode.UPDATE_REQUIRES_SINGLE_RESOURCE | "/data"
  }

  def "generic PATCH DTO binds through a parameterized JavaType"() {
    given:
    def mapper = JsonMapper.builder().build()
    def reader = JsonApiJackson3.patchDtoReader(mapper)
    def javaType = mapper.typeFactory.constructParametricType(GenericPatch, String)
    def json = '{"data":{"type":"articles","id":"42","attributes":{"title":"Hello"}}}'

    when:
    def dto = reader.readValue(json, javaType)

    then:
    dto instanceof GenericPatch
    dto.id() == "42"
    dto.title() == PatchPresence.present("Hello")
  }

  def "generic PATCH DTO binds through fromDocument with a parameterized JavaType"() {
    given:
    def mapper = JsonMapper.builder().build()
    def reader = JsonApiJackson3.patchDtoReader(mapper)
    def javaType = mapper.typeFactory.constructParametricType(GenericPatch, String)
    def document = decodeUpdateDocument(
        '{"data":{"type":"articles","id":"42","attributes":{"title":"Hi"}}}')

    when:
    def dto = reader.fromDocument(document, javaType)

    then:
    dto instanceof GenericPatch
    dto.id() == "42"
    dto.title() == PatchPresence.present("Hi")
  }

  def "to-many PatchPresence<List<AuthorId>> converts with a custom linkage mapper"() {
    given:
    def mapper = { RelationshipData data, JavaType target ->
      if (data instanceof RelationshipData.IdentifierCollectionLinkage) {
        return data.identifiers().collect { id -> new AuthorId(id.type(), id.id()) }
      }
      return null
    } as RelationshipLinkageMapper
    def reader = JsonApiJackson3.patchDtoReader(
        JsonMapper.builder().build(),
        ValidationContext.defaults(),
        IdentifierConverter.defaults(),
        [(AuthorId): mapper])
    def nonEmpty =
        '{"data":{"type":"articles","id":"1","relationships":{"contributors":{"data":[{"type":"authors","id":"a1"},{"type":"authors","id":"a2"}]}}}}'
    def empty =
        '{"data":{"type":"articles","id":"1","relationships":{"contributors":{"data":[]}}}}'

    when:
    def dto = reader.readValue(nonEmpty, AuthorListPatch)
    def emptyDto = reader.readValue(empty, AuthorListPatch)

    then:
    dto.id == "1"
    dto.contributors ==
        PatchPresence.present([
          new AuthorId("authors", "a1"),
          new AuthorId("authors", "a2")
        ])
    emptyDto.contributors == PatchPresence.present([])
  }

  def "Optional inner relationship binds null linkage as Present(Optional.empty())"() {
    given:
    def mapper = { RelationshipData data, JavaType target ->
      if (data instanceof RelationshipData.SingleLinkage) {
        def identifier = ((RelationshipData.SingleLinkage) data).identifier()
        return new AuthorId(identifier.type(), identifier.id())
      }
      return null
    } as RelationshipLinkageMapper
    def reader = JsonApiJackson3.patchDtoReader(
        JsonMapper.builder().build(),
        ValidationContext.defaults(),
        IdentifierConverter.defaults(),
        [(AuthorId): mapper])
    def json = '{"data":{"type":"articles","id":"1","relationships":{"author":{"data":null}}}}'

    when:
    def dto = reader.readValue(json, AuthorOptionalPatch)

    then:
    dto.id == "1"
    dto.author == PatchPresence.present(Optional.empty())
  }

  def "PatchPresence<Map<String,Value>> attribute binds"() {
    given:
    def reader = JsonApiJackson3.patchDtoReader(JsonMapper.builder().build())
    def json = '{"data":{"type":"articles","id":"1","attributes":{"tags":{"a":{"label":"A"}}}}}'

    when:
    def dto = reader.readValue(json, MapPatch)

    then:
    dto.id == "1"
    dto.tags == PatchPresence.present([a: new Value("A")])
  }

  def "property-level @JsonDeserialize on a PatchPresence member is rejected"() {
    given:
    def reader = JsonApiJackson3.patchDtoReader(JsonMapper.builder().build())
    def json = '{"data":{"type":"articles","id":"1","attributes":{"title":"T"}}}'

    when:
    reader.readValue(json, WrapperDeserializePatch)

    then:
    def ex = thrown(JsonApiMappingException)
    ex.diagnostic() == MappingDiagnostic.INVALID_PATCH_PROPERTY_TYPE
    ex.propertyPath() == "/attributes/title"
  }

  def "property-level @JsonSerialize on a PatchPresence member is rejected"() {
    given:
    def reader = JsonApiJackson3.patchDtoReader(JsonMapper.builder().build())
    def json = '{"data":{"type":"articles","id":"1","attributes":{"title":"T"}}}'

    when:
    reader.readValue(json, WrapperSerializePatch)

    then:
    def ex = thrown(JsonApiMappingException)
    ex.diagnostic() == MappingDiagnostic.INVALID_PATCH_PROPERTY_TYPE
    ex.propertyPath() == "/attributes/title"
  }

  def "inner type customization still works"() {
    given:
    def reader = JsonApiJackson3.patchDtoReader(JsonMapper.builder().build())
    def json = '{"data":{"type":"articles","id":"1","attributes":{"title":"hello"}}}'

    when:
    def dto = reader.readValue(json, LoudPatch)

    then:
    dto.id == "1"
    dto.title == PatchPresence.present(new LoudValue("HELLO"))
  }

  def "deserializes an atomic inner type once during DTO construction"() {
    given:
    def reader = JsonApiJackson3.patchDtoReader(JsonMapper.builder().build())
    def json = '{"data":{"type":"single-pass-things","id":"1","attributes":{"title":"hello"}}}'

    when:
    def dto = reader.readValue(json, SinglePassPatch)

    then:
    dto.title == PatchPresence.present(new SinglePassValue("hello!"))
  }

  def "property-level @JsonDeserialize(converter) on a PatchPresence member is rejected"() {
    given:
    def reader = JsonApiJackson3.patchDtoReader(JsonMapper.builder().build())
    def json = '{"data":{"type":"articles","id":"1","attributes":{"title":"T"}}}'

    when:
    reader.readValue(json, WrapperConverterDeserializePatch)

    then:
    def ex = thrown(JsonApiMappingException)
    ex.diagnostic() == MappingDiagnostic.INVALID_PATCH_PROPERTY_TYPE
    ex.propertyPath() == "/attributes/title"
  }

  def "property-level @JsonSerialize(converter) on a PatchPresence member is rejected"() {
    given:
    def reader = JsonApiJackson3.patchDtoReader(JsonMapper.builder().build())
    def json = '{"data":{"type":"articles","id":"1","attributes":{"title":"T"}}}'

    when:
    reader.readValue(json, WrapperConverterSerializePatch)

    then:
    def ex = thrown(JsonApiMappingException)
    ex.diagnostic() == MappingDiagnostic.INVALID_PATCH_PROPERTY_TYPE
    ex.propertyPath() == "/attributes/title"
  }

  def "mix-in wrapper @JsonDeserialize on a PatchPresence member is rejected"() {
    given:
    def mapper = JsonMapper.builder()
        .addMixIn(MixinTargetPatch, MixInWithDeserializer)
        .build()
    def reader = JsonApiJackson3.patchDtoReader(mapper)
    def json = '{"data":{"type":"articles","id":"1","attributes":{"title":"T"}}}'

    when:
    reader.readValue(json, MixinTargetPatch)

    then:
    def ex = thrown(JsonApiMappingException)
    ex.diagnostic() == MappingDiagnostic.INVALID_PATCH_PROPERTY_TYPE
    ex.propertyPath() == "/attributes/title"
  }

  def "naming strategy is honored for PATCH DTO members"() {
    given:
    def mapper = JsonMapper.builder()
        .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
        .build()
    def reader = JsonApiJackson3.patchDtoReader(mapper)
    def json = '{"data":{"type":"articles","id":"1","attributes":{"display_title":"T"}}}'

    when:
    def dto = reader.readValue(json, SnakePatch)

    then:
    dto.id == "1"
    dto.displayTitle == PatchPresence.present("T")
  }

  def "presence tri-state is invariant to UPPER_CAMEL_CASE naming"() {
    given:
    def mapper = JsonMapper.builder()
        .propertyNamingStrategy(PropertyNamingStrategies.UPPER_CAMEL_CASE)
        .build()
    def reader = JsonApiJackson3.patchDtoReader(mapper)
    def withNulls = '{"data":{"type":"articles","id":"1","attributes":{"Title":"T","Body":null}}}'
    def withOptional = '{"data":{"type":"articles","id":"1","attributes":{"Title":"T","Subtitle":null}}}'

    when:
    def dto = reader.readValue(withNulls, MixedPatch)
    def optionalDto = reader.readValue(withOptional, MixedPatch)

    then:
    dto.id == "1"
    dto.title == PatchPresence.present("T")
    dto.body == PatchPresence.present(null)
    dto.subtitle.isOmitted()
    optionalDto.subtitle == PatchPresence.present(Optional.empty())
  }

  def "presence tri-state is invariant to UPPER_SNAKE_CASE naming"() {
    given:
    def mapper = JsonMapper.builder()
        .propertyNamingStrategy(PropertyNamingStrategies.UPPER_SNAKE_CASE)
        .build()
    def reader = JsonApiJackson3.patchDtoReader(mapper)
    def withNulls = '{"data":{"type":"articles","id":"1","attributes":{"TITLE":"T","BODY":null}}}'
    def withOptional = '{"data":{"type":"articles","id":"1","attributes":{"TITLE":"T","SUBTITLE":null}}}'

    when:
    def dto = reader.readValue(withNulls, MixedPatch)
    def optionalDto = reader.readValue(withOptional, MixedPatch)

    then:
    dto.id == "1"
    dto.title == PatchPresence.present("T")
    dto.body == PatchPresence.present(null)
    dto.subtitle.isOmitted()
    optionalDto.subtitle == PatchPresence.present(Optional.empty())
  }

  def "omitted, present-value, and present-null are distinct"() {
    given:
    def reader = JsonApiJackson3.patchDtoReader(JsonMapper.builder().build())
    def allJson =
        '{"data":{"type":"articles","id":"1","attributes":{"title":"T","subtitle":"S","body":null}}}'
    def omittedJson = '{"data":{"type":"articles","id":"1","attributes":{"title":"T"}}}'

    when:
    def all = reader.readValue(allJson, MixedPatch)
    def omitted = reader.readValue(omittedJson, MixedPatch)

    then:
    all.title == PatchPresence.present("T")
    all.subtitle == PatchPresence.present(Optional.of("S"))
    all.body == PatchPresence.present(null)
    !all.body.isOmitted()
    omitted.title == PatchPresence.present("T")
    omitted.subtitle.isOmitted()
    omitted.body.isOmitted()
  }

  def "tri-state survives caller NON_ABSENT and NON_EMPTY inclusion"() {
    given:
    def nonAbsent = readerFor(JsonInclude.Include.NON_ABSENT)
    def nonEmpty = readerFor(JsonInclude.Include.NON_EMPTY)
    def withNull = '{"data":{"type":"articles","id":"1","attributes":{"title":"T","subtitle":null}}}'
    def withNullOnly = '{"data":{"type":"articles","id":"1","attributes":{"title":null}}}'

    when:
    def absentDto = nonAbsent.readValue(withNull, MixedPatch)
    def emptyDto = nonEmpty.readValue(withNull, MixedPatch)

    then:
    absentDto.title == PatchPresence.present("T")
    absentDto.subtitle == PatchPresence.present(Optional.empty())
    emptyDto.title == PatchPresence.present("T")
    emptyDto.subtitle == PatchPresence.present(Optional.empty())

    when:
    def absentNull = nonAbsent.readValue(withNullOnly, MixedPatch)
    def emptyNull = nonEmpty.readValue(withNullOnly, MixedPatch)

    then:
    absentNull.title == PatchPresence.present(null)
    absentNull.subtitle.isOmitted()
    emptyNull.title == PatchPresence.present(null)
    emptyNull.subtitle.isOmitted()
  }

  def "patchDtoReader derives a mapper and never mutates the caller mapper"() {
    given:
    def builder = JsonMapper.builder()
    def base = builder.build()
    def before = base.serializationConfig().getDefaultPropertyInclusion().getValueInclusion()
    def json = '{"data":{"type":"articles","id":"1","attributes":{"title":"T"}}}'

    when:
    def reader1 = JsonApiJackson3.patchDtoReader(base)
    def reader2 = JsonApiJackson3.patchDtoReader(base)
    def dto = reader1.readValue(json, MixedPatch)

    then:
    base.serializationConfig().getDefaultPropertyInclusion().getValueInclusion() == before
    dto.title == PatchPresence.present("T")
    reader2.readValue(json, MixedPatch).title == PatchPresence.present("T")
  }

  def "unknown supplied member is reported before a known member's invalid conversion"() {
    given:
    def reader = JsonApiJackson3.patchDtoReader(JsonMapper.builder().build())
    def json =
        '{"data":{"type":"articles","id":"1","attributes":{"bogus":"x","count":"not-an-int"}}}'

    when:
    reader.readValue(json, CountPatch)

    then:
    def ex = thrown(JsonApiMappingException)
    ex.diagnostic() == MappingDiagnostic.UNKNOWN_PATCH_MEMBER
    ex.propertyPath() == "/attributes/bogus"
  }

  def "unknown supplied relationship names are escaped as JSON Pointer segments"() {
    given:
    def reader = JsonApiJackson3.patchDtoReader(JsonMapper.builder().build())

    when:
    reader.readValue(
        '{"data":{"type":"articles","id":"1","relationships":{"bogus":{"data":null}}}}',
        TitlePatch)

    then:
    def ex = thrown(JsonApiMappingException)
    ex.diagnostic() == MappingDiagnostic.UNKNOWN_PATCH_MEMBER
    // Escaping itself is exercised end-to-end by the structured PATCH specs: top-level wire
    // member names are namespace-validated, so pointer-sensitive characters can only reach a
    // diagnostic location inside attribute values.
    ex.propertyPath() == "/relationships/bogus"
  }

  def "unknown supplied relationship is reported before a known relationship conversion"() {
    given:
    def reader = JsonApiJackson3.patchDtoReader(JsonMapper.builder().build())
    def json =
        '{"data":{"type":"articles","id":"1","relationships":{"bogus":{"data":null},"author":{"data":[]}}}}'

    when:
    reader.readValue(json, ArticlePatch)

    then:
    def ex = thrown(JsonApiMappingException)
    ex.diagnostic() == MappingDiagnostic.UNKNOWN_PATCH_MEMBER
    ex.propertyPath() == "/relationships/bogus"
  }

  def "unknown relationship meta diagnostic names the relationship wire name"() {
    given:
    def reader = JsonApiJackson3.patchDtoReader(JsonMapper.builder().build())
    def json =
        '{"data":{"type":"articles","id":"1","relationships":{"author":{"data":{"type":"people","id":"p1"},"meta":{"displayName":"Alice"}}}}}'

    when:
    reader.readValue(json, io.github.kazemek.jsonapi.fixtures.domainpatch.WholeMetaTargetFixtures.NoRelMetaPatch)

    then:
    def e = thrown(JsonApiMappingException)
    e.diagnostic == MappingDiagnostic.UNKNOWN_PATCH_MEMBER
    e.propertyPath() == "/relationships/author/meta"
    e.message.contains("Unknown supplied relationship meta 'author'")
    !e.message.contains("author/meta")
  }

  def "null dtoType reports dtoType in the message"() {
    given:
    def reader = JsonApiJackson3.patchDtoReader(JsonMapper.builder().build())
    def json = '{"data":{"type":"articles","id":"1"}}'

    when:
    reader.readValue(json, (Class) null)

    then:
    def ex = thrown(NullPointerException)
    ex.message == "dtoType"
  }

  def "fromDocument binds an already-validated document without re-validation"() {
    given:
    def reader = JsonApiJackson3.patchDtoReader(JsonMapper.builder().build())
    def document = decodeUpdateDocument(
        '{"data":{"type":"articles","id":"1","attributes":{"title":"Hello"}}}')

    when:
    def dto = reader.fromDocument(document, ArticlePatch)

    then:
    dto.id == "1"
    dto.title == PatchPresence.present("Hello")
    dto.body.isOmitted()
    dto.author.isOmitted()
    dto.comments.isOmitted()
  }

  def "fromDocument skips a supplied mapped relationship without data as Omitted"() {
    given:
    def reader = JsonApiJackson3.patchDtoReader(JsonMapper.builder().build())
    def document = new JsonApiDocument(
        new DocumentData.SingleResource(
        new ResourceObject(
        "articles",
        "1",
        null,
        null,
        Relationships.ofRelationships(
        [author: new Relationship(null, null, Meta.of([note: "x"]), Map.of())]),
        null,
        null,
        Map.of())),
        null, null, null, null, null, Map.of())

    when:
    def dto = reader.fromDocument(document, ArticlePatch)

    then:
    dto.id == "1"
    dto.author.isOmitted()
    dto.title.isOmitted()
  }

  def "byte array, stream, and parser entry points bind and leave caller sources open"() {
    given:
    def mapper = JsonMapper.builder().build()
    def reader = JsonApiJackson3.patchDtoReader(mapper)
    def json = '{"data":{"type":"articles","id":"1","attributes":{"title":"Hello"}}}'
    def bytes = json.bytes
    def stream = new CloseTrackingInputStream(new ByteArrayInputStream(bytes))
    def parser = mapper.createParser(bytes)

    when:
    def fromBytes = reader.readValue(bytes, ArticlePatch)
    def fromStream = reader.readValue(stream, ArticlePatch)
    def fromParser = reader.readValue(parser, ArticlePatch)

    then:
    fromBytes.title == PatchPresence.present("Hello")
    fromStream.title == PatchPresence.present("Hello")
    fromParser.title == PatchPresence.present("Hello")
    !stream.closed
    !parser.closed

    cleanup:
    parser?.close()
  }

  def "JavaType stream and parser entry points bind"() {
    given:
    def mapper = JsonMapper.builder().build()
    def reader = JsonApiJackson3.patchDtoReader(mapper)
    def javaType = mapper.constructType(ArticlePatch)
    def bytes = '{"data":{"type":"articles","id":"1","attributes":{"title":"T"}}}'.bytes
    def stream = new CloseTrackingInputStream(new ByteArrayInputStream(bytes))
    def parser = mapper.createParser(bytes)

    when:
    def fromStream = reader.readValue(stream, javaType)
    def fromParser = reader.readValue(parser, javaType)

    then:
    fromStream instanceof ArticlePatch
    ((ArticlePatch) fromStream).title == PatchPresence.present("T")
    fromParser instanceof ArticlePatch
    !stream.closed
    !parser.closed

    cleanup:
    parser?.close()
  }

  def "fromDocument rejects null and non-single-resource primary data"() {
    given:
    def reader = JsonApiJackson3.patchDtoReader(JsonMapper.builder().build())
    def metaOnly = new JsonApiDocument(
        null, null, Meta.of([note: "x"]), null, null, null, Map.of())

    when:
    reader.fromDocument(null, ArticlePatch)

    then:
    thrown(NullPointerException)

    when:
    reader.fromDocument(metaOnly, ArticlePatch)

    then:
    thrown(IllegalArgumentException)

    when:
    reader.fromDocument(
        new JsonApiDocument(DocumentData.NullData.INSTANCE, null, null, null, null, null, Map.of()),
        ArticlePatch)

    then:
    thrown(IllegalArgumentException)
  }

  def "mapper factory overloads bind successfully"() {
    given:
    def json = '{"data":{"type":"articles","id":"1","attributes":{"title":"Hello"}}}'
    def mapper = JsonMapper.builder().build()

    when:
    def fromMapper = JsonApiJackson3.patchDtoReader(mapper).readValue(json, ArticlePatch)
    def fromJavaType = JsonApiJackson3.patchDtoReader(mapper)
        .readValue(json, mapper.constructType(ArticlePatch))

    then:
    fromMapper.title == PatchPresence.present("Hello")
    fromJavaType instanceof ArticlePatch
    ((ArticlePatch) fromJavaType).title == PatchPresence.present("Hello")
  }

  def "named IdentifierConverter is used for identity"() {
    given:
    def converter = new IdentifierConverter() {
          @Override
          String convert(Object value) {
            return String.valueOf(value)
          }

          @Override
          Object parse(String wire) {
            return "parsed-" + wire
          }
        }
    def reader = JsonApiJackson3.patchDtoReader(
        JsonMapper.builder().build(), ValidationContext.defaults(), converter)
    def json = '{"data":{"type":"articles","id":"1","attributes":{"title":"T"}}}'

    when:
    def dto = reader.readValue(json, ArticlePatch)

    then:
    dto.id == "parsed-1"
  }

  def "endpoint identity mismatch fails validation on readValue"() {
    given:
    def context = ValidationContext.defaults()
        .withExpectedEndpointIdentity(new EndpointIdentity("articles", "99"))
    def reader = JsonApiJackson3.patchDtoReader(JsonMapper.builder().build(), context)
    def json = '{"data":{"type":"articles","id":"1","attributes":{"title":"T"}}}'

    when:
    reader.readValue(json, ArticlePatch)

    then:
    thrown(JsonApiDocumentReadException)
  }

  private static JsonApiPatchDtoReader readerFor(JsonInclude.Include include) {
    def builder = JsonMapper.builder()
    builder.changeDefaultPropertyInclusion({ value ->
      value.withValueInclusion(include).withContentInclusion(include)
    })
    JsonApiJackson3.patchDtoReader(builder.build())
  }

  private static Map<String, Object> objectMap(String key, Object value) {
    return [(key): value] as Map<String, Object>
  }

  private static ResourceIdentifier identifier(String type, String id, Map<String, Object> meta) {
    return new ResourceIdentifier(type, id, null, Meta.of(meta), Map.of())
  }

  private static PatchPresence<Map<String, Object>> mapPresence(Map<String, Object> value) {
    return PatchPresence.present(value)
  }

  private static PatchPresence<ResourceIdentifier> identifierPresence(ResourceIdentifier value) {
    return PatchPresence.present(value)
  }

  private static PatchPresence<String> omittedString() {
    return PatchPresence.omitted()
  }

  private static PatchPresence<List<ResourceIdentifier>> omittedIdentifierList() {
    return PatchPresence.omitted()
  }

  private static PatchPresence<RelationshipLinkage<ResourceIdentifier, AuthorIdMeta>> omittedAuthorLinkage() {
    return PatchPresence.omitted()
  }

  private static ArticlePatch expectedArticleWithAuthorIdentifierMeta() {
    return new ArticlePatch(
        "1",
        omittedString(),
        omittedString(),
        identifierPresence(authorIdentifierWithMeta()),
        omittedIdentifierList())
  }

  private static ArticleWithRelationshipLinkagePatch expectedWrapperWithCommentsIdentifierMeta() {
    return new ArticleWithRelationshipLinkagePatch(
        "1",
        omittedString(),
        omittedAuthorLinkage(),
        commentLinkages(
        commentLinkage(commentIdentifierWithMeta(), new CommentIdMeta(true)),
        commentLinkage(ResourceIdentifier.of("comments", "c2"), null)))
  }

  private static ResourceIdentifier authorIdentifierWithMeta() {
    return identifier("people", "p1", objectMap("role", "editor"))
  }

  private static ResourceIdentifier commentIdentifierWithMeta() {
    return identifier("comments", "c1", objectMap("pinned", true))
  }

  private static PatchPresence<RelationshipLinkage<ResourceIdentifier, AuthorIdMeta>> authorLinkagePresence(
      RelationshipLinkage<ResourceIdentifier, AuthorIdMeta> value) {
    return PatchPresence.present(value)
  }

  private static RelationshipLinkage<ResourceIdentifier, CommentIdMeta> commentLinkage(
      ResourceIdentifier target, CommentIdMeta meta) {
    return new RelationshipLinkage<>(target, meta)
  }

  private static PatchPresence<List<RelationshipLinkage<ResourceIdentifier, CommentIdMeta>>> commentLinkages(
      RelationshipLinkage<ResourceIdentifier, CommentIdMeta> first,
      RelationshipLinkage<ResourceIdentifier, CommentIdMeta> second) {
    return PatchPresence.present(Arrays.asList(first, second))
  }

  private static JsonApiDocument decodeUpdateDocument(String json) {
    return JsonApiJackson3.reader(
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

  @JsonApiResource(type = "articles")
  static class AuthorListPatch {
    @JsonApiId String id
    @JsonApiRelationship PatchPresence<List<AuthorId>> contributors
  }

  @JsonApiResource(type = "articles")
  static class AuthorOptionalPatch {
    @JsonApiId String id
    @JsonApiRelationship PatchPresence<Optional<AuthorId>> author
  }

  @JsonApiResource(type = "articles")
  static class MapPatch {
    @JsonApiId String id
    @JsonApiAttribute PatchPresence<Map<String, Value>> tags
  }

  @JsonApiResource(type = "articles")
  static class WrapperDeserializePatch {
    @JsonApiId String id
    @JsonDeserialize(using = UppercaseDeserializer)
    @JsonApiAttribute
    PatchPresence<String> title
  }

  @JsonApiResource(type = "articles")
  static class WrapperSerializePatch {
    @JsonApiId String id
    @JsonSerialize(using = MarkerSerializer)
    @JsonApiAttribute
    PatchPresence<String> title
  }

  @JsonApiResource(type = "articles")
  static class WrapperConverterDeserializePatch {
    @JsonApiId String id
    @JsonDeserialize(converter = IdentityConverter)
    @JsonApiAttribute
    PatchPresence<String> title
  }

  @JsonApiResource(type = "articles")
  static class WrapperConverterSerializePatch {
    @JsonApiId String id
    @JsonSerialize(converter = IdentityConverter)
    @JsonApiAttribute
    PatchPresence<String> title
  }

  @JsonApiResource(type = "articles")
  static class MixinTargetPatch {
    @JsonApiId String id
    @JsonApiAttribute PatchPresence<String> title
  }

  static abstract class MixInWithDeserializer {
    @JsonDeserialize(using = UppercaseDeserializer)
    abstract PatchPresence<String> getTitle()
  }

  @JsonApiResource(type = "articles")
  static class LoudPatch {
    @JsonApiId String id
    @JsonApiAttribute PatchPresence<LoudValue> title
  }

  @JsonApiResource(type = "single-pass-things")
  static class SinglePassPatch {
    @JsonApiId String id
    @JsonApiAttribute PatchPresence<SinglePassValue> title
  }

  @JsonApiResource(type = "articles")
  static class SnakePatch {
    @JsonApiId String id
    @JsonApiAttribute PatchPresence<String> displayTitle
  }

  @JsonApiResource(type = "articles")
  static class MixedPatch {
    @JsonApiId String id
    @JsonApiAttribute PatchPresence<String> title
    @JsonApiAttribute PatchPresence<Optional<String>> subtitle
    @JsonApiAttribute PatchPresence<String> body
  }

  @JsonApiResource(type = "articles")
  static class CountPatch {
    @JsonApiId String id
    @JsonApiAttribute PatchPresence<Integer> count
  }

  @JsonApiResource(type = "articles")
  static class TitlePatch {
    @JsonApiId String id
    @JsonApiAttribute PatchPresence<String> title
  }

  static class AuthorId {
    String type
    String id

    AuthorId() {}

    AuthorId(String type, String id) {
      this.type = type
      this.id = id
    }

    boolean equals(Object other) {
      other instanceof AuthorId && type == other.type && id == other.id
    }

    int hashCode() {
      Objects.hash(type, id)
    }
  }

  static class Value {
    String label

    Value() {}

    Value(String label) {
      this.label = label
    }

    boolean equals(Object other) {
      other instanceof Value && label == other.label
    }

    int hashCode() {
      Objects.hash(label)
    }
  }

  static class MarkerSerializer extends ValueSerializer<Object> {
    @Override
    void serialize(Object value, JsonGenerator gen, SerializationContext ctxt) {
      gen.writeString("marker")
    }
  }

  @JsonDeserialize(using = LoudDeserializer)
  @JsonSerialize(using = LoudSerializer)
  static class LoudValue {
    final String value

    LoudValue(String value) {
      this.value = value
    }

    boolean equals(Object other) {
      other instanceof LoudValue && value == other.value
    }

    int hashCode() {
      Objects.hash(value)
    }
  }

  static class LoudDeserializer extends StdDeserializer<LoudValue> {
    LoudDeserializer() {
      super(LoudValue)
    }

    @Override
    LoudValue deserialize(JsonParser parser, DeserializationContext context) {
      return new LoudValue(parser.getValueAsString().toUpperCase())
    }
  }

  static class LoudSerializer extends ValueSerializer<LoudValue> {
    @Override
    void serialize(LoudValue value, JsonGenerator gen, SerializationContext ctxt) {
      gen.writeString(value.value)
    }
  }

  @JsonDeserialize(using = SinglePassDeserializer)
  @JsonSerialize(using = SinglePassSerializer)
  static class SinglePassValue {
    final String value

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

  static class SinglePassSerializer extends ValueSerializer<SinglePassValue> {
    @Override
    void serialize(SinglePassValue value, JsonGenerator generator, SerializationContext context) {
      generator.writeString(value.value)
    }
  }

  static class IdentityConverter implements Converter<Object, Object> {
    @Override
    Object convert(DeserializationContext ctxt, Object value) {
      return value
    }

    @Override
    Object convert(SerializationContext ctxt, Object value) {
      return value
    }

    @Override
    JavaType getInputType(TypeFactory typeFactory) {
      return typeFactory.constructType(Object)
    }

    @Override
    JavaType getOutputType(TypeFactory typeFactory) {
      return typeFactory.constructType(Object)
    }
  }
}
