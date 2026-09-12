package com.kazforge.jsonapi.jackson2

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.DeserializationFeature
import com.kazforge.jsonapi.annotation.JsonApiAttribute
import com.kazforge.jsonapi.annotation.JsonApiId
import com.kazforge.jsonapi.annotation.JsonApiResource
import com.kazforge.jsonapi.core.model.Attributes
import com.kazforge.jsonapi.core.model.DocumentData
import com.kazforge.jsonapi.core.model.JsonApiDocument
import com.kazforge.jsonapi.core.model.JsonApiMembers
import com.kazforge.jsonapi.core.model.Link
import com.kazforge.jsonapi.core.model.Links
import com.kazforge.jsonapi.core.model.Meta
import com.kazforge.jsonapi.core.model.Relationship
import com.kazforge.jsonapi.core.model.RelationshipData
import com.kazforge.jsonapi.core.model.Relationships
import com.kazforge.jsonapi.core.model.ResourceIdentifier
import com.kazforge.jsonapi.core.model.ResourceObject
import com.kazforge.jsonapi.jackson.diagnostic.JsonApiMappingException
import com.kazforge.jsonapi.jackson.diagnostic.MappingDiagnostic
import com.kazforge.jsonapi.jackson.mapping.IdentifierConverter
import com.kazforge.jsonapi.jackson.mapping.RelationshipLinkage
import com.kazforge.jsonapi.fixtures.domainpatch.ArticleMeta
import com.kazforge.jsonapi.fixtures.domainpatch.ArticleWithMapMeta
import com.kazforge.jsonapi.fixtures.domainpatch.ArticleWithOptionalMeta
import com.kazforge.jsonapi.fixtures.domainpatch.AuthorIdMeta
import com.kazforge.jsonapi.fixtures.domainpatch.AuthorMeta
import com.kazforge.jsonapi.fixtures.domainpatch.CommentIdMeta
import com.kazforge.jsonapi.fixtures.domainpatch.WholeMetaTargetFixtures
import com.kazforge.jsonapi.fixtures.domainread.FlatArticle
import com.kazforge.jsonapi.fixtures.domainread.FlatArticleWithArray
import com.kazforge.jsonapi.fixtures.domainread.FlatArticleWithOptional
import com.kazforge.jsonapi.fixtures.domainread.FlatArticleWithSet
import com.kazforge.jsonapi.fixtures.domainread.FlatCountedThing
import com.kazforge.jsonapi.fixtures.domainread.FlatCreatorArticle
import com.kazforge.jsonapi.fixtures.domainread.FlatDefaultedArticle
import com.kazforge.jsonapi.fixtures.domainread.FlatInheritedBlog
import com.kazforge.jsonapi.fixtures.domainread.FlatIntIdArticle
import com.kazforge.jsonapi.fixtures.domainread.FlatNullableIdArticle
import com.kazforge.jsonapi.fixtures.domainread.FlatMetaArticle
import com.kazforge.jsonapi.fixtures.domainread.FlatMutableArticle
import com.kazforge.jsonapi.fixtures.domainread.FlatRelationshipLinkageArticle
import com.kazforge.jsonapi.fixtures.domainread.FlatRequiredThing
import com.kazforge.jsonapi.fixtures.domainread.FlatThingWithIgnored
import com.kazforge.jsonapi.fixtures.domainread.FlatThrowingCreatorThing
import com.kazforge.jsonapi.fixtures.domainread.FlatUnregisteredRelationshipsArticle
import com.kazforge.jsonapi.fixtures.domainwrite.ArticleWithUnannotatedExtra
import com.kazforge.jsonapi.fixtures.domainwrite.ConventionalId
import com.kazforge.jsonapi.fixtures.domainwrite.Person
import com.kazforge.jsonapi.fixtures.domainwrite.RelationshipLinkageContainerFixtures.MapRelationshipLinkageArticle
import com.kazforge.jsonapi.fixtures.domainwrite.RelationshipLinkageContainerFixtures.ArrayRelationshipLinkageArticle
import com.kazforge.jsonapi.fixtures.domainwrite.RelationshipLinkageContainerFixtures.OptionalRelationshipLinkageArticle
import com.kazforge.jsonapi.fixtures.domainwrite.RelationshipLinkageContainerFixtures.RenamedRelationshipLinkageArticle
import com.kazforge.jsonapi.fixtures.localid.LocalIdentityArticle
import com.kazforge.jsonapi.jackson2.LinkageMapperFixtures.FlatAuthor
import com.kazforge.jsonapi.jackson2.LinkageMapperFixtures.FlatMappedArticle
import com.kazforge.jsonapi.jackson2.LinkageMapperFixtures.FlatMappedOptionalArticle
import com.kazforge.jsonapi.jackson2.ParameterizedBindingFixtures.GenericArticle
import com.kazforge.jsonapi.jackson2.ParameterizedBindingFixtures.GenericValue
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

import com.fasterxml.jackson.annotation.JsonSetter
import com.fasterxml.jackson.annotation.Nulls
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.InjectableValues
import com.fasterxml.jackson.databind.JavaType
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.exc.InvalidDefinitionException
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.databind.module.SimpleModule
import java.util.Locale
import java.util.Optional

class ResourceBinderSpec extends Specification {

  private static final String ARTICLES = "articles"
  private static final String AUTHOR = "author"
  private static final String COMMENTS = "comments"
  private static final String NESTED = "nested-things"
  private static final String PEOPLE = "people"
  private static final String RELATED = JsonApiMembers.RELATED
  private static final String THINGS = "things"

  @Shared
  JsonApiResourceBinder binder = JsonApiJackson2.resourceBinder(JsonMapper.builder().build())

