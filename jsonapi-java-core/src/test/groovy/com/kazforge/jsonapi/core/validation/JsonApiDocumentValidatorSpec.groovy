package com.kazforge.jsonapi.core.validation

import com.kazforge.jsonapi.core.model.Attributes
import com.kazforge.jsonapi.core.model.DocumentData
import com.kazforge.jsonapi.core.model.ErrorObject
import com.kazforge.jsonapi.core.model.ErrorSource
import com.kazforge.jsonapi.core.model.JsonApiDocument
import com.kazforge.jsonapi.core.model.JsonApiObject
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

class JsonApiDocumentValidatorSpec extends Specification {

  def validator = new JsonApiDocumentValidator()

  def "create request permits resource without id"() {
    given:
    def doc = JsonApiDocument.withData(
        new DocumentData.SingleResource(ResourceObject.ofType("articles")))
    def context = ValidationContext.defaults().withDocumentUsage(DocumentUsage.CREATE_REQUEST)

    when:
    validator.validate(doc, context)

    then:
    noExceptionThrown()
  }

  def "response requires resource id"() {
    given:
    def doc = JsonApiDocument.withData(
        new DocumentData.SingleResource(ResourceObject.ofType("articles")))

    when:
    validator.validate(doc, ValidationContext.defaults())

    then:
    def ex = thrown(JsonApiValidationException)
    ex.ruleCode() == ValidationRuleCode.RESOURCE_ID_REQUIRED
    ex.jsonPointer() == "/data/id"
  }

  def "duplicate included resources are rejected"() {
    given:
    def article = ResourceObject.of("articles", "1")
    def doc = new JsonApiDocument(
        new DocumentData.SingleResource(article),
        null, null, null, null,
        [
          ResourceObject.of("comments", "99"),
          ResourceObject.of("comments", "99")
        ],
        [:])

    when:
    validator.validate(doc, ValidationContext.defaults())

    then:
    def ex = thrown(JsonApiValidationException)
    ex.ruleCode() == ValidationRuleCode.DUPLICATE_RESOURCE_IDENTITY
    ex.jsonPointer() == "/included/1"
  }

  def "full linkage is enforced for included resources"() {
    given:
    def article = ResourceObject.of("articles", "1")
    def orphan = ResourceObject.of("comments", "99")
    def doc = new JsonApiDocument(
        new DocumentData.SingleResource(article),
        null, null, null, null,
        [orphan],
        [:])

    when:
    validator.validate(doc, ValidationContext.defaults())

    then:
    def ex = thrown(JsonApiValidationException)
    ex.ruleCode() == ValidationRuleCode.FULL_LINKAGE_VIOLATION
    ex.jsonPointer() == "/included"
  }

  def "sparse-fieldset linkage exemptions treat only the exempted included resource as reachable"() {
    given:
    def article = ResourceObject.of("articles", "1")
    def orphan = ResourceObject.of("comments", "99")
    def doc = new JsonApiDocument(
        new DocumentData.SingleResource(article),
        null, null, null, null,
        [orphan],
        [:])
    def context = ValidationContext.defaults()
        .withSparseFieldsetLinkageExemptions(Set.of(ResourceIdentity.ofId("comments", "99")))

    when:
    validator.validate(doc, context)

    then:
    noExceptionThrown()
  }

  def "an unrelated unlinked included resource still violates full linkage beside an exemption"() {
    given:
    def article = ResourceObject.of("articles", "1")
    def fieldsetOrphan = ResourceObject.of("comments", "99")
    def unrelatedOrphan = ResourceObject.of("tags", "7")
    def doc = new JsonApiDocument(
        new DocumentData.SingleResource(article),
        null, null, null, null,
        [
          fieldsetOrphan,
          unrelatedOrphan
        ],
        [:])
    def context = ValidationContext.defaults()
        .withSparseFieldsetLinkageExemptions(Set.of(ResourceIdentity.ofId("comments", "99")))

    when:
    validator.validate(doc, context)

    then:
    def ex = thrown(JsonApiValidationException)
    ex.ruleCode() == ValidationRuleCode.FULL_LINKAGE_VIOLATION
  }

  def "exempted included resources extend reachability to their own subtrees"() {
    given:
    def article = ResourceObject.of("articles", "1")
    def exemptedAuthor = new ResourceObject(
        "people", "9", null, null,
        Relationships.ofRelationships([
          editor: Relationship.withData(
          new RelationshipData.SingleLinkage(ResourceIdentifier.of("people", "10")))
        ]),
        null, null, [:])
    def childOfExempted = ResourceObject.of("people", "10")
    def doc = new JsonApiDocument(
        new DocumentData.SingleResource(article),
        null, null, null, null,
        [
          exemptedAuthor,
          childOfExempted
        ],
        [:])
    def context = ValidationContext.defaults()
        .withSparseFieldsetLinkageExemptions(Set.of(ResourceIdentity.ofId("people", "9")))

    when:
    validator.validate(doc, context)

    then:
    noExceptionThrown()
  }

  def "extension members require allowed namespace"() {
    given:
    def doc = new JsonApiDocument(
        null, null, null, null, null, null,
        ["myext:version": "1.0"])

    when:
    validator.validate(doc, ValidationContext.defaults())

    then:
    def ex = thrown(JsonApiValidationException)
    ex.ruleCode() == ValidationRuleCode.DISALLOWED_ADDITIONAL_MEMBER
  }

  def "allowed extension namespace passes validation"() {
    given:
    def doc = new JsonApiDocument(
        null, null, null, null, null, null,
        ["myext:version": "1.0"])
    def context = new ValidationContext(
        DocumentUsage.RESPONSE_OR_OTHER,
        Set.of("myext"),
        Set.of(),
        Set.of(),
        Set.of(),
        LinksContext.TOP_LEVEL,
        Map.of(),
        null)

    when:
    validator.validate(doc, context)

    then:
    noExceptionThrown()
  }

  def "nested extension members pass when their namespace is allowed"() {
    given:
    def article = new ResourceObject(
        "articles", "1", null,
        Attributes.of([title: "t"], ["ext:attribute": 1]),
        Relationships.of([
          author: new Relationship(
          new RelationshipData.SingleLinkage(ResourceIdentifier.of("people", "9")),
          null,
          Meta.of(["ext:relationship": 1]),
          [:])
        ], ["ext:relationships": 1]),
        Links.ofLinks([self: new Link.StringLink("https://example.com/a/1")]),
        Meta.of(["ext:resource": 1]),
        ["ext:resource-member": 1])
    def doc = new JsonApiDocument(
        new DocumentData.SingleResource(article),
        null,
        Meta.of(["ext:document-meta": 1]),
        JsonApiObject.ofVersion("1.1"),
        Links.ofLinks([self: new Link.StringLink("https://example.com")]),
        null,
        ["ext:document-member": 1])
    def context = new ValidationContext(
        DocumentUsage.RESPONSE_OR_OTHER,
        Set.of("ext"),
        Set.of(),
        Set.of(),
        Set.of(),
        LinksContext.TOP_LEVEL,
        Map.of(),
        null)

    when:
    validator.validate(doc, context)

    then:
    noExceptionThrown()
  }

