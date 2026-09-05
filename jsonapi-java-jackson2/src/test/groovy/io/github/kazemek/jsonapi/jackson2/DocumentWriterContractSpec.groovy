package io.github.kazemek.jsonapi.jackson2

import com.fasterxml.jackson.databind.json.JsonMapper

import io.github.kazemek.jsonapi.core.model.Attributes
import io.github.kazemek.jsonapi.core.model.DocumentData
import io.github.kazemek.jsonapi.core.model.JsonApiDocument
import io.github.kazemek.jsonapi.core.model.JsonApiObject
import io.github.kazemek.jsonapi.core.model.Link
import io.github.kazemek.jsonapi.core.model.Links
import io.github.kazemek.jsonapi.core.model.Meta
import io.github.kazemek.jsonapi.core.model.Relationship
import io.github.kazemek.jsonapi.core.model.RelationshipData
import io.github.kazemek.jsonapi.core.model.Relationships
import io.github.kazemek.jsonapi.core.model.ResourceIdentifier
import io.github.kazemek.jsonapi.core.model.ResourceIdentity
import io.github.kazemek.jsonapi.core.model.ResourceObject
import io.github.kazemek.jsonapi.core.validation.DocumentUsage
import io.github.kazemek.jsonapi.core.validation.JsonApiValidationException
import io.github.kazemek.jsonapi.core.validation.LinksContext
import io.github.kazemek.jsonapi.core.validation.ValidationContext
import io.github.kazemek.jsonapi.core.validation.ValidationRuleCode
import io.github.kazemek.jsonapi.fixtures.TestFixtureResources
import io.github.kazemek.jsonapi.jackson.mapping.MappedDocument

import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

class DocumentWriterContractSpec extends Specification {

  @Shared
  JsonMapper mapper = JsonMapper.builder().build()

  @Unroll
  def "writes independently constructed #description"() {
    given:
    def writer = JsonApiJackson2.writer(mapper, ValidationContext.defaults())
    def expected = mapper.readTree(expectedJson)

    when:
    def json = writer.writeValueAsString(document)

    then:
    mapper.readTree(json) == expected

    where:
    description | document | expectedJson
    "a resource with attributes" | directResourceDocument() | '{"data":{"type":"articles","id":"1","attributes":{"title":"Title"}}}'
    "a relationship linkage" | directRelationshipDocument() | '{"data":{"type":"articles","id":"1","relationships":{"author":{"data":{"type":"people","id":"9"}}}}}'
    "a compound document" | directCompoundDocument() | '{"data":{"type":"articles","id":"1","relationships":{"author":{"data":{"type":"people","id":"9"}}}},"included":[{"type":"people","id":"9","attributes":{"name":"Dan"}}]}'
    "explicit null data and meta" | directNullDataDocument() | '{"data":null,"meta":{"reason":"deleted"}}'
  }

  def "mapped provenance composes into mapped writing"() {
    given:
    def unlinkedAuthor = ResourceObject.of("people", "9")
    def document = new JsonApiDocument(
        new DocumentData.SingleResource(ResourceObject.of("articles", "1")),
        null,
        null,
        null,
        null,
        List.of(unlinkedAuthor),
        Map.of())
    def mapped = new MappedDocument(document, Set.of(ResourceIdentity.ofId("people", "9")))
    def writer = JsonApiJackson2.writer(mapper)
    def expected = mapper.readTree(
        '{"data":{"type":"articles","id":"1"},"included":[{"type":"people","id":"9"}]}')

    when:
    writer.writeValueAsString(mapped.document())

    then:
    def exception = thrown(JsonApiValidationException)
    exception.ruleCode() == ValidationRuleCode.FULL_LINKAGE_VIOLATION

    when:
    def json = writer.writeValueAsString(mapped)

    then:
    mapped.sparseFieldsetLinkageExemptions() == Set.of(ResourceIdentity.ofId("people", "9"))
    mapper.readTree(json) == expected
  }

