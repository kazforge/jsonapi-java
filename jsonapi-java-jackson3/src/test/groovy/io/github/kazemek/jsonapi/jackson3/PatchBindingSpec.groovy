package io.github.kazemek.jsonapi.jackson3

import com.fasterxml.jackson.annotation.JsonProperty
import io.github.kazemek.jsonapi.annotation.JsonApiAttribute
import io.github.kazemek.jsonapi.annotation.JsonApiId
import io.github.kazemek.jsonapi.annotation.JsonApiResource
import io.github.kazemek.jsonapi.core.model.DocumentData
import io.github.kazemek.jsonapi.core.model.JsonApiDocument
import io.github.kazemek.jsonapi.core.model.Meta
import io.github.kazemek.jsonapi.core.model.RelationshipData
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
import io.github.kazemek.jsonapi.jackson.patch.PatchChange
import io.github.kazemek.jsonapi.jackson.patch.PatchCommand
import io.github.kazemek.jsonapi.jackson.patch.StructuredMember
import io.github.kazemek.jsonapi.jackson.patch.StructuredMemberState
import io.github.kazemek.jsonapi.jackson.patch.StructuredPatch
import io.github.kazemek.jsonapi.jackson.document.PrimaryDataKind
import io.github.kazemek.jsonapi.jackson3.ParameterizedBindingFixtures.GenericValue
import io.github.kazemek.jsonapi.jackson3.LinkageMapperFixtures.FlatAuthor
import io.github.kazemek.jsonapi.jackson3.LinkageMapperFixtures.FlatMappedArticle
import io.github.kazemek.jsonapi.jackson3.LinkageMapperFixtures.FlatMappedOptionalArticle
import io.github.kazemek.jsonapi.fixtures.domainpatch.Article
import io.github.kazemek.jsonapi.fixtures.domainpatch.ArticleWithBox
import io.github.kazemek.jsonapi.fixtures.domainpatch.ArticleWithBoxList
import io.github.kazemek.jsonapi.fixtures.domainpatch.ArticleWithContainerAddress
import io.github.kazemek.jsonapi.fixtures.domainpatch.ArticleWithDimensions
import io.github.kazemek.jsonapi.fixtures.domainpatch.ArticleWithGeoAddress
import io.github.kazemek.jsonapi.fixtures.domainpatch.ArticleWithMapMeta
import io.github.kazemek.jsonapi.fixtures.domainpatch.ArticleWithMeta
import io.github.kazemek.jsonapi.fixtures.domainpatch.ArticleWithOptionalAddress
import io.github.kazemek.jsonapi.fixtures.domainpatch.ArticleWithOptionalCity
import io.github.kazemek.jsonapi.fixtures.domainpatch.ArticleWithOptionalMeta
import io.github.kazemek.jsonapi.fixtures.domainpatch.ArticleWithRelationshipLinkage
import io.github.kazemek.jsonapi.fixtures.domainpatch.ArticleWithTags
import io.github.kazemek.jsonapi.fixtures.domainpatch.AuthorIdMeta
import io.github.kazemek.jsonapi.fixtures.domainpatch.MutableArticle
import io.github.kazemek.jsonapi.fixtures.domainpatch.PatchPresenceAddressArticle
import io.github.kazemek.jsonapi.fixtures.domainpatch.PatchPresenceAddressPatchArticle
import io.github.kazemek.jsonapi.fixtures.domainpatch.PatchPresenceTitleArticle
import io.github.kazemek.jsonapi.fixtures.domainread.FlatArticleWithArray
import io.github.kazemek.jsonapi.fixtures.domainread.FlatArticleWithOptional
import io.github.kazemek.jsonapi.fixtures.domainread.FlatArticleWithSet
import io.github.kazemek.jsonapi.fixtures.domainread.FlatArticle
import io.github.kazemek.jsonapi.fixtures.domainread.FlatCountedThing
import io.github.kazemek.jsonapi.fixtures.domainread.FlatIntIdArticle
import io.github.kazemek.jsonapi.fixtures.domainread.FlatThingWithIgnored
import io.github.kazemek.jsonapi.fixtures.domainread.FlatUnregisteredRelationshipsArticle
import io.github.kazemek.jsonapi.fixtures.TestFixtureResources
import spock.lang.Specification
import spock.lang.Unroll
import tools.jackson.core.JsonParser
import tools.jackson.databind.DeserializationContext
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.JavaType
import tools.jackson.databind.annotation.JsonDeserialize
import tools.jackson.databind.deser.std.StdDeserializer
import tools.jackson.databind.json.JsonMapper

