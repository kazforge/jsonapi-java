package com.kazforge.jsonapi.jackson2

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.json.JsonMapper
import com.kazforge.jsonapi.annotation.JsonApiId
import com.kazforge.jsonapi.annotation.JsonApiRelationship
import com.kazforge.jsonapi.annotation.JsonApiResource
import com.kazforge.jsonapi.core.model.DocumentData
import com.kazforge.jsonapi.fixtures.domainwrite.Person
import com.kazforge.jsonapi.jackson.diagnostic.JsonApiMappingException
import com.kazforge.jsonapi.jackson.diagnostic.MappingDiagnostic
import com.kazforge.jsonapi.jackson.representation.IncludePath
import com.kazforge.jsonapi.jackson.representation.IncludePolicy
import com.kazforge.jsonapi.jackson.representation.RepresentationPolicy
import com.kazforge.jsonapi.jackson.representation.RepresentationSelection
import spock.lang.Specification

class ConfiguredRelationshipIncludeSpec extends Specification {

  def "configured external relationship name drives linkage and compound inclusion"() {
    given:
    def mapper = JsonApiJackson2.resourceMapper(JsonMapper.builder().build())
    def article = new ExternalRelationshipArticle('a1', new Person('p1', 'Ada'))
    def selection = RepresentationSelection.builder().include(IncludePath.of('article-author')).build()
    def policy = RepresentationPolicy.defaults().withIncludePolicy(IncludePolicy.allowAll())

    when:
    def document = mapper.toDocument(article, null, selection, policy)
    def primary = (document.data() as DocumentData.SingleResource).resource()

    then:
    primary.relationships().relationships().keySet() == ['article-author'] as Set
    primary.relationships().relationships()['article-author'].data().identifier().type() == 'people'
    primary.relationships().relationships()['article-author'].data().identifier().id() == 'p1'
    document.included()*.type() == ['people']
    document.included()*.id() == ['p1']
  }

  def "the Java property name is not accepted as a wire include alias"() {
    given:
    def mapper = JsonApiJackson2.resourceMapper(JsonMapper.builder().build())
    def article = new ExternalRelationshipArticle('a1', new Person('p1', 'Ada'))
    def selection = RepresentationSelection.builder().include(IncludePath.of('author')).build()
    def policy = RepresentationPolicy.defaults().withIncludePolicy(IncludePolicy.allowAll())

    when:
    mapper.toDocument(article, null, selection, policy)

    then:
    def exception = thrown(JsonApiMappingException)
    exception.diagnostic() == MappingDiagnostic.INVALID_INCLUDE_PATH
    exception.resourceClass() == ExternalRelationshipArticle
  }

  @JsonApiResource(type = 'external-articles')
  static class ExternalRelationshipArticle {
    @JsonApiId
    String id

    @JsonApiRelationship
    @JsonProperty('article-author')
    Person author

    ExternalRelationshipArticle(String id, Person author) {
      this.id = id
      this.author = author
    }
  }
}
