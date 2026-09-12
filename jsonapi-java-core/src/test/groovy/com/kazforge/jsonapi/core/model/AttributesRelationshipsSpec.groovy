package com.kazforge.jsonapi.core.model

import com.kazforge.jsonapi.core.validation.JsonApiValidationException
import com.kazforge.jsonapi.core.validation.ValidationRuleCode
import spock.lang.Specification

class AttributesRelationshipsSpec extends Specification {

  def "attribute and relationship wrappers preserve values and flatten members"() {
    given:
    def attributes = Attributes.ofAttributes([title: "t"])
    def relationship = Relationship.withData(
        new RelationshipData.SingleLinkage(ResourceIdentifier.of("people", "9")))
    def relationships = Relationships.of([author: relationship], ["ext:flag": true])

    expect:
    attributes == Attributes.ofAttributes([title: "t"])
    attributes.hashCode() == Attributes.ofAttributes([title: "t"]).hashCode()
    attributes.flatten().title == "t"
    relationships == Relationships.of([author: relationship], ["ext:flag": true])
    relationships.flatten().author == relationship
    relationships.flatten()["ext:flag"] == true
  }

  def "present-empty attributes differs from absent attributes"() {
    given:
    def withEmpty = new ResourceObject("articles", "1", null, Attributes.empty(), null, null, null, [:])
    def withAbsent = ResourceObject.of("articles", "1")

    expect:
    withEmpty.attributes() != null
    withEmpty.attributes().isEmpty()
    withAbsent.attributes() == null
  }

  def "extension and at members in attributes are pass-through"() {
    when:
    def attrs = Attributes.of([title: "T"], ["@context": "http://example.com", "ext:name": "v"])

    then:
    attrs.attributes() == [title: "T"]
    attrs.additionalMembers().containsKey("@context")
    attrs.additionalMembers().containsKey("ext:name")
  }

  def "extension member cannot be a semantic attribute name"() {
    when:
    Attributes.ofAttributes(["ext:flag": true])

    then:
    def ex = thrown(JsonApiValidationException)
    ex.ruleCode() == ValidationRuleCode.RESERVED_FIELD_NAME
  }

  def "extension member cannot be a semantic relationship name"() {
    when:
    Relationships.ofRelationships([
      "ext:author": Relationship.metaOnly(Meta.of([n: 1]))
    ])

    then:
    def ex = thrown(JsonApiValidationException)
    ex.ruleCode() == ValidationRuleCode.RESERVED_FIELD_NAME
  }

  def "at member cannot be an attribute name"() {
    when:
    Attributes.ofAttributes(["@illegal": "v"])

    then:
    def ex = thrown(JsonApiValidationException)
    ex.ruleCode() == ValidationRuleCode.RESERVED_FIELD_NAME
  }

  def "reserved field names are rejected in attributes"() {
    when:
    Attributes.ofAttributes([type: "articles"])

    then:
    def ex = thrown(JsonApiValidationException)
    ex.ruleCode() == ValidationRuleCode.RESERVED_FIELD_NAME
  }

  def "non-reserved resource field names are allowed as attributes"() {
    when:
    def attrs = Attributes.ofAttributes([lid: "local", meta: 1, links: "x", attributes: "y"])

    then:
    attrs.attributes().lid == "local"
    attrs.attributes().meta == 1
  }

  def "non-reserved resource field names are allowed as relationships"() {
    when:
    def rels = Relationships.ofRelationships([
      meta : Relationship.metaOnly(Meta.of([n: 1])),
      links: Relationship.withData(RelationshipData.NullLinkage.INSTANCE),
      lid  : Relationship.linkOnly(Links.ofLinks([self: new Link.StringLink("http://example.com")]))
    ])

    then:
    rels.relationships().containsKey("meta")
    rels.relationships().containsKey("links")
    rels.relationships().containsKey("lid")
  }

