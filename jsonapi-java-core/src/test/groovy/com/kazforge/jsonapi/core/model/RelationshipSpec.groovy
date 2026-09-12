package com.kazforge.jsonapi.core.model

import com.kazforge.jsonapi.core.validation.JsonApiValidationException
import com.kazforge.jsonapi.core.validation.ValidationRuleCode
import spock.lang.Specification

class RelationshipSpec extends Specification {

  def "relationship supports absent, null, single, and collection linkage"() {
    given:
    def absent = Relationship.linkOnly(Links.ofLinks([self: new Link.StringLink("http://example.com")]))
    def explicitNull = Relationship.withData(RelationshipData.NullLinkage.INSTANCE)
    def single = Relationship.withData(
        new RelationshipData.SingleLinkage(ResourceIdentifier.of("people", "9")))
    def collection = Relationship.withData(
        RelationshipData.IdentifierCollectionLinkage.empty())

    expect:
    !absent.hasDataMember()
    explicitNull.hasDataMember()
    explicitNull.data() instanceof RelationshipData.NullLinkage
    single.data() instanceof RelationshipData.SingleLinkage
    collection.data() instanceof RelationshipData.IdentifierCollectionLinkage
  }

  def "link-only and meta-only relationships are valid"() {
    when:
    def linkOnly = Relationship.linkOnly(Links.ofLinks([
      related: new Link.StringLink("http://example.com/comments")
    ]))
    def metaOnly = Relationship.metaOnly(Meta.of([total: 0]))

    then:
    !linkOnly.hasDataMember()
    linkOnly.links() != null
    !metaOnly.hasDataMember()
    metaOnly.meta() != null
  }

  def "empty relationship is rejected"() {
    when:
    new Relationship(null, null, null, [:])

    then:
    def ex = thrown(JsonApiValidationException)
    ex.ruleCode() == ValidationRuleCode.MISSING_RELATIONSHIP_MEMBER
  }

  def "links-only relationship with empty links is rejected"() {
    when:
    Relationship.linkOnly(Links.empty())

    then:
    def ex = thrown(JsonApiValidationException)
    ex.ruleCode() == ValidationRuleCode.MISSING_RELATIONSHIP_MEMBER
  }

  def "extension-only relationship link qualifies locally"() {
    when:
    def linkOnly = Relationship.linkOnly(Links.ofLinks([
      "ext:custom": new Link.StringLink("http://example.com/ext")
    ]))

    then:
    !linkOnly.hasDataMember()
    linkOnly.links().links().containsKey("ext:custom")
  }

  def "pagination-only links-only relationship is rejected"() {
    when:
    Relationship.linkOnly(Links.ofLinks([
      first: new Link.StringLink("http://example.com/c?page=1")
    ]))

    then:
    def ex = thrown(JsonApiValidationException)
    ex.ruleCode() == ValidationRuleCode.MISSING_RELATIONSHIP_MEMBER
  }

  def "profile relation qualifies links-only relationship locally"() {
    when:
    def linkOnly = Relationship.linkOnly(Links.ofLinks([
      canonical: new Link.StringLink("http://example.com/canonical")
    ]))

    then:
    !linkOnly.hasDataMember()
    linkOnly.links().links().containsKey("canonical")
  }

  def "extension additional members qualify relationship without links"() {
    when:
    def relationship = new Relationship(null, null, null, ["ext:flag": true])

    then:
    relationship.additionalMembers().containsKey("ext:flag")
  }

  def "relationship rejects reserved additional member '#name'"(String name) {
    when:
    new Relationship(
        RelationshipData.NullLinkage.INSTANCE, null, null, [(name): true])

    then:
    def ex = thrown(JsonApiValidationException)
    ex.ruleCode() == ValidationRuleCode.RESERVED_FIELD_NAME
    ex.jsonPointer() == "/relationships/" + name

    where:
    name << ["data", "links", "meta"]
  }

  def "single linkage rejects null identifier"() {
    when:
    new RelationshipData.SingleLinkage(null)

    then:
    def ex = thrown(JsonApiValidationException)
    ex.ruleCode() == ValidationRuleCode.NULL_REQUIRED_VALUE
    ex.jsonPointer() == "/relationships/data"
  }

  def "null identifier collection payload is rejected"() {
    when:
    new RelationshipData.IdentifierCollectionLinkage(null)

    then:
    def ex = thrown(JsonApiValidationException)
    ex.ruleCode() == ValidationRuleCode.NULL_COLLECTION_PAYLOAD
  }

  def "null identifier collection elements are rejected"() {
    when:
    new RelationshipData.IdentifierCollectionLinkage([
      ResourceIdentifier.of("people", "1"),
      null
    ])

    then:
    def ex = thrown(JsonApiValidationException)
    ex.ruleCode() == ValidationRuleCode.NULL_COLLECTION_ELEMENT
    ex.jsonPointer() == "/relationships/data/1"
  }
}
