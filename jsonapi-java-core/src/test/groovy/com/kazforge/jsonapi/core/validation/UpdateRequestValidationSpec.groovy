package com.kazforge.jsonapi.core.validation

import com.kazforge.jsonapi.core.model.Attributes
import com.kazforge.jsonapi.core.model.DocumentData
import com.kazforge.jsonapi.core.model.ErrorObject
import com.kazforge.jsonapi.core.model.JsonApiDocument
import com.kazforge.jsonapi.core.model.Link
import com.kazforge.jsonapi.core.model.Links
import com.kazforge.jsonapi.core.model.Meta
import com.kazforge.jsonapi.core.model.Relationship
import com.kazforge.jsonapi.core.model.RelationshipData
import com.kazforge.jsonapi.core.model.Relationships
import com.kazforge.jsonapi.core.model.ResourceIdentifier
import com.kazforge.jsonapi.core.model.ResourceIdentity
import com.kazforge.jsonapi.core.model.ResourceObject
import spock.lang.Specification

class UpdateRequestValidationSpec extends Specification {

  def validator = new JsonApiDocumentValidator()

  static def updateContext() {
    ValidationContext.defaults().withDocumentUsage(DocumentUsage.UPDATE_REQUEST)
  }

  def "update request rejects '#name' primary data"(String name, JsonApiDocument doc) {
    given:
    def context = new ValidationContext(
        DocumentUsage.UPDATE_REQUEST,
        Set.of("ext"), Set.of(), Set.of(),
        Set.of(), LinksContext.TOP_LEVEL, Map.of(), null)

    when:
    validator.validate(doc, context)

    then:
    def ex = thrown(JsonApiValidationException)
    ex.ruleCode() == ValidationRuleCode.UPDATE_REQUIRES_SINGLE_RESOURCE
    ex.jsonPointer() == "/data"

    where:
    name                                   | doc
    "absent data (meta-only document)"     | JsonApiDocument.withMeta(Meta.empty())
    "absent data (errors-only document)"   | JsonApiDocument.withErrors([ErrorObject.ofTitle("boom")])
    "absent data (extension-only document)" | new JsonApiDocument(null, null, null, null, null, null, ["ext:doc": 1])
    "explicit null data"                   | JsonApiDocument.withData(DocumentData.NullData.INSTANCE)
    "singleton resource collection"        | JsonApiDocument.withData(
        new DocumentData.ResourceCollection([
          ResourceObject.of("articles", "1")
        ]))
    "multi-element resource collection"    | JsonApiDocument.withData(
        new DocumentData.ResourceCollection(
        [
          ResourceObject.of("articles", "1"),
          ResourceObject.of("articles", "2")
        ]))
    "single resource identifier"           | JsonApiDocument.withData(
        new DocumentData.SingleIdentifier(ResourceIdentifier.of("articles", "1")))
    "identifier collection"                | JsonApiDocument.withData(
        new DocumentData.IdentifierCollection([
          ResourceIdentifier.of("articles", "1")
        ]))
  }

  def "update request accepts a single resource with type and id"() {
    given:
    def doc = JsonApiDocument.withData(
        new DocumentData.SingleResource(ResourceObject.of("articles", "1")))

    when:
    validator.validate(doc, updateContext())

    then:
    noExceptionThrown()
  }

  def "update request rejects resource without id"() {
    given:
    def doc = JsonApiDocument.withData(
        new DocumentData.SingleResource(ResourceObject.ofType("articles")))

    when:
    validator.validate(doc, updateContext())

    then:
    def ex = thrown(JsonApiValidationException)
    ex.ruleCode() == ValidationRuleCode.RESOURCE_ID_REQUIRED
    ex.jsonPointer() == "/data/id"
  }