  @Unroll
  def "binds resource #id successfully"() {
    when:
    def actual = binder.fromResource(input as ResourceObject, targetType)

    then:
    actual == expected

    where:
    id | input | targetType | expected
    "record attributes and built-in relationships" | resource(ARTICLES, "1", attrs("title", "Hello", "body-text", "Content"), rels(AUTHOR, single(PEOPLE, "p1"), COMMENTS, collection(COMMENTS, ["c1", "c2"]))) | FlatArticle | new FlatArticle("1", "Hello", "Content", ResourceIdentifier.of(PEOPLE, "p1"), [
      ResourceIdentifier.of(COMMENTS, "c1"),
      ResourceIdentifier.of(COMMENTS, "c2")
    ])
    "mutable DTO" | resource(ARTICLES, "1", attrs("title", "Hello"), rels(AUTHOR, single(PEOPLE, "p1"))) | FlatMutableArticle | new FlatMutableArticle("1", "Hello", ResourceIdentifier.of(PEOPLE, "p1"))
    "immutable creator DTO" | resource(ARTICLES, "42", attrs("title", "Creator"), null) | FlatCreatorArticle | new FlatCreatorArticle("42", "Creator")
    "lid-only resource never binds into the id role" | resourceWithLid(ARTICLES, "lid-1", attrs("title", "T")) | FlatNullableIdArticle | new FlatNullableIdArticle(null, "T")
    "resource without id or lid" | resourceWithLid(ARTICLES, null, attrs("title", "T")) | FlatNullableIdArticle | new FlatNullableIdArticle(null, "T")
    "id and lid bind independently" | new ResourceObject(ARTICLES, "42", "lid-1", Attributes.ofAttributes(attrs("title", "T")), null, null, null, Map.of()) | LocalIdentityArticle | new LocalIdentityArticle("42", "lid-1", "T")
    "present Optional relationship" | resource(ARTICLES, "1", null, rels(AUTHOR, single(PEOPLE, "p1"))) | FlatArticleWithOptional | new FlatArticleWithOptional("1", null, Optional.of(ResourceIdentifier.of(PEOPLE, "p1")))
    "explicit null Optional relationship" | resource(ARTICLES, "1", null, rels(AUTHOR, Relationship.withData(RelationshipData.NullLinkage.INSTANCE))) | FlatArticleWithOptional | new FlatArticleWithOptional("1", null, Optional.empty())
    "array relationship" | resource(ARTICLES, "1", null, rels(COMMENTS, collection(COMMENTS, ["c1", "c2"]))) | FlatArticleWithArray | new FlatArticleWithArray("1", null, [
      ResourceIdentifier.of(COMMENTS, "c1"),
      ResourceIdentifier.of(COMMENTS, "c2")
    ] as ResourceIdentifier[])
    "Set relationship" | resource(ARTICLES, "1", null, rels("tags", collection("tags", ["t1", "t2"]))) | FlatArticleWithSet | new FlatArticleWithSet("1", null, Set.of(ResourceIdentifier.of("tags", "t1"), ResourceIdentifier.of("tags", "t2")))
    "Map RelationshipLinkage relationship" | resource(ARTICLES, "1", null, rels(COMMENTS, collectionWithIdentifierMeta(COMMENTS, ["c1"], [Meta.of([pinned: true])]))) | MapRelationshipLinkageArticle | new MapRelationshipLinkageArticle("1", [
      new RelationshipLinkage<>(identifier(COMMENTS, "c1", Meta.of([pinned: true])), [pinned: true])
    ])
    "whole resource and relationship meta" | resourceWithMeta(attrs("title", "Hello"), relsWithMeta(single(PEOPLE, "p1"), Meta.of([displayName: "Alice"])), Meta.of([source: "cms", note: "n"])) | FlatMetaArticle | new FlatMetaArticle("1", "Hello", ResourceIdentifier.of(PEOPLE, "p1"), new ArticleMeta("cms", "n"), new AuthorMeta("Alice"))
    "Map whole resource and relationship meta" | resourceWithMeta(attrs("title", "Hello"), relsWithMeta(single(PEOPLE, "p1"), Meta.of([displayName: "Alice"])), Meta.of([source: "cms"])) | ArticleWithMapMeta | new ArticleWithMapMeta("1", "Hello", ResourceIdentifier.of(PEOPLE, "p1"), [source: "cms"], [displayName: "Alice"])
    "Optional whole resource meta" | resourceWithMeta(attrs("title", "Hello"), null, Meta.of([source: "cms", note: "n"])) | ArticleWithOptionalMeta | new ArticleWithOptionalMeta("1", "Hello", null, Optional.of(new ArticleMeta("cms", "n")), Optional.empty())
    "to-one identifier meta" | resource(ARTICLES, "1", null, rels(AUTHOR, toOneWithIdentifierMeta(PEOPLE, "p1", Meta.of([role: "editor"])))) | FlatRelationshipLinkageArticle | new FlatRelationshipLinkageArticle("1", null, new RelationshipLinkage<>(identifier(PEOPLE, "p1", Meta.of([role: "editor"])), new AuthorIdMeta("editor")), null, null, null)
    "to-many identifier meta" | resource(ARTICLES, "1", null, rels(COMMENTS, collectionWithIdentifierMeta(COMMENTS, ["c1", "c2"], [Meta.of([pinned: true]), null]))) | FlatRelationshipLinkageArticle | new FlatRelationshipLinkageArticle("1", null, null, [
      new RelationshipLinkage<>(identifier(COMMENTS, "c1", Meta.of([pinned: true])), new CommentIdMeta(true)),
      new RelationshipLinkage<>(ResourceIdentifier.of(COMMENTS, "c2"), null)
    ], null, null)
    "array RelationshipLinkage relationship" | resource(ARTICLES, "1", null, rels(COMMENTS, collectionWithIdentifierMeta(COMMENTS, ["c1", "c2"], [Meta.of([pinned: true]), null]))) | ArrayRelationshipLinkageArticle | new ArrayRelationshipLinkageArticle("1", [
      new RelationshipLinkage<>(identifier(COMMENTS, "c1", Meta.of([pinned: true])), new CommentIdMeta(true)),
      new RelationshipLinkage<>(ResourceIdentifier.of(COMMENTS, "c2"), null)
    ] as RelationshipLinkage[])
    "Optional RelationshipLinkage relationship" | resource(ARTICLES, "1", null, rels(AUTHOR, toOneWithIdentifierMeta(PEOPLE, "p1", Meta.of([role: "editor"])))) | OptionalRelationshipLinkageArticle | new OptionalRelationshipLinkageArticle("1", Optional.of(new RelationshipLinkage<>(identifier(PEOPLE, "p1", Meta.of([role: "editor"])), new AuthorIdMeta("editor"))))
    "renamed RelationshipLinkage relationship" | resource(ARTICLES, "1", null, rels(AUTHOR, toOneWithIdentifierMeta(PEOPLE, "p1", Meta.of([role: "editor"])))) | RenamedRelationshipLinkageArticle | new RenamedRelationshipLinkageArticle("1", new RelationshipLinkage<>(identifier(PEOPLE, "p1", Meta.of([role: "editor"])), new AuthorIdMeta("editor")))
    "inherited properties" | resource("blogs", "b1", attrs("name", "My Blog", "description", "A description"), null) | FlatInheritedBlog | new FlatInheritedBlog("b1", "My Blog", "A description")
    "ignored property" | resource(THINGS, "1", attrs("name", "visible", "secret", "hidden"), null) | FlatThingWithIgnored | new FlatThingWithIgnored("1", "visible", null)
    "explicit null preserves default semantics" | resource(ARTICLES, "1", nullableAttr("title"), null) | FlatDefaultedArticle | new FlatDefaultedArticle("1", null, "default")
    "unannotated property is ignored" | resource(ARTICLES, "1", attrs("title", "Hello", "ignoredExtra", "secret"), null) | ArticleWithUnannotatedExtra | new ArticleWithUnannotatedExtra("1", "Hello", null)
    "conventional identifier" | resource("conventionals", "42", attrs("name", "name value"), null) | ConventionalId | new ConventionalId("42", "name value")
    "default numeric identifier conversion" | resource(ARTICLES, "42", attrs("title", "T"), null) | FlatIntIdArticle | new FlatIntIdArticle(42, "T")
    "empty List relationship" | resource(ARTICLES, "1", null, rels(COMMENTS, collection(COMMENTS, []))) | FlatArticle | new FlatArticle("1", null, null, null, [])
    "explicit null to-one relationship" | resource(ARTICLES, "1", null, rels(AUTHOR, Relationship.withData(RelationshipData.NullLinkage.INSTANCE))) | FlatArticle | new FlatArticle("1", null, null, null, null)
    "omitted relationship does not bind" | resource(ARTICLES, "1", null, rels(COMMENTS, collection(COMMENTS, ["c1"]))) | FlatArticle | new FlatArticle("1", null, null, null, [
      ResourceIdentifier.of(COMMENTS, "c1")
    ])
    "relationship meta without linkage" | resourceWithMeta(attrs("title", "Hello"), rels(AUTHOR, Relationship.metaOnly(Meta.of([displayName: "Alice"]))), null) | FlatMetaArticle | new FlatMetaArticle("1", "Hello", null, null, new AuthorMeta("Alice"))
    "link-only relationship does not bind linkage" | resource(ARTICLES, "1", null, rels(AUTHOR, Relationship.linkOnly(Links.ofLinks([(RELATED): new Link.StringLink("/articles/1/author")])))) | FlatArticle | new FlatArticle("1", null, null, null, null)
    "data-absent relationship binds Optional.empty via its missing-property default" | resource(ARTICLES, "1", null, rels(AUTHOR, Relationship.metaOnly(Meta.of([note: "x"])))) | FlatArticleWithOptional | new FlatArticleWithOptional("1", null, Optional.empty())
  }

