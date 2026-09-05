package io.github.kazemek.jsonapi.jackson3

import com.fasterxml.jackson.annotation.JsonProperty
import io.github.kazemek.jsonapi.annotation.JsonApiAttribute
import io.github.kazemek.jsonapi.annotation.JsonApiId
import io.github.kazemek.jsonapi.annotation.JsonApiResource
import io.github.kazemek.jsonapi.core.model.Attributes
import io.github.kazemek.jsonapi.core.model.DocumentData
import io.github.kazemek.jsonapi.core.model.JsonApiDocument
import io.github.kazemek.jsonapi.core.model.RelationshipData
import io.github.kazemek.jsonapi.core.model.ResourceIdentifier
import io.github.kazemek.jsonapi.core.model.ResourceIdentity
import io.github.kazemek.jsonapi.core.model.ResourceObject
import io.github.kazemek.jsonapi.core.validation.ValidationRuleCode
import io.github.kazemek.jsonapi.core.validation.ValidationContext
import io.github.kazemek.jsonapi.jackson.diagnostic.CodecFailureCategory
import io.github.kazemek.jsonapi.jackson.diagnostic.JsonApiDocumentReadException
import io.github.kazemek.jsonapi.jackson.diagnostic.JsonApiMappingException
import io.github.kazemek.jsonapi.jackson.diagnostic.MappingDiagnostic
import io.github.kazemek.jsonapi.jackson.document.DocumentReadContext
import io.github.kazemek.jsonapi.jackson.document.PrimaryDataKind
import io.github.kazemek.jsonapi.jackson.mapping.DomainData
import io.github.kazemek.jsonapi.jackson.mapping.IncludedResources
import io.github.kazemek.jsonapi.jackson.mapping.IdentifierConverter
import io.github.kazemek.jsonapi.fixtures.domainread.FlatArticle
import io.github.kazemek.jsonapi.fixtures.domainwrite.Comment
import io.github.kazemek.jsonapi.fixtures.domainwrite.Person
import io.github.kazemek.jsonapi.fixtures.domainread.FlatNullableIdArticle
import io.github.kazemek.jsonapi.fixtures.TestFixtureResources
import io.github.kazemek.jsonapi.fixtures.enveloperead.EmptyResourceType
import io.github.kazemek.jsonapi.fixtures.enveloperead.FlatNode
import io.github.kazemek.jsonapi.fixtures.enveloperead.FlatStrictArticle
import io.github.kazemek.jsonapi.fixtures.enveloperead.FlatThrowingArticle
import io.github.kazemek.jsonapi.fixtures.enveloperead.InvalidResourceType
import io.github.kazemek.jsonapi.jackson3.LinkageMapperFixtures.FlatAuthor
import io.github.kazemek.jsonapi.jackson3.LinkageMapperFixtures.FlatMappedArticle
import spock.lang.Specification
import spock.lang.Unroll
import tools.jackson.core.JsonParser
import tools.jackson.core.JsonToken
import tools.jackson.databind.DeserializationContext
import tools.jackson.databind.JavaType
import tools.jackson.databind.deser.std.StdDeserializer
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.module.SimpleModule

class DomainDocumentReaderSpec extends Specification {