class PatchBindingSpec extends Specification {

  @Unroll
  def "binds patch #id into an explicit command"() {
    given:
    def reader = JsonApiJackson3.patchCommandReader(JsonMapper.builder().build())
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
    "patch-relationship-non-empty-collection" | "relationship-non-empty-collection" | FlatArticle.class | patch(FlatArticle.class, "1", new PatchChange.RelationshipChange("comments", "comments", [
      ResourceIdentifier.of("comments", "c1"),
      ResourceIdentifier.of("comments", "c2")
    ]))
    "patch-compound-included-ignored" | "compound-included-ignored" | FlatArticle.class | patch(FlatArticle.class, "1", new PatchChange.AttributeChange("title", "title", "T"), new PatchChange.RelationshipChange("author", "author", ResourceIdentifier.of("people", "p1")))
    "patch-ordinary-domain-nested-partial" | "address-street-new-street" | Article.class | patch(Article.class, "1", new PatchChange.AttributeChange("address", "address", structured(atomic("street", "New Street"))))
    "patch-ordinary-domain-nested-multi-level" | "address-street-and-geo-lat" | ArticleWithGeoAddress.class | patch(ArticleWithGeoAddress.class, "1", new PatchChange.AttributeChange("address", "address", structured(atomic("street", "S"), nested("geo", atomic("lat", "1")))))
    "patch-ordinary-domain-optional-object" | "address-street-new-street" | ArticleWithOptionalAddress.class | patch(ArticleWithOptionalAddress.class, "1", new PatchChange.AttributeChange("address", "address", structured(atomic("street", "New Street"))))
    "patch-ordinary-domain-optional-empty-object" | "address-empty-object" | ArticleWithOptionalAddress.class | patch(ArticleWithOptionalAddress.class, "1", new PatchChange.AttributeChange("address", "address", structured()))
    "patch-ordinary-domain-optional-null" | "address-explicit-null" | ArticleWithOptionalAddress.class | patch(ArticleWithOptionalAddress.class, "1", new PatchChange.AttributeChange("address", "address", null))
    "patch-ordinary-domain-nested-optional-member" | "address-street-city-null" | ArticleWithOptionalCity.class | patch(ArticleWithOptionalCity.class, "1", new PatchChange.AttributeChange("address", "address", structured(atomic("street", "S"), atomic("city", Optional.empty()))))
    "patch-ordinary-domain-unknown-nested-skip" | "address-bogus-and-street" | Article.class | patch(Article.class, "1", new PatchChange.AttributeChange("address", "address", structured(atomic("street", "S"))))
    "patch-ordinary-domain-container-atomic" | "tags-top-level" | ArticleWithTags.class | patch(ArticleWithTags.class, "1", new PatchChange.AttributeChange("tags", "tags", ["a", "b"]))
    "patch-ordinary-domain-generic-nested-javatype" | "box-numbers" | ArticleWithBox.class | patch(ArticleWithBox.class, "1", new PatchChange.AttributeChange("box", "box", structured(atomic("numbers", [1, 2]))))
    "patch-ordinary-domain-generic-nested-multilevel-javatype" | "box-numbers-nested-lists" | ArticleWithBoxList.class | patch(ArticleWithBoxList.class, "1", new PatchChange.AttributeChange("box", "box", structured(atomic("numbers", [[1, 2], [3]]))))
    "patch-ordinary-domain-container-atomic-set" | "address-street-aliases" | ArticleWithContainerAddress.class | patch(ArticleWithContainerAddress.class, "1", new PatchChange.AttributeChange("address", "address", structured(atomic("street", "S"), atomic("aliases", ["a", "b"] as Set))))
    "patch-ordinary-domain-container-atomic-map" | "address-street-scores" | ArticleWithContainerAddress.class | patch(ArticleWithContainerAddress.class, "1", new PatchChange.AttributeChange("address", "address", structured(atomic("street", "S"), atomic("scores", [x: 1, y: 2]))))
    "patch-lowlevel-presence-scalar" | "title-only" | PatchPresenceTitleArticle.class | patch(PatchPresenceTitleArticle.class, "1", new PatchChange.AttributeChange("title", "title", "T"))
    "patch-lowlevel-presence-ordinary-domain" | "address-street" | PatchPresenceAddressArticle.class | patch(PatchPresenceAddressArticle.class, "1", new PatchChange.AttributeChange("address", "address", structured(atomic("street", "S"))))
    "patch-ordinary-domain-javabean-nested-partial" | "address-street" | MutableArticle.class | patch(MutableArticle.class, "1", new PatchChange.AttributeChange("address", "address", structured(atomic("street", "S"))))
    "patch-resource-meta-structured-ordering" | "meta-source-note-author-meta" | ArticleWithMeta.class | patch(ArticleWithMeta.class, "1", new PatchChange.ResourceMetaChange("meta", "meta", structured(atomic("source", "cms"), atomic("note", "n"))), new PatchChange.AttributeChange("title", "title", "T"), new PatchChange.RelationshipChange("author", "author", ResourceIdentifier.of("people", "p1")), new PatchChange.RelationshipMetaChange("author", "authorMeta", structured(atomic("displayName", "Alice"))))
    "patch-resource-meta-atomic-map" | "title-with-meta-source" | ArticleWithMapMeta.class | patch(ArticleWithMapMeta.class, "1", new PatchChange.ResourceMetaChange("meta", "meta", [source: "cms"]), new PatchChange.AttributeChange("title", "title", "T"))
    "patch-relationship-meta-with-data" | "author-meta-with-data" | ArticleWithMeta.class | patch(ArticleWithMeta.class, "1", new PatchChange.RelationshipChange("author", "author", ResourceIdentifier.of("people", "p1")), new PatchChange.RelationshipMetaChange("author", "authorMeta", structured(atomic("displayName", "Alice"))))
    "patch-resource-meta-supplied-unmapped-skipped" | "title-with-meta-source" | FlatArticle.class | patch(FlatArticle.class, "1", new PatchChange.AttributeChange("title", "title", "T"))
    "patch-whole-linkage-to-one-identifier-meta" | "author-identifier-meta" | FlatArticle.class | patch(FlatArticle.class, "1", new PatchChange.RelationshipChange("author", "author", identifier("people", "p1", [role: "editor"])))
    "patch-whole-linkage-to-many-identifier-meta" | "comments-identifier-meta" | FlatArticle.class | patch(FlatArticle.class, "1", new PatchChange.RelationshipChange("comments", "comments", [
      identifier("comments", "c1", [pinned: true]),
      ResourceIdentifier.of("comments", "c2")
    ]))
    "patch-wrapper-whole-linkage-is-not-an-independent-change" | "author-identifier-meta" | ArticleWithRelationshipLinkage.class | patch(ArticleWithRelationshipLinkage.class, "1", new PatchChange.RelationshipChange("author", "author", new RelationshipLinkage(identifier("people", "p1", [role: "editor"]), new AuthorIdMeta("editor"))))
    "patch-set-to-many-identifier-meta" | "tags-identifier-meta" | FlatArticleWithSet.class | patch(FlatArticleWithSet.class, "1", new PatchChange.RelationshipChange("tags", "tags", [
      identifier("tags", "t1", [pinned: true])
    ] as Set))
    "patch-empty-set-relationship" | "tags-empty" | FlatArticleWithSet.class | patch(FlatArticleWithSet.class, "1", new PatchChange.RelationshipChange("tags", "tags", [] as Set))
    "patch-optional-to-one-identifier-meta" | "author-identifier-meta" | FlatArticleWithOptional.class | patch(FlatArticleWithOptional.class, "1", new PatchChange.RelationshipChange("author", "author", Optional.of(identifier("people", "p1", [role: "editor"]))))
    "patch-optional-wrapped-meta-structured" | "title-with-meta-source-note" | ArticleWithOptionalMeta.class | patch(ArticleWithOptionalMeta.class, "1", new PatchChange.ResourceMetaChange("meta", "meta", structured(atomic("source", "cms"), atomic("note", "n"))), new PatchChange.AttributeChange("title", "title", "T"))
  }


