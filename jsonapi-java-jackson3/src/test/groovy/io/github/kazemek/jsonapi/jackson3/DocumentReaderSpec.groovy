package io.github.kazemek.jsonapi.jackson3

import java.nio.charset.StandardCharsets

import tools.jackson.core.JacksonException
import tools.jackson.core.JsonParser
import tools.jackson.core.JsonToken
import tools.jackson.core.TokenStreamLocation
import tools.jackson.core.util.JsonParserDelegate
import tools.jackson.databind.json.JsonMapper

import io.github.kazemek.jsonapi.core.model.Attributes
import io.github.kazemek.jsonapi.core.model.DocumentData
import io.github.kazemek.jsonapi.core.model.ErrorObject
import io.github.kazemek.jsonapi.core.model.ErrorSource
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
import io.github.kazemek.jsonapi.core.validation.ValidationRuleCode
import io.github.kazemek.jsonapi.jackson.diagnostic.CodecFailureCategory
import io.github.kazemek.jsonapi.jackson.diagnostic.JsonApiDocumentReadException
import io.github.kazemek.jsonapi.jackson.document.DocumentReadContext
import io.github.kazemek.jsonapi.jackson.document.PrimaryDataKind
import io.github.kazemek.jsonapi.fixtures.TestFixtureResources

import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

class DocumentReaderSpec extends Specification {

  @Shared
  JsonMapper mapper = JsonMapper.builder().build()

  @Shared
  DocumentReadContext resourceContext = DocumentReadContext.resourceDefaults()

  def "reads fixture #description into a document that matches the constructed model"() {
    given:
    def json = readCorpusText(resourcePath)
    def reader = JsonApiJackson3.reader(
        mapper, DocumentReadContext.of(validationContext, primaryDataKind))

    when:
    def document = reader.readValue(json)

    then:
    document == expected

    where:
    description                                                     | resourcePath                                          | validationContext            | primaryDataKind                     | expected
    'Single resource primary data'                                  | 'documents/single-resource.json'                      | ValidationContext.defaults() | PrimaryDataKind.RESOURCE            | singleResourceDocument()
    'Resource collection primary data'                              | 'documents/resource-collection.json'                  | ValidationContext.defaults() | PrimaryDataKind.RESOURCE            | resourceCollectionDocument()
    'Single resource identifier primary data'                       | 'documents/single-identifier.json'                    | ValidationContext.defaults() | PrimaryDataKind.RESOURCE_IDENTIFIER | singleIdentifierDocument()
    'Identifier collection primary data'                            | 'documents/identifier-collection.json'                | ValidationContext.defaults() | PrimaryDataKind.RESOURCE_IDENTIFIER | identifierCollectionDocument()
    'Explicit data null with meta'                                  | 'documents/null-data.json'                            | ValidationContext.defaults() | PrimaryDataKind.RESOURCE            | nullDataDocument()
    'Absent data; meta-only document'                               | 'documents/meta-only.json'                            | ValidationContext.defaults() | PrimaryDataKind.RESOURCE            | metaOnlyDocument()
    'Empty primary data array'                                      | 'documents/empty-identifier-collection.json'          | ValidationContext.defaults() | PrimaryDataKind.RESOURCE_IDENTIFIER | emptyIdentifierCollectionDocument()
    'Present-empty attributes, relationships, links, meta'          | 'documents/empty-wrappers.json'                       | ValidationContext.defaults() | PrimaryDataKind.RESOURCE            | emptyWrappersDocument()
    'Present-empty errors array'                                    | 'documents/empty-errors.json'                         | ValidationContext.defaults() | PrimaryDataKind.RESOURCE            | emptyErrorsDocument()
    'Present-empty included array with primary data'                | 'documents/empty-included.json'                       | ValidationContext.defaults() | PrimaryDataKind.RESOURCE            | emptyIncludedDocument()
    'Explicit null to-one relationship data'                        | 'documents/relationship-null-linkage.json'            | ValidationContext.defaults() | PrimaryDataKind.RESOURCE            | relationshipNullLinkageDocument()
    'Empty to-many relationship data array'                         | 'documents/relationship-empty-to-many.json'           | ValidationContext.defaults() | PrimaryDataKind.RESOURCE            | relationshipEmptyToManyDocument()
    'Link-only relationship without data'                           | 'documents/relationship-link-only.json'               | ValidationContext.defaults() | PrimaryDataKind.RESOURCE            | relationshipLinkOnlyDocument()
    'Meta-only relationship without data'                           | 'documents/relationship-meta-only.json'               | ValidationContext.defaults() | PrimaryDataKind.RESOURCE            | relationshipMetaOnlyDocument()
    'String link, object link, null link, canonical hreflang array' | 'documents/string-and-object-links.json'              | ValidationContext.defaults() | PrimaryDataKind.RESOURCE            | stringAndObjectLinksDocument()
    'Top-level errors with source and links'                        | 'documents/errors-document.json'                      | ValidationContext.defaults() | PrimaryDataKind.RESOURCE            | errorsDocument()
    'jsonapi version, ext, profile, and meta'                       | 'documents/jsonapi-object.json'                       | extContext()                 | PrimaryDataKind.RESOURCE            | jsonApiObjectDocument()
    'Compound document with included resources'                     | 'documents/compound-document.json'                    | ValidationContext.defaults() | PrimaryDataKind.RESOURCE            | compoundDocument()
    'Compound document with nested comments.author intermediates'   | 'documents/compound-nested-intermediate.json'         | ValidationContext.defaults() | PrimaryDataKind.RESOURCE            | compoundNestedIntermediateDocument()
    'Compound collection sharing one included author identity'      | 'documents/compound-shared-identity.json'             | ValidationContext.defaults() | PrimaryDataKind.RESOURCE            | compoundSharedIdentityDocument()
    'Resource and linkage with lid'                                 | 'documents/local-identifier.json'                     | createContext()              | PrimaryDataKind.RESOURCE            | localIdentifierDocument()
    'Extension and @ members on document and resource'              | 'documents/extension-and-at-members.json'             | extContext()                 | PrimaryDataKind.RESOURCE            | extensionAndAtMembersDocument()
    'Canonical standard member order then additional members'       | 'documents/member-order.json'                         | extContext()                 | PrimaryDataKind.RESOURCE            | memberOrderDocument()
  }