  @Unroll
  def "readValue binds #path into DomainData and IncludedResources"() {
    given:
    def reader = domainReader(resourceTypes, context)

    when:
    def envelope = reader.readValue(corpusText(path))

    then:
    envelope.data() == expectedData
    includedResources(envelope) == expectedIncludedResources
    envelope.additionalMembers() == expectedAdditionalMembers

    where:
    path                                                     | resourceTypes                         | context                                   | expectedData                                                                                                                                                                                                                       | expectedIncludedResources                  | expectedAdditionalMembers
    'envelope-binding/single-resource.json'                  | [FlatArticle]                          | DocumentReadContext.resourceDefaults() | new DomainData.SingleResource(new FlatArticle('1', 'JSON:API paints my bikeshed!', 'Content', ResourceIdentifier.of('people', 'p1'), [
      ResourceIdentifier.of('comments', 'c1')
    ]))                         | null                                       | Map.of()
    'envelope-binding/heterogeneous-collection.json'         | [FlatArticle, Person]                   | DocumentReadContext.resourceDefaults() | new DomainData.ResourceCollection([
      new FlatArticle('1', 'First', null, null, null),
      new Person('9', 'Dan')
    ])                                                                                            | null                                       | Map.of()
    'envelope-binding/at-member-document.json'               | [FlatArticle]                          | DocumentReadContext.resourceDefaults() | new DomainData.SingleResource(new FlatArticle('1', 'Hello', null, null, null))                                                                                                                               | null                                       | Map.of('@request-id', 'req-1')
    'envelope-binding/cyclic-linkage.json'                   | [FlatNode]                             | DocumentReadContext.resourceDefaults() | new DomainData.SingleResource(new FlatNode('1', ResourceIdentifier.of('nodes', '2')))                                                                                                                         | [
      new FlatNode('2', ResourceIdentifier.of('nodes', '1'))
    ] | Map.of()
    'documents/resource-collection.json'                     | [FlatArticle]                          | DocumentReadContext.resourceDefaults() | new DomainData.ResourceCollection([
      new FlatArticle('1', 'First', null, null, null),
      new FlatArticle('2', 'Second', null, null, null)
    ])                                                                      | null                                       | Map.of()
    'documents/empty-included.json'                          | [FlatArticle]                          | DocumentReadContext.resourceDefaults() | new DomainData.SingleResource(new FlatArticle('1', null, null, null, null))                                                                                                                                    | []                                         | Map.of()
    'documents/null-data.json'                               | []                                    | DocumentReadContext.resourceDefaults() | DomainData.NullData.INSTANCE                                                                                                                                                                                        | null                                       | Map.of()
    'documents/meta-only.json'                               | []                                    | DocumentReadContext.resourceDefaults() | null                                                                                                                                                                                                                         | null                                       | Map.of()
    'documents/single-identifier.json'                       | []                                    | DocumentReadContext.identifierDefaults() | new DomainData.SingleIdentifier(ResourceIdentifier.of('articles', '1'))                                                                                                                                       | null                                       | Map.of()
    'documents/identifier-collection.json'                   | []                                    | DocumentReadContext.identifierDefaults() | new DomainData.IdentifierCollection([
      ResourceIdentifier.of('articles', '1'),
      ResourceIdentifier.of('articles', '2')
    ])                                                                                       | null                                       | Map.of()
    'documents/empty-identifier-collection.json'             | []                                    | DocumentReadContext.identifierDefaults() | new DomainData.IdentifierCollection([])                                                                                                                                                                             | null                                       | Map.of()
    'documents/errors-document.json'                         | []                                    | DocumentReadContext.resourceDefaults() | null                                                                                                                                                                                                                         | null                                       | Map.of()
    'documents/compound-document.json'                       | [FlatArticle, Person]                   | DocumentReadContext.resourceDefaults() | new DomainData.SingleResource(new FlatArticle('1', null, null, ResourceIdentifier.of('people', '9'), null))                                                                                                  | [new Person('9', 'Dan')]                    | Map.of()
    'documents/compound-shared-identity.json'                | [FlatArticle, Person]                   | DocumentReadContext.resourceDefaults() | new DomainData.ResourceCollection([
      new FlatArticle('1', null, null, ResourceIdentifier.of('people', '9'), null),
      new FlatArticle('2', null, null, ResourceIdentifier.of('people', '9'), null)
    ]) | [new Person('9', 'Dan')] | Map.of()
    'documents/string-and-object-links.json'                 | [FlatArticle]                          | DocumentReadContext.resourceDefaults() | new DomainData.ResourceCollection([
      new FlatArticle('1', null, null, null, null)
    ])                                                                                                                               | null                                       | Map.of()
    'documents/empty-wrappers.json'                           | [FlatArticle]                          | DocumentReadContext.resourceDefaults() | new DomainData.SingleResource(new FlatArticle('1', null, null, null, null))                                                                                                                                    | null                                       | Map.of()
  }


  @Unroll
  def "fromDocument binds #path without reparsing"() {
    given:
    def reader = domainReader(
        [FlatArticle, Person], DocumentReadContext.resourceDefaults())
    def document = readCoreDocument(path, "people", includedId)

    when:
    def envelope = reader.fromDocument(document)

    then:
    envelope.data() == expectedData
    includedResources(envelope) == expectedIncludedResources
    envelope.additionalMembers() == Map.of()

    where:
    path                                                   | includedId | expectedData                                                                                                  | expectedIncludedResources
    'envelope-binding/shared-identity-id-and-lid.json'      | '9'        | new DomainData.SingleResource(new FlatArticle('1', null, null, null, null))                                  | [new Person('9', 'Dan')]
    'envelope-binding/independent-envelopes-matching.json'  | '9'        | new DomainData.SingleResource(new FlatArticle('1', null, null, ResourceIdentifier.of('people', '9'), null)) | [new Person('9', 'Dan')]
    'envelope-binding/independent-envelopes-unrelated.json' | '99'       | new DomainData.SingleResource(new FlatArticle('1', null, null, ResourceIdentifier.of('people', '9'), null)) | [new Person('99', 'Other')]
  }

