package com.kazforge.jsonapi.jackson3

import com.kazforge.jsonapi.core.aggregate.ValidationContext
import com.kazforge.jsonapi.core.model.DocumentData
import com.kazforge.jsonapi.core.model.JsonApiDocument
import com.kazforge.jsonapi.core.model.Meta
import com.kazforge.jsonapi.core.model.Relationship
import com.kazforge.jsonapi.core.model.RelationshipData
import com.kazforge.jsonapi.core.model.Relationships
import com.kazforge.jsonapi.core.model.ResourceObject
import com.kazforge.jsonapi.core.model.ResourceIdentifier
import com.kazforge.jsonapi.core.validation.DocumentUsage
import com.kazforge.jsonapi.diagnostic.JsonApiDocumentReadException
import com.kazforge.jsonapi.diagnostic.JsonApiMappingException
import com.kazforge.jsonapi.diagnostic.MappingDiagnostic
import com.kazforge.jsonapi.document.DocumentReadContext
import com.kazforge.jsonapi.document.PrimaryDataKind
import com.kazforge.jsonapi.fixtures.generic.ParameterizedBindingFixtures.GenericValue
import com.kazforge.jsonapi.fixtures.domainread.FlatWrappedMappedTargetArticle
import com.kazforge.jsonapi.fixtures.domainread.MappedReadTarget
import com.kazforge.jsonapi.jackson3.BindingSourceFixtures.Boundaries
import com.kazforge.jsonapi.jackson3.BindingSourceFixtures.Create
import com.kazforge.jsonapi.jackson3.BindingSourceFixtures.CaseNames
import com.kazforge.jsonapi.jackson3.BindingSourceFixtures.Contextual
import com.kazforge.jsonapi.jackson3.BindingSourceFixtures.Details
import com.kazforge.jsonapi.jackson3.BindingSourceFixtures.DetailsMixin
import com.kazforge.jsonapi.jackson3.BindingSourceFixtures.GenericSourcePatch
import com.kazforge.jsonapi.jackson3.BindingSourceFixtures.IgnoredRoot
import com.kazforge.jsonapi.jackson3.BindingSourceFixtures.LinkPatch
import com.kazforge.jsonapi.jackson3.BindingSourceFixtures.Links
import com.kazforge.jsonapi.jackson3.BindingSourceFixtures.Mutable
import com.kazforge.jsonapi.jackson3.BindingSourceFixtures.Merged
import com.kazforge.jsonapi.jackson3.BindingSourceFixtures.Patch
import com.kazforge.jsonapi.jackson3.BindingSourceFixtures.PolymorphicValue
import com.kazforge.jsonapi.jackson3.CloseTrackingFixtures.TrackingInputStream
import com.kazforge.jsonapi.jackson3.LinkageMapperFixtures.FlatAuthor
import com.kazforge.jsonapi.jackson3.LinkageMapperFixtures.FlatMappedArticle
import com.kazforge.jsonapi.jackson3.mapping.RelationshipLinkageMapper
import com.kazforge.jsonapi.mapping.BoundResource
import com.kazforge.jsonapi.mapping.BindingSource.Presence
import com.kazforge.jsonapi.patch.PatchPresence
import java.util.concurrent.atomic.AtomicInteger
import spock.lang.Specification
import tools.jackson.core.JsonParser
import tools.jackson.core.type.TypeReference
import tools.jackson.databind.DeserializationContext
import tools.jackson.databind.InjectableValues
import tools.jackson.databind.PropertyNamingStrategies
import tools.jackson.databind.MapperFeature
import tools.jackson.databind.ValueDeserializer
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.module.SimpleModule

import static com.kazforge.jsonapi.mapping.BindingSource.Presence.*

class BindingSourcesSpec extends Specification {

  private static Jackson3JsonApi api() {
    JsonApiJackson3.jsonApi(JsonMapper.builder()
        .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
        .build())
  }

