package com.kazforge.jsonapi.jackson3.internal

import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicInteger

import tools.jackson.core.JsonGenerator
import tools.jackson.databind.json.JsonMapper

import com.kazforge.jsonapi.core.model.Attributes
import com.kazforge.jsonapi.core.model.DocumentData
import com.kazforge.jsonapi.core.model.ErrorObject
import com.kazforge.jsonapi.core.model.ErrorSource
import com.kazforge.jsonapi.core.model.JsonApiDocument
import com.kazforge.jsonapi.core.model.JsonApiObject
import com.kazforge.jsonapi.core.model.Link
import com.kazforge.jsonapi.core.model.Links
import com.kazforge.jsonapi.core.model.Meta
import com.kazforge.jsonapi.core.model.Relationship
import com.kazforge.jsonapi.core.model.RelationshipData
import com.kazforge.jsonapi.core.model.Relationships
import com.kazforge.jsonapi.core.model.ResourceIdentifier
import com.kazforge.jsonapi.core.model.ResourceObject
import com.kazforge.jsonapi.jackson3.JsonApiJackson3

import spock.lang.Specification

class JsonApiWireWriterSpec extends Specification {

  def "writes every document member and nested wire shape"() {
    given:
    def mapper = JsonMapper.builder().build()
    def authorIdentifier = new ResourceIdentifier(
        'people',
        'p1',
        null,
        Meta.of([identifierNote: 'identifier']),
        ['@identifier-note': 'identifier'])
    def objectLink = new Link.ObjectLink(
        'https://example.test/people/p1',
        'related',
        new Link.StringLink('https://example.test/schema'),
        'Author',
        'application/json',
        ['en', 'fr'],
        Meta.of([linkNote: 'link']),
        ['@link-note': 'link'])
    def attributes = Attributes.of(
        [
          textValue: 'value',
          nullValue: null,
          booleanValue: true,
          byteValue: Byte.valueOf((byte) 1),
          shortValue: Short.valueOf((short) 2),
          integerValue: 3,
          longValue: 4L,
          floatValue: 5.5F,
          doubleValue: 6.5D,
          bigIntegerValue: BigInteger.valueOf(7L),
          bigDecimalValue: new BigDecimal('8.5'),
          listValue: [null, 'nested', [deep: false]],
          objectValue: [number: 9]
        ],
        ['@attribute-note': 'attribute'])
    def relationship = new Relationship(
        new RelationshipData.SingleLinkage(authorIdentifier),
        Links.of(
        [self: objectLink, related: new Link.StringLink('/articles/1/author')] as Map<String, Link>,
        ['@relationship-link-note': 'relationship-link'] as Map<String, Object>),
        Meta.of([relationshipNote: 'relationship']),
        ['@relationship-note': 'relationship'])
    def resource = new ResourceObject(
        'articles',
        '1',
        'local-article',
        attributes,
        Relationships.of([author: relationship], ['@relationships-note': 'relationships']),
        Links.of(
        [self: objectLink],
        ['@resource-link-note': 'resource-link']),
        Meta.of([resourceNote: 'resource']),
        ['@resource-note': 'resource'])
    def document = new JsonApiDocument(
        new DocumentData.SingleResource(resource),
        null,
        Meta.of([documentNote: 'document']),
        new JsonApiObject(
        '1.1',
        ['https://example.test/ext'],
        [
          'https://example.test/profile'
        ],
        Meta.of([jsonapiNote: 'jsonapi']),
        ['@jsonapi-note': 'jsonapi']),
        Links.of(
        [self: new Link.StringLink('/articles'), related: null] as Map<String, Link>,
        ['@document-link-note': 'document-link']),
        [
          ResourceObject.of('people', 'p1')
        ],
        ['@document-note': ['nested', null]])

    when:
    def json = JsonApiJackson3.writer(mapper).writeValueAsString(document)
    def tree = mapper.readTree(json)

    then:
    tree.get('data').get('type').asString() == 'articles'
    tree.get('data').get('id').asString() == '1'
    tree.get('data').get('lid').asString() == 'local-article'
    tree.get('data').get('attributes').get('nullValue').isNull()
    tree.get('data').get('attributes').get('byteValue').asInt() == 1
    tree.get('data').get('attributes').get('shortValue').asInt() == 2
    tree.get('data').get('attributes').get('integerValue').asInt() == 3
    tree.get('data').get('attributes').get('longValue').asLong() == 4L
    tree.get('data').get('attributes').get('floatValue').asDouble() == 5.5D
    tree.get('data').get('attributes').get('doubleValue').asDouble() == 6.5D
    tree.get('data').get('attributes').get('bigIntegerValue').asBigInteger() == BigInteger.valueOf(7L)
    tree.get('data').get('attributes').get('bigDecimalValue').asDecimal() == new BigDecimal('8.5')
    !tree.get('data').get('attributes').get('listValue').get(2).get('deep').booleanValue()
    tree.get('data').get('attributes').get('@attribute-note').asString() == 'attribute'
    tree.get('data').get('relationships').get('author').get('data').get('id').asString() == 'p1'
    tree.get('data').get('relationships').get('author').get('links').get('self').get('href').asString() == objectLink.href()
    tree.get('data').get('relationships').get('author').get('meta').get('relationshipNote').asString() == 'relationship'
    tree.get('data').get('links').get('self').get('hreflang').size() == 2
    tree.get('links').get('related').isNull()
    tree.get('data').get('meta').get('resourceNote').asString() == 'resource'
    tree.get('jsonapi').get('version').asString() == '1.1'
    tree.get('jsonapi').get('ext').get(0).asString() == 'https://example.test/ext'
    tree.get('jsonapi').get('profile').get(0).asString() == 'https://example.test/profile'
    tree.get('included').size() == 1
    tree.get('@document-note').get(1).isNull()
  }