  def "exposes decoded document-level members"() {
    given:
    def mapper = JsonMapper.builder().build()
    def reader = JsonApiJackson3.reader(mapper, DocumentReadContext.resourceDefaults())
    def decoded = reader.readValue(
        '{"errors":[{"status":"400"}],"meta":{"note":"n"},' +
        '"jsonapi":{"version":"1.1"},"links":{"self":"/errors"}}')
    def envelope = newReader().fromDocument(
        new JsonApiDocument(
        decoded.data(),
        decoded.errors(),
        decoded.meta(),
        decoded.jsonapi(),
        decoded.links(),
        decoded.included(),
        ['@request-id': 'req-1']))

    expect:
    envelope.errors().size() == 1
    envelope.errors().get(0).status() == '400'
    envelope.meta().members() == [note: 'n']
    envelope.jsonapi().version() == '1.1'
    envelope.links().links().get('self').href() == '/errors'
    envelope.additionalMembers() == ['@request-id': 'req-1']
  }

  def "domain envelope errors are unmodifiable"() {
    given:
    def envelope = newReader().readValue('{"errors":[{"status":"400"}]}')

    when:
    envelope.errors().clear()

    then:
    thrown(UnsupportedOperationException)
  }

  def "domain envelope additional members are unmodifiable"() {
    given:
    def envelope = newReader().readValue('{"errors":[{"status":"400"}],"@request-id":"req-1"}')

    when:
    envelope.additionalMembers().put('@other', 'value')

    then:
    thrown(UnsupportedOperationException)
  }

  def "domain envelope included resources are unmodifiable"() {
    given:
    def envelope = newReader(FlatArticle, Person).readValue(
        corpusText('documents/compound-document.json'))

    when:
    envelope.included().resources().clear()

    then:
    thrown(UnsupportedOperationException)
  }

  def "included resources defensively copy construction inputs"() {
    given:
    def identity = ResourceIdentity.ofId('people', '9')
    def person = new Person('9', 'Dan')
    def resources = new ArrayList([person])
    def identities = new ArrayList([new LinkedHashSet([identity])])
    def included = IncludedResources.of(resources, identities)

    when:
    resources.clear()
    identities[0].clear()

    then:
    included.resources() == [person]
    included.find(identity).get().is(person)

    when:
    included.resources().clear()

    then:
    thrown(UnsupportedOperationException)
  }

  @Unroll
  def "reports #expectedDiagnostic while binding #path"() {
    given:
    def reader = domainReader(resourceTypes, DocumentReadContext.resourceDefaults())

    when:
    reader.readValue(corpusText(path))

    then:
    def ex = thrown(JsonApiMappingException)
    ex.diagnostic() == expectedDiagnostic
    ex.propertyPath() == expectedPropertyPath
    ex.resourceClass() == expectedResourceClass

    where:
    path                                                    | resourceTypes                         | expectedDiagnostic                              | expectedPropertyPath                                  | expectedResourceClass
    'envelope-binding/unregistered-primary-single.json'    | []                                    | MappingDiagnostic.UNREGISTERED_RESOURCE_TYPE   | '/data'                                              | null
    'envelope-binding/unregistered-primary-collection.json' | []                                    | MappingDiagnostic.UNREGISTERED_RESOURCE_TYPE   | '/data/0'                                            | null
    'envelope-binding/binder-failure-collection.json'      | [
      FlatArticle,
      Person,
      FlatStrictArticle
    ] | MappingDiagnostic.RELATIONSHIP_CARDINALITY_MISMATCH | '/data/0/relationships/author/data'              | ResourceIdentifier
    'envelope-binding/binder-failure-single.json'           | [
      FlatArticle,
      Person,
      FlatStrictArticle
    ] | MappingDiagnostic.RELATIONSHIP_CARDINALITY_MISMATCH | '/data/relationships/author/data'                | ResourceIdentifier
    'envelope-binding/binder-failure-included.json'         | [
      FlatArticle,
      Person,
      FlatStrictArticle
    ] | MappingDiagnostic.UNSUPPORTED_ATTRIBUTE_VALUE       | '/included/1/attributes/title'                    | FlatStrictArticle
    'envelope-binding/root-level-failure.json'              | [FlatThrowingArticle]                | MappingDiagnostic.MISSING_CREATOR_INPUT        | '/data'                                              | FlatThrowingArticle
    'documents/compound-document.json'                      | [FlatArticle]                         | MappingDiagnostic.UNREGISTERED_RESOURCE_TYPE   | '/included/0'                                       | null
  }

  @Unroll
  def "rejects validation-invalid #path before typed envelope binding"() {
    given:
    def reader = domainReader(resourceTypes, DocumentReadContext.resourceDefaults())

    when:
    reader.readValue(corpusText(path))

    then:
    def ex = thrown(JsonApiDocumentReadException)
    ex.category() == CodecFailureCategory.AGGREGATE_VALIDATION
    ex.ruleCode() == ValidationRuleCode.DUPLICATE_RESOURCE_IDENTITY
    ex.jsonPointer() == expectedJsonPointer

    where:
    path                                                   | resourceTypes       | expectedJsonPointer
    'envelope-binding/duplicate-included-identities.json' | [FlatArticle, Person] | '/included/1'
  }