  def "relationship data absence and explicit null follow configured missing-vs-null semantics"() {
    expect:
    def type = RelationshipBindingFixtures.DefaultedRelationshipArticle
    binder.fromResource(
        resource("defaulted-relationship-articles", "1", null, rels(AUTHOR, Relationship.metaOnly(Meta.of([note: "x"])))),
        type).getAuthor() == type.defaultAuthor()

    and:
    binder.fromResource(
        resource("defaulted-relationship-articles", "1", null, rels(AUTHOR, Relationship.withData(RelationshipData.NullLinkage.INSTANCE))),
        type).getAuthor() == null
  }

  def "custom identifier converter converts ids"() {
    given:
    def converter = new IdentifierConverter() {
          @Override
          String convert(Object idValue) {
            "prefix-" + idValue.toString()
          }

          @Override
          Object parse(String wireIdentifier) {
            wireIdentifier - "prefix-"
          }
        }
    def localBinder = JsonApiJackson2.resourceBinder(JsonMapper.builder().build(), converter)

    when:
    def actual = localBinder.fromResource(resource(ARTICLES, "prefix-42", attrs("title", "T"), null), FlatIntIdArticle)

    then:
    actual == new FlatIntIdArticle(42, "T")
  }

  def "binds a resource collection directly"() {
    given:
    def resources = [
      resource(ARTICLES, "1", attrs("title", "One"), null),
      resource(ARTICLES, "2", attrs("title", "Two"), null)
    ]

    when:
    def actual = binder.fromResources(resources, FlatArticle)

    then:
    actual == [
      new FlatArticle("1", "One", null, null, null),
      new FlatArticle("2", "Two", null, null, null)
    ]
  }

  def "included resources do not affect direct flat resource binding"() {
    given:
    def firstPrimary = resource(
        ARTICLES, "1", attrs("title", "T"), rels(AUTHOR, single(PEOPLE, "p1")))
    def secondPrimary = resource(
        ARTICLES, "1", attrs("title", "T"), rels(AUTHOR, single(PEOPLE, "p1")))
    def firstDocument = document(firstPrimary, [
      resource(PEOPLE, "p1", attrs("name", "Alice"), null)
    ])
    def secondDocument = document(secondPrimary, [
      resource(PEOPLE, "p1", attrs("name", "AliceChanged"), null)
    ])

    when:
    def first = binder.fromResource(primaryResource(firstDocument), FlatArticle)
    def second = binder.fromResource(primaryResource(secondDocument), FlatArticle)

    then:
    first == new FlatArticle(
        "1", "T", null, ResourceIdentifier.of(PEOPLE, "p1"), null)
    second == first
  }