  def "disallowed extension members in document meta are rejected"() {
    given:
    def doc = JsonApiDocument.withMeta(Meta.of(["ext:value": 1]))

    when:
    validator.validate(doc, ValidationContext.defaults())

    then:
    def ex = thrown(JsonApiValidationException)
    ex.ruleCode() == ValidationRuleCode.DISALLOWED_ADDITIONAL_MEMBER
  }

  def "at members are accepted without profile policy"() {
    given:
    def doc = new JsonApiDocument(
        null, null, Meta.of([count: 1]), null, null, null, ["@context": "x"])

    when:
    validator.validate(doc, ValidationContext.defaults())

    then:
    noExceptionThrown()
  }

  def "relationship pagination with absent linkage is allowed without cardinality hint"() {
    given:
    def article = new ResourceObject(
        "articles", "1", null, null,
        Relationships.ofRelationships([
          comments: Relationship.linkOnly(Links.ofLinks([
            self : new Link.StringLink("http://example.com/c"),
            first: new Link.StringLink("http://example.com/c?page=1")
          ]))
        ]),
        null, null, [:])
    def doc = JsonApiDocument.withData(new DocumentData.SingleResource(article))

    when:
    validator.validate(doc, ValidationContext.defaults())

    then:
    noExceptionThrown()
  }

  def "relationship pagination with absent linkage passes with TO_MANY hint"() {
    given:
    def article = new ResourceObject(
        "articles", "1", null, null,
        Relationships.ofRelationships([
          comments: Relationship.linkOnly(Links.ofLinks([
            self : new Link.StringLink("http://example.com/c"),
            first: new Link.StringLink("http://example.com/c?page=1")
          ]))
        ]),
        null, null, [:])
    def doc = JsonApiDocument.withData(new DocumentData.SingleResource(article))
    def context = new ValidationContext(
        DocumentUsage.RESPONSE_OR_OTHER,
        Set.of(),
        Set.of(),
        Set.of(),
        Set.of(),
        LinksContext.TOP_LEVEL,
        Map.of(
        RelationshipPaginationKey.of("articles", "comments"),
        RelationshipCardinality.TO_MANY),
        null)

    when:
    validator.validate(doc, context)

    then:
    noExceptionThrown()
  }

  def "TO_ONE pagination hint rejects absent linkage"() {
    given:
    def article = new ResourceObject(
        "articles", "1", null, null,
        Relationships.ofRelationships([
          comments: Relationship.linkOnly(Links.ofLinks([
            self : new Link.StringLink("http://example.com/c"),
            first: new Link.StringLink("http://example.com/c?page=1")
          ]))
        ]),
        null, null, [:])
    def doc = JsonApiDocument.withData(new DocumentData.SingleResource(article))
    def context = new ValidationContext(
        DocumentUsage.RESPONSE_OR_OTHER,
        Set.of(),
        Set.of(),
        Set.of(),
        Set.of(),
        LinksContext.TOP_LEVEL,
        Map.of(
        RelationshipPaginationKey.of("articles", "comments"),
        RelationshipCardinality.TO_ONE),
        null)

    when:
    validator.validate(doc, context)

    then:
    def ex = thrown(JsonApiValidationException)
    ex.ruleCode() == ValidationRuleCode.PAGINATION_REQUIRES_COLLECTION
    ex.jsonPointer() == "/data/relationships/comments/links/first"
  }

  def "same relationship name with TO_MANY hint allows pagination"() {
    given:
    def article = new ResourceObject(
        "articles", "1", null, null,
        Relationships.ofRelationships([
          comments: Relationship.linkOnly(Links.ofLinks([
            self : new Link.StringLink("http://example.com/articles/1/comments"),
            first: new Link.StringLink("http://example.com/articles/1/comments?page=1")
          ]))
        ]),
        null, null, [:])
    def doc = JsonApiDocument.withData(new DocumentData.SingleResource(article))
    def context = new ValidationContext(
        DocumentUsage.RESPONSE_OR_OTHER,
        Set.of(),
        Set.of(),
        Set.of(),
        Set.of(),
        LinksContext.TOP_LEVEL,
        Map.of(
        RelationshipPaginationKey.of("articles", "comments"),
        RelationshipCardinality.TO_MANY),
        null)

    when:
    validator.validate(doc, context)

    then:
    noExceptionThrown()
  }

  def "same relationship name with TO_ONE hint rejects pagination"() {
    given:
    def person = new ResourceObject(
        "people", "9", null, null,
        Relationships.ofRelationships([
          comments: Relationship.linkOnly(Links.ofLinks([
            self : new Link.StringLink("http://example.com/people/9/comments"),
            first: new Link.StringLink("http://example.com/people/9/comments?page=1")
          ]))
        ]),
        null, null, [:])
    def doc = JsonApiDocument.withData(new DocumentData.SingleResource(person))
    def context = new ValidationContext(
        DocumentUsage.RESPONSE_OR_OTHER,
        Set.of(),
        Set.of(),
        Set.of(),
        Set.of(),
        LinksContext.TOP_LEVEL,
        Map.of(
        RelationshipPaginationKey.of("people", "comments"),
        RelationshipCardinality.TO_ONE),
        null)

    when:
    validator.validate(doc, context)

    then:
    def ex = thrown(JsonApiValidationException)
    ex.ruleCode() == ValidationRuleCode.PAGINATION_REQUIRES_COLLECTION
  }

  def "duplicate lid identities across primary and included are rejected"() {
    given:
    def primary = new ResourceObject(
        "articles", null, "a1", null, null, null, null, [:])
    def included1 = new ResourceObject("articles", null, "a1", Attributes.ofAttributes([t: 1]), null, null, null, [:])
    def included2 = new ResourceObject("articles", null, "a1", Attributes.ofAttributes([t: 2]), null, null, null, [:])
    def doc = new JsonApiDocument(
        new DocumentData.SingleResource(primary),
        null, null, null, null,
        [included1, included2],
        [:])
    def context = ValidationContext.defaults()
        .withDocumentUsage(DocumentUsage.CREATE_REQUEST)

    when:
    validator.validate(doc, context)

    then:
    def ex = thrown(JsonApiValidationException)
    ex.ruleCode() == ValidationRuleCode.DUPLICATE_RESOURCE_IDENTITY
  }