  def "mapped writing preserves unrelated caller validation settings"() {
    given:
    def resource = new ResourceObject(
        "articles",
        "1",
        null,
        null,
        null,
        null,
        null,
        ["ext:flag": true])
    def document = new JsonApiDocument(
        new DocumentData.SingleResource(resource),
        null,
        null,
        null,
        null,
        null,
        ["ext:request-id": "abc-123"])
    def mapped = new MappedDocument(document, Set.of())
    def base = new ValidationContext(
        DocumentUsage.RESPONSE_OR_OTHER,
        Set.of("ext"),
        Set.of(),
        Set.of(),
        Set.of(),
        LinksContext.TOP_LEVEL,
        Map.of(),
        null)

    when:
    JsonApiJackson2.writer(mapper, base).writeValueAsString(mapped)

    then:
    noExceptionThrown()

    when:
    JsonApiJackson2.writer(mapper).writeValueAsString(mapped)

    then:
    def exception = thrown(JsonApiValidationException)
    exception.ruleCode() == ValidationRuleCode.DISALLOWED_ADDITIONAL_MEMBER
  }

  def "mapped writing unions bound and mapped linkage exemptions"() {
    given:
    def article = ResourceObject.of("articles", "1")
    def boundOrphan = ResourceObject.of("people", "9")
    def mappedOrphan = ResourceObject.of("people", "10")
    def mapped = new MappedDocument(
        new JsonApiDocument(
        new DocumentData.SingleResource(article),
        null,
        null,
        null,
        null,
        List.of(boundOrphan, mappedOrphan),
        Map.of()),
        Set.of(ResourceIdentity.ofId("people", "10")))
    def base = ValidationContext.defaults()
        .withSparseFieldsetLinkageExemptions(Set.of(ResourceIdentity.ofId("people", "9")))

    when:
    JsonApiJackson2.writer(mapper, base).writeValueAsString(mapped)

    then:
    noExceptionThrown()
  }

  def "mapped provenance exempts only mapped roots and preserves full-linkage validation"() {
    given:
    def primary = ResourceObject.of("articles", "1")
    def exemptedAuthor = ResourceObject.of("people", "9")
    def unrelatedOrphan = ResourceObject.of("tags", "7")
    def document = new JsonApiDocument(
        new DocumentData.SingleResource(primary),
        null,
        null,
        null,
        null,
        List.of(exemptedAuthor, unrelatedOrphan),
        Map.of())
    def writer = JsonApiJackson2.writer(mapper)

    when:
    writer.writeValueAsString(new MappedDocument(
        document, Set.of(ResourceIdentity.ofId("people", "9"))))

    then:
    def exception = thrown(JsonApiValidationException)
    exception.ruleCode() == ValidationRuleCode.FULL_LINKAGE_VIOLATION

    when:
    def subtreeAuthor = new ResourceObject(
        "people",
        "9",
        null,
        null,
        Relationships.ofRelationships([
          editor: Relationship.withData(
          new RelationshipData.SingleLinkage(ResourceIdentifier.of("people", "10")))
        ]),
        null,
        null,
        Map.of())
    def childOfExempted = ResourceObject.of("people", "10")
    def subtreeDocument = new JsonApiDocument(
        new DocumentData.SingleResource(primary),
        null,
        null,
        null,
        null,
        List.of(subtreeAuthor, childOfExempted),
        Map.of())
    writer.writeValueAsString(new MappedDocument(
        subtreeDocument, Set.of(ResourceIdentity.ofId("people", "9"))))

    then:
    noExceptionThrown()
  }

  def "emits exact member order for the constructed member-order corpus shape"() {
    given:
    def context = extContext()
    def writer = JsonApiJackson2.writer(mapper, context)
    def document = memberOrderDocument()
    def expected = TestFixtureResources.readCorpusUtf8("documents/member-order.compact.json").trim()

    expect:
    writer.writeValueAsString(document) == expected
  }