  def "update request rejects lid-only resource"() {
    given:
    def doc = JsonApiDocument.withData(
        new DocumentData.SingleResource(new ResourceObject("articles", null, "lid-1", null, null, null, null, [:])))

    when:
    validator.validate(doc, updateContext())

    then:
    def ex = thrown(JsonApiValidationException)
    ex.ruleCode() == ValidationRuleCode.RESOURCE_ID_REQUIRED
    ex.jsonPointer() == "/data/id"
  }

  def "update request accepts resource with id and lid"() {
    given:
    def doc = JsonApiDocument.withData(
        new DocumentData.SingleResource(new ResourceObject("articles", "1", "lid-1", null, null, null, null, [:])))

    when:
    validator.validate(doc, updateContext())

    then:
    noExceptionThrown()
  }

  def "update request accepts absent or present-empty relationships"() {
    given:
    def absent = JsonApiDocument.withData(new DocumentData.SingleResource(ResourceObject.of("articles", "1")))
    def presentEmpty = JsonApiDocument.withData(new DocumentData.SingleResource(
        new ResourceObject("articles", "1", null, null, Relationships.empty(), null, null, [:])))

    when:
    validator.validate(absent, updateContext())
    validator.validate(presentEmpty, updateContext())

    then:
    noExceptionThrown()
  }

  def "update request accepts '#linkage' relationship data"(String linkage, RelationshipData data) {
    given:
    def article = new ResourceObject(
        "articles", "1", null, null,
        Relationships.ofRelationships([author: Relationship.withData(data)]),
        null, null, [:])
    def doc = JsonApiDocument.withData(new DocumentData.SingleResource(article))

    when:
    validator.validate(doc, updateContext())

    then:
    noExceptionThrown()

    where:
    linkage            | data
    "null linkage"     | RelationshipData.NullLinkage.INSTANCE
    "single linkage"   | new RelationshipData.SingleLinkage(ResourceIdentifier.of("people", "2"))
    "empty collection" | new RelationshipData.IdentifierCollectionLinkage([])
    "non-empty collection" | new RelationshipData.IdentifierCollectionLinkage(
        [
          ResourceIdentifier.of("tags", "1"),
          ResourceIdentifier.of("tags", "2")
        ])
  }

  def "update request rejects '#shape' relationship without data"(String shape, Relationship relationship) {
    given:
    def article = new ResourceObject(
        "articles", "1", null, null,
        Relationships.ofRelationships([author: relationship]),
        null, null, [:])
    def doc = JsonApiDocument.withData(new DocumentData.SingleResource(article))

    when:
    validator.validate(doc, updateContext())

    then:
    def ex = thrown(JsonApiValidationException)
    ex.ruleCode() == ValidationRuleCode.RELATIONSHIP_DATA_REQUIRED
    ex.jsonPointer() == "/data/relationships/author/data"

    where:
    shape                     | relationship
    "self-link-only"          | Relationship.linkOnly(
        Links.ofLinks([self: new Link.StringLink("http://example.com/authors/2")]))
    "meta-only"               | Relationship.metaOnly(Meta.of([count: 1]))
    "extension-only"          | new Relationship(null, null, null, ["ext:x": 1])
  }

  def "update request accepts relationship with data plus links, meta, and allowed extension member"() {
    given:
    def relationship = new Relationship(
        new RelationshipData.SingleLinkage(ResourceIdentifier.of("people", "2")),
        Links.ofLinks([self: new Link.StringLink("http://example.com/authors/2")]),
        Meta.of([count: 1]),
        ["ext:x": 1])
    def article = new ResourceObject(
        "articles", "1", null, null,
        Relationships.ofRelationships([author: relationship]),
        null, null, [:])
    def doc = JsonApiDocument.withData(new DocumentData.SingleResource(article))
    def context = new ValidationContext(
        DocumentUsage.UPDATE_REQUEST,
        Set.of("ext"), Set.of(), Set.of(),
        Set.of(), LinksContext.TOP_LEVEL, Map.of(), null)

    when:
    validator.validate(doc, context)

    then:
    noExceptionThrown()
  }