  def "unknown top-level unnamespaced members are rejected"() {
    given:
    def doc = new JsonApiDocument(
        null, null, Meta.of([count: 1]), null, null, null,
        [custom: "value"])

    when:
    validator.validate(doc, ValidationContext.defaults())

    then:
    def ex = thrown(JsonApiValidationException)
    ex.ruleCode() == ValidationRuleCode.UNKNOWN_ADDITIONAL_MEMBER
    ex.jsonPointer() == "/custom"
  }

  def "allowed profile member names pass validation"() {
    given:
    def doc = new JsonApiDocument(
        null, null, Meta.of([count: 1]), null, null, null,
        [custom: "value"])
    def context = new ValidationContext(
        DocumentUsage.RESPONSE_OR_OTHER,
        Set.of(),
        Set.of(),
        Set.of("custom"),
        Set.of(),
        LinksContext.TOP_LEVEL,
        Map.of(),
        null)

    when:
    validator.validate(doc, context)

    then:
    noExceptionThrown()
  }

  def "transitive full linkage reaches included relationship targets"() {
    given:
    def author = ResourceObject.of("people", "9")
    def comment = new ResourceObject(
        "comments", "5", null, null,
        Relationships.ofRelationships([
          author: Relationship.withData(
          new RelationshipData.SingleLinkage(ResourceIdentifier.of("people", "9")))
        ]),
        null, null, [:])
    def article = new ResourceObject(
        "articles", "1", null, null,
        Relationships.ofRelationships([
          comments: Relationship.withData(
          new RelationshipData.SingleLinkage(ResourceIdentifier.of("comments", "5")))
        ]),
        null, null, [:])
    def doc = new JsonApiDocument(
        new DocumentData.SingleResource(article),
        null, null, null, null,
        [comment, author],
        [:])

    when:
    validator.validate(doc, ValidationContext.defaults())

    then:
    noExceptionThrown()
  }

  def "non-transitive orphan beyond one hop is rejected"() {
    given:
    def author = ResourceObject.of("people", "9")
    def comment = ResourceObject.of("comments", "5")
    def article = new ResourceObject(
        "articles", "1", null, null,
        Relationships.ofRelationships([
          comments: Relationship.withData(
          new RelationshipData.SingleLinkage(ResourceIdentifier.of("comments", "5")))
        ]),
        null, null, [:])
    def doc = new JsonApiDocument(
        new DocumentData.SingleResource(article),
        null, null, null, null,
        [comment, author],
        [:])

    when:
    validator.validate(doc, ValidationContext.defaults())

    then:
    def ex = thrown(JsonApiValidationException)
    ex.ruleCode() == ValidationRuleCode.FULL_LINKAGE_VIOLATION
  }

  def "resource links reject non-standard members"() {
    given:
    def article = new ResourceObject(
        "articles", "1", null, null, null,
        Links.ofLinks([related: new Link.StringLink("http://example.com")]),
        null, [:])
    def doc = JsonApiDocument.withData(new DocumentData.SingleResource(article))

    when:
    validator.validate(doc, ValidationContext.defaults())

    then:
    def ex = thrown(JsonApiValidationException)
    ex.ruleCode() == ValidationRuleCode.INVALID_LINKS_CONTEXT
  }

  def "error links accept about and type"() {
    given:
    def error = new ErrorObject(
        null,
        Links.ofLinks([
          about: new Link.StringLink("http://example.com/docs"),
          type : new Link.StringLink("http://example.com/errors/1")
        ]),
        "400", null, "Bad Request", null, null, null, [:])
    def doc = JsonApiDocument.withErrors([error])

    when:
    validator.validate(doc, ValidationContext.defaults())

    then:
    noExceptionThrown()
  }

  def "error meta and nested additional members pass when allowed"() {
    given:
    def error = new ErrorObject(
        null,
        null,
        null,
        null,
        "Title",
        null,
        new ErrorSource(null, "include", null, [:]),
        Meta.of([count: 1]),
        ["ext:error": 1])
    def doc = JsonApiDocument.withErrors([error])
    def context = new ValidationContext(
        DocumentUsage.RESPONSE_OR_OTHER,
        Set.of("ext"),
        Set.of(),
        Set.of(),
        Set.of(),
        LinksContext.TOP_LEVEL,
        Map.of(),
        null)

    when:
    validator.validate(doc, context)

    then:
    noExceptionThrown()
  }

  def "disallowed profile uri is rejected"() {
    given:
    def doc = new JsonApiDocument(
        null, null, Meta.empty(),
        new JsonApiObject("1.1", null, [
          "https://example.com/profiles/a"
        ], null, [:]),
        null, null, [:])
    def context = new ValidationContext(
        DocumentUsage.RESPONSE_OR_OTHER,
        Set.of(),
        Set.of("https://example.com/profiles/b"),
        Set.of(),
        Set.of(),
        LinksContext.TOP_LEVEL,
        Map.of(),
        null)

    when:
    validator.validate(doc, context)

    then:
    def ex = thrown(JsonApiValidationException)
    ex.ruleCode() == ValidationRuleCode.DISALLOWED_ADDITIONAL_MEMBER
  }

  def "allowed profile uri passes when configured"() {
    given:
    def uri = "https://example.com/profiles/a"
    def doc = new JsonApiDocument(
        null,
        null,
        Meta.empty(),
        new JsonApiObject("1.1", null, [uri], null, [:]),
        null,
        null,
        [:])
    def context = new ValidationContext(
        DocumentUsage.RESPONSE_OR_OTHER,
        Set.of(),
        Set.of(uri),
        Set.of(),
        Set.of(),
        LinksContext.TOP_LEVEL,
        Map.of(),
        null)

    when:
    validator.validate(doc, context)

    then:
    noExceptionThrown()
  }

  def "lid alias resolves full linkage for included resource with id and lid"() {
    given:
    def comment = new ResourceObject("comments", "5", "c-local", null, null, null, null, [:])
    def article = new ResourceObject(
        "articles", "1", null, null,
        Relationships.ofRelationships([
          comments: Relationship.withData(
          new RelationshipData.SingleLinkage(ResourceIdentifier.withLid("comments", "c-local")))
        ]),
        null, null, [:])
    def doc = new JsonApiDocument(
        new DocumentData.SingleResource(article),
        null, null, null, null,
        [comment],
        [:])
    def context = ValidationContext.defaults().withDocumentUsage(DocumentUsage.CREATE_REQUEST)

    when:
    validator.validate(doc, context)

    then:
    noExceptionThrown()
  }

