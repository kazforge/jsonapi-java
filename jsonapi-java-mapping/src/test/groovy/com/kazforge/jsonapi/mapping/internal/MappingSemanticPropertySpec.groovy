package com.kazforge.jsonapi.mapping.internal

import spock.lang.Specification

class MappingSemanticPropertySpec extends Specification {

  def "keeps the stable role names"() {
    expect:
    PropertyRole.values()*.name() == [
      'ID',
      'LOCAL_ID',
      'ATTRIBUTE',
      'RELATIONSHIP',
      'RESOURCE_META',
      'RELATIONSHIP_META'
    ]
  }

  def "carries the distinct logical, backend external, and JSON:API names for every role"() {
    when:
    def property = new SemanticProperty(role, logicalName, externalName, jsonapiName)

    then:
    property.role() == role
    property.logicalName() == logicalName
    property.externalName() == externalName
    property.jsonapiName() == jsonapiName

    where:
    role                           | logicalName  | externalName    | jsonapiName
    PropertyRole.ID                | 'blogId'     | 'blog_id'       | 'id'
    PropertyRole.LOCAL_ID          | 'localId'    | 'wire-local-id' | 'lid'
    PropertyRole.ATTRIBUTE         | 'headline'   | 'wire-headline' | 'wire-headline'
    PropertyRole.RELATIONSHIP      | 'authorRef'  | 'author'        | 'author'
    PropertyRole.RESOURCE_META     | 'metaState'  | 'meta'          | 'meta'
    PropertyRole.RELATIONSHIP_META | 'authorMeta' | 'author-meta'   | 'author'
  }

  def "rejects a JSON:API name that violates its role invariant"() {
    when:
    new SemanticProperty(role, 'logical', 'external', jsonapiName)

    then:
    def failure = thrown(IllegalArgumentException)
    failure.message == expectedMessage

    where:
    role                           | jsonapiName | expectedMessage
    PropertyRole.ID                | 'blog_id'   | "ID JSON:API name must be 'id', was 'blog_id'"
    PropertyRole.LOCAL_ID          | 'tmp'       | "LOCAL_ID JSON:API name must be 'lid', was 'tmp'"
    PropertyRole.RESOURCE_META     | 'metadata'  | "RESOURCE_META JSON:API name must be 'meta', was 'metadata'"
    PropertyRole.ATTRIBUTE         | 'other'     | "ATTRIBUTE JSON:API name must equal the backend external name 'external', was 'other'"
    PropertyRole.RELATIONSHIP      | 'other'     | "RELATIONSHIP JSON:API name must equal the backend external name 'external', was 'other'"
    PropertyRole.RELATIONSHIP_META | ''          | "RELATIONSHIP_META is valid only in resolved form and requires a non-empty target JSON:API name"
  }

  def "requires every component"() {
    when:
    new SemanticProperty(role, logicalName, externalName, jsonapiName)

    then:
    thrown(NullPointerException)

    where:
    role            | logicalName | externalName | jsonapiName
    null            | 'logical'   | 'external'   | 'id'
    PropertyRole.ID | null        | 'external'   | 'id'
    PropertyRole.ID | 'logical'   | null         | 'id'
    PropertyRole.ID | 'logical'   | 'external'   | null
  }
}