  @Unroll
  def "binds array-valued relationship patch #id explicitly"() {
    given:
    def reader = JsonApiJackson3.patchCommandReader(JsonMapper.builder().build())
    def json = TestFixtureResources.readCorpusUtf8("patch/${resource}.json")

    when:
    def command = reader.readValue(json, FlatArticleWithArray)

    then:
    command.resourceType() == FlatArticleWithArray
    command.identity() == "1"
    command.changes().size() == 1
    def change = command.changes()[0]
    change instanceof PatchChange.RelationshipChange
    change.jsonapiName() == "comments"
    change.logicalName() == "comments"
    Arrays.equals(change.value() as ResourceIdentifier[], expected)

    where:
    id                      | resource                         | expected
    "with identifier meta" | "comments-identifier-meta"      | ([
      identifier("comments", "c1", [pinned: true]),
      ResourceIdentifier.of("comments", "c2")
    ] as ResourceIdentifier[])
    "when empty"            | "relationship-empty-collection" | new ResourceIdentifier[0]
  }

  @Unroll
  def "rejects patch #id with a mapping diagnostic"() {
    given:
    def reader = JsonApiJackson3.patchCommandReader(JsonMapper.builder().build())
    def json = TestFixtureResources.readCorpusUtf8("patch/${resource}.json")

    when:
    reader.readValue(json, targetType)

    then:
    def ex = thrown(JsonApiMappingException)
    ex.diagnostic() == expectedDiagnostic
    ex.propertyPath() == expectedPath

    where:
    id | resource | targetType | expectedDiagnostic | expectedPath
    "patch-relationship-cardinality-mismatch" | "relationship-cardinality-mismatch" | FlatArticle.class | MappingDiagnostic.RELATIONSHIP_CARDINALITY_MISMATCH | "/relationships/author/data"
    "patch-resource-type-mismatch" | "resource-type-mismatch" | FlatArticle.class | MappingDiagnostic.RESOURCE_TYPE_MISMATCH | "/type"
    "patch-identifier-conversion-failure" | "identifier-not-an-integer" | FlatIntIdArticle.class | MappingDiagnostic.IDENTIFIER_CONVERSION_FAILED | "/id"
    "patch-attribute-conversion-failure" | "attribute-conversion-failure" | FlatCountedThing.class | MappingDiagnostic.UNSUPPORTED_ATTRIBUTE_VALUE | "/attributes/count"
    "patch-unsupported-relationship-target" | "relationship-single-linkage" | FlatUnregisteredRelationshipsArticle.class | MappingDiagnostic.UNSUPPORTED_RELATIONSHIP_TARGET | "/relationships/author/data"
    "patch-ordinary-domain-nested-primitive-null" | "dimensions-width-null" | ArticleWithDimensions.class | MappingDiagnostic.UNSUPPORTED_ATTRIBUTE_VALUE | "/attributes/dimensions/width"
    "patch-lowlevel-presence-shape-rejected" | "address-street" | PatchPresenceAddressPatchArticle.class | MappingDiagnostic.INVALID_PATCH_PROPERTY_TYPE | "/attributes/address"
    "patch-meta-conversion-failure" | "meta-source-object" | ArticleWithMeta.class | MappingDiagnostic.UNSUPPORTED_ATTRIBUTE_VALUE | "/meta/source"
    "patch-scalar-meta-target" | "identity-only" | io.github.kazemek.jsonapi.fixtures.domainpatch.WholeMetaTargetFixtures.ScalarMetaArticle.class | MappingDiagnostic.INVALID_META_TARGET | "/meta"
    "patch-uuid-meta-target" | "identity-only" | io.github.kazemek.jsonapi.fixtures.domainpatch.WholeMetaTargetFixtures.UuidMetaArticle.class | MappingDiagnostic.INVALID_META_TARGET | "/meta"
    "patch-instant-meta-target" | "identity-only" | io.github.kazemek.jsonapi.fixtures.domainpatch.WholeMetaTargetFixtures.InstantMetaArticle.class | MappingDiagnostic.INVALID_META_TARGET | "/meta"
    "patch-uri-meta-target" | "identity-only" | io.github.kazemek.jsonapi.fixtures.domainpatch.WholeMetaTargetFixtures.UriMetaArticle.class | MappingDiagnostic.INVALID_META_TARGET | "/meta"
  }