  def "pre-bound lid alias resolves transitive full linkage for lid-only included resource"() {
    given:
    def comment = ResourceObject.of("comments", "5")
    def person = new ResourceObject(
        "people", null, "local", null,
        Relationships.ofRelationships([
          comments: Relationship.withData(
          new RelationshipData.SingleLinkage(ResourceIdentifier.of("comments", "5")))
        ]),
        null, null, [:])
    def article = new ResourceObject(
        "articles", "1", null, null,
        Relationships.ofRelationships([
          author: Relationship.withData(new RelationshipData.SingleLinkage(
          new ResourceIdentifier("people", "9", "local", null, [:])))
        ]),
        null, null, [:])
    def doc = new JsonApiDocument(
        new DocumentData.SingleResource(article),
        null, null, null, null,
        [person, comment],
        [:])
    def context = ValidationContext.defaults().withDocumentUsage(DocumentUsage.CREATE_REQUEST)

    when:
    validator.validate(doc, context)

    then:
    noExceptionThrown()
  }

  def "disallowed extension meta on object link is rejected"() {
    given:
    def doc = new JsonApiDocument(
        new DocumentData.SingleResource(ResourceObject.of("articles", "1")),
        null, null, null,
        Links.ofLinks([
          self: new Link.ObjectLink(
          "http://example.com/articles/1", null, null, null, null, null,
          Meta.of(["ext:flag": true]), [:])
        ]),
        null, [:])

    when:
    validator.validate(doc, ValidationContext.defaults())

    then:
    def ex = thrown(JsonApiValidationException)
    ex.ruleCode() == ValidationRuleCode.DISALLOWED_ADDITIONAL_MEMBER
    ex.jsonPointer() == "/links/self/meta/ext:flag"
  }

  def "allowed extension meta on object link is accepted"() {
    given:
    def doc = new JsonApiDocument(
        new DocumentData.SingleResource(ResourceObject.of("articles", "1")),
        null, null, null,
        Links.ofLinks([
          self: new Link.ObjectLink(
          "http://example.com/articles/1", null, null, null, null, null,
          Meta.of(["ext:flag": true]), [:])
        ]),
        null, [:])
    def context = new ValidationContext(
        DocumentUsage.RESPONSE_OR_OTHER,
        Set.of("ext"),
        Set.of(),
        Set.of(),
        Set.of(),
        LinksContext.TOP_LEVEL,
        Map.of(),
        null)

    when:
    validator.validate(doc, context)

    then:
    noExceptionThrown()
  }

  def "disallowed extension member on recursive describedby object link is rejected"() {
    given:
    def describedby = new Link.ObjectLink(
        "https://example.com/articles/schema", null, null, null, null, null, null,
        ["ext:flag": true])
    def doc = new JsonApiDocument(
        new DocumentData.SingleResource(ResourceObject.of("articles", "1")),
        null, null, null,
        Links.ofLinks([
          self: new Link.ObjectLink(
          "https://example.com/articles/1", null, describedby, null, null, null, null, [:])
        ]),
        null, [:])

    when:
    validator.validate(doc, ValidationContext.defaults())

    then:
    def ex = thrown(JsonApiValidationException)
    ex.ruleCode() == ValidationRuleCode.DISALLOWED_ADDITIONAL_MEMBER
    ex.jsonPointer() == "/links/self/describedby/ext:flag"
  }

  def "profile-permitted links-only relationship is accepted"() {
    given:
    def article = new ResourceObject(
        "articles", "1", null, null,
        Relationships.ofRelationships([
          author: Relationship.linkOnly(Links.ofLinks([
            canonical: new Link.StringLink("http://example.com/canonical")
          ]))
        ]),
        null, null, [:])
    def doc = JsonApiDocument.withData(new DocumentData.SingleResource(article))
    def context = new ValidationContext(
        DocumentUsage.RESPONSE_OR_OTHER,
        Set.of(),
        Set.of(),
        Set.of("canonical"),
        Set.of(),
        LinksContext.TOP_LEVEL,
        Map.of(),
        null)

    when:
    validator.validate(doc, context)

    then:
    noExceptionThrown()
  }

  def "response rejects lid-only resource identifier"() {
    given:
    def doc = JsonApiDocument.withData(
        new DocumentData.SingleIdentifier(ResourceIdentifier.withLid("articles", "local-1")))

    when:
    validator.validate(doc, ValidationContext.defaults())

    then:
    def ex = thrown(JsonApiValidationException)
    ex.ruleCode() == ValidationRuleCode.RESOURCE_ID_REQUIRED
    ex.jsonPointer() == "/data/id"
  }

  def "primary and included duplicate identity is rejected"() {
    given:
    def article = ResourceObject.of("articles", "1")
    def doc = new JsonApiDocument(
        new DocumentData.SingleResource(article),
        null, null, null, null,
        [
          ResourceObject.of("articles", "1")
        ],
        [:])

    when:
    validator.validate(doc, ValidationContext.defaults())

    then:
    def ex = thrown(JsonApiValidationException)
    ex.ruleCode() == ValidationRuleCode.DUPLICATE_RESOURCE_IDENTITY
    ex.jsonPointer() == "/included/0"
  }

  def "conflicting lid bindings across identifiers are rejected"() {
    given:
    def article = new ResourceObject(
        "articles", "1", null, null,
        Relationships.ofRelationships([
          author: Relationship.withData(new RelationshipData.SingleLinkage(
          new ResourceIdentifier("people", "9", "local", null, [:]))),
          editor: Relationship.withData(new RelationshipData.SingleLinkage(
          new ResourceIdentifier("people", "10", "local", null, [:])))
        ]),
        null, null, [:])
    def doc = JsonApiDocument.withData(new DocumentData.SingleResource(article))

    when:
    validator.validate(doc, ValidationContext.defaults())

    then:
    def ex = thrown(JsonApiValidationException)
    ex.ruleCode() == ValidationRuleCode.INCONSISTENT_LOCAL_IDENTIFIER
  }

  def "identity-less included resource is rejected"() {
    given:
    def article = ResourceObject.of("articles", "1")
    def orphan = ResourceObject.ofType("comments")
    def doc = new JsonApiDocument(
        new DocumentData.SingleResource(article),
        null, null, null, null,
        [orphan],
        [:])
    def context = ValidationContext.defaults()
        .withDocumentUsage(DocumentUsage.CREATE_REQUEST)

    when:
    validator.validate(doc, context)

    then:
    def ex = thrown(JsonApiValidationException)
    ex.ruleCode() == ValidationRuleCode.INCLUDED_RESOURCE_IDENTITY_REQUIRED
    ex.jsonPointer() == "/included/0"
  }

