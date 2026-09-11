package io.github.kazemek.jsonapi.jackson3

import java.io.IOException
import java.io.OutputStream
import java.io.Writer
import java.nio.charset.StandardCharsets

import tools.jackson.core.JacksonException
import tools.jackson.databind.json.JsonMapper

import io.github.kazemek.jsonapi.core.model.Attributes
import io.github.kazemek.jsonapi.core.model.DocumentData
import io.github.kazemek.jsonapi.core.model.JsonApiDocument
import io.github.kazemek.jsonapi.core.model.Link
import io.github.kazemek.jsonapi.core.model.Links
import io.github.kazemek.jsonapi.core.model.Meta
import io.github.kazemek.jsonapi.core.model.Relationship
import io.github.kazemek.jsonapi.core.model.RelationshipData
import io.github.kazemek.jsonapi.core.model.Relationships
import io.github.kazemek.jsonapi.core.model.ResourceIdentifier
import io.github.kazemek.jsonapi.core.model.ResourceIdentity
import io.github.kazemek.jsonapi.core.model.ResourceObject
import io.github.kazemek.jsonapi.core.validation.JsonApiValidationException
import io.github.kazemek.jsonapi.core.validation.ValidationContext
import io.github.kazemek.jsonapi.core.validation.ValidationRuleCode
import io.github.kazemek.jsonapi.jackson.mapping.MappedDocument

import spock.lang.Specification

class DocumentWriterSinkSpec extends Specification {

  def "all write sinks emit structurally identical JSON for a representative document and expose the bound context"() {
    given:
    def mapper = JsonMapper.builder().build()
    def context = ValidationContext.defaults()
    def resource = new ResourceObject(
        'articles',
        '1',
        null,
        Attributes.ofAttributes(['title': 'JSON:API paints my bikeshed!']),
        Relationships.ofRelationships([
          'author': Relationship.withData(
          new RelationshipData.SingleLinkage(ResourceIdentifier.of('people', '9')))
        ]),
        null,
        null,
        [:])
    def document = new JsonApiDocument(
        new DocumentData.SingleResource(resource),
        null,
        Meta.of(['count': '1']),
        null,
        Links.ofLinks(['self': new Link.StringLink('http://example.com/articles/1')]),
        null,
        [:])
    def writer = JsonApiJackson3.writer(mapper, context)
    def expected = mapper.readTree(
        '{"data":{"type":"articles","id":"1","attributes":{"title":"JSON:API paints my bikeshed!"},' +
        '"relationships":{"author":{"data":{"type":"people","id":"9"}}}},' +
        '"links":{"self":"http://example.com/articles/1"},"meta":{"count":"1"}}')

    def bytesOut = new ByteArrayOutputStream()
    def charsOut = new StringWriter()
    def generatorOut = new ByteArrayOutputStream()

    when:
    def asString = writer.writeValueAsString(document)
    def asBytes = writer.writeValueAsBytes(document)
    writer.writeValue(bytesOut, document)
    writer.writeValue(charsOut, document)
    def generator = writer.mapper().createGenerator(generatorOut)
    try {
      writer.writeValue(generator, document)
    } finally {
      generator.close()
    }

    then:
    writer.context().is(context)
    mapper.readTree(asString) == expected
    mapper.readTree(asBytes) == expected
    mapper.readTree(bytesOut.toByteArray()) == expected
    mapper.readTree(charsOut.toString()) == expected
    mapper.readTree(generatorOut.toByteArray()) == expected
    new String(asBytes, StandardCharsets.UTF_8) ==
        new String(bytesOut.toByteArray(), StandardCharsets.UTF_8)
    new String(bytesOut.toByteArray(), StandardCharsets.UTF_8) == charsOut.toString()
  }

  def "mapped documents use every write sink and compose provenance before validation"() {
    given:
    def mapper = JsonMapper.builder().build()
    def article = ResourceObject.of('articles', '1')
    def unlinkedAuthor = ResourceObject.of('people', '9')
    def document = new JsonApiDocument(
        new DocumentData.SingleResource(article),
        null,
        null,
        null,
        null,
        List.of(unlinkedAuthor),
        Map.of())
    def mapped = new MappedDocument(document, Set.of(ResourceIdentity.ofId('people', '9')))
    def unexempted = new MappedDocument(document, Set.of())
    def writer = JsonApiJackson3.writer(mapper, ValidationContext.defaults())
    def bytesOut = new ByteArrayOutputStream()
    def charsOut = new StringWriter()
    def generatorOut = new ByteArrayOutputStream()

    when: 'the mapped exemptions are genuinely required for the document to validate'
    writer.writeValueAsString(mapped.document())

    then:
    def exception = thrown(JsonApiValidationException)
    exception.ruleCode() == ValidationRuleCode.FULL_LINKAGE_VIOLATION

    when: 'every mapped sink composes the provenance exemptions before validation'
    def stringValue = writer.writeValueAsString(mapped)
    def byteValue = writer.writeValueAsBytes(mapped)
    writer.writeValue(bytesOut, mapped)
    writer.writeValue(charsOut, mapped)
    def generator = writer.mapper().createGenerator(generatorOut)
    try {
      writer.writeValue(generator, mapped)
    } finally {
      generator.close()
    }

    then:
    mapper.readTree(stringValue) == mapper.readTree(byteValue)
    mapper.readTree(stringValue) == mapper.readTree(bytesOut.toByteArray())
    mapper.readTree(stringValue) == mapper.readTree(charsOut.toString())
    mapper.readTree(stringValue) == mapper.readTree(generatorOut.toByteArray())

    when: 'every mapped sink validates before output (unexempted document)'
    writer.writeValueAsString(unexempted)

    then:
    def stringMappedFailure = thrown(JsonApiValidationException)
    stringMappedFailure.ruleCode() == ValidationRuleCode.FULL_LINKAGE_VIOLATION

    when:
    writer.writeValueAsBytes(unexempted)

    then:
    def bytesMappedFailure = thrown(JsonApiValidationException)
    bytesMappedFailure.ruleCode() == ValidationRuleCode.FULL_LINKAGE_VIOLATION

    when:
    writer.writeValue(new ByteArrayOutputStream(), unexempted)

    then:
    def streamMappedFailure = thrown(JsonApiValidationException)
    streamMappedFailure.ruleCode() == ValidationRuleCode.FULL_LINKAGE_VIOLATION

    when:
    writer.writeValue(new StringWriter(), unexempted)

    then:
    def charsMappedFailure = thrown(JsonApiValidationException)
    charsMappedFailure.ruleCode() == ValidationRuleCode.FULL_LINKAGE_VIOLATION

    when:
    def failingGenerator = writer.mapper().createGenerator(new ByteArrayOutputStream())
    try {
      writer.writeValue(failingGenerator, unexempted)
    } finally {
      failingGenerator.close()
    }

    then:
    def generatorMappedFailure = thrown(JsonApiValidationException)
    generatorMappedFailure.ruleCode() == ValidationRuleCode.FULL_LINKAGE_VIOLATION
  }