  def "create attributes use logical paths and exact escaped wire locations"() {
    given:
    def json = '{"data":{"type":"source-things","lid":"draft",' +
        '"attributes":{"display-name":null,"details":{"postal_code":"123","value":"v","a/b~c":"x"},' +
        '"items":[{"postal_code":"456"}],"array":[{"value":"a"}],"optional":null}}}'

    when:
    def bound = api().resources().readCreateWithSources(json, Create)

    then:
    bound.resource().localId() == "draft"
    bound.resource().details().postalCode() == "123"
    bound.resource().optional() == Optional.empty()
    matches(bound, [], PRESENT_VALUE, '/data')
    matches(bound, ['id'], OMITTED, null)
    matches(bound, ['localId'], PRESENT_VALUE, '/data/lid')
    matches(bound, ['displayName'], PRESENT_NULL, '/data/attributes/display-name')
    matches(bound, ['details', 'postalCode'], PRESENT_VALUE, '/data/attributes/details/postal_code')
    matches(bound, ['details', 'value'], PRESENT_VALUE, '/data/attributes/details/value')
    matches(bound, ['details', 'escaped'], PRESENT_VALUE, '/data/attributes/details/a~1b~0c')
    matches(bound, ['details', 'missing'], OMITTED, null)
    matches(bound, ['items', '0', 'postalCode'], PRESENT_VALUE, '/data/attributes/items/0/postal_code')
    matches(bound, ['array', '0', 'value'], PRESENT_VALUE, '/data/attributes/array/0/value')
    matches(bound, ['optional'], PRESENT_NULL, '/data/attributes/optional')
    [
      ['optional', 'value'],
      ['details', 'ignored'],
      ['unknown'],
      ['display-name'],
      ['items', '1'],
      ['items', '00'],
      ['items', '-1'],
      ['details', '']
    ].every { path -> matches(bound, path, UNRESOLVED, null) }
  }

  def "typed PATCH attributes preserve wire presence for recursive and atomic objects"() {
    given:
    def json = '{"data":{"type":"source-things","id":"1","attributes":{' +
        '"details":{"postal_code":"123"},"nested":{"value":"v","a/b~c":null},' +
        '"items":[{"value":"i"}],"array":[{"value":"a"}],"optional":null}}}'

    when:
    def bound = api().patches().readPatchWithSources(json, Patch)

    then:
    bound.resource().details().value().missing() == null
    bound.resource().nested().value().missing().isOmitted()
    bound.resource().optional() == PatchPresence.present(Optional.empty())
    matches(bound, ['id'], PRESENT_VALUE, '/data/id')
    matches(bound, ['displayName'], OMITTED, null)
    matches(bound, ['details', 'postalCode'], PRESENT_VALUE, '/data/attributes/details/postal_code')
    matches(bound, ['details', 'missing'], OMITTED, null)
    matches(bound, ['nested', 'value'], PRESENT_VALUE, '/data/attributes/nested/value')
    matches(bound, ['nested', 'escaped'], PRESENT_NULL, '/data/attributes/nested/a~1b~0c')
    matches(bound, ['nested', 'missing'], OMITTED, null)
    matches(bound, ['items', '0', 'value'], PRESENT_VALUE, '/data/attributes/items/0/value')
    matches(bound, ['array', '0', 'value'], PRESENT_VALUE, '/data/attributes/array/0/value')
    matches(bound, ['optional'], PRESENT_NULL, '/data/attributes/optional')
    matches(bound, ['optional', 'postalCode'], UNRESOLVED, null)
    matches(bound, ['nested', 'value', 'value'], UNRESOLVED, null)
  }

  def "source presence preserves absent null and empty members"(String input, Presence state) {
    given:
    def json = '{"data":{"type":"source-things","id":"1","attributes":{' + input + '}}}'

    expect:
    def create = api().resources().readCreateWithSources(json, Create)
    def patch = api().patches().readPatchWithSources(json, Patch)
    matches(create, ['details'], state, state == OMITTED ? null : '/data/attributes/details')
    matches(patch, ['details'], state, state == OMITTED ? null : '/data/attributes/details')
    matches(create, ['items'], state, state == OMITTED ? null : '/data/attributes/items')
    matches(patch, ['items'], state, state == OMITTED ? null : '/data/attributes/items')
    matches(create, ['details', 'missing'], state == PRESENT_VALUE ? OMITTED : UNRESOLVED, null)

    where:
    input                            | state
    ''                               | OMITTED
    '"details":null,"items":null'      | PRESENT_NULL
    '"details":{},"items":[]'          | PRESENT_VALUE
  }

