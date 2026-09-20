package com.kazforge.jsonapi.mapping.internal

import spock.lang.Specification

class MappingPropertyDefinitionSpec extends Specification {

  def "id mapping descriptor requires the JSON API id member name"() {
    when:
    new MappingPropertyDefinition<>(
        "handle",
        "identifier",
        "backend_id",
        "backend_id",
        MappingRole.ID,
        String,
        false)

    then:
    def ex = thrown(IllegalArgumentException)
    ex.message.contains("wire member 'id'")
  }

  def "local-id mapping descriptor requires the JSON API lid member name"() {
    when:
    new MappingPropertyDefinition<>(
        "handle",
        "localIdentifier",
        "backend_lid",
        "backend_lid",
        MappingRole.LOCAL_ID,
        String,
        false)

    then:
    def ex = thrown(IllegalArgumentException)
    ex.message.contains("wire member 'lid'")
  }

  def "identity mapping descriptor keeps logical backend and JSON API names distinct"() {
    expect:
    def id = new MappingPropertyDefinition<>(
        "handle",
        "identifier",
        "backend_id",
        "id",
        MappingRole.ID,
        String,
        false)
    id.logicalName() == "identifier"
    id.externalName() == "backend_id"
    id.jsonapiName() == "id"
  }

  def "id binding descriptor requires the JSON API id member name"() {
    when:
    new BindingPropertyDefinition<>(
        "handle",
        "identifier",
        "backend_id",
        "backend_id",
        MappingRole.ID,
        String,
        true)

    then:
    def ex = thrown(IllegalArgumentException)
    ex.message.contains("wire member 'id'")
  }

  def "local-id binding descriptor requires the JSON API lid member name"() {
    when:
    new BindingPropertyDefinition<>(
        "handle",
        "localIdentifier",
        "backend_lid",
        "backend_lid",
        MappingRole.LOCAL_ID,
        String,
        true)

    then:
    def ex = thrown(IllegalArgumentException)
    ex.message.contains("wire member 'lid'")
  }

  def "identity binding descriptor keeps logical backend and JSON API names distinct"() {
    expect:
    def id = new BindingPropertyDefinition<>(
        "handle",
        "identifier",
        "backend_id",
        "id",
        MappingRole.ID,
        String,
        true)
    id.logicalName() == "identifier"
    id.externalName() == "backend_id"
    id.jsonapiName() == "id"
  }
}