  def "included resources preserve identity aliases and instance identity"() {
    given:
    def reader = domainReader([FlatArticle, Person], DocumentReadContext.resourceDefaults())

    when:
    def envelope =
        reader.fromDocument(
        readCoreDocument('envelope-binding/shared-identity-id-and-lid.json', 'people', '9'))
    def included = envelope.included()
    def byId = included.find(ResourceIdentity.ofId('people', '9'))
    def byLid = included.find(ResourceIdentity.ofLid('people', 'tmp-9'))

    then:
    included.resources() == [new Person('9', 'Dan')]
    byId.isPresent()
    byLid.isPresent()
    byId.get().is(byLid.get())
  }

  def "metaAs returns null for both overloads when meta is absent"() {
    given:
    def reader = newReader(FlatArticle)

    when:
    def envelope = reader.readValue(corpusText('envelope-binding/single-resource.json'))
    def javaType = JsonMapper.builder().build().constructType(MetaPayload)

    then:
    envelope.metaAs(MetaPayload) == null
    envelope.metaAs(javaType) == null
  }

  def "metaAs converts via the caller-mapper module on both entry paths and both overloads"() {
    given:
    def module = new SimpleModule()
    module.addDeserializer(MetaPayload, new CountValueDeserializer())
    def base = JsonMapper.builder().addModule(module).build()
    def reader = JsonApiJackson3.domainDocumentReader(
        base, DocumentReadContext.resourceDefaults(), registry())
    def json = '{"meta":{"count":3}}'
    def payloadType = base.constructType(MetaPayload)

    when:
    def fromRead = reader.readValue(json)
    def document = JsonApiJackson3.reader(base, DocumentReadContext.resourceDefaults()).readValue(json)
    def fromDocument = reader.fromDocument(document)

    then:
    fromRead.metaAs(MetaPayload) == new MetaPayload(3)
    fromRead.metaAs(payloadType) == new MetaPayload(3)
    fromDocument.metaAs(MetaPayload) == new MetaPayload(3)
    fromDocument.metaAs(payloadType) == new MetaPayload(3)
  }

  def "incompatible metaAs target is UNSUPPORTED_ATTRIBUTE_VALUE at /meta"() {
    given:
    def reader = newReader()

    when:
    reader.readValue('{"meta":{"name":"x"}}').metaAs(MetaPayload)

    then:
    def ex = thrown(JsonApiMappingException)
    ex.diagnostic() == MappingDiagnostic.UNSUPPORTED_ATTRIBUTE_VALUE
    ex.propertyPath() == "/meta"
    ex.resourceClass() == null
  }

  def "JavaType registrations bind through the same registry gate"() {
    given:
    def base = JsonMapper.builder().build()
    def registry = ResourceTypeRegistry.builder(base)
        .register(base.constructType(FlatArticle))
        .register(Person)
        .build()
    def reader = JsonApiJackson3.domainDocumentReader(
        base, DocumentReadContext.resourceDefaults(), registry)

    when:
    def envelope = reader.readValue(corpusText('envelope-binding/heterogeneous-collection.json'))

    then:
    ((DomainData.ResourceCollection) envelope.data()).resources() ==
        [
          new FlatArticle("1", "First", null, null, null),
          new Person("9", "Dan")
        ]
  }

  def "mapper-instance domainDocumentReader overloads bind identically"() {
    given:
    def mapper = JsonMapper.builder().build()
    def registry = ResourceTypeRegistry.builder(mapper)
        .register(FlatArticle)
        .build()
    def threeArg = JsonApiJackson3.domainDocumentReader(
        mapper, DocumentReadContext.resourceDefaults(), registry)
    def fourArg = JsonApiJackson3.domainDocumentReader(
        mapper, DocumentReadContext.resourceDefaults(), registry, IdentifierConverter.defaults())
    def fiveArg = JsonApiJackson3.domainDocumentReader(
        mapper,
        DocumentReadContext.resourceDefaults(),
        registry,
        IdentifierConverter.defaults(),
        Map.of())

    when:
    def fromThree = threeArg.readValue(corpusText('documents/resource-collection.json'))
    def fromFour = fourArg.readValue(corpusText('documents/resource-collection.json'))
    def fromFive = fiveArg.readValue(corpusText('documents/resource-collection.json'))

    then:
    ((DomainData.ResourceCollection) fromThree.data()).resources()*.title == ["First", "Second"]
    ((DomainData.ResourceCollection) fromFour.data()).resources()*.title == ["First", "Second"]
    ((DomainData.ResourceCollection) fromFive.data()).resources()*.title == ["First", "Second"]
  }