  def "mix-ins and optional wrappers keep effective nested names"() {
    given:
    def runtime = JsonApiJackson3.jsonApi(JsonMapper.builder()
        .addMixIn(Details, DetailsMixin).build())
    def json = '{"data":{"type":"source-things","attributes":{' +
        '"optional":{"zip_code":"123","value":"real property"}}}}'

    when:
    def bound = runtime.resources().readCreateWithSources(json, Create)

    then:
    bound.resource().optional().get().postalCode() == "123"
    matches(bound, ['optional', 'postalCode'], PRESENT_VALUE, '/data/attributes/optional/zip_code')
    matches(bound, ['optional', 'value'], PRESENT_VALUE, '/data/attributes/optional/value')
  }

  def "unproven paths are unresolved rather than guessed omissions or indices"() {
    given:
    def json = '{"data":{"type":"boundaries","attributes":{' +
        '"unordered":[{"value":"u"}],"skipped":[null,{"value":"s"}],' +
        '"aliased":{"alternate":"accepted"},"parent":null}}}'

    when:
    def bound = api().resources().readCreateWithSources(json, Boundaries)

    then:
    bound.resource().defaulted() == "default"
    bound.resource().aliased().name() == "accepted"
    bound.resource().skipped().size() == 1
    matches(bound, ['defaulted'], OMITTED, null)
    [
      ['unordered', '0'],
      ['skipped', '0'],
      ['aliased', 'name'],
      ['parent', 'value']
    ].every { path -> matches(bound, path, UNRESOLVED, null) }
    matches(bound, ['unordered'], PRESENT_VALUE, '/data/attributes/unordered')
    matches(bound, ['parent'], PRESENT_NULL, '/data/attributes/parent')
  }

  def "merged list indices do not pretend that supplied elements replaced initialized elements"() {
    when:
    def bound = api().resources().readCreateWithSources(
        '{"data":{"type":"merged-source","attributes":{"items":[{"value":"wire"}]}}}', Merged)

    then:
    bound.resource().items*.value() == ["server", "wire"]
    matches(bound, ['items'], PRESENT_VALUE, '/data/attributes/items')
    matches(bound, ['items', '0', 'value'], UNRESOLVED, null)
    matches(bound, ['items', '1', 'value'], UNRESOLVED, null)
  }

  def "contextually filtered and unwrapped creators do not prove ordinary member presence"() {
    when:
    def bound = api().resources().readCreateWithSources(
        '{"data":{"type":"contextual-source","attributes":{' +
        '"filtered":{"value":"ignored"},"flattened":{"value":"wire"}}}}', Contextual)
    def ignoredRoot = api().resources().readCreateWithSources(
        '{"data":{"type":"ignored-source"}}', IgnoredRoot)

    then:
    bound.resource().filtered().value() == null
    bound.resource().flattened().inner().value() == "wire"
    matches(bound, ['filtered', 'value'], UNRESOLVED, null)
    matches(bound, ['flattened', 'inner'], UNRESOLVED, null)
    matches(bound, ['flattened', 'inner', 'value'], UNRESOLVED, null)
    ignoredRoot.resource().value() == null
    matches(ignoredRoot, ['value'], UNRESOLVED, null)
  }

  def "polymorphic list elements keep their source but not a guessed declared member shape"() {
    given:
    def type = new TypeReference<GenericValue<List<PolymorphicValue>>>() {}.getType()

    when:
    def bound = api().resources().readCreateWithSources(
        '{"data":{"type":"things","attributes":{"value":[{"kind":"renamed","newName":"wire"}]}}}', type)

    then:
    def dto = (GenericValue<List<PolymorphicValue>>) bound.resource()
    dto.value().getFirst().value == "wire"
    matches(bound, ['value', '0'], PRESENT_VALUE, '/data/attributes/value/0')
    matches(bound, ['value', '0', 'value'], UNRESOLVED, null)
  }

  def "erased Optional keeps its property source without resolving an unknown inner type"() {
    given:
    def type = new TypeReference<GenericValue<Optional>>() {}.getType()

    when:
    def bound = api().resources().readCreateWithSources(
        '{"data":{"type":"things","attributes":{"value":{"name":"wire"}}}}', type)

    then:
    ((GenericValue<Optional>) bound.resource()).value().get() == [name: "wire"]
    matches(bound, ['value'], PRESENT_VALUE, '/data/attributes/value')
    matches(bound, ['value', 'name'], UNRESOLVED, null)
  }

