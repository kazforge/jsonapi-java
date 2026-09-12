package com.kazforge.jsonapi.core.model

import com.kazforge.jsonapi.core.validation.JsonApiValidationException
import com.kazforge.jsonapi.core.validation.LinksContext
import com.kazforge.jsonapi.core.validation.ValidationRuleCode
import spock.lang.Specification

class LinkSpec extends Specification {

  def "links expose context-specific standard members and flatten empty values"() {
    expect:
    Links.empty().isEmpty()
    Links.empty().flatten().isEmpty()
    Links.standardMembers(LinksContext.TOP_LEVEL).contains("self")
    Links.standardMembers(LinksContext.RESOURCE).contains("self")
    Links.standardMembers(LinksContext.RELATIONSHIP).contains("related")
    Links.standardMembers(LinksContext.ERROR).contains("about")
    Links.ofLinks([self: new Link.StringLink("https://example.com")])
    .hasStandardMember("self", LinksContext.RESOURCE)
  }

  def "links compare by typed and additional members"() {
    given:
    def link = new Link.StringLink("https://example.com")
    def first = Links.ofLinks([self: link])
    def equal = Links.ofLinks([self: link])
    def differentEntries = Links.ofLinks([related: link])
    def differentAdditional = Links.of([self: link], ["ext:flag": true])

    expect:
    first == first
    first == equal
    first.hashCode() == equal.hashCode()
    first != differentEntries
    first != differentAdditional
    first != (Object) "links"
    !first.isEmpty()
    !differentAdditional.isEmpty()
  }

  def "object link factory preserves an omitted describedby link"() {
    when:
    def link = Link.ObjectLink.ofHref("https://example.com")

    then:
    link.href() == "https://example.com"
    link.describedby() == null
  }

  def "object link describedby accepts string and object links recursively"() {
    given:
    def stringDescription = new Link.StringLink("https://example.com/schema")
    def objectDescription = new Link.ObjectLink(
        "https://example.com/description", null, stringDescription, null, null, null, null, [:])

    when:
    def link = new Link.ObjectLink(
        "https://example.com/resource", null, objectDescription, null, null, null, null, [:])

    then:
    link.describedby() == objectDescription
    ((Link.ObjectLink) link.describedby()).describedby() == stringDescription
  }

  def "hreflang canonical list representation accepts single language"() {
    when:
    def link = Link.ObjectLink.withHreflang("http://example.com", "en")

    then:
    link.hreflang() == ["en"]
  }

  def "hreflang accepts multiple languages"() {
    when:
    def link = Link.ObjectLink.withHreflang("http://example.com", ["en", "de"])

    then:
    link.hreflang() == ["en", "de"]
  }

  def "invalid href fails with stable rule code"() {
    when:
    new Link.StringLink("not a valid uri with spaces")

    then:
    def ex = thrown(JsonApiValidationException)
    ex.ruleCode() == ValidationRuleCode.INVALID_URI_REFERENCE
    ex.jsonPointer().contains("href")
  }

  def "invalid language tag fails"() {
    when:
    new Link.ObjectLink("http://example.com", null, null, null, null, ["invalid tag!"], null, [:])

    then:
    def ex = thrown(JsonApiValidationException)
    ex.ruleCode() == ValidationRuleCode.INVALID_LANGUAGE_TAG
  }

  def "invalid link relation fails"() {
    when:
    new Link.ObjectLink("http://example.com", "_bad", null, null, null, null, null, [:])

    then:
    def ex = thrown(JsonApiValidationException)
    ex.ruleCode() == ValidationRuleCode.INVALID_LINK_RELATION
  }

  def "invalid media type fails"() {
    when:
    new Link.ObjectLink("http://example.com", null, null, null, "not-a-type", null, null, [:])

    then:
    def ex = thrown(JsonApiValidationException)
    ex.ruleCode() == ValidationRuleCode.INVALID_MEDIA_TYPE
  }