  def "collection relationship pagination is allowed without hint"() {
    given:
    def article = new ResourceObject(
        "articles", "1", null, null,
        Relationships.ofRelationships([
          comments: new Relationship(
          new RelationshipData.IdentifierCollectionLinkage([]),
          Links.ofLinks([first: new Link.StringLink("http://example.com/c?page=1")]),
          null,
          [:])
        ]),
        null, null, [:])
    def doc = JsonApiDocument.withData(new DocumentData.SingleResource(article))

    when:
    validator.validate(doc, ValidationContext.defaults())

    then:
    noExceptionThrown()
  }

  def "null relationship pagination is rejected"() {
    given:
    def article = new ResourceObject(
        "articles", "1", null, null,
        Relationships.ofRelationships([
          author: new Relationship(
          RelationshipData.NullLinkage.INSTANCE,
          Links.ofLinks([first: new Link.StringLink("https://example.com/a?page=1")]),
          null,
          [:])
        ]),
        null, null, [:])
    def doc = JsonApiDocument.withData(new DocumentData.SingleResource(article))

    when:
    validator.validate(doc, ValidationContext.defaults())

    then:
    def ex = thrown(JsonApiValidationException)
    ex.ruleCode() == ValidationRuleCode.PAGINATION_REQUIRES_COLLECTION
    ex.jsonPointer() == "/data/relationships/author/links/first"
  }

  def "to-many linkage validates each identifier with indexed pointer"() {
    given:
    def article = new ResourceObject(
        "articles", "1", null, null,
        Relationships.ofRelationships([
          tags: Relationship.withData(new RelationshipData.IdentifierCollectionLinkage([
            ResourceIdentifier.of("tags", "1"),
            new ResourceIdentifier("tags", "2", null, null, ["ext:bad": 1])
          ]))
        ]),
        null, null, [:])
    def doc = JsonApiDocument.withData(new DocumentData.SingleResource(article))

    when:
    validator.validate(doc, ValidationContext.defaults())

    then:
    def ex = thrown(JsonApiValidationException)
    ex.ruleCode() == ValidationRuleCode.DISALLOWED_ADDITIONAL_MEMBER
    ex.jsonPointer() == "/data/relationships/tags/data/1/ext:bad"
  }

  def "to-one relationship pagination is rejected"() {
    given:
    def article = new ResourceObject(
        "articles", "1", null, null,
        Relationships.ofRelationships([
          author: new Relationship(
          new RelationshipData.SingleLinkage(ResourceIdentifier.of("people", "9")),
          Links.ofLinks([first: new Link.StringLink("http://example.com/a?page=1")]),
          null,
          [:])
        ]),
        null, null, [:])
    def doc = JsonApiDocument.withData(new DocumentData.SingleResource(article))

    when:
    validator.validate(doc, ValidationContext.defaults())

    then:
    def ex = thrown(JsonApiValidationException)
    ex.ruleCode() == ValidationRuleCode.PAGINATION_REQUIRES_COLLECTION
    ex.jsonPointer() == "/data/relationships/author/links/first"
  }

  def "top-level pagination requires collection primary data"() {
    given:
    def doc = new JsonApiDocument(
        new DocumentData.SingleResource(ResourceObject.of("articles", "1")),
        null, null, null,
        Links.ofLinks([next: new Link.StringLink("http://example.com/page=2")]),
        null, [:])

    when:
    validator.validate(doc, ValidationContext.defaults())

    then:
    def ex = thrown(JsonApiValidationException)
    ex.ruleCode() == ValidationRuleCode.PAGINATION_REQUIRES_COLLECTION
    ex.jsonPointer() == "/links/next"
  }

  def "top-level pagination allowed for resource collection"() {
    given:
    def doc = new JsonApiDocument(
        new DocumentData.ResourceCollection([
          ResourceObject.of("articles", "1")
        ]),
        null, null, null,
        Links.ofLinks([next: new Link.StringLink("http://example.com/page=2")]),
        null, [:])

    when:
    validator.validate(doc, ValidationContext.defaults())

    then:
    noExceptionThrown()
  }

  def "relationship describedby is not a standard link"() {
    given:
    def article = new ResourceObject(
        "articles", "1", null, null,
        Relationships.ofRelationships([
          author: Relationship.linkOnly(Links.ofLinks([
            self       : new Link.StringLink("http://example.com/rel"),
            describedby: new Link.StringLink("http://example.com/docs")
          ]))
        ]),
        null, null, [:])
    def doc = JsonApiDocument.withData(new DocumentData.SingleResource(article))

    when:
    validator.validate(doc, ValidationContext.defaults())

    then:
    def ex = thrown(JsonApiValidationException)
    ex.ruleCode() == ValidationRuleCode.INVALID_LINKS_CONTEXT
    ex.jsonPointer() == "/data/relationships/author/links/describedby"
  }

  def "identifier then included resource with conflicting lid is rejected"() {
    given:
    def article = new ResourceObject(
        "articles", "1", null, null,
        Relationships.ofRelationships([
          author: Relationship.withData(new RelationshipData.SingleLinkage(
          new ResourceIdentifier("people", "9", "local", null, [:])))
        ]),
        null, null, [:])
    def included = new ResourceObject("people", "10", "local", null, null, null, null, [:])
    def doc = new JsonApiDocument(
        new DocumentData.SingleResource(article),
        null, null, null, null,
        [included],
        [:])

    when:
    validator.validate(doc, ValidationContext.defaults())

    then:
    def ex = thrown(JsonApiValidationException)
    ex.ruleCode() == ValidationRuleCode.INCONSISTENT_LOCAL_IDENTIFIER
  }

  def "included resource then identifier with conflicting lid is rejected"() {
    given:
    def primary = new ResourceObject("people", "10", "local", null, null, null, null, [:])
    def article = new ResourceObject(
        "articles", "1", null, null,
        Relationships.ofRelationships([
          author: Relationship.withData(new RelationshipData.SingleLinkage(
          new ResourceIdentifier("people", "9", "local", null, [:])))
        ]),
        null, null, [:])
    def doc = new JsonApiDocument(
        new DocumentData.SingleResource(primary),
        null, null, null, null,
        [article],
        [:])

    when:
    validator.validate(doc, ValidationContext.defaults())

    then:
    def ex = thrown(JsonApiValidationException)
    ex.ruleCode() == ValidationRuleCode.INCONSISTENT_LOCAL_IDENTIFIER
  }

  def "same id with different lid values is rejected"() {
    given:
    def article = new ResourceObject(
        "articles", "1", null, null,
        Relationships.ofRelationships([
          author: Relationship.withData(new RelationshipData.SingleLinkage(
          new ResourceIdentifier("people", "9", "lid-a", null, [:])))
        ]),
        null, null, [:])
    def included = new ResourceObject("people", "9", "lid-b", null, null, null, null, [:])
    def doc = new JsonApiDocument(
        new DocumentData.SingleResource(article),
        null, null, null, null,
        [included],
        [:])

    when:
    validator.validate(doc, ValidationContext.defaults())

    then:
    def ex = thrown(JsonApiValidationException)
    ex.ruleCode() == ValidationRuleCode.INCONSISTENT_LOCAL_IDENTIFIER
  }

