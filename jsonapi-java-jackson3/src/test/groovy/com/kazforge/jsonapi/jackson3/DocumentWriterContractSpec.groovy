package com.kazforge.jsonapi.jackson3

import tools.jackson.databind.json.JsonMapper

import com.kazforge.jsonapi.core.model.Attributes
import com.kazforge.jsonapi.core.model.DocumentData
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
import com.kazforge.jsonapi.core.validation.DocumentUsage
import com.kazforge.jsonapi.core.validation.JsonApiValidationException
import com.kazforge.jsonapi.core.validation.LinksContext
import com.kazforge.jsonapi.core.validation.ValidationContext
import com.kazforge.jsonapi.core.validation.ValidationRuleCode
import com.kazforge.jsonapi.jackson.document.DocumentEnvelope
import com.kazforge.jsonapi.jackson.document.DocumentReadContext
import com.kazforge.jsonapi.jackson.document.PrimaryDataKind
import com.kazforge.jsonapi.jackson.mapping.MappedDocument
import com.kazforge.jsonapi.jackson.representation.IncludePath
import com.kazforge.jsonapi.jackson.representation.IncludePolicy
import com.kazforge.jsonapi.jackson.representation.RepresentationPolicy
import com.kazforge.jsonapi.jackson.representation.RepresentationSelection
import com.kazforge.jsonapi.fixtures.TestFixtureResources
import com.kazforge.jsonapi.fixtures.domainwrite.Article
import com.kazforge.jsonapi.fixtures.domainwrite.Person

import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

class DocumentWriterContractSpec extends Specification {

  @Shared
  JsonMapper mapper = JsonMapper.builder().build()

  @Unroll
  def "writes independently constructed #description"() {
    given:
    def writer = JsonApiJackson3.writer(mapper, ValidationContext.defaults())
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
    def resourceMapper = JsonApiJackson3.resourceMapper(mapper)
    def mapped = resourceMapper.toMappedDocument(
        new Article("1", "Title", "Body", List.of(), new Person("9", "Dan")),
        null,
        RepresentationSelection.builder()
        .include(IncludePath.of("author"))
        .fields("articles", "title")
        .build(),
        RepresentationPolicy.defaults().withIncludePolicy(IncludePolicy.allowAll()))
    def writer = JsonApiJackson3.writer(mapper)
    def expected = mapper.readTree(
        '{"data":{"type":"articles","id":"1","attributes":{"title":"Title"}},' +
        '"included":[{"type":"people","id":"9","attributes":{"name":"Dan"}}]}')

    when:
    def json = writer.writeValueAsString(mapped)

    then:
    mapped.sparseFieldsetLinkageExemptions() == Set.of(ResourceIdentity.ofId("people", "9"))
    mapper.readTree(json) == expected

    when:
    writer.writeValueAsString(mapped.document())

    then:
    def exception = thrown(JsonApiValidationException)
    exception.ruleCode() == ValidationRuleCode.FULL_LINKAGE_VIOLATION
  }