  def "update request rejects lid-only linkage identifier in single relationship data"() {
    given:
    def article = new ResourceObject(
        "articles", "1", null, null,
        Relationships.ofRelationships([
          author: Relationship.withData(
          new RelationshipData.SingleLinkage(ResourceIdentifier.withLid("people", "people-lid")))
        ]),
        null, null, [:])
    def doc = JsonApiDocument.withData(new DocumentData.SingleResource(article))

    when:
    validator.validate(doc, updateContext())

    then:
    def ex = thrown(JsonApiValidationException)
    ex.ruleCode() == ValidationRuleCode.RESOURCE_ID_REQUIRED
    ex.jsonPointer() == "/data/relationships/author/data/id"
  }

  def "update request rejects lid-only linkage identifier in collection relationship data"() {
    given:
    def article = new ResourceObject(
        "articles", "1", null, null,
        Relationships.ofRelationships([
          tags: Relationship.withData(new RelationshipData.IdentifierCollectionLinkage([
            ResourceIdentifier.of("tags", "1"),
            ResourceIdentifier.withLid("tags", "tag-lid")
          ]))
        ]),
        null, null, [:])
    def doc = JsonApiDocument.withData(new DocumentData.SingleResource(article))

    when:
    validator.validate(doc, updateContext())

    then:
    def ex = thrown(JsonApiValidationException)
    ex.ruleCode() == ValidationRuleCode.RESOURCE_ID_REQUIRED
    ex.jsonPointer() == "/data/relationships/tags/data/1/id"
  }

  def "update request preserves '#attribute' attribute state"(String attribute, ResourceObject resource, Attributes expectedAttributes) {
    given:
    def doc = JsonApiDocument.withData(new DocumentData.SingleResource(resource))

    when:
    validator.validate(doc, updateContext())

    then:
    noExceptionThrown()
    resource.attributes() == expectedAttributes

    where:
    attribute       | resource                                                                             | expectedAttributes
    "absent"        | ResourceObject.of("articles", "1")                                                   | null
    "empty"         | new ResourceObject("articles", "1", null, Attributes.empty(), null, null, null, [:]) | Attributes.empty()
    "value"         | new ResourceObject(
        "articles", "1", null, Attributes.ofAttributes([title: "value"]), null, null, null, [:])         | Attributes.ofAttributes([title: "value"])
    "explicit null" | new ResourceObject(
        "articles", "1", null, Attributes.ofAttributes([title: null]), null, null, null, [:])            | Attributes.ofAttributes([title: null])
  }

  def "update request accepts matching expected endpoint identity"() {
    given:
    def doc = JsonApiDocument.withData(
        new DocumentData.SingleResource(ResourceObject.of("articles", "1")))
    def context = updateContext().withExpectedEndpointIdentity(new EndpointIdentity("articles", "1"))

    when:
    validator.validate(doc, context)

    then:
    noExceptionThrown()
  }

  def "expected endpoint identity survives context derivation"() {
    given:
    def identity = new EndpointIdentity("articles", "1")
    def base = ValidationContext.defaults().withExpectedEndpointIdentity(identity)

    expect:
    base.withDocumentUsage(DocumentUsage.UPDATE_REQUEST).expectedEndpointIdentity() == identity
    base.withLinksContext(LinksContext.RESOURCE).expectedEndpointIdentity() == identity
    base.withSparseFieldsetLinkageExemptions(
        Set.of(ResourceIdentity.ofId("comments", "99"))).expectedEndpointIdentity() == identity
  }

  def "update request rejects type mismatch with expected endpoint identity"() {
    given:
    def doc = JsonApiDocument.withData(
        new DocumentData.SingleResource(ResourceObject.of("articles", "1")))
    def context = updateContext().withExpectedEndpointIdentity(new EndpointIdentity("comments", "1"))

    when:
    validator.validate(doc, context)

    then:
    def ex = thrown(JsonApiValidationException)
    ex.ruleCode() == ValidationRuleCode.ENDPOINT_IDENTITY_MISMATCH
    ex.jsonPointer() == "/data/type"
  }

