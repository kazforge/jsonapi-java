package com.kazforge.jsonapi.fixtures.contract

import com.kazforge.jsonapi.annotation.JsonApiResource
import com.kazforge.jsonapi.api.JsonApi
import com.kazforge.jsonapi.core.model.Attributes
import com.kazforge.jsonapi.core.model.DocumentData
import com.kazforge.jsonapi.core.model.JsonApiDocument
import com.kazforge.jsonapi.core.model.ResourceIdentifier
import com.kazforge.jsonapi.core.model.ResourceIdentity
import com.kazforge.jsonapi.core.model.ResourceObject
import com.kazforge.jsonapi.diagnostic.JsonApiMappingException
import com.kazforge.jsonapi.diagnostic.MappingDiagnostic
import com.kazforge.jsonapi.document.DocumentReadContext
import com.kazforge.jsonapi.fixtures.TestFixtureResources
import com.kazforge.jsonapi.fixtures.domainread.FlatArticle
import com.kazforge.jsonapi.fixtures.domainwrite.Person
import com.kazforge.jsonapi.fixtures.enveloperead.FlatStrictArticle
import com.kazforge.jsonapi.fixtures.enveloperead.FlatThrowingArticle
import com.kazforge.jsonapi.mapping.DomainData
import com.kazforge.jsonapi.mapping.ResourceTypeRegistry
import spock.lang.Specification

/**
 * Typed-envelope document-binding characterization contract. These semantics have no Level-1
 * {@code JsonApi} entry point; concrete adapter subclasses invoke the native typed-envelope reader
 * through {@link #bind} and return only the neutral envelope observation. Assertions stay at the
 * JSON:API member level: diagnostic code, resource class, document-relative location, and shared
 * message text. Binder-local messages remain adapter-owned.
 */
abstract class TypedEnvelopeCharacterizationSpec extends Specification {

  protected abstract JsonApi api()

  protected abstract BoundTypedEnvelope bind(
  JsonApiDocument document, ResourceTypeRegistry registry)

  def "rejects a mismatched unused registration eagerly with the shared diagnostic"() {
    given:
    def registry = ResourceTypeRegistry.builder().register("people", FlatArticle).build()

    when:
    bind(read(META_ONLY), registry)

    then:
    def failure = thrown(JsonApiMappingException)
    failure.diagnostic() == MappingDiagnostic.RESOURCE_TYPE_MISMATCH
    failure.resourceClass() == FlatArticle
    failure.location() == null
    failure.message ==
        "Registered JSON:API type 'people' for " +
        FlatArticle.name +
        " does not match configured resource type 'articles'"
  }

  def "reports missing resource metadata through the resolver diagnostic"() {
    given:
    def registry = ResourceTypeRegistry.builder().register("articles", Object).build()

    when:
    bind(read(META_ONLY), registry)

    then:
    def failure = thrown(JsonApiMappingException)
    failure.diagnostic() == MappingDiagnostic.MISSING_RESOURCE_ANNOTATION
    failure.resourceClass() == Object
    failure.location() == null
    failure.message == "Missing @JsonApiResource on java.lang.Object"
  }

  def "empty registries stay legal for construction"() {
    when:
    def envelope = bind(read(META_ONLY), ResourceTypeRegistry.builder().build())

    then:
    envelope.data() == null
    envelope.included() == null
  }

  def "unrelated property invalidity stays deferred until binding"() {
    given:
    def registry = types(FlatThrowingArticle)

    when:
    def envelope = bind(read(META_ONLY), registry)

    then:
    envelope.data() == null

    when:
    bind(
        new JsonApiDocument(
        new DocumentData.SingleResource(
        throwingArticle("1", "boom")),
        null,
        null,
        null,
        null,
        null,
        Map.of()),
        registry)

    then:
    def failure = thrown(JsonApiMappingException)
    failure.diagnostic() == MappingDiagnostic.MISSING_CREATOR_INPUT
    failure.resourceClass() == FlatThrowingArticle
    failure.propertyPath() == "/data"
  }