  def "extension-only relationship link is accepted when namespace allowed"() {
    given:
    def article = new ResourceObject(
        "articles", "1", null, null,
        Relationships.ofRelationships([
          author: Relationship.linkOnly(Links.ofLinks([
            "ext:custom": new Link.StringLink("http://example.com/ext")
          ]))
        ]),
        null, null, [:])
    def doc = JsonApiDocument.withData(new DocumentData.SingleResource(article))
    def context = new ValidationContext(
        DocumentUsage.RESPONSE_OR_OTHER,
        Set.of("ext"),
        Set.of(),
        Set.of(),
        Set.of(),
        LinksContext.TOP_LEVEL,
        Map.of(),
        null)

    when:
    validator.validate(doc, context)

    then:
    noExceptionThrown()
  }

  def "extension relationship link is rejected when namespace disallowed"() {
    given:
    def article = new ResourceObject(
        "articles", "1", null, null,
        Relationships.ofRelationships([
          author: Relationship.linkOnly(Links.ofLinks([
            "ext:custom": new Link.StringLink("http://example.com/ext")
          ]))
        ]),
        null, null, [:])
    def doc = JsonApiDocument.withData(new DocumentData.SingleResource(article))

    when:
    validator.validate(doc, ValidationContext.defaults())

    then:
    def ex = thrown(JsonApiValidationException)
    ex.ruleCode() == ValidationRuleCode.DISALLOWED_ADDITIONAL_MEMBER
    ex.jsonPointer() == "/data/relationships/author/links/ext:custom"
  }

  def "profile-permitted link relation is accepted"() {
    given:
    def article = new ResourceObject(
        "articles", "1", null, null, null,
        Links.ofLinks([canonical: new Link.StringLink("http://example.com/canonical")]),
        null, [:])
    def doc = JsonApiDocument.withData(new DocumentData.SingleResource(article))
    def context = new ValidationContext(
        DocumentUsage.RESPONSE_OR_OTHER,
        Set.of(),
        Set.of(),
        Set.of("canonical"),
        Set.of(),
        LinksContext.TOP_LEVEL,
        Map.of(),
        null)

    when:
    validator.validate(doc, context)

    then:
    noExceptionThrown()
  }

  def "binding then lid-only included resource with different attributes is rejected"() {
    given:
    def primary = new ResourceObject(
        "people", "9", "local", Attributes.ofAttributes([name: "primary"]), null, null, null, [:])
    def included = new ResourceObject(
        "people", null, "local", Attributes.ofAttributes([name: "other"]), null, null, null, [:])
    def doc = new JsonApiDocument(
        new DocumentData.SingleResource(primary),
        null, null, null, null,
        [included],
        [:])
    def context = ValidationContext.defaults().withDocumentUsage(DocumentUsage.CREATE_REQUEST)

    when:
    validator.validate(doc, context)

    then:
    def ex = thrown(JsonApiValidationException)
    ex.ruleCode() == ValidationRuleCode.DUPLICATE_RESOURCE_IDENTITY
  }

  def "id binding then later id binding with different resource is rejected"() {
    given:
    def first = new ResourceObject(
        "people", "9", "local", Attributes.ofAttributes([name: "a"]), null, null, null, [:])
    def second = new ResourceObject(
        "people", "9", "local", Attributes.ofAttributes([name: "b"]), null, null, null, [:])
    def doc = new JsonApiDocument(
        new DocumentData.ResourceCollection([first, second]),
        null, null, null, null, null, [:])

    when:
    validator.validate(doc, ValidationContext.defaults())

    then:
    def ex = thrown(JsonApiValidationException)
    ex.ruleCode() == ValidationRuleCode.DUPLICATE_RESOURCE_IDENTITY
  }

  def "distinct type-id tuples with embedded colons do not collide"() {
    given:
    def first = ResourceObject.of("a", "b:c")
    def second = ResourceObject.of("a:b", "c")
    def doc = new JsonApiDocument(
        new DocumentData.ResourceCollection([first, second]),
        null, null, null, null, null, [:])

    when:
    validator.validate(doc, ValidationContext.defaults())

    then:
    noExceptionThrown()
  }

  def "whitespace resource id satisfies response identity requirement"() {
    given:
    def doc = JsonApiDocument.withData(
        new DocumentData.SingleResource(new ResourceObject("articles", " ", null, null, null, null, null, [:])))

    when:
    validator.validate(doc, ValidationContext.defaults())

    then:
    noExceptionThrown()
  }

  def "disallowed extension attribute additional member is rejected"() {
    given:
    def article = new ResourceObject(
        "articles", "1", null,
        Attributes.of([:], ["ext:flag": true]),
        null, null, null, [:])
    def doc = JsonApiDocument.withData(new DocumentData.SingleResource(article))

    when:
    validator.validate(doc, ValidationContext.defaults())

    then:
    def ex = thrown(JsonApiValidationException)
    ex.ruleCode() == ValidationRuleCode.DISALLOWED_ADDITIONAL_MEMBER
  }

  def "allowed extension attribute additional member passes"() {
    given:
    def article = new ResourceObject(
        "articles", "1", null,
        Attributes.of([:], ["ext:flag": true]),
        null, null, null, [:])
    def doc = JsonApiDocument.withData(new DocumentData.SingleResource(article))
    def context = new ValidationContext(
        DocumentUsage.RESPONSE_OR_OTHER,
        Set.of("ext"),
        Set.of(),
        Set.of(),
        Set.of(),
        LinksContext.TOP_LEVEL,
        Map.of(),
        null)

    when:
    validator.validate(doc, context)

    then:
    noExceptionThrown()
  }

  def "validation context copies are isolated from caller mutation"() {
    given:
    def hints = new LinkedHashMap<RelationshipPaginationKey, RelationshipCardinality>()
    hints.put(
        RelationshipPaginationKey.of("articles", "comments"),
        RelationshipCardinality.TO_MANY)
    def namespaces = new HashSet<String>()
    namespaces.add("myext")
    def context = new ValidationContext(
        DocumentUsage.RESPONSE_OR_OTHER,
        namespaces,
        Set.of(),
        Set.of(),
        Set.of(),
        LinksContext.TOP_LEVEL,
        hints,
        null)
    def article = new ResourceObject(
        "articles", "1", null, null,
        Relationships.ofRelationships([
          comments: Relationship.linkOnly(Links.ofLinks([
            self : new Link.StringLink("http://example.com/c"),
            first: new Link.StringLink("http://example.com/c?page=1")
          ]))
        ]),
        null, null, [:])
    def doc = JsonApiDocument.withData(new DocumentData.SingleResource(article))

    when:
    hints.put(
        RelationshipPaginationKey.of("articles", "comments"),
        RelationshipCardinality.TO_ONE)
    namespaces.clear()
    validator.validate(doc, context)

    then:
    noExceptionThrown()
    context.relationshipPaginationHint("articles", "comments").orElse(null) ==
        RelationshipCardinality.TO_MANY
    context.allowedExtensionNamespaces().contains("myext")
  }

