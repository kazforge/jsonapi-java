package io.github.kazemek.jsonapi.jackson2

import java.security.MessageDigest

import com.fasterxml.jackson.databind.json.JsonMapper

import com.networknt.schema.InputFormat
import com.networknt.schema.Schema
import com.networknt.schema.SchemaRegistry
import com.networknt.schema.dialect.Dialects

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
import io.github.kazemek.jsonapi.core.model.ResourceObject
import io.github.kazemek.jsonapi.core.validation.DocumentUsage
import io.github.kazemek.jsonapi.core.validation.LinksContext
import io.github.kazemek.jsonapi.core.validation.ValidationContext
import io.github.kazemek.jsonapi.fixtures.TestFixtureResources

import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

/**
 * Adapter-local cross-check of direct writer output and corpus documents against the pinned
 * JSON:API 1.1 draft schemas owned by the Jackson API test fixtures. Pin/integrity of those
 * resources lives in the test-fixture resources; this spec owns the adapter-specific schema
 * checks.
 *
 * The draft schemas are unreleased and not an official conformance oracle: a schema result never
 * changes a conformance status in docs/conformance.md.
 */
class JsonApiDraftSchemaSpec extends Specification {

  private static final String DRAFT_URI = "https://jsonapi.org/schemas/spec/v1.1/draft"
  private static final String META_SCHEMA_URI = "https://json-schema.org/draft/2020-12/schema"
  private static final String PINNED_COMMIT = "4ee1c644fcc273044ecec39a6b8c0f0485abdc0e"

  private static final List<String> SCHEMA_FILES = [
    "schema.json",
    "schema_create_resource.json",
    "schema_update_resource.json",
    "schema_update_relationship.json",
  ]

  private static final String META_SCHEMA_ORIGIN = "https://json-schema.org/"

  private static final Map<String, String> SCHEMA_FILE_BY_KIND = [
    response: "schema.json",
    create: "schema_create_resource.json",
    update: "schema_update_resource.json",
    updateRelationship: "schema_update_relationship.json",
  ]

  @Shared
  JsonMapper mapper = JsonMapper.builder().build()

  @Shared
  SchemaRegistry registry = SchemaRegistry.withDialect(Dialects.getDraft202012(), JsonApiDraftSchemaSpec.&configureRegistry)

  @Shared
  Map<String, Schema> schemas = SCHEMA_FILE_BY_KIND.collectEntries { kind, file ->
    [(kind): loadSchema(file)]
  }

  def "vendored draft schemas match the recorded sha256 pin"() {
    given:
    def checksums = sha256sums()

    expect:
    checksums.keySet() == SCHEMA_FILES.toSet()
    SCHEMA_FILES.every { file -> digest(TestFixtureResources.readSchemaBytes(file)) == checksums[file] }
  }

  def "vendored schemas declare the Draft 2020-12 dialect and draft URI"() {
    expect:
    SCHEMA_FILES.every { file ->
      mapper.readTree(TestFixtureResources.readSchemaUtf8(file)).get('$schema').asText() == META_SCHEMA_URI
    }
    mapper.readTree(TestFixtureResources.readSchemaUtf8("schema.json")).get('$id').asText() == DRAFT_URI
  }

  def "schema pin metadata is documented"() {
    given:
    def readme = TestFixtureResources.readSchemaUtf8("README.md")

    expect:
    readme.contains(PINNED_COMMIT)
  }

  def "writable corpus document #corpusPath validates against #schemaKind draft schema"() {
    given:
    def json = TestFixtureResources.readCorpusUtf8(corpusPath)
    def errors = schemas[schemaKind].validate(json, InputFormat.JSON)

    expect:
    errors.isEmpty()

    where:
    corpusPath                                    | schemaKind
    "documents/single-resource.json"              | "response"
    "documents/resource-collection.json"          | "response"
    "documents/single-identifier.json"            | "response"
    "documents/identifier-collection.json"        | "response"
    "documents/null-data.json"                    | "response"
    "documents/meta-only.json"                    | "response"
    "documents/empty-identifier-collection.json"  | "response"
    "documents/empty-wrappers.json"               | "response"
    "documents/empty-errors.json"                 | "response"
    "documents/empty-included.json"               | "response"
    "documents/open-values.json"                  | "response"
    "documents/relationship-null-linkage.json"    | "response"
    "documents/relationship-empty-to-many.json"   | "response"
    "documents/relationship-link-only.json"       | "response"
    "documents/relationship-meta-only.json"       | "response"
    "documents/errors-document.json"              | "response"
    "documents/jsonapi-object.json"               | "response"
    "documents/compound-document.json"            | "response"
    "documents/compound-nested-intermediate.json" | "response"
    "documents/compound-shared-identity.json"     | "response"
    "documents/local-identifier.json"             | "create"
  }