  @Unroll
  def "fails to bind resource #id due to mapping diagnostic"() {
    when:
    binder.fromResource(input as ResourceObject, targetType)

    then:
    def ex = thrown(JsonApiMappingException)
    ex.diagnostic() == diagnostic
    ex.propertyPath() == propertyPath
    ex.resourceClass() == resourceClass

    where:
    id | input | targetType | diagnostic | propertyPath | resourceClass
    "resource type mismatch" | resource(PEOPLE, "p1", null, null) | FlatArticle | MappingDiagnostic.RESOURCE_TYPE_MISMATCH | "/type" | FlatArticle
    "unregistered to-one relationship target" | resource(ARTICLES, "1", null, rels(AUTHOR, single(PEOPLE, "p1"))) | FlatUnregisteredRelationshipsArticle | MappingDiagnostic.UNSUPPORTED_RELATIONSHIP_TARGET | "/relationships/author/data" | Person
    "unregistered to-many relationship target" | resource(ARTICLES, "1", null, rels(COMMENTS, collection(COMMENTS, ["c1"]))) | FlatUnregisteredRelationshipsArticle | MappingDiagnostic.UNSUPPORTED_RELATIONSHIP_TARGET | "/relationships/comments/data" | List
    "identifier cannot be coerced" | resource(ARTICLES, "not-a-number", null, null) | FlatIntIdArticle | MappingDiagnostic.IDENTIFIER_CONVERSION_FAILED | "/id" | FlatIntIdArticle
    "required creator input is absent" | resource(THINGS, "1", attrs("title", "present"), null) | FlatRequiredThing | MappingDiagnostic.MISSING_CREATOR_INPUT | "/attributes/required" | FlatRequiredThing
    "creator rejects supplied value" | resource(THINGS, "1", attrs("title", "boom"), null) | FlatThrowingCreatorThing | MappingDiagnostic.MISSING_CREATOR_INPUT | null | FlatThrowingCreatorThing
    "attribute value cannot be coerced" | resource(THINGS, "1", [count: [nested: 1]], null) | FlatCountedThing | MappingDiagnostic.UNSUPPORTED_ATTRIBUTE_VALUE | "/attributes/count" | FlatCountedThing
    "scalar whole-meta target" | resource(ARTICLES, "1", null, null) | WholeMetaTargetFixtures.ScalarMetaArticle | MappingDiagnostic.INVALID_META_TARGET | "/meta" | WholeMetaTargetFixtures.ScalarMetaArticle
    "list whole-meta target" | resource(ARTICLES, "1", null, null) | WholeMetaTargetFixtures.ListMetaArticle | MappingDiagnostic.INVALID_META_TARGET | "/meta" | WholeMetaTargetFixtures.ListMetaArticle
    "UUID whole-meta target" | resource(ARTICLES, "1", null, null) | WholeMetaTargetFixtures.UuidMetaArticle | MappingDiagnostic.INVALID_META_TARGET | "/meta" | WholeMetaTargetFixtures.UuidMetaArticle
    "java.time whole-meta target" | resource(ARTICLES, "1", null, null) | WholeMetaTargetFixtures.InstantMetaArticle | MappingDiagnostic.INVALID_META_TARGET | "/meta" | WholeMetaTargetFixtures.InstantMetaArticle
    "URI whole-meta target" | resource(ARTICLES, "1", null, null) | WholeMetaTargetFixtures.UriMetaArticle | MappingDiagnostic.INVALID_META_TARGET | "/meta" | WholeMetaTargetFixtures.UriMetaArticle
    "collection linkage on to-one" | resource(ARTICLES, "1", null, rels(AUTHOR, collection(PEOPLE, ["p1", "p2"]))) | FlatArticle | MappingDiagnostic.RELATIONSHIP_CARDINALITY_MISMATCH | "/relationships/author/data" | ResourceIdentifier
    "null linkage on to-many" | resource(ARTICLES, "1", null, rels(COMMENTS, Relationship.withData(RelationshipData.NullLinkage.INSTANCE))) | FlatArticle | MappingDiagnostic.RELATIONSHIP_CARDINALITY_MISMATCH | "/relationships/comments/data" | List
    "single linkage on to-many" | resource(ARTICLES, "1", null, rels(COMMENTS, single(COMMENTS, "c1"))) | FlatArticle | MappingDiagnostic.RELATIONSHIP_CARDINALITY_MISMATCH | "/relationships/comments/data" | List
    "empty collection linkage on to-one" | resource(ARTICLES, "1", null, rels(AUTHOR, collection(PEOPLE, []))) | FlatArticle | MappingDiagnostic.RELATIONSHIP_CARDINALITY_MISMATCH | "/relationships/author/data" | ResourceIdentifier
    "getter-only attribute" | resource("getter-only", "1", attrs("title", "supplied"), null) | DirectionalityReadFixtures.GetterOnly | MappingDiagnostic.NON_DESERIALIZABLE_PROPERTY | "/attributes/title" | DirectionalityReadFixtures.GetterOnly
    "getter-only identifier from id" | resource("getter-only-id", "supplied", null, null) | DirectionalityReadFixtures.GetterOnlyIdentifier | MappingDiagnostic.NON_DESERIALIZABLE_PROPERTY | "/id" | DirectionalityReadFixtures.GetterOnlyIdentifier
    "getter-only local-id from lid" | resourceWithLid("getter-only-lid", "client-lid", null) | LocalIdFixtures.GetterOnlyLocalId | MappingDiagnostic.NON_DESERIALIZABLE_PROPERTY | "/lid" | LocalIdFixtures.GetterOnlyLocalId
  }

  def "a construction failure whose Jackson path starts at the supplied id reclassifies as identifier conversion failure"() {
    given:
    // The converter parses successfully, but the returned value cannot coerce into the id
    // property's type during whole-bean construction.
    def converter = new IdentifierConverter() {
          @Override
          String convert(Object idValue) {
            idValue.toString()
          }

          @Override
          Object parse(String wireIdentifier) {
            Map.of("type", wireIdentifier)
          }
        }
    def localBinder = JsonApiJackson2.resourceBinder(JsonMapper.builder().build(), converter)

    when:
    localBinder.fromResource(resource(ARTICLES, "42", null, null), FlatIntIdArticle)

    then:
    def ex = thrown(JsonApiMappingException)
    ex.diagnostic() == MappingDiagnostic.IDENTIFIER_CONVERSION_FAILED
    ex.propertyPath() == "/id"
    ex.resourceClass() == FlatIntIdArticle
  }