  def "validation context rejects null pagination hint values"() {
    given:
    def hints = new LinkedHashMap<RelationshipPaginationKey, RelationshipCardinality>()
    hints.put(RelationshipPaginationKey.of("articles", "comments"), null)

    when:
    new ValidationContext(
        DocumentUsage.RESPONSE_OR_OTHER,
        Set.of(),
        Set.of(),
        Set.of(),
        Set.of(),
        LinksContext.TOP_LEVEL,
        hints,
        null)

    then:
    def ex = thrown(JsonApiValidationException)
    ex.ruleCode() == ValidationRuleCode.NULL_REQUIRED_VALUE
    ex.jsonPointer() == "/relationshipPaginationHints"
  }

  def "validation context rejects null pagination hint keys"() {
    given:
    def hints = new LinkedHashMap<RelationshipPaginationKey, RelationshipCardinality>()
    hints.put(null, RelationshipCardinality.TO_MANY)

    when:
    new ValidationContext(
        DocumentUsage.RESPONSE_OR_OTHER,
        Set.of(),
        Set.of(),
        Set.of(),
        Set.of(),
        LinksContext.TOP_LEVEL,
        hints,
        null)

    then:
    def ex = thrown(JsonApiValidationException)
    ex.ruleCode() == ValidationRuleCode.NULL_REQUIRED_VALUE
    ex.jsonPointer() == "/relationshipPaginationHints"
  }

  def "validation context rejects null documentUsage"() {
    when:
    new ValidationContext(
        null,
        Set.of(),
        Set.of(),
        Set.of(),
        Set.of(),
        LinksContext.TOP_LEVEL,
        Map.of(),
        null)

    then:
    def ex = thrown(JsonApiValidationException)
    ex.ruleCode() == ValidationRuleCode.NULL_REQUIRED_VALUE
    ex.jsonPointer() == "/documentUsage"
  }

  def "validation context rejects null policy sets"() {
    when:
    new ValidationContext(
        DocumentUsage.RESPONSE_OR_OTHER,
        null,
        Set.of(),
        Set.of(),
        Set.of(),
        LinksContext.TOP_LEVEL,
        Map.of(),
        null)

    then:
    def ex = thrown(JsonApiValidationException)
    ex.ruleCode() == ValidationRuleCode.NULL_REQUIRED_VALUE
    ex.jsonPointer() == "/allowedExtensionNamespaces"
  }

  def "validation context rejects null policy set elements"() {
    given:
    def namespaces = new HashSet<String>()
    namespaces.add(null)

    when:
    new ValidationContext(
        DocumentUsage.RESPONSE_OR_OTHER,
        namespaces,
        Set.of(),
        Set.of(),
        Set.of(),
        LinksContext.TOP_LEVEL,
        Map.of(),
        null)

    then:
    def ex = thrown(JsonApiValidationException)
    ex.ruleCode() == ValidationRuleCode.NULL_REQUIRED_VALUE
    ex.jsonPointer() == "/allowedExtensionNamespaces/0"
  }

  def "validation context rejects null hints map"() {
    when:
    new ValidationContext(
        DocumentUsage.RESPONSE_OR_OTHER,
        Set.of(),
        Set.of(),
        Set.of(),
        Set.of(),
        LinksContext.TOP_LEVEL,
        null,
        null)

    then:
    def ex = thrown(JsonApiValidationException)
    ex.ruleCode() == ValidationRuleCode.NULL_REQUIRED_VALUE
    ex.jsonPointer() == "/relationshipPaginationHints"
  }

  def "duplicate primary collection identities are rejected"() {
    given:
    def doc = new JsonApiDocument(
        new DocumentData.ResourceCollection([
          ResourceObject.of("articles", "1"),
          ResourceObject.of("articles", "1")
        ]),
        null, null, null, null, null, [:])

    when:
    validator.validate(doc, ValidationContext.defaults())

    then:
    def ex = thrown(JsonApiValidationException)
    ex.ruleCode() == ValidationRuleCode.DUPLICATE_RESOURCE_IDENTITY
    ex.jsonPointer() == "/data/1"
  }

  def "duplicate primary identifier collection id-only identities are rejected"() {
    given:
    def doc = new JsonApiDocument(
        new DocumentData.IdentifierCollection([
          ResourceIdentifier.of("articles", "1"),
          ResourceIdentifier.of("articles", "1")
        ]),
        null, null, null, null, null, [:])

    when:
    validator.validate(doc, ValidationContext.defaults())

    then:
    def ex = thrown(JsonApiValidationException)
    ex.ruleCode() == ValidationRuleCode.DUPLICATE_RESOURCE_IDENTITY
    ex.jsonPointer() == "/data/1"
  }

  def "duplicate primary identifier collection lid identities are rejected"() {
    given:
    def doc = new JsonApiDocument(
        new DocumentData.IdentifierCollection([
          ResourceIdentifier.withLid("articles", "local-1"),
          ResourceIdentifier.withLid("articles", "local-1")
        ]),
        null, null, null, null, null, [:])

    when:
    validator.validate(doc, ValidationContext.defaults())

    then:
    def ex = thrown(JsonApiValidationException)
    ex.ruleCode() == ValidationRuleCode.DUPLICATE_RESOURCE_IDENTITY
    ex.jsonPointer() == "/data/1"
  }

  def "duplicate relationship identifier collection id-only identities are rejected"() {
    given:
    def article = new ResourceObject(
        "articles", "1", null, null,
        Relationships.ofRelationships([
          comments: Relationship.withData(new RelationshipData.IdentifierCollectionLinkage([
            ResourceIdentifier.of("comments", "5"),
            ResourceIdentifier.of("comments", "5")
          ]))
        ]),
        null, null, [:])
    def doc = JsonApiDocument.withData(new DocumentData.SingleResource(article))

    when:
    validator.validate(doc, ValidationContext.defaults())

    then:
    def ex = thrown(JsonApiValidationException)
    ex.ruleCode() == ValidationRuleCode.DUPLICATE_RESOURCE_IDENTITY
    ex.jsonPointer() == "/data/relationships/comments/data/1"
  }