  def "output is fully visible in caller OutputStream and Writer immediately after writing without an explicit flush"() {
    given:
    def mapper = JsonMapper.builder().build()
    def resource = new ResourceObject(
        'articles',
        '1',
        null,
        Attributes.ofAttributes(['title': 'JSON:API paints my bikeshed!']),
        null,
        null,
        null,
        [:])
    def document = JsonApiDocument.withData(new DocumentData.SingleResource(resource))
    def writer = JsonApiJackson3.writer(mapper, ValidationContext.defaults())
    def expected = mapper.readTree(
        '{"data":{"type":"articles","id":"1","attributes":{"title":"JSON:API paints my bikeshed!"}}}')
    def bytesOut = new ByteArrayOutputStream()
    def charsOut = new StringWriter()

    when:
    writer.writeValue(bytesOut, document)
    writer.writeValue(charsOut, document)

    then:
    mapper.readTree(bytesOut.toByteArray()) == expected
    mapper.readTree(charsOut.toString()) == expected
  }

  def "non-closing OutputStream delegates bulk writes to the caller sink"() {
    given:
    def mapper = JsonMapper.builder().build()
    def writer = JsonApiJackson3.writer(mapper, ValidationContext.defaults())
    def out = new BulkOnlyOutputStream()

    when:
    writer.writeValue(out, JsonApiDocument.withMeta(Meta.of([count: 2])))

    then:
    out.bulkWriteCount() > 0
    mapper.readTree(out.bytes()) == mapper.readTree('{"meta":{"count":2}}')
  }

  def "writer leaves caller-owned sinks open when emission fails"() {
    given:
    def writer = JsonApiJackson3.writer(JsonMapper.builder().build(), ValidationContext.defaults())
    def document = JsonApiDocument.withMeta(Meta.of([count: 1]))
    def stream = new FailingCloseTrackingOutputStream()
    def chars = new FailingCloseTrackingWriter()

    when:
    writer.writeValue(stream, document)

    then:
    thrown(JacksonException)
    !stream.closed

    when:
    writer.writeValue(chars, document)

    then:
    thrown(JacksonException)
    !chars.closed
  }

  def "writer leaves a caller-created JsonGenerator open for the caller to close"() {
    given:
    def mapper = JsonMapper.builder().build()
    def resource = new ResourceObject(
        'articles',
        '1',
        null,
        Attributes.ofAttributes(['title': 'JSON:API paints my bikeshed!']),
        null,
        null,
        null,
        [:])
    def document = JsonApiDocument.withData(new DocumentData.SingleResource(resource))
    def writer = JsonApiJackson3.writer(mapper, ValidationContext.defaults())
    def sink = new ByteArrayOutputStream()
    def generator = writer.mapper().createGenerator(sink)

    when:
    writer.writeValue(generator, document)

    then:
    !generator.isClosed()

    when:
    generator.close()

    then:
    mapper.readTree(sink.toByteArray()) == mapper.readTree(
        '{"data":{"type":"articles","id":"1","attributes":{"title":"JSON:API paints my bikeshed!"}}}')
  }

  private static final class BulkOnlyOutputStream extends OutputStream {
    private final ByteArrayOutputStream delegate = new ByteArrayOutputStream()
    private int bulkWriteCount

    @Override
    void write(int value) {
      throw new AssertionError('Expected bulk write delegation')
    }

    @Override
    void write(byte[] source, int offset, int length) {
      bulkWriteCount++
      delegate.write(source, offset, length)
    }

    int bulkWriteCount() {
      return bulkWriteCount
    }

    byte[] bytes() {
      return delegate.toByteArray()
    }
  }

  private static final class FailingCloseTrackingOutputStream extends OutputStream {
    boolean closed

    @Override
    void write(int value) throws IOException {
      throw new IOException('synthetic output failure')
    }

    @Override
    void write(byte[] source, int offset, int length) throws IOException {
      throw new IOException('synthetic output failure')
    }

    @Override
    void close() {
      closed = true
    }
  }

  private static final class FailingCloseTrackingWriter extends Writer {
    boolean closed

    @Override
    void write(char[] source, int offset, int length) throws IOException {
      throw new IOException('synthetic writer failure')
    }

    @Override
    void flush() {
      // No buffered state: this double fails on write before any flush path is exercised.
    }

    @Override
    void close() {
      closed = true
    }
  }
}