  @Unroll
  def "rejects patch #id during document validation"() {
    given:
    def context = endpointIdentity == null
        ? ValidationContext.defaults()
        : ValidationContext.defaults().withExpectedEndpointIdentity(endpointIdentity)
    def reader = JsonApiJackson3.patchCommandReader(JsonMapper.builder().build(), context)
    def json = TestFixtureResources.readCorpusUtf8("patch/${resource}.json")

    when:
    reader.readValue(json, targetType)

    then:
    def ex = thrown(JsonApiDocumentReadException)
    ex.ruleCode() == expectedRule
    ex.jsonPointer() == expectedPointer

    where:
    id | resource | targetType | endpointIdentity | expectedRule | expectedPointer
    "patch-endpoint-identity-mismatch" | "title-only" | FlatArticle.class | new EndpointIdentity("articles", "99") | ValidationRuleCode.ENDPOINT_IDENTITY_MISMATCH | "/data/id"
    "patch-missing-relationship-data" | "missing-relationship-data" | FlatArticle.class | null | ValidationRuleCode.RELATIONSHIP_DATA_REQUIRED | "/data/relationships/author/data"
    "patch-wrong-primary-shape" | "wrong-primary-shape" | FlatArticle.class | null | ValidationRuleCode.UPDATE_REQUIRES_SINGLE_RESOURCE | "/data"
  }

