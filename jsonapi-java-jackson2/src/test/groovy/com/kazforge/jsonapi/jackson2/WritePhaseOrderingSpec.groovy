package com.kazforge.jsonapi.jackson2

import com.fasterxml.jackson.databind.json.JsonMapper
import com.kazforge.jsonapi.annotation.JsonApiAttribute
import com.kazforge.jsonapi.annotation.JsonApiId
import com.kazforge.jsonapi.annotation.JsonApiMeta
import com.kazforge.jsonapi.annotation.JsonApiRelationship
import com.kazforge.jsonapi.annotation.JsonApiRelationshipMeta
import com.kazforge.jsonapi.annotation.JsonApiResource
import com.kazforge.jsonapi.core.model.ResourceIdentifier
import com.kazforge.jsonapi.diagnostic.JsonApiMappingException
import com.kazforge.jsonapi.diagnostic.MappingDiagnostic
import spock.lang.Specification

/**
 * Focused write-phase ordering characterization: adapter-owned meta phases keep their position
 * relative to the basic member reads delegated to the shared writer.
 */
class WritePhaseOrderingSpec extends Specification {

  def "validates whole-meta targets before reading basic members"() {
    given:
    def mapper = JsonApiJackson2.resourceMapper(JsonMapper.builder().build())

    when:
    mapper.toResource(new InvalidMetaTargetWithFailingAttribute("1", "T", "scalar"))

    then:
    def failure = thrown(JsonApiMappingException)
    failure.diagnostic() == MappingDiagnostic.INVALID_META_TARGET
    failure.propertyPath() == '/meta'
  }

  def "applies each relationship's meta before reading later relationships"() {
    given:
    def mapper = JsonApiJackson2.resourceMapper(JsonMapper.builder().build())

    when:
    mapper.toResource(
        new RelationshipMetaBeforeLaterRead(
        '1', ResourceIdentifier.of('people', 'p1'), ['': 'bad'], null))

    then:
    def failure = thrown(JsonApiMappingException)
    failure.diagnostic() == MappingDiagnostic.INVALID_META_TARGET
    failure.propertyPath() == '/relationships/author/meta'
  }

  @JsonApiResource(type = 'ordering-articles')
  static final class InvalidMetaTargetWithFailingAttribute {

    @JsonApiId
    private final String id

    @JsonApiAttribute
    private final String title

    @JsonApiMeta
    private final String meta

    InvalidMetaTargetWithFailingAttribute(String id, String title, String meta) {
      this.id = id
      this.title = title
      this.meta = meta
    }

    String getId() {
      id
    }

    String getTitle() throws IOException {
      throw new IOException('title read failure for ' + id)
    }

    String getMeta() {
      meta
    }
  }

  @JsonApiResource(type = 'ordering-articles')
  static final class RelationshipMetaBeforeLaterRead {

    @JsonApiId
    private final String id

    @JsonApiRelationship
    private final ResourceIdentifier author

    @JsonApiRelationshipMeta(relationship = 'author')
    private final Map<String, Object> authorMeta

    @JsonApiRelationship
    private final String comments

    RelationshipMetaBeforeLaterRead(
    String id, ResourceIdentifier author, Map<String, Object> authorMeta, String comments) {
      this.id = id
      this.author = author
      this.authorMeta = authorMeta
      this.comments = comments
    }

    String getId() {
      id
    }

    ResourceIdentifier getAuthor() {
      author
    }

    Map<String, Object> getAuthorMeta() {
      authorMeta
    }

    String getComments() throws IOException {
      throw new IOException('comments read failure for ' + id)
    }
  }
}