  def "writes string and recursive object describedby links while omitting an absent value"() {
    given:
    def stringDescription = new Link.StringLink('https://example.test/schemas/article')
    def nestedDescription = new Link.ObjectLink(
        'https://example.test/schemas/article',
        null,
        new Link.StringLink('https://example.test/schemas/article-v1'),
        null,
        'application/schema+json',
        null,
        Meta.of([revision: 2]),
        Map.of())
    def links = Links.ofLinks([
      'string-description': new Link.ObjectLink(
      'https://example.test/articles/1', null, stringDescription, null, null, null, null, Map.of()),
      'object-description': new Link.ObjectLink(
      'https://example.test/articles/2', null, nestedDescription, null, null, null, null, Map.of()),
      'no-description': new Link.ObjectLink(
      'https://example.test/articles/3', null, null, null, null, null, null, Map.of())
    ])

    when:
    def tree = readTree(writeDirect { JsonGenerator generator ->
      JsonApiWireWriter.writeLinks(links, generator)
    })

    then:
    tree.get('string-description').get('describedby').asString() ==
        'https://example.test/schemas/article'
    def objectDescription = tree.get('object-description').get('describedby')
    objectDescription.get('href').asString() == 'https://example.test/schemas/article'
    objectDescription.get('describedby').asString() == 'https://example.test/schemas/article-v1'
    objectDescription.get('type').asString() == 'application/schema+json'
    objectDescription.get('meta').get('revision').asInt() == 2
    !tree.get('no-description').has('describedby')
  }