  def "custom deserializer applies to attribute change"() {
    given:
    def reader = JsonApiJackson3.patchCommandReader(JsonMapper.builder().build())
    def json = '{"data":{"type":"things","id":"1","attributes":{"title":"hello"}}}'

    when:
    def command = reader.readValue(json, FlatLoudThing)

    then:
    command.identity() == "1"
    command.changes() == [
      new PatchChange.AttributeChange("title", "title", "HELLO")
    ]
  }

  def "patch-custom-linkage-conversion"() {
    given:
    def mapper = { RelationshipData data, target ->
      def identifier = ((RelationshipData.SingleLinkage) data).identifier()
      return new FlatAuthor(identifier.type(), identifier.id())
    } as RelationshipLinkageMapper
    def reader = JsonApiJackson3.patchCommandReader(
        JsonMapper.builder().build(),
        ValidationContext.defaults(),
        IdentifierConverter.defaults(),
        [(FlatAuthor): mapper])
    def json =
        '{"data":{"type":"articles","id":"1","relationships":{"author":{"data":{"type":"people","id":"p1"}}}}}'

    when:
    def command = reader.readValue(json, FlatMappedArticle)

    then:
    command.identity() == "1"
    command.changes() == [
      new PatchChange.RelationshipChange("author", "author", new FlatAuthor("people", "p1"))
    ]
  }

  def "explicit null on Optional attribute stores value == null"() {
    given:
    def reader = JsonApiJackson3.patchCommandReader(JsonMapper.builder().build())
    def json = '{"data":{"type":"articles","id":"1","attributes":{"title":null}}}'

    when:
    def command = reader.readValue(json, FlatOptionalTitleArticle)

    then:
    command.changes().size() == 1
    command.changes()[0].value() == null
    !(command.changes()[0].value() instanceof Optional)
  }

  def "fromDocument missing id"() {
    given:
    def reader = JsonApiJackson3.patchCommandReader(JsonMapper.builder().build())
    def document = new JsonApiDocument(
        new DocumentData.SingleResource(ResourceObject.ofType("articles")),
        null, null, null, null, null, Map.of())

    when:
    reader.fromDocument(document, FlatArticle)

    then:
    def ex = thrown(JsonApiMappingException)
    ex.diagnostic() == MappingDiagnostic.IDENTIFIER_CONVERSION_FAILED
    ex.propertyPath() == "/id"
  }

  def "fromDocument JavaType returns PatchCommand wildcard with raw resourceType"() {
    given:
    def mapper = JsonMapper.builder().build()
    def reader = JsonApiJackson3.patchCommandReader(mapper)
    def document = decodeUpdateDocument(
        '{"data":{"type":"articles","id":"1","attributes":{"title":"Hello"}}}')
    def javaType = mapper.constructType(FlatArticle)

    when:
    PatchCommand<?> command = reader.fromDocument(document, javaType)

    then:
    command.resourceType() == FlatArticle
    command.identity() == "1"
    command.changes() == [
      new PatchChange.AttributeChange("title", "title", "Hello")
    ]
  }