  def "emits array-form hreflang"() {
    given:
    def related = new Link.ObjectLink(
        "https://example.com/articles/1/related",
        "related",
        null,
        "Related",
        "application/vnd.api+json",
        ["en"],
        null,
        Map.of())
    def topLinks = new LinkedHashMap<String, Link>()
    topLinks.put("self", new Link.StringLink("https://example.com/articles/1"))
    topLinks.put("related", related)
    def document = new JsonApiDocument(
        new DocumentData.SingleResource(ResourceObject.of("articles", "1")),
        null,
        null,
        null,
        Links.ofLinks(topLinks),
        null,
        Map.of())
    def writer = JsonApiJackson2.writer(mapper, ValidationContext.defaults())

    when:
    def json = writer.writeValueAsString(document)

    then:
    json.contains('"hreflang":["en"]')
    !json.contains('"hreflang":"en"')
  }

  private static ValidationContext extContext() {
    return new ValidationContext(
        DocumentUsage.RESPONSE_OR_OTHER,
        Set.of('ext'),
        Set.of(),
        Set.of(),
        Set.of(),
        LinksContext.TOP_LEVEL,
        Map.of(),
        null)
  }

  private static JsonApiDocument memberOrderDocument() {
    def self = new Link.StringLink("http://example.com/articles/1")
    def relationships = new LinkedHashMap<String, Relationship>()
    relationships.put(
        "author",
        Relationship.withData(
        new RelationshipData.SingleLinkage(ResourceIdentifier.of("people", "9"))))
    def article = new ResourceObject(
        "articles",
        "1",
        "temp-1",
        Attributes.ofAttributes(["title": "Ordered"]),
        Relationships.ofRelationships(relationships),
        Links.ofLinks([self: self]),
        Meta.of(["created": "2026-01-01"]),
        ["ext:flag": true])
    return new JsonApiDocument(
        new DocumentData.SingleResource(article),
        null,
        Meta.of(["copyright": "Copyright 2026"]),
        JsonApiObject.ofVersion("1.1"),
        Links.ofLinks([self: self]),
        List.of(ResourceObject.of("people", "9")),
        ["ext:trace": "t-1"])
  }

  private static JsonApiDocument directResourceDocument() {
    def resource = new ResourceObject(
        "articles",
        "1",
        null,
        Attributes.ofAttributes(["title": "Title"]),
        null,
        null,
        null,
        Map.of())
    return JsonApiDocument.withData(new DocumentData.SingleResource(resource))
  }

  private static JsonApiDocument directRelationshipDocument() {
    def relationships = new LinkedHashMap<String, Relationship>()
    relationships.put(
        "author",
        Relationship.withData(
        new RelationshipData.SingleLinkage(ResourceIdentifier.of("people", "9"))))
    def resource = new ResourceObject(
        "articles",
        "1",
        null,
        null,
        Relationships.ofRelationships(relationships),
        null,
        null,
        Map.of())
    return JsonApiDocument.withData(new DocumentData.SingleResource(resource))
  }

  private static JsonApiDocument directCompoundDocument() {
    def relationships = new LinkedHashMap<String, Relationship>()
    relationships.put(
        "author",
        Relationship.withData(
        new RelationshipData.SingleLinkage(ResourceIdentifier.of("people", "9"))))
    def primary = new ResourceObject(
        "articles",
        "1",
        null,
        null,
        Relationships.ofRelationships(relationships),
        null,
        null,
        Map.of())
    def included = new ResourceObject(
        "people",
        "9",
        null,
        Attributes.ofAttributes(["name": "Dan"]),
        null,
        null,
        null,
        Map.of())
    return new JsonApiDocument(
        new DocumentData.SingleResource(primary), null, null, null, null, List.of(included), Map.of())
  }

  private static JsonApiDocument directNullDataDocument() {
    return new JsonApiDocument(
        DocumentData.NullData.INSTANCE,
        null,
        Meta.of(["reason": "deleted"]),
        null,
        null,
        null,
        Map.of())
  }
}
