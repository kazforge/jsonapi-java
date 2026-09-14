package com.kazforge.jsonapi.core.validation

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
import com.kazforge.jsonapi.core.model.ResourceObject
import spock.lang.Specification

class CreateRequestValidationSpec extends Specification {

  def validator = new JsonApiDocumentValidator()

  static def createContext() {
    ValidationContext.defaults().withDocumentUsage(DocumentUsage.CREATE_REQUEST)
  }

  def "create request rejects '#name' primary data"(String name, JsonApiDocument doc) {
    given:
    def context = new ValidationContext(
        DocumentUsage.CREATE_REQUEST,
        PrimaryDataContext.RESOURCE,
        Set.of("ext"), Set.of(), Set.of(),
        Set.of(),
        Map.of(), null)

    when:
    validator.validate(doc, context)

    then:
    def ex = thrown(JsonApiValidationException)
    ex.ruleCode() == ValidationRuleCode.CREATE_REQUIRES_SINGLE_RESOURCE
    ex.jsonPointer() == "/data"

    where:
    name                                    | doc
    "absent data (meta-only document)"      | JsonApiDocument.withMeta(Meta.empty())
    "absent data (errors-only document)"    | JsonApiDocument.withErrors([ErrorObject.ofTitle("boom")])
    "absent data (extension-only document)" | new JsonApiDocument(null, null, null, null, null, null, ["ext:doc": 1])
    "explicit null data"                    | JsonApiDocument.withData(DocumentData.NullData.INSTANCE)
    "singleton resource collection"         | JsonApiDocument.withData(
        new DocumentData.ResourceCollection([
          ResourceObject.ofType("articles")
        ]))
    "multi-element resource collection"     | JsonApiDocument.withData(
        new DocumentData.ResourceCollection(
        [
          ResourceObject.ofType("articles"),
          ResourceObject.of("articles", "2")
        ]))
    "single resource identifier"            | JsonApiDocument.withData(
        new DocumentData.SingleIdentifier(ResourceIdentifier.of("articles", "1")))
    "identifier collection"                 | JsonApiDocument.withData(
        new DocumentData.IdentifierCollection([
          ResourceIdentifier.of("articles", "1")
        ]))
  }

  def "create request accepts '#identity' primary resource identity"(String identity, ResourceObject resource) {
    given:
    def doc = JsonApiDocument.withData(new DocumentData.SingleResource(resource))

    when:
    validator.validate(doc, createContext())

    then:
    noExceptionThrown()

    where:
    identity       | resource
    "no id, no lid" | ResourceObject.ofType("articles")
    "id only"       | ResourceObject.of("articles", "1")
    "lid only"      | new ResourceObject("articles", null, "lid-1", null, null, null, null, [:])
    "id and lid"    | new ResourceObject("articles", "1", "lid-1", null, null, null, null, [:])
  }

  def "create request accepts absent or present-empty relationships"() {
    given:
    def absent = JsonApiDocument.withData(new DocumentData.SingleResource(ResourceObject.ofType("articles")))
    def presentEmpty = JsonApiDocument.withData(new DocumentData.SingleResource(
        new ResourceObject("articles", null, null, null, Relationships.empty(), null, null, [:])))

    when:
    validator.validate(absent, createContext())
    validator.validate(presentEmpty, createContext())

    then:
    noExceptionThrown()
  }

  def "create request accepts '#linkage' relationship linkage"(String linkage, Relationship relationship) {
    given:
    def article = new ResourceObject(
        "articles", null, null, null,
        Relationships.ofRelationships([author: relationship]),
        null, null, [:])
    def doc = JsonApiDocument.withData(new DocumentData.SingleResource(article))

    when:
    validator.validate(doc, createContext())

    then:
    noExceptionThrown()

    where:
    linkage                          | relationship
    "null linkage"                   | Relationship.withData(RelationshipData.NullLinkage.INSTANCE)
    "single linkage"                 | Relationship.withData(
        new RelationshipData.SingleLinkage(ResourceIdentifier.of("people", "2")))
    "empty collection"               | Relationship.withData(
        new RelationshipData.IdentifierCollectionLinkage([]))
    "non-empty collection"           | Relationship.withData(
        new RelationshipData.IdentifierCollectionLinkage(
        [
          ResourceIdentifier.of("tags", "1"),
          ResourceIdentifier.of("tags", "2")
        ]))
    "single linkage plus links"      | new Relationship(
        new RelationshipData.SingleLinkage(ResourceIdentifier.of("people", "2")),
        Links.ofLinks([self: new Link.StringLink("http://example.com/authors/2")]),
        null,
        [:])
    "single linkage plus meta"       | new Relationship(
        new RelationshipData.SingleLinkage(ResourceIdentifier.of("people", "2")),
        null,
        Meta.of([count: 1]),
        [:])
    "collection linkage plus links and meta" | new Relationship(
        new RelationshipData.IdentifierCollectionLinkage([
          ResourceIdentifier.of("tags", "1")
        ]),
        Links.ofLinks([self: new Link.StringLink("http://example.com/tags")]),
        Meta.of([count: 1]),
        [:])
  }