  def "generic attribute type resolves through the parameterized JavaType"() {
    given:
    def mapper = JsonMapper.builder().build()
    def reader = JsonApiJackson3.patchCommandReader(mapper)
    def document = decodeUpdateDocument(
        '{"data":{"type":"things","id":"1","attributes":{"value":"42"}}}')
    def javaType = mapper.typeFactory.constructParametricType(GenericValue, Integer)

    when:
    PatchCommand<?> command = reader.fromDocument(document, javaType)

    then:
    command.resourceType() == GenericValue
    command.identity() == "1"
    command.changes().size() == 1
    command.changes()[0].value() == 42
    command.changes()[0].value() instanceof Integer
  }

  def "explicit null on primitive attribute fails even when FAIL_ON_NULL_FOR_PRIMITIVES is off"() {
    given:
    def mapper = JsonMapper.builder()
        .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
        .build()
    def reader = JsonApiJackson3.patchCommandReader(mapper)
    def json = '{"data":{"type":"things","id":"1","attributes":{"count":null}}}'

    when:
    reader.readValue(json, FlatCountedThing)

    then:
    def ex = thrown(JsonApiMappingException)
    ex.diagnostic() == MappingDiagnostic.UNSUPPORTED_ATTRIBUTE_VALUE
    ex.propertyPath() == "/attributes/count"
  }

  def "mapper and JavaType factory overloads bind successfully"() {
    given:
    def json = '{"data":{"type":"articles","id":"1","attributes":{"title":"Hello"}}}'
    def mapper = JsonMapper.builder().build()
    def mapperReader = JsonApiJackson3.patchCommandReader(mapper)
    def javaType = JsonMapper.builder().build().constructType(FlatArticle)

    when:
    def fromMapper = mapperReader.readValue(json, FlatArticle)
    def fromJavaType = JsonApiJackson3.patchCommandReader(JsonMapper.builder().build())
        .readValue(json, javaType)

    then:
    fromMapper.identity() == "1"
    fromMapper.changes() == [
      new PatchChange.AttributeChange("title", "title", "Hello")
    ]
    fromJavaType.resourceType() == FlatArticle
    fromJavaType.identity() == "1"
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
    def reader = JsonApiJackson3.patchCommandReader(
        JsonMapper.builder().build(), ValidationContext.defaults(), converter)
    def json = '{"data":{"type":"articles","id":"1","attributes":{"title":"T"}}}'

    when:
    def command = reader.readValue(json, FlatArticle)

    then:
    command.identity() == "parsed-1"
  }

  def "fromDocument rejects null and non-single-resource primary data"() {
    given:
    def reader = JsonApiJackson3.patchCommandReader(JsonMapper.builder().build())
    def metaOnly = new JsonApiDocument(
        null, null, Meta.of([note: "x"]), null, null, null, Map.of())

    when:
    reader.fromDocument(null, FlatArticle)

    then:
    thrown(NullPointerException)

    when:
    reader.fromDocument(metaOnly, FlatArticle)

    then:
    thrown(IllegalArgumentException)

    when:
    reader.fromDocument(
        new JsonApiDocument(DocumentData.NullData.INSTANCE, null, null, null, null, null, Map.of()),
        FlatArticle)

    then:
    thrown(IllegalArgumentException)

    when:
    reader.fromDocument(
        new JsonApiDocument(
        new DocumentData.ResourceCollection(List.of()),
        null, null, null, null, null, Map.of()),
        FlatArticle)

    then:
    thrown(IllegalArgumentException)

    when:
    reader.fromDocument(
        new JsonApiDocument(
        new DocumentData.SingleIdentifier(ResourceIdentifier.of("articles", "1")),
        null, null, null, null, null, Map.of()),
        FlatArticle)

    then:
    thrown(IllegalArgumentException)
  }