  def "mapped writing preserves unrelated caller validation settings"() {
    given:
    def resourceMapper = JsonApiJackson3.resourceMapper(mapper)
    def mapped = resourceMapper.toMappedDocument(
        new Article("1", "Title", "Body", List.of(), new Person("9", "Dan")),
        new DocumentEnvelope(null, Meta.of(["myext:version": "1.0"]), null),
        RepresentationSelection.builder()
        .include(IncludePath.of("author"))
        .fields("articles", "title")
        .build(),
        RepresentationPolicy.defaults().withIncludePolicy(IncludePolicy.allowAll()))
    def base = new ValidationContext(
        DocumentUsage.RESPONSE_OR_OTHER,
        Set.of("myext"),
        Set.of(),
        Set.of(),
        Set.of(),
        LinksContext.TOP_LEVEL,
        Map.of(),
        null)

    when:
    JsonApiJackson3.writer(mapper, base).writeValueAsString(mapped)

    then:
    noExceptionThrown()

    when:
    JsonApiJackson3.writer(mapper).writeValueAsString(mapped)

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
    JsonApiJackson3.writer(mapper, base).writeValueAsString(mapped)

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
    def writer = JsonApiJackson3.writer(mapper)

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

  def "roundtrips fixture #path through reader and writer"() {
    given:
    def json = readCorpusText(path)
    def readContext = DocumentReadContext.of(
        context, primaryDataKind ?: PrimaryDataKind.RESOURCE)
    def reader = JsonApiJackson3.reader(mapper, readContext)
    def writer = JsonApiJackson3.writer(mapper, context)

    when:
    def document = reader.readValue(json)
    def actual = writer.writeValueAsString(document)

    then:
    mapper.readTree(actual) == mapper.readTree(json)

    where:
    path                                               | context                     | primaryDataKind
    'documents/single-resource.json'                   | ValidationContext.defaults() | PrimaryDataKind.RESOURCE
    'documents/resource-collection.json'               | ValidationContext.defaults() | PrimaryDataKind.RESOURCE
    'documents/ambiguous-empty-array-primary-data.json' | ValidationContext.defaults() | PrimaryDataKind.RESOURCE
    'documents/single-identifier.json'                 | ValidationContext.defaults() | PrimaryDataKind.RESOURCE_IDENTIFIER
    'documents/identifier-collection.json'             | ValidationContext.defaults() | PrimaryDataKind.RESOURCE_IDENTIFIER
    'documents/null-data.json'                         | ValidationContext.defaults() | null
    'documents/meta-only.json'                         | ValidationContext.defaults() | null
    'documents/empty-identifier-collection.json'      | ValidationContext.defaults() | PrimaryDataKind.RESOURCE_IDENTIFIER
    'documents/empty-wrappers.json'                    | ValidationContext.defaults() | PrimaryDataKind.RESOURCE
    'documents/empty-errors.json'                      | ValidationContext.defaults() | null
    'documents/empty-included.json'                    | ValidationContext.defaults() | PrimaryDataKind.RESOURCE
    'documents/open-values.json'                       | ValidationContext.defaults() | PrimaryDataKind.RESOURCE
    'documents/relationship-null-linkage.json'         | ValidationContext.defaults() | PrimaryDataKind.RESOURCE
    'documents/relationship-empty-to-many.json'        | ValidationContext.defaults() | PrimaryDataKind.RESOURCE
    'documents/relationship-link-only.json'            | ValidationContext.defaults() | PrimaryDataKind.RESOURCE
    'documents/relationship-meta-only.json'            | ValidationContext.defaults() | PrimaryDataKind.RESOURCE
    'documents/string-and-object-links.json'           | ValidationContext.defaults() | PrimaryDataKind.RESOURCE
    'documents/errors-document.json'                   | ValidationContext.defaults() | null
    'documents/jsonapi-object.json'                    | extContext()                | PrimaryDataKind.RESOURCE
    'documents/compound-document.json'                 | ValidationContext.defaults() | PrimaryDataKind.RESOURCE
    'documents/compound-nested-intermediate.json'      | ValidationContext.defaults() | PrimaryDataKind.RESOURCE
    'documents/compound-shared-identity.json'          | ValidationContext.defaults() | PrimaryDataKind.RESOURCE
    'documents/local-identifier.json'                  | createContext()             | PrimaryDataKind.RESOURCE
    'documents/extension-and-at-members.json'          | extContext()                | PrimaryDataKind.RESOURCE
    'documents/member-order.json'                      | extContext()                | PrimaryDataKind.RESOURCE
  }

  def "emits exact member order"() {
    given:
    def context = extContext()
    def reader = JsonApiJackson3.reader(
        mapper, DocumentReadContext.of(context, PrimaryDataKind.RESOURCE))
    def document = reader.readValue(readCorpusText('documents/member-order.json'))
    def writer = JsonApiJackson3.writer(mapper, context)
    def expected = readCorpusText('documents/member-order.compact.json').trim()

    expect:
    writer.writeValueAsString(document) == expected
  }

  def "emits array-form hreflang"() {
    given:
    def context = ValidationContext.defaults()
    def reader = JsonApiJackson3.reader(
        mapper, DocumentReadContext.of(context, PrimaryDataKind.RESOURCE))
    def document = reader.readValue(readCorpusText('documents/string-and-object-links.json'))
    def writer = JsonApiJackson3.writer(mapper, context)
    def json = writer.writeValueAsString(document)

    expect:
    json.contains('"hreflang":["en"]')
    !json.contains('"hreflang":"en"')
  }

  def "reader and writer round-trip recursive describedby links"() {
    given:
    def baseSchema = new Link.StringLink('https://example.test/schemas/base')
    def describedby = new Link.ObjectLink(
        'https://example.test/schemas/article',
        null,
        baseSchema,
        null,
        'application/schema+json',
        null,
        Meta.of([revision: 1]),
        Map.of())
    def self = new Link.ObjectLink(
        'https://example.test/articles/1', null, describedby, null, null, null, null, Map.of())
    def document = new JsonApiDocument(
        null, null, Meta.of([count: 1]), null, Links.ofLinks([self: self]), null, Map.of())
    def reader = JsonApiJackson3.reader(mapper, DocumentReadContext.resourceDefaults())
    def writer = JsonApiJackson3.writer(mapper, ValidationContext.defaults())

    when:
    def json = writer.writeValueAsString(document)
    def tree = mapper.readTree(json)
    def roundTrip = reader.readValue(json)

    then:
    tree.get('links').get('self').get('href').asString() == 'https://example.test/articles/1'
    tree.get('links').get('self').get('describedby').get('href').asString() ==
        'https://example.test/schemas/article'
    tree.get('links').get('self').get('describedby').get('describedby').asString() ==
        'https://example.test/schemas/base'
    tree.get('links').get('self').get('describedby').get('type').asString() ==
        'application/schema+json'
    tree.get('links').get('self').get('describedby').get('meta').get('revision').asInt() == 1
    roundTrip == document
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

  private static ValidationContext createContext() {
    return new ValidationContext(
        DocumentUsage.CREATE_REQUEST,
        Set.of(),
        Set.of(),
        Set.of(),
        Set.of(),
        LinksContext.TOP_LEVEL,
        Map.of(),
        null)
  }

  private static String readCorpusText(String relativePath) {
    return TestFixtureResources.readCorpusUtf8(relativePath)
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
