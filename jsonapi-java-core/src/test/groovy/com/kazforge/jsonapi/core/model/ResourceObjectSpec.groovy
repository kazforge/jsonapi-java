package com.kazforge.jsonapi.core.model

import com.kazforge.jsonapi.core.validation.JsonApiValidationException
import com.kazforge.jsonapi.core.validation.ValidationRuleCode
import spock.lang.Specification

class ResourceObjectSpec extends Specification {

  def "resource copy method #method replaces only its component and supports clearing"() {
    given:
    def base = new ResourceObject("articles", "1", "local-1",
        Attributes.ofAttributes([title: "original", missing: null]),
        Relationships.ofRelationships([author: Relationship.metaOnly(Meta.of([role: "author"]))]),
        Links.ofLinks([self: null]), Meta.of([source: "import", missing: null]),
        ["ext:source": "legacy", "@missing": null])
    def originalValues = ResourceObject.recordComponents.collect { it.accessor.invoke(base) }
    def expectedValues = new ArrayList(originalValues)
    expectedValues[index] = replacement
    def clearedValues = new ArrayList(originalValues)
    clearedValues[index] = index == 7 ? [:] : null

    when:
    ResourceObject changed = base."$method"(replacement)
    def cleared = changed."$method"(null)

    then:
    changed == new ResourceObject(*expectedValues)
    !changed.is(base)
    ResourceObject.recordComponents.collect { it.accessor.invoke(base) } == originalValues
    changed.id() == "1"
    changed.lid() == (method == "withLid" ? replacement : "local-1")
    changed.additionalMembers().containsKey("@missing") || method == "withAdditionalMembers"
    cleared == new ResourceObject(*clearedValues)

    where:
    method                  | index | replacement
    "withLid"               | 2     | "local-2"
    "withAttributes"        | 3     | Attributes.empty()
    "withRelationships"     | 4     | Relationships.empty()
    "withLinks"             | 5     | Links.empty()
    "withMeta"              | 6     | Meta.empty()
    "withAdditionalMembers" | 7     | ["@replacement": null]
  }

  def "resource helpers compose from type-only and preserve defensive JSON-null snapshots"() {
    given:
    def members = ["@details": [values: [1, null]], "@missing": null]

    when:
    def resource = ResourceObject.ofType("articles").withLid("local-1")
        .withAttributes(Attributes.ofAttributes([title: null]))
        .withAdditionalMembers(members)
    members["@details"].values.add(2)
    members["@missing"] = "changed"

    then:
    resource.id() == null
    resource.identityKey() == ResourceIdentity.ofLid("articles", "local-1")
    resource.attributes().attributes().containsKey("title")
    resource.attributes().attributes().title == null
    resource.additionalMembers() == ["@details": [values: [1, null]], "@missing": null]
    resource.toIdentifier().additionalMembers() == resource.additionalMembers()

    when:
    resource.additionalMembers().put("@other", 1)

    then:
    thrown(UnsupportedOperationException)

    when:
    resource.additionalMembers()["@details"].values.add(3)

    then:
    thrown(UnsupportedOperationException)
  }

  def "resource copy methods retain canonical namespace validation in either order"() {
    given:
    def attributes = Attributes.ofAttributes([author: "name"])
    def relationships = Relationships.ofRelationships([author: Relationship.metaOnly(Meta.empty())])

    when:
    ResourceObject.of("articles", "1").withAttributes(attributes).withRelationships(relationships)

    then:
    def ex = thrown(JsonApiValidationException)
    ex.ruleCode() == ValidationRuleCode.MEMBER_NAME_COLLISION
    ex.jsonPointer() == "/data/relationships/author"

    when:
    ResourceObject.of("articles", "1").withRelationships(relationships).withAttributes(attributes)

    then:
    def reverse = thrown(JsonApiValidationException)
    reverse.ruleCode() == ex.ruleCode()
    reverse.jsonPointer() == ex.jsonPointer()
  }

  def "additional-member replacement retains canonical validation for #members"() {
    when:
    ResourceObject.of("articles", "1").withAdditionalMembers(members)

    then:
    def ex = thrown(JsonApiValidationException)
    ex.ruleCode() == code
    ex.jsonPointer() == pointer

    where:
    members     | code                                    | pointer
    [type: "x"] | ValidationRuleCode.RESERVED_FIELD_NAME  | "/data/type"
    ["_bad": 1] | ValidationRuleCode.INVALID_MEMBER_NAME  | "/data/_bad"
  }

  def "identifier meta copies preserve both identities and JSON-null additional members"() {
    given:
    def original = new ResourceIdentifier("articles", "1", "local-1", Meta.empty(), ["@missing": null])
    def meta = Meta.of([role: "editor", missing: null])

    when:
    def changed = original.withMeta(meta)

    then:
    changed == new ResourceIdentifier("articles", "1", "local-1", meta, ["@missing": null])
    original.meta() == Meta.empty()
    changed.withMeta(null) == new ResourceIdentifier("articles", "1", "local-1", null, ["@missing": null])
    ResourceIdentifier.withLid("articles", "local-1").withMeta(meta).id() == null
    ResourceIdentifier.of("articles", "1").withMeta(meta).lid() == null
  }