  def "null relationship values are rejected"() {
    given:
    def input = new LinkedHashMap<String, Relationship>()
    input.put("author", null)

    when:
    Relationships.ofRelationships(input)

    then:
    def ex = thrown(JsonApiValidationException)
    ex.ruleCode() == ValidationRuleCode.NULL_RELATIONSHIP_VALUE
    ex.jsonPointer() == "/relationships/author"
  }

  def "attribute and relationship name collision is rejected"() {
    when:
    new ResourceObject(
        "articles", "1", null,
        Attributes.ofAttributes([author: "a"]),
        Relationships.ofRelationships([author: Relationship.withData(
          new RelationshipData.SingleLinkage(ResourceIdentifier.of("people", "9")))]),
        null, null, [:])

    then:
    def ex = thrown(JsonApiValidationException)
    ex.ruleCode() == ValidationRuleCode.MEMBER_NAME_COLLISION
  }

  def "reserved field names are rejected in relationships"() {
    when:
    Relationships.ofRelationships([type: Relationship.metaOnly(Meta.of([x: 1]))])

    then:
    def ex = thrown(JsonApiValidationException)
    ex.ruleCode() == ValidationRuleCode.RESERVED_FIELD_NAME
  }

  def "flatten rejects collisions between groups"() {
    when:
    Attributes.of([title: "T"], [title: "duplicate"])

    then:
    def ex = thrown(JsonApiValidationException)
    ex.ruleCode() == ValidationRuleCode.MEMBER_NAME_COLLISION
  }

  def "present-empty relationships differs from absent relationships"() {
    given:
    def withEmpty = new ResourceObject("articles", "1", null, null, Relationships.empty(), null, null, [:])
    def withAbsent = ResourceObject.of("articles", "1")

    expect:
    withEmpty.relationships() != null
    withEmpty.relationships().isEmpty()
    withAbsent.relationships() == null
  }

  def "extension and at members in relationships are pass-through"() {
    when:
    def rels = Relationships.of([:], ["@context": "http://example.com", "ext:name": "v"])

    then:
    rels.additionalMembers().containsKey("@context")
    rels.additionalMembers().containsKey("ext:name")
  }

  def "extension and at members in links are pass-through"() {
    when:
    def links = Links.of([:], ["@context": "http://example.com", "ext:name": "v"])

    then:
    links.additionalMembers().containsKey("@context")
    links.additionalMembers().containsKey("ext:name")
  }

  def "attributes and links preserve explicit null values on flatten"() {
    when:
    def attrs = Attributes.ofAttributes([title: null, body: "x"])
    def links = Links.ofLinks([next: null, self: new Link.StringLink("http://example.com")])

    then:
    attrs.flatten().containsKey("title")
    attrs.flatten().title == null
    links.flatten().containsKey("next")
    links.flatten().next == null
  }

  def "resource identifier rejects reserved additional member names"() {
    when:
    new ResourceIdentifier("articles", "1", null, null, [type: "x"])

    then:
    def ex = thrown(JsonApiValidationException)
    ex.ruleCode() == ValidationRuleCode.RESERVED_FIELD_NAME
  }

  def "identity keys distinguish id values that look like lid prefixes"() {
    expect:
    ResourceIdentifier.of("articles", "lid:temp").identityKey() !=
        ResourceIdentifier.withLid("articles", "temp").identityKey()
  }

  def "structured identity keys do not collide on embedded colons"() {
    expect:
    ResourceIdentity.ofId("a", "b:c") != ResourceIdentity.ofId("a:b", "c")
    ResourceIdentifier.of("a", "b:c").identityKey() != ResourceIdentifier.of("a:b", "c").identityKey()
  }

  def "empty and whitespace ids are present identifier values"() {
    when:
    def emptyId = ResourceIdentifier.of("articles", "")
    def blankId = new ResourceObject("articles", " ", null, null, null, null, null, [:])

    then:
    emptyId.hasId()
    emptyId.id() == ""
    blankId.hasId()
    blankId.id() == " "
  }
}