  def "throwing converter produces the expected diagnostic"() {
    given:
    def converter = new IdentifierConverter() {
          @Override
          String convert(Object idValue) {
            idValue.toString()
          }

          @Override
          Object parse(String wireIdentifier) {
            throw new IllegalArgumentException("bad id")
          }
        }
    def localBinder = JsonApiJackson2.resourceBinder(JsonMapper.builder().build(), converter)

    when:
    localBinder.fromResource(resource(ARTICLES, "42", null, null), FlatIntIdArticle)

    then:
    def ex = thrown(JsonApiMappingException)
    ex.diagnostic() == MappingDiagnostic.IDENTIFIER_CONVERSION_FAILED
    ex.propertyPath() == "/id"
    ex.resourceClass() == Integer
  }

  def "null-returning converter produces the expected diagnostic"() {
    given:
    def converter = new IdentifierConverter() {
          @Override
          String convert(Object idValue) {
            idValue.toString()
          }

          @Override
          Object parse(String wireIdentifier) {
            null
          }
        }
    def localBinder = JsonApiJackson2.resourceBinder(JsonMapper.builder().build(), converter)

    when:
    localBinder.fromResource(resource(ARTICLES, "42", null, null), FlatIntIdArticle)

    then:
    def ex = thrown(JsonApiMappingException)
    ex.diagnostic() == MappingDiagnostic.IDENTIFIER_CONVERSION_FAILED
    ex.propertyPath() == "/id"
    ex.resourceClass() == Integer
  }

  def "resource collection validates every element type"() {
    given:
    def resources = [
      resource(ARTICLES, "1", null, null),
      resource(PEOPLE, "p1", null, null)
    ]

    when:
    binder.fromResources(resources, FlatArticle)

    then:
    def ex = thrown(JsonApiMappingException)
    ex.diagnostic() == MappingDiagnostic.RESOURCE_TYPE_MISMATCH
    ex.propertyPath() == "/type"
    ex.resourceClass() == FlatArticle
  }

  def "naming strategy renames bound attribute keys"() {
    given:
    def jsonMapper = JsonMapper.builder()
        .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
        .build()
    def localBinder = JsonApiJackson2.resourceBinder(jsonMapper)
    def resource = resource("words", "1", [long_field_name: 10, other_value: 42], null)

    when:
    def thing = localBinder.fromResource(resource, FlatWords)

    then:
    thing.longFieldName == 10
    thing.otherValue == 42
  }

  def "mix-in attribute name is honored"() {
    given:
    def jsonMapper = JsonMapper.builder()
        .addMixIn(FlatNamedThing, FlatMixInDef)
        .build()
    def localBinder = JsonApiJackson2.resourceBinder(jsonMapper)
    def resource = resource("named", "1", ["custom-name": "hello"], null)

    when:
    def thing = localBinder.fromResource(resource, FlatNamedThing)

    then:
    thing.value == "hello"
  }

  def "crossed direction-specific names retain their logical read mappings"() {
    given:
    def namingStrategy = new DirectionalityReadFixtures.CrossedDirectionPropertyNamingStrategy()
    def mapper = JsonMapper.builder().propertyNamingStrategy(namingStrategy).build()
    def localBinder = JsonApiJackson2.resourceBinder(mapper)
    def resource = resource(
        "crossed-names",
        "1",
        ["attribute-wire": "bound"],
        rels("relationship-wire", single(PEOPLE, "p1")))

    when:
    def dto = localBinder.fromResource(resource, DirectionalityReadFixtures.CrossedNames)

    then:
    dto.id == "1"
    dto.attributeValue() == "bound"
    dto.relationshipValue() == ResourceIdentifier.of(PEOPLE, "p1")
  }

  def "custom deserializer applies to attribute value"() {
    given:
    def localBinder = JsonApiJackson2.resourceBinder(JsonMapper.builder().build())
    def resource = resource("things", "1", [title: "hello"], null)

    when:
    def thing = localBinder.fromResource(resource, FlatLoudThing)

    then:
    thing.title == "HELLO"
  }

  def "setter-only mapped property is supported by ordinary flat reads"() {
    given:
    def localBinder = JsonApiJackson2.resourceBinder(JsonMapper.builder().build())

    when:
    def dto = localBinder.fromResource(resource("setter-only", "1", [title: "bound"], null),
    DirectionalityReadFixtures.SetterOnly)

    then:
    dto.id == "1"
    dto.titleValue() == "bound"
  }

  def "creator-only mapped property is supported by ordinary flat reads"() {
    given:
    def localBinder = JsonApiJackson2.resourceBinder(JsonMapper.builder().build())

    when:
    def dto = localBinder.fromResource(resource("creator-only", "1", [title: "bound"], null),
    DirectionalityReadFixtures.CreatorOnly)

    then:
    dto.idValue() == "1"
    dto.titleValue() == "bound"
  }

  def "supplied injection-only creator property is rejected"() {
    given:
    def mapper = JsonMapper.builder()
        .injectableValues(new InjectableValues.Std().addValue("injected-title", "injected"))
        .build()
    def localBinder = JsonApiJackson2.resourceBinder(mapper)

    when:
    localBinder.fromResource(
        resource("injection-only", "1", [title: "supplied"], null),
        DirectionalityReadFixtures.InjectionOnly)

    then:
    def ex = thrown(JsonApiMappingException)
    ex.diagnostic() == MappingDiagnostic.NON_DESERIALIZABLE_PROPERTY
    ex.propertyPath() == "/attributes/title"
    ex.resourceClass() == DirectionalityReadFixtures.InjectionOnly
  }

  def "omitted injection-only creator property still uses Jackson injection"() {
    given:
    def mapper = JsonMapper.builder()
        .injectableValues(new InjectableValues.Std().addValue("injected-title", "injected"))
        .build()
    def localBinder = JsonApiJackson2.resourceBinder(mapper)

    when:
    def dto = localBinder.fromResource(
        resource("injection-only", "1", null, null),
        DirectionalityReadFixtures.InjectionOnly)

    then:
    dto.idValue() == "1"
    dto.titleValue() == "injected"
  }

  def "root type information does not hide effective flat-read properties"() {
    given:
    def localBinder = JsonApiJackson2.resourceBinder(JsonMapper.builder().build())

    when:
    def dto = localBinder.fromResource(
        resource("root-typed", "1", [title: "bound"], null),
        DirectionalityReadFixtures.RootTyped)

    then:
    dto.id == "1"
    dto.title == "bound"
  }