  def "open values decode numbers to faithful Java types"() {
    given:
    def json = readCorpusText('documents/open-values.json')
    def reader = JsonApiJackson3.reader(mapper, resourceContext)

    when:
    def document = reader.readValue(json)

    then:
    def resource = (document.data() as DocumentData.SingleResource).resource()
    def attributes = resource.attributes().attributes()
    def meta = document.meta().members()

    attributes.get('nullable') == null
    def nested = attributes.get('nested') as Map
    nested.get('tags') == ['a', 'b']
    def counts = nested.get('counts') as Map
    counts.get('views') == 2
    counts.get('views').getClass() == Integer

    attributes.get('intValue') == 42
    attributes.get('intValue').getClass() == Integer
    attributes.get('longValue') == 9007199254740991L
    attributes.get('longValue').getClass() == Long
    attributes.get('floatValue') == 1.5d
    attributes.get('floatValue').getClass() == Double
    attributes.get('doubleValue') == 2.25d
    attributes.get('doubleValue').getClass() == Double
    attributes.get('bigIntValue') == new BigInteger('123456789012345678901234567890')
    attributes.get('bigIntValue').getClass() == BigInteger
    attributes.get('bigDecimalValue') == 1234567890.123456789d
    attributes.get('bigDecimalValue').getClass() == Double

    meta.get('flag') == true
    meta.get('flag').getClass() == Boolean
    meta.get('nullMeta') == null
  }

  def "object link describedby decodes string and recursive object link forms while preserving omission"() {
    given:
    def reader = JsonApiJackson3.reader(
        mapper, DocumentReadContext.of(extContext(), PrimaryDataKind.RESOURCE))
    def json = '''
      {
        "meta": {},
        "links": {
          "ext:string-description": {
            "href": "https://example.test/articles/1",
            "describedby": "https://example.test/schemas/article"
          },
          "ext:object-description": {
            "href": "https://example.test/articles/2",
            "describedby": {
              "href": "https://example.test/schemas/article",
              "describedby": "https://example.test/schemas/article-v1",
              "type": "application/schema+json",
              "meta": {"revision": 2}
            }
          },
          "ext:no-description": {"href": "https://example.test/articles/3"}
        }
      }
      '''

    when:
    def document = reader.readValue(json)
    def links = document.links().links()

    then:
    ((Link.ObjectLink) links.get('ext:string-description')).describedby() ==
        new Link.StringLink('https://example.test/schemas/article')
    ((Link.ObjectLink) links.get('ext:object-description')).describedby() == new Link.ObjectLink(
        'https://example.test/schemas/article',
        null,
        new Link.StringLink('https://example.test/schemas/article-v1'),
        null,
        'application/schema+json',
        null,
        Meta.of([revision: 2]),
        Map.of())
    ((Link.ObjectLink) links.get('ext:no-description')).describedby() == null
  }

