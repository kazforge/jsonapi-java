package com.kazforge.jsonapi.mapping.internal

import com.kazforge.jsonapi.core.model.DocumentData
import com.kazforge.jsonapi.core.model.ErrorObject
import com.kazforge.jsonapi.core.model.JsonApiDocument
import com.kazforge.jsonapi.core.model.Meta
import com.kazforge.jsonapi.core.model.ResourceIdentifier
import com.kazforge.jsonapi.core.model.ResourceIdentity
import com.kazforge.jsonapi.core.model.ResourceObject
import com.kazforge.jsonapi.diagnostic.JsonApiMappingException
import com.kazforge.jsonapi.diagnostic.MappingDiagnostic
import com.kazforge.jsonapi.diagnostic.MappingLocation
import com.kazforge.jsonapi.mapping.DomainData
import com.kazforge.jsonapi.mapping.ResourceTypeRegistry
import java.lang.reflect.Type
import spock.lang.Specification

/**
 * Neutral typed-envelope orchestration semantics proven against a fake backend: registry coherence,
 * primary-data dispatch, included identity handling, binder-failure composition, evaluation order,
 * and {@code /meta} conversion-failure translation.
 */
class MappingTypedEnvelopeBinderSpec extends Specification {

  def "rejects a mismatched unused registration eagerly"() {
    given:
    def backend = backend(String, "articles")

    when:
    new TypedEnvelopeBinder<>(
        ResourceTypeRegistry.builder().register("people", String).build(), backend)

    then:
    def failure = thrown(JsonApiMappingException)
    failure.diagnostic() == MappingDiagnostic.RESOURCE_TYPE_MISMATCH
    failure.resourceClass() == String
    failure.location() == null
    failure.message ==
        "Registered JSON:API type 'people' for java.lang.String does not match configured resource type 'articles'"
    backend.boundResources == []
  }

  def "missing configured metadata reports the backend resolver diagnostic"() {
    given:
    def backend = new FakeBackend()

    when:
    new TypedEnvelopeBinder<>(
        ResourceTypeRegistry.builder().register("articles", String).build(), backend)

    then:
    def failure = thrown(JsonApiMappingException)
    failure.diagnostic() == MappingDiagnostic.MISSING_RESOURCE_ANNOTATION
    failure.resourceClass() == String
    failure.location() == null
    failure.message == "Missing @JsonApiResource on java.lang.String"
  }

  def "accepts native method-reference backends"() {
    given:
    def nativeBackend =
        TypedEnvelopeBinder.backend(
        { Type registered -> (Class<?>) registered },
        { Class<?> target -> target },
        { Class<?> target -> "articles" },
        { resource, target -> resource.type() + ":" + resource.id() })
    def binder =
        new TypedEnvelopeBinder<>(
        ResourceTypeRegistry.builder().register("articles", String).build(), nativeBackend)

    expect:
    binder.bind(singleResource("articles", "1")).data() ==
        new DomainData.SingleResource("articles:1")
  }

  def "empty registries stay legal and unused bind failures stay deferred"() {
    given:
    def empty = new FakeBackend()
    def deferred = backend(String, "articles")
    deferred.bindFailure =
        new JsonApiMappingException(
        MappingDiagnostic.MISSING_CREATOR_INPUT, String, null, "creator")

    when:
    def emptyBinder = new TypedEnvelopeBinder<>(ResourceTypeRegistry.builder().build(), empty)
    def deferredBinder =
        new TypedEnvelopeBinder<>(
        ResourceTypeRegistry.builder().register("articles", String).build(), deferred)

    then:
    emptyBinder.bind(metaOnly()).data() == null
    deferredBinder.bind(metaOnly()).data() == null
    deferred.boundResources == []

    when:
    deferredBinder.bind(singleResource("articles", "1"))

    then:
    def failure = thrown(JsonApiMappingException)
    failure.diagnostic() == MappingDiagnostic.MISSING_CREATOR_INPUT
    failure.resourceClass() == String
    failure.propertyPath() == "/data"
  }