  def "create request accepts lid-only self-reference to the primary create resource"(String linkage, Relationship relationship) {
    given:
    def article = new ResourceObject(
        "articles", null, "article-lid", null,
        Relationships.ofRelationships([origin: relationship]),
        null, null, [:])
    def doc = JsonApiDocument.withData(new DocumentData.SingleResource(article))

    when:
    validator.validate(doc, createContext())

    then:
    noExceptionThrown()

    where:
    linkage                          | relationship
    "single self-reference"          | Relationship.withData(
        new RelationshipData.SingleLinkage(ResourceIdentifier.withLid("articles", "article-lid")))
    "collection self-reference"      | Relationship.withData(
        new RelationshipData.IdentifierCollectionLinkage(
        [
          ResourceIdentifier.withLid("articles", "article-lid")
        ]))
  }

  def "create request rejects unrelated lid-only relationship linkage"(String linkage, Relationship relationship, String pointer) {
    given:
    def article = new ResourceObject(
        "articles", null, "article-lid", null,
        Relationships.ofRelationships([author: relationship]),
        null, null, [:])
    def doc = JsonApiDocument.withData(new DocumentData.SingleResource(article))

    when:
    validator.validate(doc, createContext())

    then:
    def ex = thrown(JsonApiValidationException)
    ex.ruleCode() == ValidationRuleCode.RESOURCE_ID_REQUIRED
    ex.jsonPointer() == pointer

    where:
    linkage                                | relationship                                                                 | pointer
    "single lid linkage, other type"       | Relationship.withData(new RelationshipData.SingleLinkage(ResourceIdentifier.withLid("people", "people-lid"))) | "/data/relationships/author/data/id"
    "single lid linkage, other lid"        | Relationship.withData(new RelationshipData.SingleLinkage(ResourceIdentifier.withLid("articles", "other-lid"))) | "/data/relationships/author/data/id"
    "lid collection, unrelated"            | Relationship.withData(new RelationshipData.IdentifierCollectionLinkage([
      ResourceIdentifier.withLid("tags", "tag-lid")
    ])) | "/data/relationships/author/data/0/id"
  }

  def "create request rejects lid-only linkage when the primary carries no lid"() {
    given:
    def article = new ResourceObject(
        "articles", null, null, null,
        Relationships.ofRelationships([
          author: Relationship.withData(
          new RelationshipData.SingleLinkage(ResourceIdentifier.withLid("articles", "orphan-lid")))
        ]),
        null, null, [:])
    def doc = JsonApiDocument.withData(new DocumentData.SingleResource(article))

    when:
    validator.validate(doc, createContext())

    then:
    def ex = thrown(JsonApiValidationException)
    ex.ruleCode() == ValidationRuleCode.RESOURCE_ID_REQUIRED
    ex.jsonPointer() == "/data/relationships/author/data/id"
  }

  def "create request accepts relationship with data plus links, meta, and allowed extension member"() {
    given:
    def relationship = new Relationship(
        new RelationshipData.SingleLinkage(ResourceIdentifier.of("people", "2")),
        Links.ofLinks([self: new Link.StringLink("http://example.com/authors/2")]),
        Meta.of([count: 1]),
        ["ext:x": 1])
    def article = new ResourceObject(
        "articles", null, "article-lid", null,
        Relationships.ofRelationships([author: relationship]),
        null, null, [:])
    def doc = JsonApiDocument.withData(new DocumentData.SingleResource(article))
    def context = new ValidationContext(
        DocumentUsage.CREATE_REQUEST,
        PrimaryDataContext.RESOURCE,
        Set.of("ext"), Set.of(), Set.of(),
        Set.of(),
        Map.of(), null)

    when:
    validator.validate(doc, context)

    then:
    noExceptionThrown()
  }

  def "create request rejects '#shape' relationship without data"(String shape, Relationship relationship) {
    given:
    def article = new ResourceObject(
        "articles", null, null, null,
        Relationships.ofRelationships([author: relationship]),
        null, null, [:])
    def doc = JsonApiDocument.withData(new DocumentData.SingleResource(article))

    when:
    validator.validate(doc, createContext())

    then:
    def ex = thrown(JsonApiValidationException)
    ex.ruleCode() == ValidationRuleCode.RELATIONSHIP_DATA_REQUIRED
    ex.jsonPointer() == "/data/relationships/author/data"

    where:
    shape                        | relationship
    "self-link-only"             | Relationship.linkOnly(
        Links.ofLinks([self: new Link.StringLink("http://example.com/authors/2")]))
    "meta-only"                  | Relationship.metaOnly(Meta.of([count: 1]))
    "links plus meta, no data"   | new Relationship(
        null,
        Links.ofLinks([self: new Link.StringLink("http://example.com/authors/2")]),
        Meta.of([count: 1]),
        [:])
    "extension-only"             | new Relationship(null, null, null, ["ext:x": 1])
  }