  def "resource identifier preserves lid, meta, and additional members"() {
    when:
    def identifier = new ResourceIdentifier(
        "articles", null, "local-1", Meta.of([source: "import"]), ["ext:source": "legacy"])

    then:
    !identifier.hasId()
    identifier.hasLid()
    identifier.identityKey() == ResourceIdentity.ofLid("articles", "local-1")
    identifier.meta().members().source == "import"
    identifier.additionalMembers()["ext:source"] == "legacy"
  }

  def "resource identifier requires type and id or lid"() {
    when:
    ResourceIdentifier.of("articles", "1")

    then:
    noExceptionThrown()

    when:
    ResourceIdentifier.withLid("articles", "local-1")

    then:
    noExceptionThrown()

    when:
    new ResourceIdentifier("articles", null, null, null, [:])

    then:
    def ex = thrown(JsonApiValidationException)
    ex.ruleCode() == ValidationRuleCode.MISSING_RESOURCE_ID
  }

  def "resource object permits type-only construction for create context"() {
    when:
    def resource = ResourceObject.ofType("articles")

    then:
    resource.type() == "articles"
    !resource.hasId()
    !resource.hasLid()
  }

  def "resource object preserves lid identity and converts it to an identifier"() {
    when:
    def resource = new ResourceObject(
        "articles", null, "a1", null, null, null, Meta.of([source: "import"]),
        ["ext:source": "legacy"])

    then:
    resource.hasLid()
    resource.identityKey() == ResourceIdentity.ofLid("articles", "a1")
    def identifier = resource.toIdentifier()
    identifier.type() == "articles"
    identifier.lid() == "a1"
    identifier.meta().members().source == "import"
    identifier.additionalMembers()["ext:source"] == "legacy"
  }

  def "resource identity rejects null '#component'"(String component, Closure construct) {
    when:
    construct.call()

    then:
    def ex = thrown(JsonApiValidationException)
    ex.ruleCode() == ValidationRuleCode.NULL_REQUIRED_VALUE
    ex.jsonPointer() == "/resourceIdentity/" + component

    where:
    component | construct
    "kind"    | { new ResourceIdentity(null, "articles", "1") }
    "type"    | { new ResourceIdentity(ResourceIdentity.Kind.ID, null, "1") }
    "value"   | { new ResourceIdentity(ResourceIdentity.Kind.ID, "articles", null) }
  }

  def "invalid resource type fails with stable code"() {
    when:
    ResourceObject.of("_bad", "1")

    then:
    def ex = thrown(JsonApiValidationException)
    ex.ruleCode() == ValidationRuleCode.INVALID_MEMBER_NAME
  }

  def "empty and ascii-space resource types fail member-name grammar"() {
    when:
    new ResourceObject(" ", "1", null, null, null, null, null, [:])

    then:
    def ex = thrown(JsonApiValidationException)
    ex.ruleCode() == ValidationRuleCode.INVALID_MEMBER_NAME

    when:
    new ResourceObject("", "1", null, null, null, null, null, [:])

    then:
    def ex2 = thrown(JsonApiValidationException)
    ex2.ruleCode() == ValidationRuleCode.INVALID_MEMBER_NAME
  }

  def "unicode whitespace resource type is accepted when member-name grammar allows it"() {
    when:
    def resource = new ResourceObject("\u2003", "1", null, null, null, null, null, [:])
    def identifier = new ResourceIdentifier("\u2003", "1", null, null, [:])

    then:
    resource.type() == "\u2003"
    identifier.type() == "\u2003"
  }

  def "null resource type fails as missing"() {
    when:
    new ResourceObject(null, "1", null, null, null, null, null, [:])

    then:
    def ex = thrown(JsonApiValidationException)
    ex.ruleCode() == ValidationRuleCode.MISSING_RESOURCE_TYPE
  }

  def "resource object rejects invalid additional member names"() {
    when:
    new ResourceObject("articles", "1", null, null, null, null, null, ["_bad": 1])

    then:
    def ex = thrown(JsonApiValidationException)
    ex.ruleCode() == ValidationRuleCode.INVALID_MEMBER_NAME
  }

  def "non-at pass-through fields share the resource field namespace"() {
    when:
    new ResourceObject("articles", "1", null, attributes, relationships, null, null, [:])

    then:
    def ex = thrown(JsonApiValidationException)
    ex.ruleCode() == ValidationRuleCode.MEMBER_NAME_COLLISION
    ex.jsonPointer() == "/data/relationships/" + name

    where:
    name         | attributes                                              | relationships
    "author"     | Attributes.ofAttributes([author: "semantic"])          | Relationships.ofRelationships([author: Relationship.metaOnly(Meta.of([count: 1]))])
    "author"     | Attributes.ofAttributes([author: "semantic"])          | Relationships.of([:], [author: "pass-through"])
    "author"     | Attributes.of([:], [author: "pass-through"])           | Relationships.ofRelationships([author: Relationship.metaOnly(Meta.of([count: 1]))])
    "author"     | Attributes.of([:], [author: "attribute-pass-through"]) | Relationships.of([:], [author: "relationship-pass-through"])
    "ext:author" | Attributes.of([:], ["ext:author": "attribute"])       | Relationships.of([:], ["ext:author": "relationship"])
  }

  def "at pass-through fields are outside the resource field namespace"() {
    when:
    new ResourceObject(
        "articles", "1", null,
        Attributes.of([:], ["@context": "attribute-context"]),
        Relationships.of([:], ["@context": "relationship-context"]),
        null, null, [:])

    then:
    noExceptionThrown()
  }
}
