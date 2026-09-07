package io.github.kazemek.jsonapi.jackson2

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.json.JsonMapper
import io.github.kazemek.jsonapi.annotation.JsonApiAttribute
import io.github.kazemek.jsonapi.annotation.JsonApiId
import io.github.kazemek.jsonapi.annotation.JsonApiResource
import io.github.kazemek.jsonapi.jackson.diagnostic.JsonApiMappingException
import io.github.kazemek.jsonapi.jackson.diagnostic.MappingDiagnostic
import io.github.kazemek.jsonapi.jackson.mapping.IdentifierConverter
import io.github.kazemek.jsonapi.jackson2.JacksonFeatureFixtures.ArticleWithFormattedTitle
import io.github.kazemek.jsonapi.jackson2.JacksonFeatureFixtures.CreatorBasedArticle
import io.github.kazemek.jsonapi.jackson2.JacksonFeatureFixtures.FormattedTitle
import io.github.kazemek.jsonapi.fixtures.domainwrite.Article
import spock.lang.Specification

// Jackson 2 mechanism probes: mix-ins, @JsonIgnore, naming strategies, @JsonCreator, custom
// serializers, and identifier-converter wiring. Major-neutral Optional/array/inheritance/
// mixed-relationship semantics are exercised by direct adapter-owned cases.
class ResourceMappingJacksonFeaturesSpec extends Specification {

  @JsonApiResource(type = "things")
  static class ThingWithIgnored {
    @JsonApiId String id
    @JsonIgnore
    @JsonApiAttribute @JsonProperty("secret") String confidential
    @JsonApiAttribute String name
  }

  def "@JsonIgnore excludes property from mapping"() {
    given:
    def mapper = JsonApiJackson2.resourceMapper(JsonMapper.builder().build())
    def thing = new ThingWithIgnored(id: "1", confidential: "hidden", name: "visible")

    when:
    def resource = mapper.toResource(thing)

    then:
    resource.attributes().attributes().containsKey("name")
    !resource.attributes().attributes().containsKey("secret")
  }

  @JsonApiResource(type = "named")
  static class NamedThing {
    @JsonApiId String id
    String value
  }

  abstract static class MixInDef {
    @JsonApiAttribute @JsonProperty("custom-name")
    abstract String getValue()
  }

  def "mix-in resolves property-level annotation"() {
    given:
    def jsonMapper = JsonMapper.builder()
        .addMixIn(NamedThing, MixInDef)
        .build()
    def mapper = JsonApiJackson2.resourceMapper(jsonMapper)
    def thing = new NamedThing(id: "1", value: "hello")

    when:
    def resource = mapper.toResource(thing)

    then:
    resource.attributes().attributes().containsKey("custom-name")
  }

  def "naming strategy renames attributes"() {
    given:
    def jsonMapper = JsonMapper.builder()
        .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
        .build()
    def mapper = JsonApiJackson2.resourceMapper(jsonMapper)
    def thing = new ThingWithMultipleWords(id: "1", longFieldName: 10, otherValue: 42)

    when:
    def resource = mapper.toResource(thing)

    then:
    resource.attributes().attributes().containsKey("long_field_name")
    resource.attributes().attributes().containsKey("other_value")
  }

  @JsonApiResource(type = "words")
  static class ThingWithMultipleWords {
    @JsonApiId String id
    @JsonApiAttribute int longFieldName
    @JsonApiAttribute int otherValue
  }

  def "creator-based immutable POJO maps properties"() {
    given:
    def mapper = JsonApiJackson2.resourceMapper(JsonMapper.builder().build())
    def article = new CreatorBasedArticle("42", "Hello")

    when:
    def resource = mapper.toResource(article)

    then:
    resource.type() == "articles"
    resource.id() == "42"
    resource.attributes().attributes().title == "Hello"
  }

  def "custom ValueSerializer honored via convertValue"() {
    given:
    def mapper = JsonApiJackson2.resourceMapper(JsonMapper.builder().build())
    def article = new ArticleWithFormattedTitle("1", new FormattedTitle("Hello"))

    when:
    def resource = mapper.toResource(article)

    then:
    resource.attributes().attributes().title == "[FORMATTED] Hello"
  }

  def "unwrapping property with runtime POJO keeps the transformed member names"() {
    given:
    def mapper = JsonApiJackson2.resourceMapper(JsonMapper.builder().build())
    def thing =
        new JacksonFeatureFixtures.UnwrappedObjectThing("1", new JacksonFeatureFixtures.UnwrappedNestedValue())

    when:
    def resource = mapper.toResource(thing)

    then:
    // The raw path resolves the unwrapping serializer through the dynamic (declared-Object)
    // route. Property-scoped mapping reads the flattened name/value tokens back as one untyped
    // value — the flattened member name is consumed and the value lands under the member's wire
    // name, exactly as the Jackson 3 reference does for the same fixture.
    resource.attributes().attributes() == [details: "iv"]
  }

  def "custom introspector unwrapping transformer is honored without @JsonUnwrapped"() {
    given:
    def jacksonMapper = JsonMapper.builder()
        .annotationIntrospector(new JacksonFeatureFixtures.CustomUnwrappingIntrospector())
        .build()
    def mapper = JsonApiJackson2.resourceMapper(jacksonMapper)
    def thing =
        new JacksonFeatureFixtures.CustomUnwrappedThing("1", new JacksonFeatureFixtures.UnwrappedNestedValue())

    when:
    def resource = mapper.toResource(thing)

    then:
    // Same property-scoped collapse as above; the custom-introspector transformer drives the
    // dynamic-serializer resolution inside the raw path.
    resource.attributes().attributes() == [details: "iv"]
  }

  def "identifier converter returning null is rejected"() {
    given:
    def converter = new IdentifierConverter() {
          @Override
          String convert(Object idValue) {
            return null
          }
        }
    def mapper = JsonApiJackson2.resourceMapper(JsonMapper.builder().build(), converter)
    def article = new Article("1", "T", "B", List.of(), null)

    when:
    mapper.toResource(article)

    then:
    def ex = thrown(JsonApiMappingException)
    ex.diagnostic() == MappingDiagnostic.MISSING_IDENTIFIER
  }

  def "identifier converter throwing RuntimeException is propagated"() {
    given:
    def converter = new IdentifierConverter() {
          @Override
          String convert(Object idValue) {
            throw new IllegalArgumentException("bad id")
          }
        }
    def mapper = JsonApiJackson2.resourceMapper(JsonMapper.builder().build(), converter)
    def article = new Article("1", "T", "B", List.of(), null)

    when:
    mapper.toResource(article)

    then:
    thrown(IllegalArgumentException)
  }
}