  def "custom linkage mappers apply to primary and included resources"() {
    given:
    def authorMapper = { RelationshipData data, JavaType target ->
      if (data instanceof RelationshipData.SingleLinkage) {
        def identifier = ((RelationshipData.SingleLinkage) data).identifier()
        return new Person(identifier.id(), null)
      }
      ((RelationshipData.IdentifierCollectionLinkage) data).identifiers().collect {
        new Person(it.id(), null)
      }
    } as RelationshipLinkageMapper
    def flatAuthorMapper = { RelationshipData data, JavaType target ->
      if (data instanceof RelationshipData.SingleLinkage) {
        def identifier = ((RelationshipData.SingleLinkage) data).identifier()
        return new FlatAuthor(identifier.type(), identifier.id())
      }
      ((RelationshipData.IdentifierCollectionLinkage) data).identifiers().collect {
        new FlatAuthor(it.type(), it.id())
      }
    } as RelationshipLinkageMapper
    def reader = JsonApiJackson3.domainDocumentReader(
        JsonMapper.builder().build(),
        DocumentReadContext.resourceDefaults(),
        registry(FlatMappedArticle, Comment, Person),
        IdentifierConverter.defaults(),
        [(FlatAuthor): flatAuthorMapper, (Person): authorMapper])
    def json =
        '''
        {
          "data": {
            "type": "articles",
            "id": "1",
            "relationships": {
              "author": {
                "data": {
                  "type": "people",
                  "id": "p1"
                }
              },
              "contributors": {
                "data": [
                  {
                    "type": "comments",
                    "id": "c1"
                  }
                ]
              }
            }
          },
          "included": [
            {
              "type": "comments",
              "id": "c1",
              "relationships": {
                "author": {
                  "data": {
                    "type": "people",
                    "id": "p1"
                  }
                }
              }
            },
            {
              "type": "people",
              "id": "p1"
            }
          ]
        }
        '''

    when:
    def envelope = reader.readValue(json)

    then:
    ((DomainData.SingleResource) envelope.data()).resource() as FlatMappedArticle ==
        new FlatMappedArticle(
        "1", null, new FlatAuthor("people", "p1"), [
          new FlatAuthor("comments", "c1")
        ])
    envelope.included().resources() == [
      new Comment("c1", null, new Person("p1", null)),
      new Person("p1", null)
    ]
  }

  def "caller-owned stream and parser remain open on success and failure"() {
    given:
    def reader = newReader(FlatArticle)
    def successBytes = corpusText('envelope-binding/single-resource.json').bytes
    def successStream = new CloseTrackingInputStream(new ByteArrayInputStream(successBytes))
    def failureStream = new CloseTrackingInputStream(new ByteArrayInputStream('{"data":'.bytes))
    def parser = JsonMapper.builder().build().createParser(
        corpusText('envelope-binding/single-resource.json'))

    when:
    def envelope = reader.readValue(successStream)
    def fromParser = reader.readValue(parser)

    then:
    envelope.data() instanceof DomainData.SingleResource
    fromParser.data() instanceof DomainData.SingleResource
    !successStream.closed
    !parser.closed

    when:
    reader.readValue(failureStream)

    then:
    thrown(JsonApiDocumentReadException)
    !failureStream.closed

    cleanup:
    parser?.close()
  }

  def "malformed input stays JsonApiDocumentReadException with category and location"() {
    given:
    def reader = newReader()

    when:
    reader.readValue('{"data":')

    then:
    def ex = thrown(JsonApiDocumentReadException)
    ex.category() == CodecFailureCategory.MALFORMED_JSON
    ex.jsonPointer() == ""
  }

  def "validation failures keep the originating rule code"() {
    given:
    def reader = newReader(FlatArticle, Person)

    when:
    reader.readValue(corpusText('envelope-binding/duplicate-included-identities.json'))

    then:
    def ex = thrown(JsonApiDocumentReadException)
    ex.category() == CodecFailureCategory.AGGREGATE_VALIDATION
    ex.ruleCode() == ValidationRuleCode.DUPLICATE_RESOURCE_IDENTITY
    ex.jsonPointer() == "/included/1"
  }

  def "typed envelope binding rejects supplied getter-only mapped members"() {
    given:
    def registry = ResourceTypeRegistry.builder(JsonMapper.builder().build())
        .register(DirectionalityReadFixtures.GetterOnly)
        .build()
    def reader = JsonApiJackson3.domainDocumentReader(
        JsonMapper.builder().build(), DocumentReadContext.resourceDefaults(), registry)

    when:
    reader.readValue(
        '{"data":{"type":"getter-only","id":"1","attributes":{"title":"supplied"}}}')

    then:
    def ex = thrown(JsonApiMappingException)
    ex.diagnostic() == MappingDiagnostic.NON_DESERIALIZABLE_PROPERTY
    ex.propertyPath() == "/data/attributes/title"
    ex.resourceClass() == DirectionalityReadFixtures.GetterOnly
  }

