package com.kazforge.jsonapi.jackson2

import com.kazforge.jsonapi.jackson2.mapping.RelationshipLinkageMapper

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JavaType
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.databind.module.SimpleModule
import com.kazforge.jsonapi.annotation.JsonApiResource
import com.kazforge.jsonapi.core.model.RelationshipData
import com.kazforge.jsonapi.core.model.ResourceIdentifier
import com.kazforge.jsonapi.core.validation.ValidationRuleCode
import com.kazforge.jsonapi.diagnostic.CodecFailureCategory
import com.kazforge.jsonapi.diagnostic.JsonApiDocumentReadException
import com.kazforge.jsonapi.diagnostic.JsonApiMappingException
import com.kazforge.jsonapi.diagnostic.MappingDiagnostic
import com.kazforge.jsonapi.document.DocumentReadContext
import com.kazforge.jsonapi.fixtures.TestFixtureResources
import com.kazforge.jsonapi.fixtures.domainread.FlatArticle
import com.kazforge.jsonapi.fixtures.domainwrite.Comment
import com.kazforge.jsonapi.fixtures.domainwrite.Person
import com.kazforge.jsonapi.jackson2.LinkageMapperFixtures.FlatAuthor
import com.kazforge.jsonapi.jackson2.LinkageMapperFixtures.FlatMappedArticle
import com.kazforge.jsonapi.jackson2.ParameterizedBindingFixtures.GenericValue
import com.kazforge.jsonapi.mapping.DomainData
import com.kazforge.jsonapi.mapping.IdentifierConverter
import com.kazforge.jsonapi.mapping.ResourceTypeRegistry
import spock.lang.Specification
import spock.lang.Unroll

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
    envelope.additionalMembers() == Map.of()

    where:
    path                                    | resourceTypes         | context                                | expectedData                                                                                                                                                                                                                       | expectedIncludedResources
    'envelope-binding/single-resource.json' | [FlatArticle]         | DocumentReadContext.resourceDefaults() | new DomainData.SingleResource(new FlatArticle('1', 'JSON:API paints my bikeshed!', 'Content', ResourceIdentifier.of('people', 'p1'), [
      ResourceIdentifier.of('comments', 'c1')
    ]))                         | null
    'documents/resource-collection.json'    | [FlatArticle]         | DocumentReadContext.resourceDefaults() | new DomainData.ResourceCollection([
      new FlatArticle('1', 'First', null, null, null),
      new FlatArticle('2', 'Second', null, null, null)
    ])                                                                                            | null
    'documents/compound-document.json'      | [FlatArticle, Person] | DocumentReadContext.resourceDefaults() | new DomainData.SingleResource(new FlatArticle('1', null, null, ResourceIdentifier.of('people', '9'), null))                                                                                                                        | [new Person('9', 'Dan')]
  }

  def "metaAs returns null through the JavaType overload when meta is absent"() {
    given:
    def reader = newReader(FlatArticle)
    def javaType = JsonMapper.builder().build().constructType(MetaPayload)

    when:
    def envelope = reader.readValue(corpusText('envelope-binding/single-resource.json'))

    then:
    envelope.metaAs(javaType) == null
  }

  def "metaAs converts via the caller-mapper module on both entry paths and both overloads"() {
    given:
    def module = new SimpleModule()
    module.addDeserializer(MetaPayload, new CountValueDeserializer())
    def base = JsonMapper.builder().addModule(module).build()
    def reader = JsonApiJackson2.domainDocumentReader(
        base, DocumentReadContext.resourceDefaults(), registry())
    def json = '{"meta":{"count":3}}'
    def payloadType = base.constructType(MetaPayload)

    when:
    def fromRead = reader.readValue(json)
    def document = JsonApiJackson2.reader(base, DocumentReadContext.resourceDefaults()).readValue(json)
    def fromDocument = reader.fromDocument(document)

    then:
    fromRead.metaAs(MetaPayload) == new MetaPayload(3)
    fromRead.metaAs(payloadType) == new MetaPayload(3)
    fromDocument.metaAs(MetaPayload) == new MetaPayload(3)
    fromDocument.metaAs(payloadType) == new MetaPayload(3)
  }

  def "JavaType registrations bind through the same registry gate"() {
    given:
    def base = JsonMapper.builder().build()
    def registry = ResourceTypeRegistry.builder()
        .register("articles", base.constructType(FlatArticle))
        .register("people", Person)
        .build()
    def reader = JsonApiJackson2.domainDocumentReader(
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

  def "parameterized reflection Type registrations preserve generic envelope binding"() {
    given:
    def targetType = new TypeReference<GenericValue<String>>() {}.getType()
    def registry = ResourceTypeRegistry.builder()
        .register("things", targetType)
        .build()
    def reader = JsonApiJackson2.domainDocumentReader(
        JsonMapper.builder().build(), DocumentReadContext.resourceDefaults(), registry)

    when:
    def envelope = reader.readValue(
        '{"data":{"type":"things","id":"1","attributes":{"value":"text"}}}')

    then:
    def value = ((DomainData.SingleResource) envelope.data()).resource()
    value instanceof GenericValue
    value.value() == "text"
  }

  def "mapper-instance domainDocumentReader overloads bind identically"() {
    given:
    def mapper = JsonMapper.builder().build()
    def registry = ResourceTypeRegistry.builder()
        .register("articles", FlatArticle)
        .build()
    def threeArg = JsonApiJackson2.domainDocumentReader(
        mapper, DocumentReadContext.resourceDefaults(), registry)
    def fourArg = JsonApiJackson2.domainDocumentReader(
        mapper, DocumentReadContext.resourceDefaults(), registry, IdentifierConverter.defaults())
    def fiveArg = JsonApiJackson2.domainDocumentReader(
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
    def reader = JsonApiJackson2.domainDocumentReader(
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

  def "all domain reader factory forms enforce registry coherence"() {
    given:
    def overrideMapper = JsonMapper.builder()
        .addMixIn(FlatArticle, OverrideArticlesMixin)
        .build()
    def registry = ResourceTypeRegistry.builder()
        .register("articles", FlatArticle)
        .build()

    when:
    JsonApiJackson2.domainDocumentReader(
        overrideMapper, DocumentReadContext.resourceDefaults(), registry)

    then:
    def threeArg = thrown(JsonApiMappingException)
    threeArg.diagnostic() == MappingDiagnostic.RESOURCE_TYPE_MISMATCH
    threeArg.resourceClass() == FlatArticle
    threeArg.location() == null
    threeArg.message.contains("articles")
    threeArg.message.contains("override-articles")

    when:
    JsonApiJackson2.domainDocumentReader(
        overrideMapper,
        DocumentReadContext.resourceDefaults(),
        registry,
        IdentifierConverter.defaults())

    then:
    thrown(JsonApiMappingException)

    when:
    JsonApiJackson2.domainDocumentReader(
        overrideMapper,
        DocumentReadContext.resourceDefaults(),
        registry,
        IdentifierConverter.defaults(),
        Map.of())

    then:
    def fiveArg = thrown(JsonApiMappingException)
    fiveArg.diagnostic() == MappingDiagnostic.RESOURCE_TYPE_MISMATCH
    fiveArg.resourceClass() == FlatArticle
    fiveArg.location() == null
  }

  def "JavaType registrations keep their binding target under an equivalent mapper"() {
    given:
    def registryMapper = JsonMapper.builder().build()
    def readerMapper = JsonMapper.builder().build()
    def registry = ResourceTypeRegistry.builder()
        .register("articles", registryMapper.constructType(FlatArticle))
        .register("people", Person)
        .build()
    def reader = JsonApiJackson2.domainDocumentReader(
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

  @JsonApiResource(type = "override-articles")
  interface OverrideArticlesMixin {}

  private static String corpusText(String path) {
    TestFixtureResources.readCorpusUtf8(path)
  }

  private static List<Object> includedResources(JsonApiDomainDocument envelope) {
    envelope.included() == null ? null : envelope.included().resources()
  }

  private static JsonApiDomainDocumentReader domainReader(
      List<Class<?>> targetClasses, DocumentReadContext context) {
    JsonApiJackson2.domainDocumentReader(
        JsonMapper.builder().build(), context, registry(targetClasses))
  }

  private static ResourceTypeRegistry registry(List<Class<?>> targetClasses) {
    def builder = ResourceTypeRegistry.builder()
    for (Class<?> target : targetClasses) {
      builder.register(registryType(target), target)
    }
    builder.build()
  }

  private static ResourceTypeRegistry registry(Class<?>... targetClasses) {
    registry(targetClasses.toList())
  }

  private static String registryType(Class<?> target) {
    target.getAnnotation(JsonApiResource).type()
  }

  private static JsonApiDomainDocumentReader newReader(Class<?>... targetClasses) {
    JsonApiJackson2.domainDocumentReader(
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
      if (parser.currentToken() == JsonToken.FIELD_NAME) {
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