  def "absent primary data stays null"() {
    when:
    def envelope = bind(read(META_ONLY), ResourceTypeRegistry.builder().build())

    then:
    envelope.data() == null
  }

  def "explicit null primary data is NullData and never binds"() {
    when:
    def envelope = bind(read("documents/null-data.json"), ResourceTypeRegistry.builder().build())

    then:
    envelope.data() == DomainData.NullData.INSTANCE
  }

  def "single resource primary data binds the registered DTO"() {
    when:
    def envelope =
        bind(read("envelope-binding/single-resource.json"), types(FlatArticle))

    then:
    envelope.data() ==
        new DomainData.SingleResource(
        new FlatArticle(
        "1", "JSON:API paints my bikeshed!", "Content",
        ResourceIdentifier.of("people", "p1"),
        [
          ResourceIdentifier.of("comments", "c1")
        ]))
    envelope.included() == null
  }

  def "resource collection primary data binds in wire order"() {
    when:
    def envelope = bind(read("documents/resource-collection.json"), types(FlatArticle))

    then:
    envelope.data() ==
        new DomainData.ResourceCollection([
          new FlatArticle("1", "First", null, null, null),
          new FlatArticle("2", "Second", null, null, null)
        ])
  }

  def "single identifier primary data never binds"() {
    when:
    def envelope =
        bind(
        api().documents().read(
        corpus("documents/single-identifier.json"), DocumentReadContext.identifierDefaults()),
        ResourceTypeRegistry.builder().build())

    then:
    envelope.data() ==
        new DomainData.SingleIdentifier(ResourceIdentifier.of("articles", "1"))
  }

  def "identifier collection primary data never binds"() {
    when:
    def envelope =
        bind(
        api().documents().read(
        corpus("documents/identifier-collection.json"), DocumentReadContext.identifierDefaults()),
        ResourceTypeRegistry.builder().build())

    then:
    envelope.data() ==
        new DomainData.IdentifierCollection([
          ResourceIdentifier.of("articles", "1"),
          ResourceIdentifier.of("articles", "2")
        ])
  }

  def "absent included stays null and present-empty included is an empty IncludedResources"() {
    when:
    def absent = bind(read("envelope-binding/single-resource.json"), types(FlatArticle))
    def presentEmpty = bind(read("documents/empty-included.json"), types(FlatArticle))

    then:
    absent.included() == null
    presentEmpty.included().resources() == []
  }

  def "included resources preserve wire order and id/lid aliases to the same instance"() {
    given:
    def document =
        new JsonApiDocument(
        new DocumentData.SingleResource(ResourceObject.of("articles", "1")),
        null,
        null,
        null,
        null,
        [
          new ResourceObject(
          "people",
          "9",
          "tmp-9",
          Attributes.ofAttributes([name: "Dan"]),
          null,
          null,
          null,
          Map.of())
        ],
        Map.of())

    when:
    def envelope = bind(document, types(FlatArticle, Person))
    def included = envelope.included()
    def byId = included.find(ResourceIdentity.ofId("people", "9"))
    def byLid = included.find(ResourceIdentity.ofLid("people", "tmp-9"))

    then:
    included.resources() == [new Person("9", "Dan")]
    byId.isPresent()
    byLid.isPresent()
    byId.get().is(byLid.get())
  }

  def "duplicate included identities fail at the later document pointer with the shared message"() {
    given:
    def identity = ResourceIdentity.ofId("people", "9")
    def document =
        new JsonApiDocument(
        new DocumentData.SingleResource(ResourceObject.of("articles", "1")),
        null,
        null,
        null,
        null,
        [
          ResourceObject.of("people", "9"),
          ResourceObject.of("people", "9")
        ],
        Map.of())

    when:
    bind(document, types(FlatArticle, Person))

    then:
    def failure = thrown(JsonApiMappingException)
    failure.diagnostic() == MappingDiagnostic.CONFLICTING_INCLUDED_REPRESENTATION
    failure.resourceClass() == null
    failure.propertyPath() == "/included/1"
    failure.message == "Duplicate included identity " + identity
  }