  def "caller-owned stream and parser remain open on success and failure"() {
    given:
    def reader = JsonApiJackson3.patchCommandReader(JsonMapper.builder().build())
    def mapper = JsonMapper.builder().build()
    def successBytes =
        '{"data":{"type":"articles","id":"1","attributes":{"title":"T"}}}'.bytes
    def failureBytes = '{"data":'.bytes
    def successStream = new CloseTrackingInputStream(new ByteArrayInputStream(successBytes))
    def failureStream = new CloseTrackingInputStream(new ByteArrayInputStream(failureBytes))
    def parser = mapper.createParser(successBytes)
    def failureParser = mapper.createParser(failureBytes)

    when:
    def command = reader.readValue(successStream, FlatArticle)
    def fromParser = reader.readValue(parser, FlatArticle)

    then:
    command.identity() == "1"
    fromParser.identity() == "1"
    !successStream.closed
    !parser.closed

    when:
    reader.readValue(failureStream, FlatArticle)

    then:
    thrown(JsonApiDocumentReadException)
    !failureStream.closed

    when:
    reader.readValue(failureParser, FlatArticle)

    then:
    thrown(JsonApiDocumentReadException)
    !failureParser.closed

    cleanup:
    parser?.close()
    failureParser?.close()
  }

  def "duplicate mapping definitions fail before a command escapes"() {
    given:
    def reader = JsonApiJackson3.patchCommandReader(JsonMapper.builder().build())
    def json = '{"data":{"type":"articles","id":"1","attributes":{"title":"T"}}}'

    when:
    reader.readValue(json, FlatDuplicateAttributeArticle)

    then:
    thrown(JsonApiMappingException)
  }

  def "typed identity is never listed among changes"() {
    given:
    def reader = JsonApiJackson3.patchCommandReader(JsonMapper.builder().build())
    def json =
        '{"data":{"type":"articles","id":"1","attributes":{"title":"T"},"relationships":{"author":{"data":null}}}}'

    when:
    def command = reader.readValue(json, FlatArticle)

    then:
    command.identity() == "1"
    command.changes()*.logicalName() == ["title", "author"]
  }

  def "byte array Class and JavaType entry points bind successfully"() {
    given:
    def mapper = JsonMapper.builder().build()
    def reader = JsonApiJackson3.patchCommandReader(mapper)
    def json = '{"data":{"type":"articles","id":"1","attributes":{"title":"Hello"}}}'.bytes
    def javaType = mapper.constructType(FlatArticle)

    when:
    def fromClass = reader.readValue(json, FlatArticle)
    def fromJavaType = reader.readValue(json, javaType)

    then:
    fromClass.identity() == "1"
    fromJavaType.resourceType() == FlatArticle
    fromJavaType.identity() == "1"
  }

  def "JavaType stream and parser entry points leave caller-owned sources open"() {
    given:
    def mapper = JsonMapper.builder().build()
    def reader = JsonApiJackson3.patchCommandReader(mapper)
    def javaType = mapper.constructType(FlatArticle)
    def bytes = '{"data":{"type":"articles","id":"1","attributes":{"title":"T"}}}'.bytes
    def stream = new CloseTrackingInputStream(new ByteArrayInputStream(bytes))
    def parser = mapper.createParser(bytes)

    when:
    def fromStream = reader.readValue(stream, javaType)
    def fromParser = reader.readValue(parser, javaType)

    then:
    fromStream.identity() == "1"
    fromParser.identity() == "1"
    !stream.closed
    !parser.closed

    cleanup:
    parser?.close()
  }

  def "IdentifierConverter parse failure and null are IDENTIFIER_CONVERSION_FAILED"() {
    given:
    def throwing = new IdentifierConverter() {
          @Override
          String convert(Object value) {
            return String.valueOf(value)
          }

          @Override
          Object parse(String wire) {
            throw new IllegalStateException("boom")
          }
        }
    def nullParse = new IdentifierConverter() {
          @Override
          String convert(Object value) {
            return String.valueOf(value)
          }

          @Override
          Object parse(String wire) {
            return null
          }
        }
    def json = '{"data":{"type":"articles","id":"1","attributes":{"title":"T"}}}'

    when:
    JsonApiJackson3.patchCommandReader(
        JsonMapper.builder().build(), ValidationContext.defaults(), throwing)
        .readValue(json, FlatArticle)

    then:
    def throwingEx = thrown(JsonApiMappingException)
    throwingEx.diagnostic() == MappingDiagnostic.IDENTIFIER_CONVERSION_FAILED

    when:
    JsonApiJackson3.patchCommandReader(
        JsonMapper.builder().build(), ValidationContext.defaults(), nullParse)
        .readValue(json, FlatArticle)

    then:
    def nullEx = thrown(JsonApiMappingException)
    nullEx.diagnostic() == MappingDiagnostic.IDENTIFIER_CONVERSION_FAILED
  }