  def "Jackson write-only mapped property is supported by ordinary flat reads"() {
    given:
    def localBinder = JsonApiJackson2.resourceBinder(JsonMapper.builder().build())

    when:
    def dto = localBinder.fromResource(resource("write-only", "1", [title: "bound"], null),
    DirectionalityReadFixtures.WriteOnly)

    then:
    dto.id == "1"
    dto.titleValue() == "bound"
  }

  def "supplied getter-only mapped property is rejected instead of silently discarded"() {
    given:
    def localBinder = JsonApiJackson2.resourceBinder(JsonMapper.builder().build())

    when:
    localBinder.fromResource(resource("getter-only", "1", [title: "supplied"], null),
    DirectionalityReadFixtures.GetterOnly)

    then:
    def ex = thrown(JsonApiMappingException)
    ex.diagnostic() == MappingDiagnostic.NON_DESERIALIZABLE_PROPERTY
    ex.propertyPath() == "/attributes/title"
    ex.resourceClass() == DirectionalityReadFixtures.GetterOnly
  }

  def "supplied getter-only identifier is rejected at /id"() {
    given:
    def localBinder = JsonApiJackson2.resourceBinder(JsonMapper.builder().build())

    when:
    localBinder.fromResource(resource("getter-only-id", "supplied", null, null),
        DirectionalityReadFixtures.GetterOnlyIdentifier)

    then:
    def ex = thrown(JsonApiMappingException)
    ex.diagnostic() == MappingDiagnostic.NON_DESERIALIZABLE_PROPERTY
    ex.propertyPath() == "/id"
    ex.resourceClass() == DirectionalityReadFixtures.GetterOnlyIdentifier
  }

  def "supplied getter-only local-id is rejected at /lid"() {
    given:
    def localBinder = JsonApiJackson2.resourceBinder(JsonMapper.builder().build())
    def resource = resourceWithLid("getter-only-lid", "client-lid", null)

    when:
    localBinder.fromResource(resource, LocalIdFixtures.GetterOnlyLocalId)

    then:
    def ex = thrown(JsonApiMappingException)
    ex.diagnostic() == MappingDiagnostic.NON_DESERIALIZABLE_PROPERTY
    ex.propertyPath() == "/lid"
    ex.resourceClass() == LocalIdFixtures.GetterOnlyLocalId
  }

  def "supplied property excluded by the default deserialization view is rejected"() {
    given:
    def mapper = JsonMapper.builder().build()
    mapper.setConfig(mapper.getDeserializationConfig().withView(DirectionalityReadFixtures.IncludedInReadView))
    def localBinder = JsonApiJackson2.resourceBinder(mapper)

    when:
    localBinder.fromResource(
        resource("view-restricted", "1", [hidden: "supplied"], null),
        DirectionalityReadFixtures.ViewRestricted)

    then:
    def ex = thrown(JsonApiMappingException)
    ex.diagnostic() == MappingDiagnostic.NON_DESERIALIZABLE_PROPERTY
    ex.propertyPath() == "/attributes/hidden"
    ex.resourceClass() == DirectionalityReadFixtures.ViewRestricted
  }

  def "Jackson 2 permissive primitive-null default applies unchanged"() {
    given:
    def localBinder = JsonApiJackson2.resourceBinder(JsonMapper.builder().build())

    when:
    def thing = localBinder.fromResource(
        resource(THINGS, "1", nullableAttr("count"), null), FlatCountedThing)

    then:
    thing.count == 0
  }

  def "strict primitive-null configuration preserves the caller's coercion policy"() {
    given:
    def mapper = JsonMapper.builder()
        .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
        .build()
    def localBinder = JsonApiJackson2.resourceBinder(mapper)

    when:
    localBinder.fromResource(resource(THINGS, "1", nullableAttr("count"), null), FlatCountedThing)

    then:
    def ex = thrown(JsonApiMappingException)
    ex.diagnostic() == MappingDiagnostic.UNSUPPORTED_ATTRIBUTE_VALUE
    ex.propertyPath() == "/attributes/count"
    ex.resourceClass() == FlatCountedThing
  }

  def "nested construction failure path translates through the nested attribute shape"() {
    when:
    binder.fromResource(resource(NESTED, "1", [point: [x: "boom"]], null), FlatNestedThing)

    then:
    def ex = thrown(JsonApiMappingException)
    ex.diagnostic() == MappingDiagnostic.UNSUPPORTED_ATTRIBUTE_VALUE
    ex.propertyPath() == "/attributes/point/x"
    ex.resourceClass() == FlatNestedThing
  }

  def "property null provider applies to the explicitly bound null value"() {
    when:
    def thing = binder.fromResource(
        resource("null-provider-things", "1", nullableAttr("title"), null),
        FlatNullProviderThing)

    then: // the property's null provider, not the String deserializer's null value
    thing.titleValue() == ""
  }

  def "JavaType entry points bind resource and collection"() {
    given:
    def localBinder = JsonApiJackson2.resourceBinder(JsonMapper.builder().build())
    def javaType = JsonMapper.builder().build().constructType(FlatArticle)

    when:
    def article = localBinder.fromResource(resource(ARTICLES, "1", [title: "T"], null), javaType)
    def articles = localBinder.fromResources(
        [
          resource(ARTICLES, "1", null, null),
          resource(ARTICLES, "2", null, null)
        ], javaType)

    then:
    article instanceof FlatArticle
    (article as FlatArticle).title() == "T"
    articles.size() == 2
  }

  def "generic relationship target resolves through the parameterized JavaType"() {
    given:
    def mapper = JsonMapper.builder().build()
    def localBinder = JsonApiJackson2.resourceBinder(mapper)
    def javaType =
        mapper.typeFactory.constructParametricType(GenericArticle, ResourceIdentifier)
    def resource = resource(ARTICLES, "1", null, [author: single(PEOPLE, "p1")])

    when:
    def dto = localBinder.fromResource(resource, javaType)

    then:
    dto instanceof GenericArticle
    ((GenericArticle) dto).author() == ResourceIdentifier.of(PEOPLE, "p1")
  }