  def "unregistered resource types fail at the document pointer with the shared message"() {
    when:
    bind(read("envelope-binding/unregistered-primary-single.json"), ResourceTypeRegistry.builder().build())

    then:
    def single = thrown(JsonApiMappingException)
    single.diagnostic() == MappingDiagnostic.UNREGISTERED_RESOURCE_TYPE
    single.resourceClass() == null
    single.propertyPath() == "/data"
    single.message == "No DTO target registered for JSON:API resource type 'articles'"

    when:
    bind(
        read("envelope-binding/unregistered-primary-collection.json"),
        ResourceTypeRegistry.builder().build())

    then:
    def collection = thrown(JsonApiMappingException)
    collection.diagnostic() == MappingDiagnostic.UNREGISTERED_RESOURCE_TYPE
    collection.resourceClass() == null
    collection.propertyPath() == "/data/0"
    collection.message == "No DTO target registered for JSON:API resource type 'articles'"

    when:
    bind(read("documents/compound-document.json"), types(FlatArticle))

    then:
    def included = thrown(JsonApiMappingException)
    included.diagnostic() == MappingDiagnostic.UNREGISTERED_RESOURCE_TYPE
    included.resourceClass() == null
    included.propertyPath() == "/included/0"
    included.message == "No DTO target registered for JSON:API resource type 'people'"
  }

  def "resource-relative binder failures join under the document prefix"() {
    when:
    bind(
        read("envelope-binding/binder-failure-single.json"),
        types(FlatArticle, Person, FlatStrictArticle))

    then:
    def single = thrown(JsonApiMappingException)
    single.diagnostic() == MappingDiagnostic.RELATIONSHIP_CARDINALITY_MISMATCH
    single.resourceClass() == ResourceIdentifier
    single.propertyPath() == "/data/relationships/author/data"

    when:
    bind(
        read("envelope-binding/binder-failure-collection.json"),
        types(FlatArticle, Person, FlatStrictArticle))

    then:
    def collection = thrown(JsonApiMappingException)
    collection.diagnostic() == MappingDiagnostic.RELATIONSHIP_CARDINALITY_MISMATCH
    collection.resourceClass() == ResourceIdentifier
    collection.propertyPath() == "/data/0/relationships/author/data"

    when:
    bind(
        read("envelope-binding/binder-failure-included.json"),
        types(FlatArticle, Person, FlatStrictArticle))

    then:
    def included = thrown(JsonApiMappingException)
    included.diagnostic() == MappingDiagnostic.UNSUPPORTED_ATTRIBUTE_VALUE
    included.resourceClass() == FlatStrictArticle
    included.propertyPath() == "/included/1/attributes/title"
  }

  def "locationless binder failures report only the document prefix"() {
    when:
    bind(read("envelope-binding/root-level-failure.json"), types(FlatThrowingArticle))

    then:
    def primary = thrown(JsonApiMappingException)
    primary.diagnostic() == MappingDiagnostic.MISSING_CREATOR_INPUT
    primary.resourceClass() == FlatThrowingArticle
    primary.propertyPath() == "/data"

    when:
    bind(
        new JsonApiDocument(
        new DocumentData.SingleResource(ResourceObject.of("throwing-articles", "1")),
        null,
        null,
        null,
        null,
        [throwingArticle("2", "boom")],
        Map.of()),
        types(FlatThrowingArticle))

    then:
    def included = thrown(JsonApiMappingException)
    included.diagnostic() == MappingDiagnostic.MISSING_CREATOR_INPUT
    included.resourceClass() == FlatThrowingArticle
    included.propertyPath() == "/included/0"
  }