  def "dispatches primary data including identifier pass-through"() {
    given:
    def backend = backend(String, "articles")
    def binder =
        new TypedEnvelopeBinder<>(
        ResourceTypeRegistry.builder().register("articles", String).build(), backend)

    expect:
    binder.bind(metaOnly()).data() == null
    binder.bind(JsonApiDocument.withData(DocumentData.NullData.INSTANCE)).data() ==
        DomainData.NullData.INSTANCE
    binder.bind(singleResource("articles", "1")).data() ==
        new DomainData.SingleResource("articles:1")
    binder.bind(
        JsonApiDocument.withData(
        new DocumentData.ResourceCollection([
          ResourceObject.of("articles", "1"),
          ResourceObject.of("articles", "2")
        ]))).data() ==
        new DomainData.ResourceCollection(["articles:1", "articles:2"])
    binder.bind(
        JsonApiDocument.withData(
        new DocumentData.SingleIdentifier(ResourceIdentifier.of("articles", "1")))).data() ==
        new DomainData.SingleIdentifier(ResourceIdentifier.of("articles", "1"))
    binder.bind(
        JsonApiDocument.withData(
        new DocumentData.IdentifierCollection([
          ResourceIdentifier.of("articles", "1")
        ]))).data() ==
        new DomainData.IdentifierCollection([
          ResourceIdentifier.of("articles", "1")
        ])
    backend.boundResources == [
      "articles:1",
      "articles:1",
      "articles:2"
    ]
  }

  def "included resources preserve wire order and id/lid aliases to the same instance"() {
    given:
    def backend = backend(String, "articles", Integer, "people")
    def binder =
        new TypedEnvelopeBinder<>(
        ResourceTypeRegistry.builder()
        .register("articles", String)
        .register("people", Integer)
        .build(),
        backend)

    when:
    def included =
        binder.bind(
        new JsonApiDocument(
        new DocumentData.SingleResource(ResourceObject.of("articles", "1")),
        null,
        null,
        null,
        null,
        [
          new ResourceObject("people", "9", "tmp-9", null, null, null, null, Map.of())
        ],
        Map.of())).included()
    def byId = included.find(ResourceIdentity.ofId("people", "9"))
    def byLid = included.find(ResourceIdentity.ofLid("people", "tmp-9"))

    then:
    included.resources() == ["people:9"]
    byId.get().is(byLid.get())
    binder.bind(singleResource("articles", "1")).included() == null
    binder.bind(
        new JsonApiDocument(
        new DocumentData.SingleResource(ResourceObject.of("articles", "1")),
        null,
        null,
        null,
        null,
        [],
        Map.of())).included().resources() == []
  }

  def "duplicate included identities fail after both elements bind"() {
    given:
    def backend = backend(String, "articles", Integer, "people")
    def binder =
        new TypedEnvelopeBinder<>(
        ResourceTypeRegistry.builder()
        .register("articles", String)
        .register("people", Integer)
        .build(),
        backend)

    when:
    binder.bind(
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
        Map.of()))

