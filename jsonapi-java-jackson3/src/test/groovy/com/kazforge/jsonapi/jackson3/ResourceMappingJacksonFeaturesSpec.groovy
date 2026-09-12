package com.kazforge.jsonapi.jackson3

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import com.kazforge.jsonapi.annotation.JsonApiAttribute
import com.kazforge.jsonapi.annotation.JsonApiId
import com.kazforge.jsonapi.annotation.JsonApiResource
import com.kazforge.jsonapi.jackson.mapping.IdentifierConverter
import com.kazforge.jsonapi.jackson.diagnostic.JsonApiMappingException
import com.kazforge.jsonapi.jackson.diagnostic.MappingDiagnostic
import com.kazforge.jsonapi.jackson3.JacksonFeatureFixtures.ArticleWithFormattedTitle
import com.kazforge.jsonapi.jackson3.JacksonFeatureFixtures.CreatorBasedArticle
import com.kazforge.jsonapi.jackson3.JacksonFeatureFixtures.FormattedTitle
import com.kazforge.jsonapi.fixtures.domainwrite.Article
import spock.lang.Specification
import tools.jackson.databind.PropertyNamingStrategies
import tools.jackson.databind.json.JsonMapper

// Jackson 3 mechanism probes: mix-ins, @JsonIgnore, naming strategies, @JsonCreator, custom
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
    def mapper = JsonApiJackson3.resourceMapper(JsonMapper.builder().build())
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
    def mapper = JsonApiJackson3.resourceMapper(jsonMapper)
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
    def mapper = JsonApiJackson3.resourceMapper(jsonMapper)
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
    def mapper = JsonApiJackson3.resourceMapper(JsonMapper.builder().build())
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
    def mapper = JsonApiJackson3.resourceMapper(JsonMapper.builder().build())
    def article = new ArticleWithFormattedTitle("1", new FormattedTitle("Hello"))

    when:
    def resource = mapper.toResource(article)

    then:
    resource.attributes().attributes().title == "[FORMATTED] Hello"
  }

  def "identifier converter returning null is rejected"() {
    given:
    def converter = new IdentifierConverter() {
          @Override
          String convert(Object idValue) {
            return null
          }
        }
    def mapper = JsonApiJackson3.resourceMapper(JsonMapper.builder().build(), converter)
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
    def mapper = JsonApiJackson3.resourceMapper(JsonMapper.builder().build(), converter)
    def article = new Article("1", "T", "B", List.of(), null)

    when:
    mapper.toResource(article)

    then:
    thrown(IllegalArgumentException)
  }
}