  def "binds primary data before included resources"() {
    given:
    def document =
        new JsonApiDocument(
        new DocumentData.SingleResource(ResourceObject.of("unknown-primary", "1")),
        null,
        null,
        null,
        null,
        [
          ResourceObject.of("unknown-included", "2")
        ],
        Map.of())

    when:
    bind(document, ResourceTypeRegistry.builder().build())

    then:
    def failure = thrown(JsonApiMappingException)
    failure.diagnostic() == MappingDiagnostic.UNREGISTERED_RESOURCE_TYPE
    failure.propertyPath() == "/data"
    failure.message == "No DTO target registered for JSON:API resource type 'unknown-primary'"
  }

  def "binds an included element before checking its identity"() {
    given:
    def document =
        new JsonApiDocument(
        new DocumentData.SingleResource(ResourceObject.of("articles", "1")),
        null,
        null,
        null,
        null,
        [
          strictArticle("1", 1),
          strictArticle("1", "boom")
        ],
        Map.of())

    when:
    bind(document, types(FlatArticle, FlatStrictArticle))

    then:
    def failure = thrown(JsonApiMappingException)
    failure.diagnostic() == MappingDiagnostic.UNSUPPORTED_ATTRIBUTE_VALUE
    failure.resourceClass() == FlatStrictArticle
    failure.propertyPath() == "/included/1/attributes/title"
  }

  def "metaAs returns null when meta is absent"() {
    when:
    def envelope = bind(read("envelope-binding/single-resource.json"), types(FlatArticle))

    then:
    envelope.metaAs(Integer) == null
  }

  def "incompatible metaAs fails at /meta with the shared diagnostic"() {
    when:
    bind(read(META_ONLY), ResourceTypeRegistry.builder().build()).metaAs(Integer)

    then:
    def failure = thrown(JsonApiMappingException)
    failure.diagnostic() == MappingDiagnostic.UNSUPPORTED_ATTRIBUTE_VALUE
    failure.resourceClass() == null
    failure.propertyPath() == "/meta"
    failure.message == "Failed to convert meta members to " + Integer
  }

  def "errors additional members and included resources are unmodifiable"() {
    when:
    bind(read("documents/errors-document.json"), ResourceTypeRegistry.builder().build())
        .errors()
        .clear()

    then:
    thrown(UnsupportedOperationException)

    when:
    bind(read("envelope-binding/at-member-document.json"), types(FlatArticle))
        .additionalMembers()
        .put("@other", "value")

    then:
    thrown(UnsupportedOperationException)

    when:
    bind(read("documents/compound-document.json"), types(FlatArticle, Person))
        .included()
        .resources()
        .clear()

    then:
    thrown(UnsupportedOperationException)
  }

  private JsonApiDocument read(String corpusPath) {
    api().documents().read(corpus(corpusPath), DocumentReadContext.resourceDefaults())
  }

  private static String corpus(String relativePath) {
    TestFixtureResources.readCorpusUtf8(relativePath)
  }

  private static ResourceTypeRegistry types(Class<?>... targets) {
    def builder = ResourceTypeRegistry.builder()
    for (Class<?> target : targets) {
      builder.register(target.getAnnotation(JsonApiResource).type(), target)
    }
    builder.build()
  }

  private static ResourceObject throwingArticle(String id, String title) {
    new ResourceObject(
        "throwing-articles",
        id,
        null,
        Attributes.ofAttributes([title: title]),
        null,
        null,
        null,
        Map.of())
  }

  private static ResourceObject strictArticle(String id, Object title) {
    new ResourceObject(
        "strict-articles",
        id,
        null,
        Attributes.ofAttributes([title: title]),
        null,
        null,
        null,
        Map.of())
  }

  private static final String META_ONLY = "documents/meta-only.json"
}