    then:
    def failure = thrown(JsonApiMappingException)
    failure.diagnostic() == MappingDiagnostic.CONFLICTING_INCLUDED_REPRESENTATION
    failure.resourceClass() == null
    failure.propertyPath() == "/included/1"
    failure.message == "Duplicate included identity " + ResourceIdentity.ofId("people", "9")
    backend.boundResources == [
      "articles:1",
      "people:9",
      "people:9"
    ]
  }

  def "unregistered types fail at the document pointer"() {
    given:
    def empty =
        new TypedEnvelopeBinder<>(ResourceTypeRegistry.builder().build(), new FakeBackend())
    def articlesOnly =
        new TypedEnvelopeBinder<>(
        ResourceTypeRegistry.builder().register("articles", String).build(),
        backend(String, "articles"))

    when:
    empty.bind(singleResource("articles", "1"))

    then:
    def primary = thrown(JsonApiMappingException)
    primary.diagnostic() == MappingDiagnostic.UNREGISTERED_RESOURCE_TYPE
    primary.resourceClass() == null
    primary.propertyPath() == "/data"
    primary.message == "No DTO target registered for JSON:API resource type 'articles'"

    when:
    empty.bind(
        JsonApiDocument.withData(
        new DocumentData.ResourceCollection([
          ResourceObject.of("articles", "1")
        ])))

    then:
    def collection = thrown(JsonApiMappingException)
    collection.propertyPath() == "/data/0"

    when:
    articlesOnly.bind(
        new JsonApiDocument(
        new DocumentData.SingleResource(ResourceObject.of("articles", "1")),
        null,
        null,
        null,
        null,
        [
          ResourceObject.of("people", "9")
        ],
        Map.of()))

    then:
    def included = thrown(JsonApiMappingException)
    included.diagnostic() == MappingDiagnostic.UNREGISTERED_RESOURCE_TYPE
    included.propertyPath() == "/included/0"
    included.message == "No DTO target registered for JSON:API resource type 'people'"
  }

  def "composes located and locationless binder failures under the document prefix"() {
    given:
    def located = backend(String, "articles")
    located.bindFailure =
        new JsonApiMappingException(
        MappingDiagnostic.UNSUPPORTED_ATTRIBUTE_VALUE,
        String,
        MappingLocation.of("attributes", "title"),
        "bad title")
    def locationless = backend(String, "articles", Integer, "people")
    locationless.bindFailure =
        new JsonApiMappingException(
        MappingDiagnostic.MISSING_CREATOR_INPUT, Integer, null, "creator")
    locationless.failOnlyForId = "2"

    when:
    new TypedEnvelopeBinder<>(
        ResourceTypeRegistry.builder().register("articles", String).build(), located)
        .bind(singleResource("articles", "1"))

    then:
    def joined = thrown(JsonApiMappingException)
    joined.diagnostic() == MappingDiagnostic.UNSUPPORTED_ATTRIBUTE_VALUE
    joined.resourceClass() == String
    joined.propertyPath() == "/data/attributes/title"
    joined.message == "bad title"

    when:
    new TypedEnvelopeBinder<>(
        ResourceTypeRegistry.builder()
        .register("articles", String)
        .register("people", Integer)
        .build(),
        locationless)
        .bind(
        new JsonApiDocument(
        new DocumentData.SingleResource(ResourceObject.of("articles", "1")),
        null,
        null,
        null,
        null,
        [
          ResourceObject.of("people", "2")
        ],
        Map.of()))

    then:
    def prefix = thrown(JsonApiMappingException)
    prefix.diagnostic() == MappingDiagnostic.MISSING_CREATOR_INPUT
    prefix.resourceClass() == Integer
    prefix.propertyPath() == "/included/0"
  }

  def "binds primary data before included and binds an element before checking identity"() {
    given:
    def primaryFirst = backend(String, "articles", Integer, "people")
    primaryFirst.bindFailure =
        new JsonApiMappingException(
        MappingDiagnostic.MISSING_CREATOR_INPUT, String, null, "primary")
    primaryFirst.failOnlyForId = "1"
    def bindBeforeIdentity = backend(String, "articles", Integer, "people")
    bindBeforeIdentity.bindFailure =
        new JsonApiMappingException(
        MappingDiagnostic.UNSUPPORTED_ATTRIBUTE_VALUE,
        Integer,
        MappingLocation.of("attributes", "title"),
        "boom")
    bindBeforeIdentity.failOnlyForId = "dup"

    when:
    new TypedEnvelopeBinder<>(
        ResourceTypeRegistry.builder()
        .register("articles", String)
        .register("people", Integer)
        .build(),
        primaryFirst)
        .bind(
        new JsonApiDocument(
        new DocumentData.SingleResource(ResourceObject.of("articles", "1")),
        null,
        null,
        null,
        null,
        [
          ResourceObject.of("people", "9")
        ],
        Map.of()))

    then:
    def primary = thrown(JsonApiMappingException)
    primary.propertyPath() == "/data"
    primaryFirst.boundResources == ["articles:1"]

    when:
    new TypedEnvelopeBinder<>(
        ResourceTypeRegistry.builder()
        .register("articles", String)
        .register("people", Integer)
        .build(),
        bindBeforeIdentity)
        .bind(
        new JsonApiDocument(
        new DocumentData.SingleResource(ResourceObject.of("articles", "keep")),
        null,
        null,
        null,
        null,
        [
          ResourceObject.of("people", "dup"),
          ResourceObject.of("people", "dup")
        ],
        Map.of()))

    then:
    def binderFailure = thrown(JsonApiMappingException)
    binderFailure.diagnostic() == MappingDiagnostic.UNSUPPORTED_ATTRIBUTE_VALUE
    binderFailure.propertyPath() == "/included/0/attributes/title"
    bindBeforeIdentity.boundResources == ["articles:keep", "people:dup"]
  }

  def "defensively copies errors and additional members"() {
    given:
    def errors = [
      new ErrorObject(null, null, "400", null, null, null, null, null, Map.of())
    ]
    def members = new LinkedHashMap<String, Object>(["@request-id": "req-1"])
    def binder = new TypedEnvelopeBinder<>(ResourceTypeRegistry.builder().build(), new FakeBackend())

    when:
    def bound = binder.bind(new JsonApiDocument(null, errors, null, null, null, null, members))
    errors.clear()
    members.put("@other", "x")

    then:
    bound.errors().size() == 1
    bound.additionalMembers() == ["@request-id": "req-1"]

    when:
    bound.errors().clear()

    then:
    thrown(UnsupportedOperationException)

    when:
    bound.additionalMembers().put("@other", "x")

    then:
    thrown(UnsupportedOperationException)
  }

  def "convertMeta returns null when meta is absent and translates conversion failure"() {
    expect:
    TypedEnvelopeBinder.convertMeta(null, Integer, { meta -> 1 }) == null

    when:
    TypedEnvelopeBinder.convertMeta(
        Meta.of([note: "n"]), Integer, { meta -> throw new IllegalArgumentException("no") })

    then:
    def failure = thrown(JsonApiMappingException)
    failure.diagnostic() == MappingDiagnostic.UNSUPPORTED_ATTRIBUTE_VALUE
    failure.resourceClass() == null
    failure.propertyPath() == "/meta"
    failure.message == "Failed to convert meta members to " + Integer
    failure.cause instanceof IllegalArgumentException
  }

  private static FakeBackend backend(Object... pairs) {
    def backend = new FakeBackend()
    for (int i = 0; i < pairs.length; i += 2) {
      backend.configuredNames[(Class<?>) pairs[i]] = (String) pairs[i + 1]
    }
    backend
  }

  private static JsonApiDocument metaOnly() {
    new JsonApiDocument(null, null, Meta.of([note: "n"]), null, null, null, Map.of())
  }

  private static JsonApiDocument singleResource(String type, String id) {
    JsonApiDocument.withData(new DocumentData.SingleResource(ResourceObject.of(type, id)))
  }

  private static final class FakeBackend implements TypedEnvelopeBinder.Backend<Class<?>> {
    final Map<Class<?>, String> configuredNames = [:]
    JsonApiMappingException bindFailure
    String failOnlyForId
    final List<String> boundResources = []

    @Override
    Class<?> targetType(Type registeredType) {
      (Class<?>) registeredType
    }

    @Override
    Class<?> rawClass(Class<?> targetType) {
      targetType
    }

    @Override
    String requireResourceTypeName(Class<?> targetType) {
      def name = configuredNames[targetType]
      if (name == null) {
        throw JsonApiMappingException.withoutLocation(
        MappingDiagnostic.MISSING_RESOURCE_ANNOTATION,
        targetType,
        "Missing @JsonApiResource on " + targetType.name)
      }
      name
    }

    @Override
    Object bindResource(ResourceObject resource, Class<?> targetType) {
      boundResources << resource.type() + ":" + resource.id()
      if (bindFailure != null && (failOnlyForId == null || failOnlyForId == resource.id())) {
        throw bindFailure
      }
      resource.type() + ":" + resource.id()
    }
  }
}