  def "pass-through members inside attributes, relationships, and relationship objects decode into additional members"() {
    given:
    def json = '''
      {
        "data": {
          "type": "articles",
          "id": "1",
          "attributes": {"title": "Hello", "ext:note": 1, "@flag": true},
          "relationships": {
            "author": {
              "data": {"type": "people", "id": "9"},
              "ext:rel": "r"
            },
            "ext:peer": {"a": 1}
          }
        }
      }
      '''
    def context = DocumentReadContext.of(extContext(), PrimaryDataKind.RESOURCE)

    when:
    def document = JsonApiJackson3.reader(mapper, context).readValue(json)
    def resource = (document.data() as DocumentData.SingleResource).resource()

    then:
    resource.attributes().attributes() == [title: 'Hello']
    resource.attributes().additionalMembers() == ['ext:note': 1, '@flag': true]
    def author = resource.relationships().relationships().get('author')
    author.data() == new RelationshipData.SingleLinkage(ResourceIdentifier.of('people', '9'))
    author.additionalMembers() == ['ext:rel': 'r']
    resource.relationships().additionalMembers() == ['ext:peer': [a: 1]]
  }

  def "all read sources decode one representative document equivalently"() {
    given:
    def json = readCorpusText('documents/single-resource.json')
    def reader = JsonApiJackson3.reader(mapper, resourceContext)
    def expected = reader.readValue(json)
    def tracking = new CloseTrackingInputStream(
        new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)))
    def parser = reader.mapper().createParser(json)

    when:
    def fromBytes = reader.readValue(json.getBytes(StandardCharsets.UTF_8))
    def fromStream = reader.readValue(tracking)
    def fromParser = reader.readValue(parser)

    then:
    fromBytes == expected
    fromStream == expected
    fromParser == expected
    !tracking.closed
    !parser.closed

    cleanup:
    parser?.close()
  }

  @Unroll
  def "locationless malformed parser failure #position falls back to the parser cursor"() {
    given:
    def delegate = mapper.createParser('{"meta":{}}')
    if (insideDocument) {
      delegate.nextToken()
    }
    def parser = new MissingLocationParser(delegate)
    def expectedLocation = parser.currentLocation()

    when:
    JsonApiJackson3.reader(mapper, resourceContext).readValue(parser)

    then:
    def ex = thrown(JsonApiDocumentReadException)
    ex.category() == CodecFailureCategory.MALFORMED_JSON
    ex.jsonPointer() == ''
    ex.ruleCode() == null
    ex.sourceLocation().isKnown()
    ex.sourceLocation().lineNumber() == expectedLocation.getLineNr()
    ex.sourceLocation().columnNumber() == expectedLocation.getColumnNr()
    ex.sourceLocation().charOffset() == expectedLocation.getCharOffset()
    ex.sourceLocation().byteOffset() == expectedLocation.getByteOffset()
    ex.message == 'Malformed JSON'
    ex.cause == null

    cleanup:
    parser?.close()

    where:
    position               | insideDocument
    'before document read' | false
    'inside document read' | true
  }

  def "ambiguous case #description decodes under both PrimaryDataKind values"() {
    given:
    def json = readCorpusText(resourcePath)

    when:
    def asResource = JsonApiJackson3.reader(
        mapper, DocumentReadContext.of(ValidationContext.defaults(), PrimaryDataKind.RESOURCE))
        .readValue(json)
    def asIdentifier = JsonApiJackson3.reader(
        mapper, DocumentReadContext.of(ValidationContext.defaults(), PrimaryDataKind.RESOURCE_IDENTIFIER))
        .readValue(json)

    then:
    asResource == resourceExpected
    asIdentifier == identifierExpected

    where:
    description                | resourcePath                                        | resourceExpected                                                                                                                                                                                            | identifierExpected
    'object primary data'      | 'documents/ambiguous-object-primary-data.json'      | JsonApiDocument.withData(new DocumentData.SingleResource(new ResourceObject('articles', '1', null, null, null, null, null, Map.of())))                                                                      | JsonApiDocument.withData(new DocumentData.SingleIdentifier(ResourceIdentifier.of('articles', '1')))
    'empty-array primary data' | 'documents/ambiguous-empty-array-primary-data.json' | JsonApiDocument.withData(new DocumentData.ResourceCollection(List.of()))                                                                                                                                    | JsonApiDocument.withData(new DocumentData.IdentifierCollection(List.of()))
  }

  def "negative corpus case #description fails with the documented diagnostics"() {
    given:
    def json = readCorpusText(resourcePath)
    def reader = JsonApiJackson3.reader(mapper, resourceContext)

    when:
    reader.readValue(json)

    then:
    def ex = thrown(JsonApiDocumentReadException)
    ex.category() == category
    ex.jsonPointer() == pointer
    ex.ruleCode() == ruleCode
    ex.sourceLocation().isKnown()
    ex.cause == null
    !ex.message.contains(json)

    where:
    description                                                                       | resourcePath                                            | category                                 | pointer                              | ruleCode
    'Unterminated object; diagnostics must not echo the source text'                  | 'negative/malformed-json-without-payload.json'          | CodecFailureCategory.MALFORMED_JSON      | ''                                   | null
    'End-of-input inside primary data; pointer captures the enclosing path'           | 'negative/truncated-document-enclosing-path.json'       | CodecFailureCategory.MALFORMED_JSON      | '/data'                              | null
    'Whole-input reads reject content after the document'                             | 'negative/trailing-content-after-document.json'         | CodecFailureCategory.UNEXPECTED_TOKEN    | ''                                   | null
    'Wrong root token reports path and location'                                      | 'negative/unexpected-token-path-location.json'          | CodecFailureCategory.UNEXPECTED_TOKEN    | ''                                   | null
    'Repeated object member is rejected'                                              | 'negative/duplicate-members.json'                       | CodecFailureCategory.DUPLICATE_MEMBER    | '/meta'                              | null
    'Core constructor failure exposes a rule code and top-level path'                 | 'negative/local-validation-top-level.json'              | CodecFailureCategory.LOCAL_VALIDATION    | '/data/type'                         | ValidationRuleCode.MISSING_RESOURCE_TYPE
    'Included resource without type reports a nested pointer'                         | 'negative/included-missing-type.json'                   | CodecFailureCategory.LOCAL_VALIDATION    | '/included/0/type'                   | ValidationRuleCode.MISSING_RESOURCE_TYPE
    'Collection element without type reports an indexed pointer'                      | 'negative/collection-missing-type.json'                 | CodecFailureCategory.LOCAL_VALIDATION    | '/data/0/type'                       | ValidationRuleCode.MISSING_RESOURCE_TYPE
    'Relationship identifier without type reports a nested pointer'                   | 'negative/relationship-identifier-missing-type.json'    | CodecFailureCategory.LOCAL_VALIDATION    | '/data/relationships/author/data/type' | ValidationRuleCode.MISSING_RESOURCE_TYPE
    'Reserved member name inside attributes'                                          | 'negative/reserved-attribute.json'                      | CodecFailureCategory.LOCAL_VALIDATION    | '/data/attributes/type'              | ValidationRuleCode.RESERVED_FIELD_NAME
    'Object-form link without href'                                                   | 'negative/missing-link-href.json'                       | CodecFailureCategory.LOCAL_VALIDATION    | '/data/links/self/href'              | ValidationRuleCode.NULL_REQUIRED_VALUE
    'Dynamic link relation escapes pointer segments'                                  | 'negative/invalid-dynamic-link-relation.json'           | CodecFailureCategory.LOCAL_VALIDATION    | '/links/foo~0bar~1baz'               | ValidationRuleCode.INVALID_LINK_RELATION
    'Dynamic attribute name escapes pointer segments'                                 | 'negative/invalid-dynamic-attribute-name.json'          | CodecFailureCategory.LOCAL_VALIDATION    | '/data/attributes/foo~0bar~1baz'     | ValidationRuleCode.INVALID_MEMBER_NAME
    'Aggregate link-context failure escapes pointer segments'                         | 'negative/aggregate-uri-link-relation.json'             | CodecFailureCategory.AGGREGATE_VALIDATION | '/links/http:~1~1example.com~1rel'  | ValidationRuleCode.INVALID_LINKS_CONTEXT
    'Aggregate failure locates the offending resource; Jackson 3 also asserts line 4' | 'negative/aggregate-validation-resource-location.json'  | CodecFailureCategory.AGGREGATE_VALIDATION | '/data/1'                           | ValidationRuleCode.DUPLICATE_RESOURCE_IDENTITY
    'Valid extension document fails under a context without the extension namespace'  | 'documents/extension-and-at-members.json'               | CodecFailureCategory.AGGREGATE_VALIDATION | '/ext:request-id'                   | ValidationRuleCode.DISALLOWED_ADDITIONAL_MEMBER
  }

  @Unroll
  def "empty String input #description reports MALFORMED_JSON"() {
    when:
    JsonApiJackson3.reader(mapper, resourceContext).readValue(input)

    then:
    def ex = thrown(JsonApiDocumentReadException)
    ex.category() == CodecFailureCategory.MALFORMED_JSON
    ex.jsonPointer() == ''
    ex.message == 'Expected a JSON:API document object'

    where:
    description       | input
    'empty'           | ''
    'whitespace-only' | '   '
  }

  @Unroll
  def "empty byte input #description reports MALFORMED_JSON"() {
    when:
    JsonApiJackson3.reader(mapper, resourceContext).readValue(input)

    then:
    def ex = thrown(JsonApiDocumentReadException)
    ex.category() == CodecFailureCategory.MALFORMED_JSON
    ex.jsonPointer() == ''
    ex.message == 'Expected a JSON:API document object'

    where:
    description       | input
    'empty'           | ''.getBytes(StandardCharsets.UTF_8)
    'whitespace-only' | '  '.getBytes(StandardCharsets.UTF_8)
  }

  def "empty corpus file input reports MALFORMED_JSON"() {
    given:
    def json = readCorpusText('negative/empty-input.json')

    when:
    JsonApiJackson3.reader(mapper, resourceContext).readValue(json)

    then:
    def ex = thrown(JsonApiDocumentReadException)
    ex.category() == CodecFailureCategory.MALFORMED_JSON
    ex.jsonPointer() == ''
    ex.message == 'Expected a JSON:API document object'
    ex.cause == null
  }

  def "aggregate validation resource location is precise on Jackson 3"() {
    given:
    def json = readCorpusText('negative/aggregate-validation-resource-location.json')

    when:
    JsonApiJackson3.reader(mapper, resourceContext).readValue(json)

    then:
    def ex = thrown(JsonApiDocumentReadException)
    ex.sourceLocation().isKnown()
    ex.sourceLocation().lineNumber() == 4
    ex.sourceLocation().charOffset() < json.length() - 1
  }

  def "preserves JSON null elements inside open-value arrays"() {
    given:
    def json = '{"meta":{"values":[null],"nested":{"items":[null,1]}}}'
    def reader = JsonApiJackson3.reader(mapper, resourceContext)

    when:
    def document = reader.readValue(json)

    then:
    document.meta().members().get('values') == [null]
    document.meta().members().get('nested').get('items') == [null, 1]
  }

  def "caller-owned parser may sequence multiple root documents"() {
    given:
    def reader = JsonApiJackson3.reader(mapper, resourceContext)
    def parser = reader.mapper().createParser('{"meta":{"a":1}}{"meta":{"b":2}}')

    when:
    def first = reader.readValue(parser)
    def second = reader.readValue(parser)

    then:
    first.meta().members().get('a') == 1
    second.meta().members().get('b') == 2

    cleanup:
    parser?.close()
  }

  def "caller-owned parser advances past a prior scalar root before a document"() {
    given:
    def reader = JsonApiJackson3.reader(mapper, resourceContext)
    def parser = reader.mapper().createParser('"skip"{"meta":{"a":1}}')
    parser.nextToken() // VALUE_STRING

    when:
    def document = reader.readValue(parser)

    then:
    document.meta().members().get('a') == 1

    cleanup:
    parser?.close()
  }

  def "namespaced link relation decodes as Link not additional member"() {
    given:
    def context = DocumentReadContext.of(extContext(), PrimaryDataKind.RESOURCE)
    def reader = JsonApiJackson3.reader(mapper, context)

    when:
    def document = reader.readValue('''
      {
        "meta":{},
        "links":{"ext:custom":"https://example.com/ext"}
      }
      ''')

    then:
    document.links().links().get('ext:custom') instanceof Link.StringLink
    ((Link.StringLink) document.links().links().get('ext:custom')).href() == 'https://example.com/ext'
    !document.links().additionalMembers().containsKey('ext:custom')
  }

  def "bound context is exposed"() {
    given:
    def validation = ValidationContext.defaults().withDocumentUsage(DocumentUsage.CREATE_REQUEST)
    def context = DocumentReadContext.resourceDefaults()
        .withValidationContext(validation)
        .withPrimaryDataKind(PrimaryDataKind.RESOURCE_IDENTIFIER)
    def reader = JsonApiJackson3.reader(mapper, context)

    expect:
    reader.context().is(context)
    reader.context().validationContext().is(validation)
    reader.context().primaryDataKind() == PrimaryDataKind.RESOURCE_IDENTIFIER
  }

  private static String readCorpusText(String relativePath) {
    return TestFixtureResources.readCorpusUtf8(relativePath)
  }

  private static JsonApiDocument singleResourceDocument() {
    Map<String, Object> attributes = new LinkedHashMap<>()
    attributes.put('title', 'JSON:API paints my bikeshed!')
    def article = resource('articles', '1', Attributes.ofAttributes(attributes))
    return JsonApiDocument.withData(new DocumentData.SingleResource(article))
  }

  private static JsonApiDocument resourceCollectionDocument() {
    Map<String, Object> firstAttributes = new LinkedHashMap<>()
    firstAttributes.put('title', 'First')
    Map<String, Object> secondAttributes = new LinkedHashMap<>()
    secondAttributes.put('title', 'Second')
    def first = resource('articles', '1', Attributes.ofAttributes(firstAttributes))
    def second = resource('articles', '2', Attributes.ofAttributes(secondAttributes))
    return JsonApiDocument.withData(
        new DocumentData.ResourceCollection(List.of(first, second)))
  }

  private static JsonApiDocument singleIdentifierDocument() {
    return JsonApiDocument.withData(
        new DocumentData.SingleIdentifier(ResourceIdentifier.of('articles', '1')))
  }

  private static JsonApiDocument identifierCollectionDocument() {
    return JsonApiDocument.withData(
        new DocumentData.IdentifierCollection(
        List.of(ResourceIdentifier.of('articles', '1'), ResourceIdentifier.of('articles', '2'))))
  }

  private static JsonApiDocument nullDataDocument() {
    Map<String, Object> meta = new LinkedHashMap<>()
    meta.put('reason', 'deleted')
    return new JsonApiDocument(
        DocumentData.NullData.INSTANCE, null, Meta.of(meta), null, null, null, Map.of())
  }

  private static JsonApiDocument metaOnlyDocument() {
    Map<String, Object> meta = new LinkedHashMap<>()
    meta.put('copyright', 'Copyright 2026')
    return JsonApiDocument.withMeta(Meta.of(meta))
  }

  private static JsonApiDocument emptyIdentifierCollectionDocument() {
    return JsonApiDocument.withData(
        new DocumentData.IdentifierCollection(List.of()))
  }

  private static JsonApiDocument emptyWrappersDocument() {
    def article = resourceWithAll(
        'articles', '1', Attributes.empty(), Relationships.empty(), Links.empty(), Meta.empty())
    return JsonApiDocument.withData(new DocumentData.SingleResource(article))
  }

  private static JsonApiDocument emptyErrorsDocument() {
    return JsonApiDocument.withErrors(List.of())
  }

  private static JsonApiDocument emptyIncludedDocument() {
    def article = resource('articles', '1')
    return new JsonApiDocument(
        new DocumentData.SingleResource(article), null, null, null, null, List.of(), Map.of())
  }

  private static JsonApiDocument relationshipNullLinkageDocument() {
    Map<String, Relationship> relationships = new LinkedHashMap<>()
    relationships.put('author', new Relationship(RelationshipData.NullLinkage.INSTANCE, null, null, Map.of()))
    def article = resourceWithRelationships('articles', '1', Relationships.ofRelationships(relationships))
    return JsonApiDocument.withData(new DocumentData.SingleResource(article))
  }

  private static JsonApiDocument relationshipEmptyToManyDocument() {
    Map<String, Relationship> relationships = new LinkedHashMap<>()
    relationships.put(
        'comments',
        new Relationship(
        RelationshipData.IdentifierCollectionLinkage.empty(), null, null, Map.of()))
    def article = resourceWithRelationships('articles', '1', Relationships.ofRelationships(relationships))
    return JsonApiDocument.withData(new DocumentData.SingleResource(article))
  }

  private static JsonApiDocument relationshipLinkOnlyDocument() {
    Map<String, Link> authorLinks = new LinkedHashMap<>()
    authorLinks.put('self', new Link.StringLink('http://example.com/articles/1/relationships/author'))
    authorLinks.put('related', new Link.StringLink('http://example.com/articles/1/author'))
    Map<String, Relationship> relationships = new LinkedHashMap<>()
    relationships.put('author', Relationship.linkOnly(Links.ofLinks(authorLinks)))
    def article = resourceWithRelationships('articles', '1', Relationships.ofRelationships(relationships))
    return JsonApiDocument.withData(new DocumentData.SingleResource(article))
  }

  private static JsonApiDocument relationshipMetaOnlyDocument() {
    Map<String, Object> meta = new LinkedHashMap<>()
    meta.put('inferred', true)
    Map<String, Relationship> relationships = new LinkedHashMap<>()
    relationships.put('author', Relationship.metaOnly(Meta.of(meta)))
    def article = resourceWithRelationships('articles', '1', Relationships.ofRelationships(relationships))
    return JsonApiDocument.withData(new DocumentData.SingleResource(article))
  }

  private static JsonApiDocument stringAndObjectLinksDocument() {
    String selfHref = 'http://example.com/articles/1'
    Map<String, Link> resourceLinks = new LinkedHashMap<>()
    resourceLinks.put('self', new Link.StringLink(selfHref))
    def article = resourceWithLinks('articles', '1', Links.ofLinks(resourceLinks))

    Map<String, Object> relatedMeta = new LinkedHashMap<>()
    relatedMeta.put('count', 1)
    def related = new Link.ObjectLink(
        'http://example.com/articles/1/related',
        'related',
        null,
        'Related',
        'application/vnd.api+json',
        List.of('en'),
        Meta.of(relatedMeta),
        Map.of())

    Map<String, Link> topLinkEntries = new LinkedHashMap<>()
    topLinkEntries.put('self', new Link.StringLink(selfHref))
    topLinkEntries.put('related', related)
    topLinkEntries.put('next', null)

    return new JsonApiDocument(
        new DocumentData.ResourceCollection(List.of(article)),
        null,
        null,
        null,
        Links.ofLinks(topLinkEntries),
        null,
        Map.of())
  }

  private static JsonApiDocument errorsDocument() {
    Map<String, Link> errorLinks = new LinkedHashMap<>()
    errorLinks.put('about', new Link.StringLink('http://example.com/docs/errors/invalid'))
    def error = new ErrorObject(
        '1',
        Links.ofLinks(errorLinks),
        '422',
        'invalid',
        'Invalid Attribute',
        'Title is required',
        new ErrorSource('/data/attributes/title', null, null, Map.of()),
        null,
        Map.of())
    return JsonApiDocument.withErrors(List.of(error))
  }

  private static JsonApiDocument jsonApiObjectDocument() {
    Map<String, Object> meta = new LinkedHashMap<>()
    meta.put('impl', 'jsonapi-java')
    def jsonapi = new JsonApiObject(
        '1.1',
        List.of('https://jsonapi.org/ext/atomic'),
        List.of('https://example.com/profiles/flex'),
        Meta.of(meta),
        Map.of())
    return new JsonApiDocument(
        new DocumentData.SingleResource(new ResourceObject('articles', '1', null, null, null, null, null, Map.of())),
        null,
        null,
        jsonapi,
        null,
        null,
        Map.of())
  }

  private static JsonApiDocument compoundDocument() {
    Map<String, Relationship> relationships = new LinkedHashMap<>()
    relationships.put(
        'author',
        new Relationship(
        new RelationshipData.SingleLinkage(ResourceIdentifier.of('people', '9')),
        null,
        null,
        Map.of()))
    def article = resourceWithRelationships('articles', '1', Relationships.ofRelationships(relationships))
    Map<String, Object> includedAttributes = new LinkedHashMap<>()
    includedAttributes.put('name', 'Dan')
    def included = resource('people', '9', Attributes.ofAttributes(includedAttributes))
    return new JsonApiDocument(
        new DocumentData.SingleResource(article), null, null, null, null, List.of(included), Map.of())
  }

  private static JsonApiDocument compoundNestedIntermediateDocument() {
    Map<String, Relationship> articleRelationships = new LinkedHashMap<>()
    articleRelationships.put(
        'author',
        new Relationship(
        new RelationshipData.SingleLinkage(ResourceIdentifier.of('people', '9')),
        null,
        null,
        Map.of()))
    articleRelationships.put(
        'comments',
        new Relationship(
        new RelationshipData.IdentifierCollectionLinkage(
        List.of(ResourceIdentifier.of('comments', '5'), ResourceIdentifier.of('comments', '12'))),
        null,
        null,
        Map.of()))
    def article = resourceWithRelationships('articles', '1', Relationships.ofRelationships(articleRelationships))

    Map<String, Relationship> comment5Relationships = new LinkedHashMap<>()
    comment5Relationships.put(
        'author',
        new Relationship(
        new RelationshipData.SingleLinkage(ResourceIdentifier.of('people', '2')),
        null,
        null,
        Map.of()))
    def comment5 = resourceWithAttributesAndRelationships(
        'comments', '5', Attributes.ofAttributes(attribute('body', 'First!')),
        Relationships.ofRelationships(comment5Relationships))
    def person2 = resource('people', '2', Attributes.ofAttributes(attribute('name', 'Ezra')))

    Map<String, Relationship> comment12Relationships = new LinkedHashMap<>()
    comment12Relationships.put(
        'author',
        new Relationship(
        new RelationshipData.SingleLinkage(ResourceIdentifier.of('people', '9')),
        null,
        null,
        Map.of()))
    def comment12 = resourceWithAttributesAndRelationships(
        'comments', '12', Attributes.ofAttributes(attribute('body', 'I like XML better')),
        Relationships.ofRelationships(comment12Relationships))
    def person9 = resource('people', '9', Attributes.ofAttributes(attribute('name', 'Dan')))

    return new JsonApiDocument(
        new DocumentData.SingleResource(article),
        null,
        null,
        null,
        null,
        List.of(comment5, comment12, person2, person9),
        Map.of())
  }

  private static JsonApiDocument compoundSharedIdentityDocument() {
    def article1 = resourceWithRelationships('articles', '1', sharedAuthorRelationships())
    def article2 = resourceWithRelationships('articles', '2', sharedAuthorRelationships())
    Map<String, Object> includedAttributes = new LinkedHashMap<>()
    includedAttributes.put('name', 'Dan')
    def included = resource('people', '9', Attributes.ofAttributes(includedAttributes))
    return new JsonApiDocument(
        new DocumentData.ResourceCollection(List.of(article1, article2)),
        null,
        null,
        null,
        null,
        List.of(included),
        Map.of())
  }

  private static JsonApiDocument localIdentifierDocument() {
    Map<String, Relationship> relationships = new LinkedHashMap<>()
    relationships.put(
        'author',
        new Relationship(
        new RelationshipData.SingleLinkage(ResourceIdentifier.withLid('people', 'temp-author')),
        null,
        null,
        Map.of()))
    def article = new ResourceObject(
        'articles', null, 'temp-1', null, Relationships.ofRelationships(relationships), null, null, Map.of())
    return JsonApiDocument.withData(new DocumentData.SingleResource(article))
  }

  private static JsonApiDocument extensionAndAtMembersDocument() {
    Map<String, Object> attributes = new LinkedHashMap<>()
    attributes.put('title', 'Hello')
    Map<String, Object> additionalMembers = new LinkedHashMap<>()
    additionalMembers.put('@copyright', 'Copyright 2026')
    additionalMembers.put('ext:version', 1)
    def article = new ResourceObject(
        'articles',
        '1',
        null,
        Attributes.ofAttributes(attributes),
        null,
        null,
        null,
        additionalMembers)
    Map<String, Object> documentMembers = new LinkedHashMap<>()
    documentMembers.put('ext:request-id', 'abc-123')
    return new JsonApiDocument(
        new DocumentData.SingleResource(article), null, null, null, null, null, documentMembers)
  }

  private static JsonApiDocument memberOrderDocument() {
    def self = new Link.StringLink('http://example.com/articles/1')
    Map<String, Object> attributes = new LinkedHashMap<>()
    attributes.put('title', 'Ordered')
    Map<String, Relationship> relationships = new LinkedHashMap<>()
    relationships.put(
        'author',
        new Relationship(
        new RelationshipData.SingleLinkage(ResourceIdentifier.of('people', '9')),
        null,
        null,
        Map.of()))
    Map<String, Link> resourceLinks = new LinkedHashMap<>()
    resourceLinks.put('self', self)
    Map<String, Object> resourceMeta = new LinkedHashMap<>()
    resourceMeta.put('created', '2026-01-01')
    Map<String, Object> resourceMembers = new LinkedHashMap<>()
    resourceMembers.put('ext:flag', true)
    def article = new ResourceObject(
        'articles',
        '1',
        'temp-1',
        Attributes.ofAttributes(attributes),
        Relationships.ofRelationships(relationships),
        Links.ofLinks(resourceLinks),
        Meta.of(resourceMeta),
        resourceMembers)

    Map<String, Object> documentMeta = new LinkedHashMap<>()
    documentMeta.put('copyright', 'Copyright 2026')
    Map<String, Link> documentLinks = new LinkedHashMap<>()
    documentLinks.put('self', self)
    Map<String, Object> documentMembers = new LinkedHashMap<>()
    documentMembers.put('ext:trace', 't-1')
    return new JsonApiDocument(
        new DocumentData.SingleResource(article),
        null,
        Meta.of(documentMeta),
        JsonApiObject.ofVersion('1.1'),
        Links.ofLinks(documentLinks),
        List.of(ResourceObject.of('people', '9')),
        documentMembers)
  }

  private static ResourceObject resource(String type, String id) {
    return new ResourceObject(type, id, null, null, null, null, null, Map.of())
  }

  private static ResourceObject resource(String type, String id, Attributes attributes) {
    return new ResourceObject(type, id, null, attributes, null, null, null, Map.of())
  }

  private static ResourceObject resourceWithRelationships(
      String type, String id, Relationships relationships) {
    return new ResourceObject(type, id, null, null, relationships, null, null, Map.of())
  }

  private static ResourceObject resourceWithLinks(String type, String id, Links links) {
    return new ResourceObject(type, id, null, null, null, links, null, Map.of())
  }

  private static ResourceObject resourceWithAll(
      String type,
      String id,
      Attributes attributes,
      Relationships relationships,
      Links links,
      Meta meta) {
    return new ResourceObject(type, id, null, attributes, relationships, links, meta, Map.of())
  }

  private static ResourceObject resourceWithAttributesAndRelationships(
      String type, String id, Attributes attributes, Relationships relationships) {
    return new ResourceObject(type, id, null, attributes, relationships, null, null, Map.of())
  }

  private static Relationships sharedAuthorRelationships() {
    Map<String, Relationship> relationships = new LinkedHashMap<>()
    relationships.put(
        'author',
        new Relationship(
        new RelationshipData.SingleLinkage(ResourceIdentifier.of('people', '9')),
        null,
        null,
        Map.of()))
    return Relationships.ofRelationships(relationships)
  }

  private static Map<String, Object> attribute(String name, String value) {
    Map<String, Object> attributes = new LinkedHashMap<>()
    attributes.put(name, value)
    return attributes
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

  private static final class CloseTrackingInputStream extends FilterInputStream {
    private boolean closed

    CloseTrackingInputStream(InputStream delegate) {
      super(delegate)
    }

    @Override
    void close() {
      closed = true
      super.close()
    }

    boolean isClosed() {
      return closed
    }
  }

  private static final class MissingLocationParser extends JsonParserDelegate {

    MissingLocationParser(JsonParser delegate) {
      super(delegate)
    }

    @Override
    JsonToken nextToken() {
      throw new MissingLocationException()
    }
  }

  private static final class MissingLocationException extends JacksonException {

    MissingLocationException() {
      super('synthetic parser failure', TokenStreamLocation.NA, null)
    }
  }
}