  def "typed envelope binding rejects a supplied getter-only identifier at /data/id"() {
    given:
    def registry = ResourceTypeRegistry.builder(JsonMapper.builder().build())
        .register(DirectionalityReadFixtures.GetterOnlyIdentifier)
        .build()
    def reader = JsonApiJackson3.domainDocumentReader(
        JsonMapper.builder().build(), DocumentReadContext.resourceDefaults(), registry)

    when:
    reader.readValue('{"data":{"type":"getter-only-id","id":"supplied"}}')

    then:
    def ex = thrown(JsonApiMappingException)
    ex.diagnostic() == MappingDiagnostic.NON_DESERIALIZABLE_PROPERTY
    ex.propertyPath() == "/data/id"
    ex.resourceClass() == DirectionalityReadFixtures.GetterOnlyIdentifier
  }

  def "typed envelope binding rejects a supplied getter-only local-id at /data/lid"() {
    given:
    def registry = ResourceTypeRegistry.builder(JsonMapper.builder().build())
        .register(LocalIdFixtures.GetterOnlyLocalId)
        .build()
    def reader = JsonApiJackson3.domainDocumentReader(
        JsonMapper.builder().build(), DocumentReadContext.resourceDefaults(), registry)
    def document = new JsonApiDocument(
        new DocumentData.SingleResource(
        new ResourceObject("getter-only-lid", null, "client-lid", null, null, null, null, Map.of())),
        null,
        null,
        null,
        null,
        null,
        Map.of())

    when:
    reader.fromDocument(document)

    then:
    def ex = thrown(JsonApiMappingException)
    ex.diagnostic() == MappingDiagnostic.NON_DESERIALIZABLE_PROPERTY
    ex.propertyPath() == "/data/lid"
    ex.resourceClass() == LocalIdFixtures.GetterOnlyLocalId
  }

  // ============================== MAPPING-LOCATION COMPOSITION ==============================
  //
  // Binder failures compose structurally with the document prefix: a resource-relative binder
  // location joins under /data, /data/<index>, or /included/<index>; a binder failure without a
  // location reports just the document prefix. Locations carry wire names, never Jackson logical
  // names, and segments are RFC 6901-escaped.

  def "single primary data composes to /data plus the resource-local pointer"() {
    given:
    def reader = newReader(LocationArticle)

    when:
    reader.readValue(
        '{"data":{"type":"loc-articles","id":"1","attributes":{"title":"oops"}}}')

    then:
    def ex = thrown(JsonApiMappingException)
    ex.diagnostic() == MappingDiagnostic.UNSUPPORTED_ATTRIBUTE_VALUE
    ex.propertyPath() == "/data/attributes/title"
  }

  def "collection primary data composes the element index"() {
    given:
    def reader = newReader(LocationArticle)

    when:
    reader.readValue(
        '{"data":[' +
        '{"type":"loc-articles","id":"1","attributes":{"title":"1"}},' +
        '{"type":"loc-articles","id":"2","attributes":{"title":"oops"}}]}')

    then:
    def ex = thrown(JsonApiMappingException)
    ex.diagnostic() == MappingDiagnostic.UNSUPPORTED_ATTRIBUTE_VALUE
    ex.propertyPath() == "/data/1/attributes/title"
  }

  def "included resources compose the included index"() {
    given:
    def reader = newReader(LocationArticle)
    // fromDocument skips aggregate validation, so the included element can be reached directly.
    def document = new JsonApiDocument(
        new DocumentData.SingleResource(ResourceObject.of("loc-articles", "1")),
        null,
        null,
        null,
        null,
        List.of(
        new ResourceObject(
        "loc-articles",
        "9",
        null,
        Attributes.ofAttributes(Map.of("title", "oops")),
        null,
        null,
        null,
        Map.of())),
        Map.of())

    when:
    reader.fromDocument(document)

    then:
    def ex = thrown(JsonApiMappingException)
    ex.diagnostic() == MappingDiagnostic.UNSUPPORTED_ATTRIBUTE_VALUE
    ex.propertyPath() == "/included/0/attributes/title"
  }

  def "nested resource-local locations compose deeply under the document prefix"() {
    given:
    def reader = newReader(NestedLocationArticle)

    when:
    reader.readValue(
        '{"data":{"type":"loc-nested","id":"1","attributes":{"address":{"city":"oops"}}}}')

    then:
    def ex = thrown(JsonApiMappingException)
    ex.diagnostic() == MappingDiagnostic.UNSUPPORTED_ATTRIBUTE_VALUE
    ex.propertyPath() == "/data/attributes/address/city"
  }

  def "renamed wire members report the JSON:API name, never the logical name"() {
    given:
    def reader = newReader(RenamedLocationArticle)

    when:
    reader.readValue(
        '{"data":{"type":"loc-renamed","id":"1","attributes":{"headline":"oops"}}}')

    then:
    def ex = thrown(JsonApiMappingException)
    ex.diagnostic() == MappingDiagnostic.UNSUPPORTED_ATTRIBUTE_VALUE
    // Wire coordinate headline; the Jackson/logical property name title must not leak.
    ex.propertyPath() == "/data/attributes/headline"
  }