  def "relationship cardinality mismatch is reported before linkage mapper runs"() {
    given:
    def invoked = false
    def mapper = { RelationshipData data, JavaType target ->
      invoked = true
      return null
    } as RelationshipLinkageMapper
    def reader = JsonApiJackson3.patchCommandReader(
        JsonMapper.builder().build(),
        ValidationContext.defaults(),
        IdentifierConverter.defaults(),
        [(FlatAuthor): mapper])
    def json =
        '{"data":{"type":"articles","id":"1","relationships":{"author":{"data":[{"type":"people","id":"p1"}]},"contributors":{"data":{"type":"people","id":"p1"}}}}}'

    when:
    reader.readValue(json, FlatMappedArticle)

    then:
    def ex = thrown(JsonApiMappingException)
    ex.diagnostic() == MappingDiagnostic.RELATIONSHIP_CARDINALITY_MISMATCH
    !invoked
  }

  def "linkage mapper exception is LINKAGE_MAPPING_FAILED"() {
    given:
    def mapper = { RelationshipData data, JavaType target ->
      throw new IllegalStateException("boom")
    } as RelationshipLinkageMapper
    def reader = JsonApiJackson3.patchCommandReader(
        JsonMapper.builder().build(),
        ValidationContext.defaults(),
        IdentifierConverter.defaults(),
        [(FlatAuthor): mapper])
    def json =
        '{"data":{"type":"articles","id":"1","relationships":{"author":{"data":{"type":"people","id":"p1"}}}}}'

    when:
    reader.readValue(json, FlatMappedArticle)

    then:
    def ex = thrown(JsonApiMappingException)
    ex.diagnostic() == MappingDiagnostic.LINKAGE_MAPPING_FAILED
    ex.propertyPath() == "/relationships/author/data"
  }

  def "empty to-many and Optional relationship changes bind"() {
    given:
    def mapper = { RelationshipData data, JavaType target ->
      if (data instanceof RelationshipData.NullLinkage) {
        return null
      }
      if (data instanceof RelationshipData.IdentifierCollectionLinkage) {
        return data.identifiers().collect { id -> new FlatAuthor(id.type(), id.id()) }
      }
      def identifier = ((RelationshipData.SingleLinkage) data).identifier()
      return new FlatAuthor(identifier.type(), identifier.id())
    } as RelationshipLinkageMapper
    def reader = JsonApiJackson3.patchCommandReader(
        JsonMapper.builder().build(),
        ValidationContext.defaults(),
        IdentifierConverter.defaults(),
        [(FlatAuthor): mapper])
    def json =
        '{"data":{"type":"articles","id":"1","relationships":{"author":{"data":null},"contributors":{"data":[]}}}}'

    when:
    def command = reader.readValue(json, FlatMappedOptionalArticle)

    then:
    command.changes() == [
      new PatchChange.RelationshipChange("author", "author", Optional.empty()),
      new PatchChange.RelationshipChange("contributors", "contributors", [])
    ]
  }

  def "mapper factory with ValidationContext and IdentifierConverter binds"() {
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
    def reader = JsonApiJackson3.patchCommandReader(
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

  private static StructuredMember nested(String name, StructuredMember... members) {
    return new StructuredMember(name, name, new StructuredMemberState.Structured(Arrays.asList(members)))
  }

  private static ResourceIdentifier identifier(String type, String id, Map<String, Object> meta) {
    return new ResourceIdentifier(type, id, null, Meta.of(meta), Map.of())
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

  @JsonApiResource(type = "things")
  static class FlatLoudThing {
    @JsonApiId String id
    @JsonDeserialize(using = UppercaseDeserializer)
    @JsonApiAttribute
    String title
  }

  @JsonApiResource(type = "articles")
  static class FlatOptionalTitleArticle {
    @JsonApiId String id
    @JsonApiAttribute Optional<String> title
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