  @Unroll
  def "writer output retains the documented draft-schema gap for #description"() {
    given:
    def json = JsonApiJackson2.writer(mapper, context).writeValueAsString(document)
    def errors = schemas[schemaKind].validate(json, InputFormat.JSON)
    def observed = errors.collect { [keyword: it.keyword, path: it.instanceLocation.toString()] }

    expect:
    expected.every { expectedError ->
      observed.any { actual ->
        actual.keyword == expectedError.keyword && actual.path == expectedError.path
      }
    }

    where:
    description | document | context | schemaKind | expected
    "canonical array-form hreflang" | stringAndObjectLinksDocument() | ValidationContext.defaults() | "response" | [
      [keyword: "type", path: "/links/related/hreflang"]
    ]
    "top-level extension member" | extensionAndAtMembersDocument() | extContext() | "response" | [
      [keyword: "unevaluatedProperties", path: ""]
    ]
    "response lid and extension members" | memberOrderDocument() | extContext() | "response" | [
      [keyword: "not", path: "/data"],
      [keyword: "unevaluatedProperties", path: ""]
    ]
  }

  def "invalid control #file fails the #kind schema at #path with #keyword"() {
    given:
    def json = TestFixtureResources.readSchemaUtf8("invalid-controls/" + file)
    def errors = schemas[kind].validate(json, InputFormat.JSON)

    expect:
    errors.any { it.keyword == keyword && it.instanceLocation.toString() == path }

    where:
    file                                       | kind               | keyword  | path
    "response-missing-primary.json"            | "response"         | "required" | ""
    "create-invalid-lid-type.json"             | "create"           | "type"     | "/data/lid"
    "update-invalid-missing-id.json"           | "update"           | "required" | "/data"
    "update-relationship-invalid-linkage.json" | "updateRelationship" | "oneOf"    | "/data"
  }

  private static void configureRegistry(SchemaRegistry.Builder builder) {
    builder
        .schemaLoader { loader ->
          loader.allow { iri ->
            String value = iri.toString()
            value == DRAFT_URI || value.startsWith(META_SCHEMA_ORIGIN)
          }
        }
        .schemas([(DRAFT_URI): TestFixtureResources.readSchemaUtf8("schema.json")])
  }

  private Schema loadSchema(String file) {
    return registry.getSchema(TestFixtureResources.readSchemaUtf8(file))
  }

  private static Map<String, String> sha256sums() {
    Map<String, String> checksums = [:]
    TestFixtureResources.readSchemaUtf8("sha256.sum").readLines()
        .findAll { line -> !line.trim().isEmpty() }
        .each { line ->
          def parts = line.tokenize()
          checksums.put(parts[1], parts[0])
        }
    checksums
  }

  private static String digest(byte[] bytes) {
    MessageDigest.getInstance("SHA-256").digest(bytes).collect { String.format("%02x", it) }.join()
  }

  private static ValidationContext extContext() {
    return new ValidationContext(
        DocumentUsage.RESPONSE_OR_OTHER,
        Set.of("ext"),
        Set.of(),
        Set.of(),
        Set.of(),
        LinksContext.TOP_LEVEL,
        Map.of(),
        null)
  }

  private static JsonApiDocument stringAndObjectLinksDocument() {
    String selfHref = "https://example.com/articles/1"
    def resourceLinks = new LinkedHashMap<String, Link>()
    resourceLinks.put("self", new Link.StringLink(selfHref))
    def article = new ResourceObject(
        "articles", "1", null, null, null, Links.ofLinks(resourceLinks), null, Map.of())

    def related = new Link.ObjectLink(
        "https://example.com/articles/1/related",
        "related",
        null,
        "Related",
        "application/vnd.api+json",
        ["en"],
        Meta.of(["count": 1]),
        Map.of())
    def topLinks = new LinkedHashMap<String, Link>()
    topLinks.put("self", new Link.StringLink(selfHref))
    topLinks.put("related", related)
    topLinks.put("next", null)

    return new JsonApiDocument(
        new DocumentData.ResourceCollection(List.of(article)),
        null,
        null,
        null,
        Links.ofLinks(topLinks),
        null,
        Map.of())
  }

  private static JsonApiDocument extensionAndAtMembersDocument() {
    def article = new ResourceObject(
        "articles",
        "1",
        null,
        Attributes.ofAttributes(["title": "Hello"]),
        null,
        null,
        null,
        ["@copyright": "Copyright 2026", "ext:version": 1])
    return new JsonApiDocument(
        new DocumentData.SingleResource(article),
        null,
        null,
        null,
        null,
        null,
        ["ext:request-id": "abc-123"])
  }

  private static JsonApiDocument memberOrderDocument() {
    def self = new Link.StringLink("https://example.com/articles/1")
    def relationships = new LinkedHashMap<String, Relationship>()
    relationships.put(
        "author",
        Relationship.withData(
        new RelationshipData.SingleLinkage(ResourceIdentifier.of("people", "9"))))
    def resourceLinks = new LinkedHashMap<String, Link>()
    resourceLinks.put("self", self)
    def article = new ResourceObject(
        "articles",
        "1",
        "temp-1",
        Attributes.ofAttributes(["title": "Ordered"]),
        Relationships.ofRelationships(relationships),
        Links.ofLinks(resourceLinks),
        Meta.of(["created": "2026-01-01"]),
        ["ext:flag": true])
    def documentLinks = new LinkedHashMap<String, Link>()
    documentLinks.put("self", self)

    return new JsonApiDocument(
        new DocumentData.SingleResource(article),
        null,
        Meta.of(["copyright": "Copyright 2026"]),
        JsonApiObject.ofVersion("1.1"),
        Links.ofLinks(documentLinks),
        List.of(ResourceObject.of("people", "9")),
        ["ext:trace": "t-1"])
  }
}
