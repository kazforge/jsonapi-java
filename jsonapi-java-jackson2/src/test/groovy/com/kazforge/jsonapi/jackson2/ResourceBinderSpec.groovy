package com.kazforge.jsonapi.jackson2

import com.kazforge.jsonapi.jackson2.mapping.RelationshipLinkageMapper

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.DeserializationFeature
import com.kazforge.jsonapi.annotation.JsonApiAttribute
import com.kazforge.jsonapi.annotation.JsonApiId
import com.kazforge.jsonapi.annotation.JsonApiResource
import com.kazforge.jsonapi.core.model.Attributes
import com.kazforge.jsonapi.core.model.Relationship
import com.kazforge.jsonapi.core.model.RelationshipData
import com.kazforge.jsonapi.core.model.Relationships
import com.kazforge.jsonapi.core.model.ResourceIdentifier
import com.kazforge.jsonapi.core.model.ResourceObject
import com.kazforge.jsonapi.diagnostic.JsonApiMappingException
import com.kazforge.jsonapi.diagnostic.MappingDiagnostic
import com.kazforge.jsonapi.mapping.IdentifierConverter
import com.kazforge.jsonapi.fixtures.domainread.FlatArticle
import com.kazforge.jsonapi.fixtures.domainread.FlatCountedThing
import com.kazforge.jsonapi.fixtures.domainread.FlatInheritedBlog
import com.kazforge.jsonapi.fixtures.domainread.FlatIntIdArticle
import com.kazforge.jsonapi.fixtures.domainread.FlatUnregisteredRelationshipsArticle
import com.kazforge.jsonapi.jackson2.LinkageMapperFixtures.FlatAuthor
import com.kazforge.jsonapi.jackson2.LinkageMapperFixtures.FlatMappedArticle
import com.kazforge.jsonapi.jackson2.LinkageMapperFixtures.FlatMappedOptionalArticle
import com.kazforge.jsonapi.fixtures.generic.ParameterizedBindingFixtures.GenericArticle
import com.kazforge.jsonapi.fixtures.generic.ParameterizedBindingFixtures.GenericValue
import spock.lang.Shared
import spock.lang.Specification

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
import com.fasterxml.jackson.databind.exc.MismatchedInputException
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
  private static final String THINGS = "things"

  @Shared
  JsonApiResourceBinder binder = JsonApiJackson2.resourceBinder(JsonMapper.builder().build())

  def "binds a resource directly"() {
    when:
    def actual = binder.fromResource(
        resource(ARTICLES, "1", attrs("title", "Hello", "body-text", "Content"),
        rels(AUTHOR, single(PEOPLE, "p1"), COMMENTS, collection(COMMENTS, ["c1", "c2"]))),
        FlatArticle)

    then:
    actual == new FlatArticle("1", "Hello", "Content", ResourceIdentifier.of(PEOPLE, "p1"), [
      ResourceIdentifier.of(COMMENTS, "c1"),
      ResourceIdentifier.of(COMMENTS, "c2")
    ])
  }

  def "binds inherited identity and attribute properties"() {
    when:
    def actual = binder.fromResource(
        resource("blogs", "b1", attrs("name", "My Blog", "description", "A description"), null),
        FlatInheritedBlog)

    then:
    actual == new FlatInheritedBlog("b1", "My Blog", "A description")
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

  def "Jackson 2 permissive primitive-null default applies"() {
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

  def "nested construction path walking uses the effective deserialization type, not the declared type"() {
    when:
    binder.fromResource(
        resource("refined-nested", "1", [profile: [geo: [lon: "boom"]]], null),
        FlatRefinedNestedThing)

    then:
    def ex = thrown(JsonApiMappingException)
    ex.diagnostic() == MappingDiagnostic.UNSUPPORTED_ATTRIBUTE_VALUE
    ex.propertyPath() == "/attributes/profile/geo/lon"
    ex.resourceClass() == FlatRefinedNestedThing
  }

  def "nested construction path stops at a property-scoped custom deserializer boundary"() {
    when:
    binder.fromResource(
        resource("boundary-nested", "1", [profile: [custom: [whatever: 1]]], null),
        FlatBoundaryNestedThing)

    then:
    def ex = thrown(JsonApiMappingException)
    ex.diagnostic() == MappingDiagnostic.UNSUPPORTED_ATTRIBUTE_VALUE
    ex.propertyPath() == "/attributes/profile/custom"
    ex.resourceClass() == FlatBoundaryNestedThing
  }

  def "nested construction path walks through an Optional attribute container"() {
    when:
    binder.fromResource(
        resource("optional-nested", "1", [point: [x: "boom"]], null), FlatOptionalNestedThing)

    then:
    def ex = thrown(JsonApiMappingException)
    ex.diagnostic() == MappingDiagnostic.UNSUPPORTED_ATTRIBUTE_VALUE
    ex.propertyPath() == "/attributes/point/x"
    ex.resourceClass() == FlatOptionalNestedThing
  }

  def "an unannotated required creator input absent from the synthetic input is a missing creator input"() {
    when:
    binder.fromResource(
        resource("unmapped-required-things", "1", null, null), FlatUnmappedRequiredThing)

    then:
    def ex = thrown(JsonApiMappingException)
    ex.diagnostic() == MappingDiagnostic.MISSING_CREATOR_INPUT
    ex.propertyPath() == null
    ex.resourceClass() == FlatUnmappedRequiredThing
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

  def "unregistered relationship target still fails for null and empty linkage before short-circuiting"() {
    when:
    binder.fromResource(
        resource(ARTICLES, "1", null,
        [author: Relationship.withData(RelationshipData.NullLinkage.INSTANCE)]),
        FlatUnregisteredRelationshipsArticle)

    then:
    def nullFailure = thrown(JsonApiMappingException)
    nullFailure.diagnostic() == MappingDiagnostic.UNSUPPORTED_RELATIONSHIP_TARGET
    nullFailure.propertyPath() == "/relationships/author/data"

    when:
    binder.fromResource(
        resource(ARTICLES, "1", null,
        [comments: Relationship.withData(RelationshipData.IdentifierCollectionLinkage.empty())]),
        FlatUnregisteredRelationshipsArticle)

    then:
    def emptyFailure = thrown(JsonApiMappingException)
    emptyFailure.diagnostic() == MappingDiagnostic.UNSUPPORTED_RELATIONSHIP_TARGET
    emptyFailure.propertyPath() == "/relationships/comments/data"
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

  private static Relationship single(String type, String id) {
    Relationship.withData(new RelationshipData.SingleLinkage(ResourceIdentifier.of(type, id)))
  }

  private static Relationship collection(String type, List<String> ids) {
    Relationship.withData(
        new RelationshipData.IdentifierCollectionLinkage(
        ids.collect { ResourceIdentifier.of(type, it) }))
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

  @JsonApiResource(type = "refined-nested")
  static class FlatRefinedNestedThing {
    @JsonApiId String id
    @JsonApiAttribute FlatProfile profile
  }

  static class FlatProfile {
    @JsonDeserialize(as = FlatSubGeo.class)
    FlatGeo geo
  }

  static class FlatGeo {
    int lat
  }

  static class FlatSubGeo extends FlatGeo {
    int lon
  }

  @JsonApiResource(type = "boundary-nested")
  static class FlatBoundaryNestedThing {
    @JsonApiId String id
    @JsonApiAttribute FlatBoundaryProfile profile
  }

  @JsonApiResource(type = "optional-nested")
  static class FlatOptionalNestedThing {
    @JsonApiId String id
    @JsonApiAttribute Optional<NestedPoint> point
  }

  static class FlatBoundaryProfile {
    @JsonDeserialize(using = BoundaryGeoDeserializer)
    FlatGeo custom
  }

  static class BoundaryGeoDeserializer extends JsonDeserializer<FlatGeo> {
    @Override
    FlatGeo deserialize(JsonParser parser, DeserializationContext context) {
      def failure = MismatchedInputException.from(parser, FlatGeo, "custom boundary")
      failure.prependPath(FlatGeo, "lat")
      throw failure
    }
  }

  @JsonApiResource(type = "unmapped-required-things")
  static class FlatUnmappedRequiredThing {
    private final String id
    private final String secret

    @JsonCreator
    FlatUnmappedRequiredThing(
    @JsonProperty("id") @JsonApiId String id,
    @JsonProperty(value = "secret", required = true) String secret) {
      this.id = id
      this.secret = secret
    }

    String idValue() {
      return id
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