  def "locationless binder failures report only the document prefix"() {
    given:
    def reader = newReader(FlatThrowingArticle)
    // fromDocument skips aggregate validation, so the included element can be reached directly.
    def document = new JsonApiDocument(
        new DocumentData.SingleResource(ResourceObject.of("throwing-articles", "1")),
        null,
        null,
        null,
        null,
        List.of(
        new ResourceObject(
        "throwing-articles",
        "2",
        null,
        Attributes.ofAttributes(Map.of("title", "boom")),
        null,
        null,
        null,
        Map.of())),
        Map.of())

    when:
    reader.fromDocument(document)

    then:
    def ex = thrown(JsonApiMappingException)
    ex.diagnostic() == MappingDiagnostic.MISSING_CREATOR_INPUT
    // No member location exists on the failure path; composition keeps the meaningful document
    // location instead of inventing one.
    ex.location().pointer() == "/included/0"
  }

  def "registry declaration failures carry no member location"() {
    when:
    ResourceTypeRegistry.builder(JsonMapper.builder().build())
        .register(FlatArticle)
        .register(FlatNullableIdArticle)
        .build()

    then:
    def conflicting = thrown(JsonApiMappingException)
    conflicting.diagnostic() == MappingDiagnostic.CONFLICTING_TYPE_REGISTRATION
    conflicting.location() == null

    when:
    ResourceTypeRegistry.builder(JsonMapper.builder().build()).register(Object.class).build()

    then:
    def missing = thrown(JsonApiMappingException)
    missing.diagnostic() == MappingDiagnostic.MISSING_RESOURCE_ANNOTATION
    missing.location() == null
  }

  @Unroll
  def "registry rejects #resourceClass with an invalid resource type"() {
    when:
    ResourceTypeRegistry.builder(JsonMapper.builder().build()).register(resourceClass).build()

    then:
    def exception = thrown(JsonApiMappingException)
    exception.diagnostic() == MappingDiagnostic.INVALID_RESOURCE_TYPE
    exception.resourceClass() == resourceClass
    exception.location() == null

    where:
    resourceClass << [
      EmptyResourceType,
      InvalidResourceType
    ]
  }

  def "domain reader checks an unused registration eagerly"() {
    given:
    def base = JsonMapper.builder().build()
    def overrideMapper = JsonMapper.builder()
        .addMixIn(FlatArticle, OverrideArticlesMixin)
        .build()
    def registry = ResourceTypeRegistry.builder(base)
        .register(FlatArticle)
        .register(Person)
        .build()

    when:
    JsonApiJackson3.domainDocumentReader(
        overrideMapper, DocumentReadContext.resourceDefaults(), registry)

    then:
    def ex = thrown(JsonApiMappingException)
    ex.diagnostic() == MappingDiagnostic.RESOURCE_TYPE_MISMATCH
    ex.resourceClass() == FlatArticle
    ex.location() == null
    ex.message.contains("articles")
    ex.message.contains("override-articles")
  }

  def "all domain reader factory forms enforce registry coherence"() {
    given:
    def base = JsonMapper.builder().build()
    def overrideMapper = JsonMapper.builder()
        .addMixIn(FlatArticle, OverrideArticlesMixin)
        .build()
    def registry = ResourceTypeRegistry.builder(base)
        .register(FlatArticle)
        .build()

    when:
    JsonApiJackson3.domainDocumentReader(
        overrideMapper, DocumentReadContext.resourceDefaults(), registry)

    then:
    thrown(JsonApiMappingException)

    when:
    JsonApiJackson3.domainDocumentReader(
        overrideMapper,
        DocumentReadContext.resourceDefaults(),
        registry,
        IdentifierConverter.defaults())

    then:
    thrown(JsonApiMappingException)

    when:
    JsonApiJackson3.domainDocumentReader(
        overrideMapper,
        DocumentReadContext.resourceDefaults(),
        registry,
        IdentifierConverter.defaults(),
        Map.of())

    then:
    def ex = thrown(JsonApiMappingException)
    ex.diagnostic() == MappingDiagnostic.RESOURCE_TYPE_MISMATCH
    ex.resourceClass() == FlatArticle
    ex.location() == null
  }

  def "empty registries stay legal for construction"() {
    given:
    def mapper = JsonMapper.builder().build()
    def registry = ResourceTypeRegistry.builder(mapper).build()

    when:
    def reader = JsonApiJackson3.domainDocumentReader(
        mapper, DocumentReadContext.resourceDefaults(), registry)

    then:
    noExceptionThrown()
    reader != null
  }