  def "nullable pagination links are preserved"() {
    when:
    def links = Links.ofLinks([
      first: new Link.StringLink("http://example.com/1"),
      next : null
    ])

    then:
    links.links().containsKey("next")
    links.links().get("next") == null
  }

  def "object link preserves extension and at additional members"() {
    when:
    def link = new Link.ObjectLink(
        "http://example.com", null, null, null, null, null, null,
        ["ext:flag": true, "@context": "https://example.com/ctx"])

    then:
    link.additionalMembers()["ext:flag"] == true
    link.additionalMembers()["@context"] == "https://example.com/ctx"
  }

  def "object link rejects reserved additional member names"() {
    when:
    new Link.ObjectLink("http://example.com", null, null, null, null, null, null, [href: "x"])

    then:
    def ex = thrown(JsonApiValidationException)
    ex.ruleCode() == ValidationRuleCode.RESERVED_FIELD_NAME
  }

  def "links input map is defensively copied"() {
    given:
    def input = [self: new Link.StringLink("http://example.com")]
    def links = Links.ofLinks(input)

    when:
    input.next = new Link.StringLink("http://example.com/2")

    then:
    !links.links().containsKey("next")
  }

  def "at members are rejected as semantic link keys"() {
    when:
    Links.ofLinks(["@context": new Link.StringLink("http://example.com")])

    then:
    def ex = thrown(JsonApiValidationException)
    ex.ruleCode() == ValidationRuleCode.RESERVED_FIELD_NAME
  }

  def "invalid link relation name is rejected at construction"() {
    when:
    Links.ofLinks(["has_underscore": new Link.StringLink("http://example.com")])

    then:
    def ex = thrown(JsonApiValidationException)
    ex.ruleCode() == ValidationRuleCode.INVALID_LINK_RELATION
  }

  def "extension-shaped link keys are accepted locally"() {
    when:
    def links = Links.ofLinks(["ext:custom": new Link.StringLink("http://example.com")])

    then:
    links.links().containsKey("ext:custom")
  }

  def "at members are accepted via additional members"() {
    when:
    def links = Links.of([:], ["@context": "https://example.com/ctx"])

    then:
    links.additionalMembers()["@context"] == "https://example.com/ctx"
    links.flatten().containsKey("@context")
  }

  def "links rejects reserved additional member '#name'"(String name) {
    when:
    Links.of([:], [(name): 42])

    then:
    def ex = thrown(JsonApiValidationException)
    ex.ruleCode() == ValidationRuleCode.RESERVED_FIELD_NAME
    ex.jsonPointer() == "/links/" + name

    where:
    name << [
      "self",
      "related",
      "describedby",
      "first",
      "last",
      "prev",
      "next",
      "about",
      "type"
    ]
  }

  def "reserved names remain valid in the typed links map"(String name) {
    when:
    def links = Links.ofLinks([(name): new Link.StringLink("http://example.com")])

    then:
    links.links().containsKey(name)
    links.links().get(name) instanceof Link.StringLink

    where:
    name << [
      "self",
      "related",
      "describedby",
      "first",
      "last",
      "prev",
      "next",
      "about",
      "type"
    ]
  }

  def "non-reserved overlapping keys collide between maps"() {
    when:
    Links.of(
        ["ext:custom": new Link.StringLink("http://example.com")],
        ["ext:custom": true])

    then:
    def ex = thrown(JsonApiValidationException)
    ex.ruleCode() == ValidationRuleCode.MEMBER_NAME_COLLISION
  }

  def "reserved name in both maps fails as reserved before collision"() {
    when:
    Links.of(
        [self: new Link.StringLink("http://example.com")],
        [self: 42])

    then:
    def ex = thrown(JsonApiValidationException)
    ex.ruleCode() == ValidationRuleCode.RESERVED_FIELD_NAME
    ex.jsonPointer() == "/links/self"
  }
}
