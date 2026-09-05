package io.github.kazemek.jsonapi.jackson2.internal

import java.math.BigDecimal
import java.math.BigInteger
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicInteger

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.json.JsonMapper

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
import io.github.kazemek.jsonapi.jackson2.JsonApiJackson2

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
        'https://example.test/schema',
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
    def json = JsonApiJackson2.writer(mapper).writeValueAsString(document)
    def tree = mapper.readTree(json)

    then:
    tree.get('data').get('type').asText() == 'articles'
    tree.get('data').get('id').asText() == '1'
    tree.get('data').get('lid').asText() == 'local-article'
    tree.get('data').get('attributes').get('nullValue').isNull()
    tree.get('data').get('attributes').get('byteValue').asInt() == 1
    tree.get('data').get('attributes').get('shortValue').asInt() == 2
    tree.get('data').get('attributes').get('integerValue').asInt() == 3
    tree.get('data').get('attributes').get('longValue').asLong() == 4L
    tree.get('data').get('attributes').get('floatValue').asDouble() == 5.5D
    tree.get('data').get('attributes').get('doubleValue').asDouble() == 6.5D
    tree.get('data').get('attributes').get('bigIntegerValue').bigIntegerValue() == BigInteger.valueOf(7L)
    tree.get('data').get('attributes').get('bigDecimalValue').decimalValue() == new BigDecimal('8.5')
    !tree.get('data').get('attributes').get('listValue').get(2).get('deep').booleanValue()
    tree.get('data').get('attributes').get('@attribute-note').asText() == 'attribute'
    tree.get('data').get('relationships').get('author').get('data').get('id').asText() == 'p1'
    tree.get('data').get('relationships').get('author').get('data').get('meta').get('identifierNote').asText() == 'identifier'
    tree.get('data').get('relationships').get('author').get('data').get('@identifier-note').asText() == 'identifier'
    tree.get('data').get('relationships').get('author').get('links').get('self').get('href').asText() == objectLink.href()
    tree.get('data').get('relationships').get('author').get('meta').get('relationshipNote').asText() == 'relationship'
    tree.get('data').get('links').get('self').get('hreflang').size() == 2
    tree.get('links').get('related').isNull()
    tree.get('data').get('meta').get('resourceNote').asText() == 'resource'
    tree.get('jsonapi').get('version').asText() == '1.1'
    tree.get('jsonapi').get('ext').get(0).asText() == 'https://example.test/ext'
    tree.get('jsonapi').get('profile').get(0).asText() == 'https://example.test/profile'
    tree.get('included').size() == 1
    tree.get('@document-note').get(1).isNull()
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
    def tree = mapper.readTree(JsonApiJackson2.writer(mapper).writeValueAsString(document))
    def serializedError = tree.get('errors').get(0)

    then:
    serializedError.get('id').asText() == 'error-1'
    serializedError.get('links').get('about').asText() == 'https://example.test/errors/1'
    serializedError.get('status').asText() == '422'
    serializedError.get('code').asText() == 'invalid-attribute'
    serializedError.get('title').asText() == 'Invalid attribute'
    serializedError.get('detail').asText() == 'The title is invalid'
    serializedError.get('source').get('pointer').asText() == '/data/attributes/title'
    serializedError.get('source').get('parameter').asText() == 'filter[title]'
    serializedError.get('source').get('header').asText() == 'X-Request-ID'
    serializedError.get('source').get('@source-note').asText() == 'source'
    serializedError.get('meta').get('errorNote').asText() == 'error'
    serializedError.get('@error-note').asText() == 'error'
  }

  def "writes every document data variant"() {
    expect:
    readTree(writeDirect { JsonGenerator generator -> JsonApiWireWriter.writeDocumentData(data, generator) }).toString() == expected

    where:
    data                                                               | expected
    null                                                               | 'null'
    DocumentData.NullData.INSTANCE                                    | 'null'
    new DocumentData.SingleIdentifier(ResourceIdentifier.of('people', 'p1')) | '{"type":"people","id":"p1"}'
    new DocumentData.SingleIdentifier(new ResourceIdentifier(
        'people', 'p2', null, Meta.of([identifierNote: 'note']), ['@identifier-note': 'note'])
        ) | '{"type":"people","id":"p2","meta":{"identifierNote":"note"},"@identifier-note":"note"}'
    new DocumentData.IdentifierCollection([
      ResourceIdentifier.withLid('people', 'local-p1')
    ]) | '[{"type":"people","lid":"local-p1"}]'
    new DocumentData.ResourceCollection([
      ResourceObject.of('articles', '1')
    ]) | '[{"type":"articles","id":"1"}]'
  }

  def "writes every relationship data variant"() {
    expect:
    readTree(writeDirect { JsonGenerator generator -> JsonApiWireWriter.writeRelationshipData(data, generator) }).toString() == expected

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