  def "generic attribute resolves through the parameterized JavaType"() {
    given:
    def mapper = JsonMapper.builder().build()
    def localBinder = JsonApiJackson2.resourceBinder(mapper)
    def javaType = mapper.typeFactory.constructParametricType(GenericValue, String)
    def resource = resource(THINGS, "1", [value: "bound"], null)

    when:
    def dto = localBinder.fromResource(resource, javaType)

    then:
    dto instanceof GenericValue
    ((GenericValue) dto).value() == "bound"
  }

  def "registered linkage mapper binds to-one single linkage and to-many collection"() {
    given:
    def mapper = { RelationshipData data, JavaType target ->
      if (data instanceof RelationshipData.SingleLinkage) {
        def identifier = ((RelationshipData.SingleLinkage) data).identifier()
        return new FlatAuthor(identifier.type(), identifier.id())
      }
      ((RelationshipData.IdentifierCollectionLinkage) data).identifiers().collect {
        new FlatAuthor(it.type(), it.id())
      }
    } as RelationshipLinkageMapper
    def localBinder = JsonApiJackson2.resourceBinder(
        JsonMapper.builder().build(), IdentifierConverter.defaults(), [(FlatAuthor): mapper])
    def resource = resource(
        ARTICLES, "1", null,
        [author: single(PEOPLE, "p1"),
          contributors: collection(PEOPLE, ["p1", "p2"])])

    when:
    def article = localBinder.fromResource(resource, FlatMappedArticle)

    then:
    article.author() == new FlatAuthor(PEOPLE, "p1")
    article.contributors() == [
      new FlatAuthor(PEOPLE, "p1"),
      new FlatAuthor(PEOPLE, "p2")
    ]
  }

  def "mapper receives Optional-unwrapped to-one type and collection to-many type"() {
    given:
    def seenTypes = []
    def mapper = { RelationshipData data, JavaType target ->
      seenTypes.add(target)
      if (data instanceof RelationshipData.SingleLinkage) {
        def identifier = ((RelationshipData.SingleLinkage) data).identifier()
        return new FlatAuthor(identifier.type(), identifier.id())
      }
      ((RelationshipData.IdentifierCollectionLinkage) data).identifiers().collect {
        new FlatAuthor(it.type(), it.id())
      }
    } as RelationshipLinkageMapper
    def localBinder = JsonApiJackson2.resourceBinder(
        JsonMapper.builder().build(), IdentifierConverter.defaults(), [(FlatAuthor): mapper])
    def resource = resource(
        ARTICLES, "1", null,
        [author: single(PEOPLE, "p1"),
          contributors: collection(PEOPLE, ["p1", "p2"])])

    when:
    def article = localBinder.fromResource(resource, FlatMappedOptionalArticle)

    then:
    seenTypes*.rawClass == [FlatAuthor, List]
    article.author == Optional.of(new FlatAuthor(PEOPLE, "p1"))
    article.contributors == [
      new FlatAuthor(PEOPLE, "p1"),
      new FlatAuthor(PEOPLE, "p2")
    ]
  }

  def "NullLinkage and empty linkage short-circuit without invoking the mapper"() {
    given:
    def invoked = false
    def mapper = { RelationshipData data, JavaType target ->
      invoked = true
      return null
    } as RelationshipLinkageMapper
    def localBinder = JsonApiJackson2.resourceBinder(
        JsonMapper.builder().build(), IdentifierConverter.defaults(), [(FlatAuthor): mapper])
    def resource = resource(
        ARTICLES, "1", null,
        [author: Relationship.withData(RelationshipData.NullLinkage.INSTANCE),
          contributors: Relationship.withData(RelationshipData.IdentifierCollectionLinkage.empty())])

    when:
    def article = localBinder.fromResource(resource, FlatMappedArticle)

    then:
    article.author() == null
    article.contributors() == []
    !invoked
  }

  def "cardinality is enforced before the mapper is invoked"() {
    given:
    def invoked = false
    def mapper = { RelationshipData data, JavaType target ->
      invoked = true
      return null
    } as RelationshipLinkageMapper
    def localBinder = JsonApiJackson2.resourceBinder(
        JsonMapper.builder().build(), IdentifierConverter.defaults(), [(FlatAuthor): mapper])
    def resource = resource(
        ARTICLES, "1", null,
        [author: collection(PEOPLE, ["p1"]),
          contributors: single(PEOPLE, "p1")])

    when:
    localBinder.fromResource(resource, FlatMappedArticle)

    then:
    def ex = thrown(JsonApiMappingException)
    ex.diagnostic() == MappingDiagnostic.RELATIONSHIP_CARDINALITY_MISMATCH
    !invoked
  }

  def "mapper exception is reported as LINKAGE_MAPPING_FAILED"() {
    given:
    def mapper = { RelationshipData data, JavaType target ->
      throw new IllegalStateException("boom")
    } as RelationshipLinkageMapper
    def localBinder = JsonApiJackson2.resourceBinder(
        JsonMapper.builder().build(), IdentifierConverter.defaults(), [(FlatAuthor): mapper])
    def resource = resource(ARTICLES, "1", null, [author: single(PEOPLE, "p1")])

    when:
    localBinder.fromResource(resource, FlatMappedArticle)

    then:
    def ex = thrown(JsonApiMappingException)
    ex.diagnostic() == MappingDiagnostic.LINKAGE_MAPPING_FAILED
    ex.propertyPath() == "/relationships/author/data"
  }

  def "mapper returning null binds null property"() {
    given:
    def mapper = { RelationshipData data, JavaType target ->
      null
    } as RelationshipLinkageMapper
    def localBinder = JsonApiJackson2.resourceBinder(
        JsonMapper.builder().build(), IdentifierConverter.defaults(), [(FlatAuthor): mapper])
    def resource = resource(ARTICLES, "1", null, [author: single(PEOPLE, "p1")])

    when:
    def article = localBinder.fromResource(resource, FlatMappedArticle)

    then:
    article.author() == null
  }

  def "the binder's Optional fallback stays on the derived mapper; the caller mapper is not upgraded"() {
    given:
    def caller = JsonMapper.builder().build()

    when:
    JsonApiJackson2.resourceBinder(caller)
    caller.readValue('"probe"', Optional)

    then:
    def ex = thrown(InvalidDefinitionException)
    ex.message.contains("jackson-datatype-jdk8")
  }