  def "duplicate relationship identifier collection lid-only identities are rejected"() {
    given:
    def article = new ResourceObject(
        "articles", "1", null, null,
        Relationships.ofRelationships([
          comments: Relationship.withData(new RelationshipData.IdentifierCollectionLinkage([
            ResourceIdentifier.withLid("comments", "c-local"),
            ResourceIdentifier.withLid("comments", "c-local")
          ]))
        ]),
        null, null, [:])
    def doc = JsonApiDocument.withData(new DocumentData.SingleResource(article))
    def context = ValidationContext.defaults().withDocumentUsage(DocumentUsage.CREATE_REQUEST)

    when:
    validator.validate(doc, context)

    then:
    def ex = thrown(JsonApiValidationException)
    ex.ruleCode() == ValidationRuleCode.DUPLICATE_RESOURCE_IDENTITY
    ex.jsonPointer() == "/data/relationships/comments/data/1"
  }

  def "create request reports primary-data shape before aggregate alias binding"() {
    given:
    def binder = new ResourceObject("people", "9", "p-local", null, null, null, null, [:])
    def doc = new JsonApiDocument(
        new DocumentData.IdentifierCollection([
          ResourceIdentifier.of("people", "9"),
          ResourceIdentifier.withLid("people", "p-local")
        ]),
        null, null, null, null,
        [binder],
        [:])
    def context = ValidationContext.defaults().withDocumentUsage(DocumentUsage.CREATE_REQUEST)

    when:
    validator.validate(doc, context)

    then:
    def ex = thrown(JsonApiValidationException)
    ex.ruleCode() == ValidationRuleCode.CREATE_REQUIRES_SINGLE_RESOURCE
    ex.jsonPointer() == "/data"
  }

  def "cross-alias duplicate relationship identifier collection is rejected after binding"() {
    given:
    def comment = new ResourceObject("comments", "5", "c-local", null, null, null, null, [:])
    def article = new ResourceObject(
        "articles", "1", null, null,
        Relationships.ofRelationships([
          comments: Relationship.withData(new RelationshipData.IdentifierCollectionLinkage([
            ResourceIdentifier.of("comments", "5"),
            ResourceIdentifier.withLid("comments", "c-local")
          ]))
        ]),
        null, null, [:])
    def doc = new JsonApiDocument(
        new DocumentData.SingleResource(article),
        null, null, null, null,
        [comment],
        [:])
    def context = ValidationContext.defaults().withDocumentUsage(DocumentUsage.CREATE_REQUEST)

    when:
    validator.validate(doc, context)

    then:
    def ex = thrown(JsonApiValidationException)
    ex.ruleCode() == ValidationRuleCode.DUPLICATE_RESOURCE_IDENTITY
    ex.jsonPointer() == "/data/relationships/comments/data/1"
  }

  def "cross-alias duplicate relationship collection is rejected when included is absent"() {
    given:
    def article = new ResourceObject(
        "articles", "1", "a-local", null,
        Relationships.ofRelationships([
          related: Relationship.withData(new RelationshipData.IdentifierCollectionLinkage([
            ResourceIdentifier.of("articles", "1"),
            ResourceIdentifier.withLid("articles", "a-local")
          ]))
        ]),
        null, null, [:])
    def doc = JsonApiDocument.withData(new DocumentData.SingleResource(article))
    def context = ValidationContext.defaults().withDocumentUsage(DocumentUsage.CREATE_REQUEST)

    when:
    validator.validate(doc, context)

    then:
    def ex = thrown(JsonApiValidationException)
    ex.ruleCode() == ValidationRuleCode.DUPLICATE_RESOURCE_IDENTITY
    ex.jsonPointer() == "/data/relationships/related/data/1"
  }

  def "cyclic included graph validates when fully linked"() {
    given:
    def person = new ResourceObject(
        "people", "9", null, null,
        Relationships.ofRelationships([
          articles: Relationship.withData(
          new RelationshipData.SingleLinkage(ResourceIdentifier.of("articles", "1")))
        ]),
        null, null, [:])
    def article = new ResourceObject(
        "articles", "1", null, null,
        Relationships.ofRelationships([
          author: Relationship.withData(
          new RelationshipData.SingleLinkage(ResourceIdentifier.of("people", "9")))
        ]),
        null, null, [:])
    def doc = new JsonApiDocument(
        new DocumentData.SingleResource(article),
        null, null, null, null,
        [person],
        [:])

    when:
    validator.validate(doc, ValidationContext.defaults())

    then:
    noExceptionThrown()
  }

  def "multi-primary shared included identity validates"() {
    given:
    def author = new ResourceObject(
        "people", "9", null,
        Attributes.ofAttributes([name: "Dan"]),
        null, null, null, [:])
    def article1 = new ResourceObject(
        "articles", "1", null, null,
        Relationships.ofRelationships([
          author: Relationship.withData(
          new RelationshipData.SingleLinkage(ResourceIdentifier.of("people", "9")))
        ]),
        null, null, [:])
    def article2 = new ResourceObject(
        "articles", "2", null, null,
        Relationships.ofRelationships([
          author: Relationship.withData(
          new RelationshipData.SingleLinkage(ResourceIdentifier.of("people", "9")))
        ]),
        null, null, [:])
    def doc = new JsonApiDocument(
        new DocumentData.ResourceCollection([article1, article2]),
        null, null, null, null,
        [author],
        [:])

    when:
    validator.validate(doc, ValidationContext.defaults())

    then:
    noExceptionThrown()
  }

  def "alternate-only links-only relationship is rejected at aggregate"() {
    given:
    def article = new ResourceObject(
        "articles", "1", null, null,
        Relationships.ofRelationships([
          author: Relationship.linkOnly(Links.ofLinks([
            alternate: new Link.StringLink("http://example.com/alternate")
          ]))
        ]),
        null, null, [:])
    def doc = JsonApiDocument.withData(new DocumentData.SingleResource(article))

    when:
    validator.validate(doc, ValidationContext.defaults())

    then:
    def ex = thrown(JsonApiValidationException)
    ex.ruleCode() == ValidationRuleCode.MISSING_RELATIONSHIP_MEMBER
  }

  def "top-level pagination allowed for identifier collection"() {
    given:
    def doc = new JsonApiDocument(
        new DocumentData.IdentifierCollection([
          ResourceIdentifier.of("articles", "1")
        ]),
        null, null, null,
        Links.ofLinks([next: new Link.StringLink("http://example.com/page=2")]),
        null, [:])

    when:
    validator.validate(doc, ValidationContext.defaults())

    then:
    noExceptionThrown()
  }
}