  def "update request rejects id mismatch with expected endpoint identity"() {
    given:
    def doc = JsonApiDocument.withData(
        new DocumentData.SingleResource(ResourceObject.of("articles", "1")))
    def context = updateContext().withExpectedEndpointIdentity(new EndpointIdentity("articles", "99"))

    when:
    validator.validate(doc, context)

    then:
    def ex = thrown(JsonApiValidationException)
    ex.ruleCode() == ValidationRuleCode.ENDPOINT_IDENTITY_MISMATCH
    ex.jsonPointer() == "/data/id"
  }

  def "endpoint identity comparison stays off when none is supplied"() {
    given:
    def doc = JsonApiDocument.withData(
        new DocumentData.SingleResource(ResourceObject.of("articles", "1")))

    when:
    validator.validate(doc, updateContext().withExpectedEndpointIdentity(null))

    then:
    noExceptionThrown()
  }

  def "endpoint identity rejects null type and id"(String type, String id, String pointer) {
    when:
    new EndpointIdentity(type, id)

    then:
    def ex = thrown(JsonApiValidationException)
    ex.ruleCode() == ValidationRuleCode.NULL_REQUIRED_VALUE
    ex.jsonPointer() == pointer

    where:
    type       | id   | pointer
    null       | "1"  | "/endpointIdentity/type"
    "articles" | null | "/endpointIdentity/id"
  }

  def "update request composes with allowed extension members"() {
    given:
    def doc = new JsonApiDocument(
        new DocumentData.SingleResource(ResourceObject.of("articles", "1")),
        null, null, null, null, null,
        ["ext:doc": 1])
    def context = new ValidationContext(
        DocumentUsage.UPDATE_REQUEST,
        Set.of("ext"), Set.of(), Set.of(),
        Set.of(), LinksContext.TOP_LEVEL, Map.of(), null)

    when:
    validator.validate(doc, context)

    then:
    noExceptionThrown()
  }

  def "update request still enforces full linkage for included resources"() {
    given:
    def doc = new JsonApiDocument(
        new DocumentData.SingleResource(ResourceObject.of("articles", "1")),
        null, null, null, null,
        [
          ResourceObject.of("people", "2")
        ],
        [:])

    when:
    validator.validate(doc, updateContext())

    then:
    def ex = thrown(JsonApiValidationException)
    ex.ruleCode() == ValidationRuleCode.FULL_LINKAGE_VIOLATION
    ex.jsonPointer() == "/included"
  }

  def "update request accepts compound document without applying update rules to included resources"() {
    given:
    def article = new ResourceObject(
        "articles", "1", null, null,
        Relationships.ofRelationships([
          author: Relationship.withData(
          new RelationshipData.SingleLinkage(ResourceIdentifier.of("people", "2")))
        ]),
        null, null, [:])
    def author = new ResourceObject(
        "people", "2", null, null,
        Relationships.ofRelationships([
          avatar: Relationship.linkOnly(
          Links.ofLinks([self: new Link.StringLink("http://example.com/avatars/2")]))
        ]),
        null, null, [:])
    def doc = new JsonApiDocument(
        new DocumentData.SingleResource(article),
        null, null, null, null,
        [author],
        [:])
    def context = updateContext().withExpectedEndpointIdentity(new EndpointIdentity("articles", "1"))

    when:
    validator.validate(doc, context)

    then:
    noExceptionThrown()
  }

  def "create request still permits id-less resources"() {
    given:
    def doc = JsonApiDocument.withData(
        new DocumentData.SingleResource(ResourceObject.ofType("articles")))
    def context = ValidationContext.defaults().withDocumentUsage(DocumentUsage.CREATE_REQUEST)

    when:
    validator.validate(doc, context)

    then:
    noExceptionThrown()
  }
}