  def "exact case-sensitive input names remain distinct while accepted folded names are unresolved"() {
    given:
    def runtime = JsonApiJackson3.jsonApi(JsonMapper.builder().build())
    def genericType = new TypeReference<GenericValue<CaseNames>>() {}.getType()

    when:
    def top = runtime.resources().readCreateWithSources(
        '{"data":{"type":"case-source","attributes":{"name":"lower","NAME":"upper"}}}', CaseNames)
    def nested = runtime.resources().readCreateWithSources(
        '{"data":{"type":"things","attributes":{"value":{"name":"lower","NAME":"upper"}}}}', genericType)
    def folded = JsonApiJackson3.jsonApi(JsonMapper.builder()
        .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES).build()).resources()
        .readCreateWithSources(
        '{"data":{"type":"source-things","attributes":{"details":{"VALUE":"wire"}}}}', Create)

    then:
    top.resource().name() == "lower"
    top.resource().upper() == "upper"
    matches(top, ['name'], PRESENT_VALUE, '/data/attributes/name')
    matches(top, ['upper'], PRESENT_VALUE, '/data/attributes/NAME')
    matches(nested, ['value', 'name'], PRESENT_VALUE, '/data/attributes/value/name')
    matches(nested, ['value', 'upper'], PRESENT_VALUE, '/data/attributes/value/NAME')
    folded.resource().details().value() == "wire"
    matches(folded, ['details', 'value'], UNRESOLVED, null)
  }

  def "a mapped linkage target does not hide independently bound wrapper identifier meta"() {
    given:
    def linkageMapper = { data, target ->
      ((RelationshipData.SingleLinkage) data).identifier().type() == "people"
          ? null : new MappedReadTarget("comments", "custom")
    } as RelationshipLinkageMapper
    def runtime = JsonApiJackson3.builder(JsonMapper.builder().build())
        .linkageMappers([(MappedReadTarget): linkageMapper]).build()
    def json = '{"data":{"type":"articles","relationships":{' +
        '"author":{"data":{"type":"people","id":"p1","meta":{"role":"editor"}}},' +
        '"comments":{"data":[{"type":"comments","id":"c1","meta":{"pinned":true}}]}}}}'

    when:
    def bound = runtime.resources().readCreateWithSources(json, FlatWrappedMappedTargetArticle)

    then:
    bound.resource().comments().getFirst().target() == new MappedReadTarget("comments", "custom")
    bound.resource().comments().getFirst().meta().pinned()
    bound.resource().author() == null
    matches(bound, ['author'], PRESENT_VALUE, '/data/relationships/author/data')
    matches(bound, ['author', 'meta', 'role'], UNRESOLVED, null)
    matches(bound, ['comments', '0', 'target'], UNRESOLVED, null)
    matches(bound, [
      'comments',
      '0',
      'meta',
      'pinned'
    ], PRESENT_VALUE,
    '/data/relationships/comments/data/0/meta/pinned')
  }

  def "direct linkage and three meta locations are distinct for #operation"() {
    given:
    def json = '{"data":{"type":"source-links","id":"1","meta":{"value":"resource"},' +
        '"relationships":{"author":{"data":{"type":"people","id":"p1","meta":{"value":"identifier"}},' +
        '"meta":{"value":"relationship"}},"owners":{"data":[{"type":"people","id":"p2",' +
        '"meta":{"value":"owner"}}]},"tags":{"data":[{"type":"people","id":"p3"}]}}}}'

    when:
    BoundResource<?> bound = operation == "create"
        ? api().resources().readCreateWithSources(json, Links)
        : api().patches().readPatchWithSources(json, LinkPatch)

    then:
    matches(bound, ['author'], PRESENT_VALUE, '/data/relationships/author/data')
    matches(bound, ['author', 'id'], PRESENT_VALUE, '/data/relationships/author/data/id')
    matches(bound, ['author', 'lid'], OMITTED, null)
    matches(bound, ['author', 'meta'], PRESENT_VALUE, '/data/relationships/author/data/meta')
    matches(bound, ['metadata', 'value'], PRESENT_VALUE, '/data/meta/value')
    matches(bound, ['authorMetadata', 'value'], PRESENT_VALUE, '/data/relationships/author/meta/value')
    matches(bound, ['owners', '0', 'target', 'id'], PRESENT_VALUE, '/data/relationships/owners/data/0/id')
    matches(bound, [
      'owners',
      '0',
      'meta',
      'value'
    ], PRESENT_VALUE, '/data/relationships/owners/data/0/meta/value')
    matches(bound, ['tags', '0'], UNRESOLVED, null)

    where:
    operation << ["create", "patch"]
  }

  def "generic stream and string routes preserve DTO types and caller ownership"() {
    given:
    def runtime = api()
    def createType = new TypeReference<GenericValue<List<Details>>>() {}.getType()
    def patchType = new TypeReference<GenericSourcePatch<List<Details>>>() {}.getType()
    def createJson = '{"data":{"type":"things","attributes":{"value":[{"postal_code":"123"}]}}}'
    def patchJson = '{"data":{"type":"articles","id":"1","attributes":{"title":[{"postal_code":"123"}]}}}'
    def createInput = new TrackingInputStream(createJson.bytes)
    def patchInput = new TrackingInputStream(patchJson.bytes)
    def context = DocumentReadContext.of(
        ValidationContext.defaults().withDocumentUsage(DocumentUsage.UPDATE_REQUEST),
        PrimaryDataKind.RESOURCE)
    def document = runtime.documents().read(patchJson, context)

    expect:
    [
      runtime.resources().readCreateWithSources(createJson, createType),
      runtime.resources().readCreateWithSources(createInput, createType)
    ].every { bound ->
      def dto = (GenericValue<List<Details>>) bound.resource()
      assert dto.value().get(0) instanceof Details
      matches(bound, ['value', '0', 'postalCode'], PRESENT_VALUE, '/data/attributes/value/0/postal_code')
    }
    [
      runtime.patches().readPatchWithSources(patchJson, patchType),
      runtime.patches().readPatchWithSources(patchInput, patchType),
      runtime.patches().bindPatchWithSources(document, patchType)
    ].every { bound ->
      def dto = (GenericSourcePatch<List<Details>>) bound.resource()
      assert dto.title().value().get(0) instanceof Details
      matches(bound, ['title', '0', 'postalCode'], PRESENT_VALUE, '/data/attributes/title/0/postal_code')
    }
    !createInput.closed
    !patchInput.closed
  }

  def "validated binding skips revalidation and preserves absent relationship data"() {
    given:
    def resource = new ResourceObject("source-links", "1", null, null,
        Relationships.ofRelationships([author: new Relationship(null, null, Meta.of([value: "ignored"]), [:])]),
        null, null, [:])
    def document = new JsonApiDocument(
        new DocumentData.SingleResource(resource), null, null, null, null, null, [:])

    when:
    def bound = api().patches().bindPatchWithSources(document, LinkPatch)

    then:
    bound.resource().author().isOmitted()
    bound.resource().authorMetadata().isOmitted()
    matches(bound, ['author'], OMITTED, null)
    matches(bound, ['authorMetadata'], UNRESOLVED, null)
  }

  def "Class stream routes preserve null and empty linkage and caller ownership"() {
    given:
    def json = '{"data":{"type":"source-things","id":"1","relationships":{' +
        '"author":{"data":null},"comments":{"data":[]}}}}'
    def createInput = new TrackingInputStream(json.bytes)
    def patchInput = new TrackingInputStream(json.bytes)

    expect:
    [
      api().resources().readCreateWithSources(createInput, Create),
      api().patches().readPatchWithSources(patchInput, Patch)
    ].every { bound ->
      matches(bound, ['author'], PRESENT_NULL, '/data/relationships/author/data') &&
      matches(bound, ['comments'], PRESENT_VALUE, '/data/relationships/comments/data') &&
      matches(bound, ['comments', '0'], UNRESOLVED, null)
    }
    !createInput.closed
    !patchInput.closed
  }

  def "custom linkage is opaque below data and never hydrates included resources"() {
    given:
    def calls = new AtomicInteger()
    def linkageMapper = { data, target ->
      calls.incrementAndGet()
      new FlatAuthor("people", "mapped")
    } as RelationshipLinkageMapper
    def runtime = JsonApiJackson3.builder(JsonMapper.builder().build())
        .linkageMappers([(FlatAuthor): linkageMapper]).build()
    def json = '{"data":{"type":"articles","relationships":{' +
        '"author":{"data":{"type":"people","id":"p1"}}}},' +
        '"included":[{"type":"people","id":"p1","attributes":{"name":"Ada"}}]}'

    when:
    def bound = runtime.resources().readCreateWithSources(json, FlatMappedArticle)

    then:
    bound.resource().author() == new FlatAuthor("people", "mapped")
    matches(bound, ['author'], PRESENT_VALUE, '/data/relationships/author/data')
    matches(bound, ['author', 'id'], UNRESOLVED, null)
    matches(bound, ['author', 'name'], UNRESOLVED, null)
    calls.get() == 1
  }

  def "source capture never converts twice or traverses an opaque custom value for #operation"() {
    given:
    def conversions = new AtomicInteger()
    def module = new SimpleModule().addDeserializer(Details, new ValueDeserializer<Details>() {
          @Override
          Details deserialize(JsonParser parser, DeserializationContext context) {
            parser.skipChildren()
            conversions.incrementAndGet()
            new Details("custom", null, null, null, null)
          }
        })
    module.addDeserializer(ResourceIdentifier, new ValueDeserializer<ResourceIdentifier>() {
          @Override
          ResourceIdentifier deserialize(JsonParser parser, DeserializationContext context) {
            parser.skipChildren()
            conversions.incrementAndGet()
            ResourceIdentifier.of("people", "custom")
          }
        })
    def runtime = JsonApiJackson3.jsonApi(JsonMapper.builder().addModule(module).build())
    def json = '{"data":{"type":"source-things","id":"1","attributes":{"details":{"value":"wire"}},' +
        '"relationships":{"author":{"data":{"type":"people","id":"p1"}}}}}'

    when:
    def bound = operation == "create"
        ? runtime.resources().readCreateWithSources(json, Create)
        : runtime.patches().readPatchWithSources(json, Patch)

    then:
    matches(bound, ['details'], PRESENT_VALUE, '/data/attributes/details')
    matches(bound, ['details', 'value'], UNRESOLVED, null)
    matches(bound, ['details', 'postalCode'], UNRESOLVED, null)
    matches(bound, ['author'], PRESENT_VALUE, '/data/relationships/author/data')
    matches(bound, ['author', 'id'], UNRESOLVED, null)
    conversions.get() == 2

    where:
    operation << ["create", "patch"]
  }

  def "source snapshots survive mutation and exclude injection-only properties"() {
    given:
    def runtime = api()
    def bound = runtime.resources().readCreateWithSources(
        '{"data":{"type":"mutable-source","attributes":{"details":{"value":"old"}}}}', Mutable)
    def injectedRuntime = JsonApiJackson3.jsonApi(JsonMapper.builder()
        .injectableValues(new InjectableValues.Std().addValue("injected-title", "server")).build())

    when:
    bound.resource().details = null
    def injected = injectedRuntime.resources().readCreateWithSources(
        '{"data":{"type":"injection-only","id":"1"}}', DirectionalityReadFixtures.InjectionOnly)

    then:
    matches(bound, ['details', 'value'], PRESENT_VALUE, '/data/attributes/details/value')
    injected.resource().titleValue() == "server"
    matches(injected, ['title'], UNRESOLVED, null)

    when:
    bound.source([null])

    then:
    thrown(NullPointerException)
  }

  def "opt-in routes preserve validation and mapping failure precedence and stream ownership"() {
    given:
    def invalid = new TrackingInputStream('{"data":{"type":"source-things"}}'.bytes)

    when:
    api().patches().readPatchWithSources(invalid, Patch)

    then:
    thrown(JsonApiDocumentReadException)
    !invalid.closed

    when:
    api().resources().readCreateWithSources('{"data":{"type":"wrong"}}', Create)

    then:
    def failure = thrown(JsonApiMappingException)
    failure.diagnostic() == MappingDiagnostic.RESOURCE_TYPE_MISMATCH
    failure.location().pointer() == '/type'

    when:
    api().patches().readPatchWithSources(
        '{"data":{"type":"source-things","id":"1","attributes":{"unknown":true,"details":42}}}', Patch)

    then:
    def unknown = thrown(JsonApiMappingException)
    unknown.diagnostic() == MappingDiagnostic.UNKNOWN_PATCH_MEMBER
  }

  private static boolean matches(BoundResource<?> bound, List<String> path, Presence state, String pointer) {
    def source = bound.source(path)
    assert source.presence() == state: "Presence at $path"
    assert source.location()?.pointer() == pointer: "Location at $path"
    true
  }
}