  def "writes every error member and error source member"() {
    given:
    def mapper = JsonMapper.builder().build()
    def error = new ErrorObject(
        'error-1',
        Links.of(
        [about: new Link.StringLink('https://example.test/errors/1')] as Map<String, Link>,
        ['@error-link-note': 'error-link']),
        '422',
        'invalid-attribute',
        'Invalid attribute',
        'The title is invalid',
        new ErrorSource(
        '/data/attributes/title',
        'filter[title]',
        'X-Request-ID',
        ['@source-note': 'source']),
        Meta.of([errorNote: 'error']),
        ['@error-note': 'error'])
    def document = new JsonApiDocument(
        null,
        [error],
        Meta.of([documentNote: 'errors']),
        new JsonApiObject('1.1', null, null, null, [:]),
        Links.ofLinks([self: new Link.StringLink('/errors')]),
        null,
        ['@document-note': 'errors'])

    when:
    def tree = mapper.readTree(JsonApiJackson3.writer(mapper).writeValueAsString(document))
    def serializedError = tree.get('errors').get(0)

    then:
    serializedError.get('id').asString() == 'error-1'
    serializedError.get('links').get('about').asString() == 'https://example.test/errors/1'
    serializedError.get('status').asString() == '422'
    serializedError.get('code').asString() == 'invalid-attribute'
    serializedError.get('title').asString() == 'Invalid attribute'
    serializedError.get('detail').asString() == 'The title is invalid'
    serializedError.get('source').get('pointer').asString() == '/data/attributes/title'
    serializedError.get('source').get('parameter').asString() == 'filter[title]'
    serializedError.get('source').get('header').asString() == 'X-Request-ID'
    serializedError.get('source').get('@source-note').asString() == 'source'
    serializedError.get('meta').get('errorNote').asString() == 'error'
    serializedError.get('@error-note').asString() == 'error'
  }

  def "writes every document data variant"() {
    expect:
    readTree(writeDirect { JsonGenerator generator ->
      JsonApiWireWriter.writeDocumentData(data, generator)
    }).toString() == expected

    where:
    data                                                               | expected
    null                                                               | 'null'
    DocumentData.NullData.INSTANCE                                    | 'null'
    new DocumentData.SingleIdentifier(ResourceIdentifier.of('people', 'p1')) | '{"type":"people","id":"p1"}'
    new DocumentData.IdentifierCollection([
      ResourceIdentifier.withLid('people', 'local-p1')
    ]) | '[{"type":"people","lid":"local-p1"}]'
    new DocumentData.ResourceCollection([
      ResourceObject.of('articles', '1')
    ]) | '[{"type":"articles","id":"1"}]'
  }

  def "writes every relationship data variant"() {
    expect:
    readTree(writeDirect { JsonGenerator generator ->
      JsonApiWireWriter.writeRelationshipData(data, generator)
    }).toString() == expected

    where:
    data                                                                   | expected
    null                                                                   | 'null'
    RelationshipData.NullLinkage.INSTANCE                                 | 'null'
    new RelationshipData.SingleLinkage(ResourceIdentifier.of('people', 'p1')) | '{"type":"people","id":"p1"}'
    new RelationshipData.IdentifierCollectionLinkage([])                  | '[]'
  }

  def "writes nullable resource and error collections as empty arrays"() {
    expect:
    writeDirect { JsonGenerator generator -> JsonApiWireWriter.writeResourceObjects(null, generator) } == '[]'
    writeDirect { JsonGenerator generator -> JsonApiWireWriter.writeErrorObjects(null, generator) } == '[]'
  }

  def "writes a number implementation outside the core JSON number set as text"() {
    expect:
    readTree(writeDirect { JsonGenerator generator ->
      JsonApiWireWriter.writeOpenValue(new AtomicInteger(7), generator)
    }).asInt() == 7
  }

  def "rejects unsupported open JSON values"() {
    when:
    writeDirect { JsonGenerator generator -> JsonApiWireWriter.writeOpenValue(new Object(), generator) }

    then:
    def exception = thrown(IllegalArgumentException)
    exception.message.contains('Unsupported open JSON value type')
  }

  private static Object readTree(String json) {
    return JsonMapper.builder().build().readTree(json)
  }

  private static String writeDirect(Closure writer) {
    def output = new ByteArrayOutputStream()
    def generator = JsonMapper.builder().build().createGenerator(output)
    try {
      writer.call(generator)
    } finally {
      generator.close()
    }
    return new String(output.toByteArray(), StandardCharsets.UTF_8)
  }
}