  def "create request accepts compound document without applying primary relationship rules to included resources"() {
    given:
    def article = new ResourceObject(
        "articles", null, "article-lid", null,
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

    when:
    validator.validate(doc, createContext())

    then:
    noExceptionThrown()
  }

  def "create request rejects lid-only included resources"() {
    given:
    def article = new ResourceObject(
        "articles", null, "article-lid", null,
        Relationships.ofRelationships([
          author: Relationship.withData(
          new RelationshipData.SingleLinkage(ResourceIdentifier.of("people", "2")))
        ]),
        null, null, [:])
    def author = new ResourceObject(
        "people", null, "author-lid", null, null, null, null, [:])
    def doc = new JsonApiDocument(
        new DocumentData.SingleResource(article),
        null, null, null, null,
        [author],
        [:])

    when:
    validator.validate(doc, createContext())

    then:
    def ex = thrown(JsonApiValidationException)
    ex.ruleCode() == ValidationRuleCode.RESOURCE_ID_REQUIRED
    ex.jsonPointer() == "/included/0/id"
  }

  def "lid-only linkage remains rejected outside create-request contexts"() {
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
    validator.validate(doc, context)

    then:
    def ex = thrown(JsonApiValidationException)
    ex.ruleCode() == ValidationRuleCode.RESOURCE_ID_REQUIRED
    ex.jsonPointer() == "/data/relationships/author/data/id"

    where:
    context << [
      ValidationContext.defaults(),
      ValidationContext.defaults().withDocumentUsage(DocumentUsage.UPDATE_REQUEST)
    ]
  }

  def "create request still enforces full linkage for included resources"() {
    given:
    def doc = new JsonApiDocument(
        new DocumentData.SingleResource(ResourceObject.ofType("articles")),
        null, null, null, null,
        [
          ResourceObject.of("people", "2")
        ],
        [:])

    when:
    validator.validate(doc, createContext())

    then:
    def ex = thrown(JsonApiValidationException)
    ex.ruleCode() == ValidationRuleCode.FULL_LINKAGE_VIOLATION
    ex.jsonPointer() == "/included"
  }

  def "data-less relationship remains valid outside create-request contexts"(String shape, Relationship relationship) {
    given:
    def article = new ResourceObject(
        "articles", "1", null, null,
        Relationships.ofRelationships([author: relationship]),
        null, null, [:])
    def doc = JsonApiDocument.withData(new DocumentData.SingleResource(article))

    when:
    validator.validate(doc, ValidationContext.defaults())

    then:
    noExceptionThrown()

    where:
    shape                      | relationship
    "self-link-only"           | Relationship.linkOnly(
        Links.ofLinks([self: new Link.StringLink("http://example.com/authors/2")]))
    "meta-only"                | Relationship.metaOnly(Meta.of([count: 1]))
    "links plus meta, no data" | new Relationship(
        null,
        Links.ofLinks([self: new Link.StringLink("http://example.com/authors/2")]),
        Meta.of([count: 1]),
        [:])
  }

  def "create operation accepts linkage primary data under relationship role"(DocumentData data) {
    given:
    def doc = JsonApiDocument.withData(data)
    def context = ValidationContext.defaults()
        .withDocumentUsage(DocumentUsage.CREATE_REQUEST)
        .withPrimaryDataContext(PrimaryDataContext.RELATIONSHIP)

    when:
    validator.validate(doc, context)

    then:
    noExceptionThrown()

    where:
    data << [
      new DocumentData.SingleIdentifier(ResourceIdentifier.of("articles", "1")),
      new DocumentData.IdentifierCollection([
        ResourceIdentifier.of("articles", "1")
      ]),
      DocumentData.NullData.INSTANCE
    ]
  }

  def "create operation under relationship role rejects resource objects with context mismatch"() {
    given:
    def doc = JsonApiDocument.withData(
        new DocumentData.SingleResource(ResourceObject.ofType("articles")))
    def context = ValidationContext.defaults()
        .withDocumentUsage(DocumentUsage.CREATE_REQUEST)
        .withPrimaryDataContext(PrimaryDataContext.RELATIONSHIP)

    when:
    validator.validate(doc, context)

    then:
    def ex = thrown(JsonApiValidationException)
    ex.ruleCode() == ValidationRuleCode.PRIMARY_DATA_CONTEXT_MISMATCH
    ex.jsonPointer() == "/data"
  }
}