  def "caller-supplied Optional deserialization is preserved without the fallback"() {
    given:
    // A caller-configured Optional deserializer reads a wrapped shape; the adapter must not
    // replace it with the JDK 8 module (which only reads unwrapped scalar shapes).
    def callerMapper = JsonMapper.builder()
        .addModule(new SimpleModule("optional-wrapped")
        .addDeserializer(Optional, new WrappedOptionalDeserializer()))
        .build()
    def localBinder = JsonApiJackson2.resourceBinder(callerMapper)
    def resource = resource("wrapped-optionals", "1", [subtitle: [wrapped: "Sub"]], null)

    when:
    def article = localBinder.fromResource(resource, FlatWrappedOptionalArticle)

    then:
    article.subtitleValue() == Optional.of("Sub")
  }

  private static ResourceObject resource(String type, String id, Map attrs, Map rels) {
    new ResourceObject(
        type,
        id,
        null,
        attrs == null ? null : Attributes.ofAttributes(attrs),
        rels == null ? null : Relationships.ofRelationships(rels),
        null,
        null,
        Map.of())
  }

  private static ResourceObject resourceWithLid(String type, String lid, Map attrs) {
    new ResourceObject(
        type,
        null,
        lid,
        attrs == null ? null : Attributes.ofAttributes(attrs),
        null,
        null,
        null,
        Map.of())
  }

  private static ResourceObject resourceWithMeta(Map attrs, Map rels, Meta meta) {
    new ResourceObject(
        ARTICLES,
        "1",
        null,
        attrs == null ? null : Attributes.ofAttributes(attrs),
        rels == null ? null : Relationships.ofRelationships(rels),
        null,
        meta,
        Map.of())
  }

  private static JsonApiDocument document(ResourceObject resource, List<ResourceObject> included) {
    new JsonApiDocument(
        new DocumentData.SingleResource(resource),
        null,
        null,
        null,
        null,
        included,
        Map.of())
  }

  private static Relationship single(String type, String id) {
    Relationship.withData(new RelationshipData.SingleLinkage(ResourceIdentifier.of(type, id)))
  }

  private static Relationship collection(String type, List<String> ids) {
    Relationship.withData(
        new RelationshipData.IdentifierCollectionLinkage(
        ids.collect { ResourceIdentifier.of(type, it) }))
  }

  private static Relationship collectionWithIdentifierMeta(
      String type, List<String> ids, List<Meta> metas) {
    List<ResourceIdentifier> identifiers = new ArrayList<>(ids.size())
    for (int i = 0; i < ids.size(); i++) {
      identifiers.add(identifier(type, ids.get(i), metas.get(i)))
    }
    Relationship.withData(new RelationshipData.IdentifierCollectionLinkage(identifiers))
  }

  private static Relationship toOneWithIdentifierMeta(String type, String id, Meta meta) {
    new Relationship(
        new RelationshipData.SingleLinkage(identifier(type, id, meta)),
        null,
        null,
        Map.of())
  }

  private static Map relsWithMeta(Relationship relationship, Meta meta) {
    [(AUTHOR): new Relationship(relationship.data(), relationship.links(), meta, Map.of())]
  }

  private static ResourceIdentifier identifier(String type, String id, Meta meta) {
    new ResourceIdentifier(type, id, null, meta, Map.of())
  }

  private static Map attrs(Object... keyValues) {
    Map attributes = new LinkedHashMap()
    for (int i = 0; i < keyValues.length; i += 2) {
      attributes.put(keyValues[i], keyValues[i + 1])
    }
    attributes
  }

  private static Map nullableAttr(String name) {
    [(name): null]
  }

  private static Map rels(Object... keyValues) {
    Map relationships = new LinkedHashMap()
    for (int i = 0; i < keyValues.length; i += 2) {
      relationships.put(keyValues[i], keyValues[i + 1])
    }
    relationships
  }

  private static ResourceObject primaryResource(JsonApiDocument document) {
    def data = document.data()
    assert data instanceof DocumentData.SingleResource
    ((DocumentData.SingleResource) data).resource()
  }

  @JsonApiResource(type = "nested-things")
  static class FlatNestedThing {
    @JsonApiId String id
    @JsonApiAttribute NestedPoint point

    String idValue() {
      return id
    }
  }

  static class NestedPoint {
    private int x
    private int y

    int getX() {
      return x
    }

    void setX(int x) {
      this.x = x
    }

    int getY() {
      return y
    }

    void setY(int y) {
      this.y = y
    }
  }

  @JsonApiResource(type = "null-provider-things")
  static class FlatNullProviderThing {
    @JsonApiId String id

    @JsonSetter(nulls = Nulls.AS_EMPTY)
    @JsonApiAttribute
    private String title

    String titleValue() {
      return title
    }
  }

  @JsonApiResource(type = "wrapped-optionals")
  static class FlatWrappedOptionalArticle {
    @JsonApiId String id
    @JsonApiAttribute Optional<String> subtitle

    String idValue() {
      return id
    }

    Optional<String> subtitleValue() {
      return subtitle
    }
  }

  static class WrappedOptionalDeserializer extends JsonDeserializer<Optional<String>> {
    @Override
    Optional<String> deserialize(JsonParser parser, DeserializationContext context) {
      String wrapped = null
      JsonToken token = parser.nextToken()
      while (token != null && token != JsonToken.END_OBJECT) {
        if (token == JsonToken.FIELD_NAME && "wrapped" == parser.currentName()) {
          wrapped = parser.nextTextValue()
        }
        token = parser.nextToken()
      }
      Optional.ofNullable(wrapped)
    }
  }

  @JsonApiResource(type = "words")
  static class FlatWords {
    @JsonApiId String id
    @JsonApiAttribute int longFieldName
    @JsonApiAttribute int otherValue
  }

  @JsonApiResource(type = "named")
  static class FlatNamedThing {
    @JsonApiId String id
    String value
  }

  abstract static class FlatMixInDef {
    @JsonApiAttribute @JsonProperty("custom-name")
    abstract String getValue()
  }

  static class UppercaseDeserializer extends StdDeserializer<String> {
    UppercaseDeserializer() {
      super(String.class)
    }

    @Override
    String deserialize(JsonParser parser, DeserializationContext context) {
      parser.getValueAsString().toUpperCase(Locale.ROOT)
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
