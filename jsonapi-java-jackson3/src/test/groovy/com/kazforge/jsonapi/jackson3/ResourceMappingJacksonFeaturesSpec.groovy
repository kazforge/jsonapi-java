package com.kazforge.jsonapi.jackson3

import com.fasterxml.jackson.annotation.JsonProperty
import com.kazforge.jsonapi.annotation.JsonApiAttribute
import com.kazforge.jsonapi.annotation.JsonApiId
import com.kazforge.jsonapi.annotation.JsonApiResource
import com.kazforge.jsonapi.jackson3.JacksonFeatureFixtures.ArticleWithFormattedTitle
import com.kazforge.jsonapi.jackson3.JacksonFeatureFixtures.CreatorBasedArticle
import com.kazforge.jsonapi.jackson3.JacksonFeatureFixtures.FormattedTitle
import spock.lang.Specification
import tools.jackson.databind.PropertyNamingStrategies
import tools.jackson.databind.json.JsonMapper

// Jackson 3 mechanism probes: mix-ins, naming strategies, @JsonCreator, and custom serializers.
class ResourceMappingJacksonFeaturesSpec extends Specification {

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

  def "custom ValueSerializer is honored by property-scoped serialization"() {
    given:
    def mapper = JsonApiJackson3.resourceMapper(JsonMapper.builder().build())
    def article = new ArticleWithFormattedTitle("1", new FormattedTitle("Hello"))

    when:
    def resource = mapper.toResource(article)

    then:
    resource.attributes().attributes().title == "[FORMATTED] Hello"
  }
}