  def "JavaType registrations keep their binding target under an equivalent mapper"() {
    given:
    def registryMapper = JsonMapper.builder().build()
    def readerMapper = JsonMapper.builder().build()
    def registry = ResourceTypeRegistry.builder(registryMapper)
        .register(registryMapper.constructType(FlatArticle))
        .register(Person)
        .build()
    def reader = JsonApiJackson3.domainDocumentReader(
        readerMapper, DocumentReadContext.resourceDefaults(), registry)

    when:
    def envelope = reader.readValue(corpusText('envelope-binding/heterogeneous-collection.json'))

    then:
    ((DomainData.ResourceCollection) envelope.data()).resources() ==
        [
          new FlatArticle("1", "First", null, null, null),
          new Person("9", "Dan")
        ]
  }

  def "unrelated property invalidity stays deferred until binding"() {
    given:
    def mapper = JsonMapper.builder().build()
    def registry = ResourceTypeRegistry.builder(mapper)
        .register(FlatThrowingArticle)
        .build()

    when:
    def reader = JsonApiJackson3.domainDocumentReader(
        mapper, DocumentReadContext.resourceDefaults(), registry)

    then:
    noExceptionThrown()

    when:
    reader.fromDocument(
        new JsonApiDocument(
        new DocumentData.SingleResource(
        new ResourceObject(
        "throwing-articles",
        "1",
        null,
        Attributes.ofAttributes(Map.of("title", "boom")),
        null,
        null,
        null,
        Map.of())),
        null,
        null,
        null,
        null,
        null,
        Map.of()))

    then:
    def ex = thrown(JsonApiMappingException)
    ex.diagnostic() == MappingDiagnostic.MISSING_CREATOR_INPUT
  }

  @JsonApiResource(type = "override-articles")
  interface OverrideArticlesMixin {}

  @JsonApiResource(type = "loc-articles")
  static class LocationArticle {
    @JsonApiId String id
    @JsonApiAttribute int title
  }

  @JsonApiResource(type = "loc-renamed")
  static class RenamedLocationArticle {
    @JsonApiId String id
    @JsonApiAttribute @JsonProperty("headline") int title
  }

  static class NestedAddress {
    int city
  }

  @JsonApiResource(type = "loc-nested")
  static class NestedLocationArticle {
    @JsonApiId String id
    @JsonApiAttribute NestedAddress address
  }

  private static String corpusText(String path) {
    TestFixtureResources.readCorpusUtf8(path)
  }


  private static List<Object> includedResources(JsonApiDomainDocument envelope) {
    envelope.included() == null ? null : envelope.included().resources()
  }

  private static JsonApiDocument readCoreDocument(
      String path, String exemptedType, String exemptedId) {
    def context =
        DocumentReadContext.of(
        ValidationContext.defaults().withSparseFieldsetLinkageExemptions(
        Set.of(ResourceIdentity.ofId(exemptedType, exemptedId))),
        PrimaryDataKind.RESOURCE)
    JsonApiJackson3.reader(JsonMapper.builder().build(), context).readValue(corpusText(path))
  }

  private static JsonApiDomainDocumentReader domainReader(
      List<Class<?>> targetClasses, DocumentReadContext context) {
    JsonApiJackson3.domainDocumentReader(
        JsonMapper.builder().build(), context, registry(targetClasses))
  }

  private static ResourceTypeRegistry registry(List<Class<?>> targetClasses) {
    def builder = ResourceTypeRegistry.builder(JsonMapper.builder().build())
    for (Class<?> target : targetClasses) {
      builder.register(target)
    }
    builder.build()
  }

  private static ResourceTypeRegistry registry(Class<?>... targetClasses) {
    def builder = ResourceTypeRegistry.builder(JsonMapper.builder().build())
    for (Class<?> target : targetClasses) {
      builder.register(target)
    }
    builder.build()
  }

  private static JsonApiDomainDocumentReader newReader(Class<?>... targetClasses) {
    JsonApiJackson3.domainDocumentReader(
        JsonMapper.builder().build(), DocumentReadContext.resourceDefaults(), registry(targetClasses))
  }

  static class MetaPayload {
    final int count

    MetaPayload(int count) {
      this.count = count
    }

    boolean equals(Object other) {
      other instanceof MetaPayload && ((MetaPayload) other).count == count
    }

    int hashCode() {
      count
    }
  }

  static class CountValueDeserializer extends StdDeserializer<MetaPayload> {
    CountValueDeserializer() {
      super(MetaPayload)
    }

    @Override
    MetaPayload deserialize(JsonParser parser, DeserializationContext context) {
      if (parser.currentToken() == JsonToken.START_OBJECT) {
        parser.nextToken()
      }
      if (parser.currentToken() == JsonToken.PROPERTY_NAME) {
        parser.nextToken()
      }
      new MetaPayload(parser.getIntValue())
    }
  }

  static class CloseTrackingInputStream extends FilterInputStream {
    boolean closed = false

    CloseTrackingInputStream(InputStream delegate) {
      super(delegate)
    }

    @Override
    void close() {
      closed = true
      super.close()
    }
  }
}
