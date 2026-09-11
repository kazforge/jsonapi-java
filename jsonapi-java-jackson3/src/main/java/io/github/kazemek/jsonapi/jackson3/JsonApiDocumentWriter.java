package io.github.kazemek.jsonapi.jackson3;

import io.github.kazemek.jsonapi.core.model.JsonApiDocument;
import io.github.kazemek.jsonapi.core.model.ResourceIdentity;
import io.github.kazemek.jsonapi.core.validation.JsonApiDocumentValidator;
import io.github.kazemek.jsonapi.core.validation.ValidationContext;
import io.github.kazemek.jsonapi.jackson.mapping.MappedDocument;
import java.io.FilterOutputStream;
import java.io.FilterWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.json.JsonMapper;

/**
 * Validates a {@link JsonApiDocument} against a bound {@link ValidationContext}, then writes it
 * with deterministic JSON:API v1.1 wire semantics.
 *
 * <p>Aggregate validation always runs before generator output starts, so validation failure cannot
 * leave a partially written document.
 *
 * <p>Writing a {@link MappedDocument} is provenance-aware: this writer composes its bound context
 * with the mapping's sparse-fieldset linkage exemptions before validation, so callers never
 * translate mapping provenance into validation policy themselves. Every other bound setting is
 * preserved; an empty exemption set validates exactly like plain document writing. All output forms
 * share one composition path.
 *
 * <p>Caller-provided {@link OutputStream} and {@link Writer} sinks remain caller-owned and are
 * never closed. The caller owns the buffering and flushing lifecycle of layers it supplies.
 */
public final class JsonApiDocumentWriter {

  private static final String DOCUMENT_PARAM = "document";
  private static final String MAPPED_PARAM = "mapped";

  private final JsonMapper mapper;
  private final ValidationContext context;
  private final JsonApiDocumentValidator validator = new JsonApiDocumentValidator();

  JsonApiDocumentWriter(JsonMapper mapper, ValidationContext context) {
    this.mapper = Objects.requireNonNull(mapper, "mapper");
    this.context = Objects.requireNonNull(context, "context");
  }

  /** Validation context bound to this writer (before mapped-document provenance composition). */
  public ValidationContext context() {
    return context;
  }

  /** Codec-configured mapper used for emission (derived; not the caller's original mapper). */
  JsonMapper mapper() {
    return mapper;
  }

  public String writeValueAsString(JsonApiDocument document) {
    Objects.requireNonNull(document, DOCUMENT_PARAM);
    validator.validate(document, context);
    return mapper.writeValueAsString(document);
  }

  public byte[] writeValueAsBytes(JsonApiDocument document) {
    Objects.requireNonNull(document, DOCUMENT_PARAM);
    validator.validate(document, context);
    return mapper.writeValueAsBytes(document);
  }

  /**
   * Validates {@code document} against the bound context, then writes it to {@code out}. The stream
   * remains caller-owned and is not closed; the caller owns its buffering and flushing lifecycle.
   * Only the generator created for this call is closed.
   */
  public void writeValue(OutputStream out, JsonApiDocument document) {
    Objects.requireNonNull(out, "out");
    Objects.requireNonNull(document, DOCUMENT_PARAM);
    validator.validate(document, context);
    mapper.writeValue(nonClosing(out), document);
  }

  /**
   * Validates {@code document} against the bound context, then writes it to {@code out}. The writer
   * remains caller-owned and is not closed; the caller owns its buffering and flushing lifecycle.
   * Only the generator created for this call is closed.
   */
  public void writeValue(Writer out, JsonApiDocument document) {
    Objects.requireNonNull(out, "out");
    Objects.requireNonNull(document, DOCUMENT_PARAM);
    validator.validate(document, context);
    mapper.writeValue(nonClosing(out), document);
  }

  /**
   * Validates {@code document} against the bound context, then writes it through {@code generator}.
   * The caller owns the generator and closes it.
   */
  public void writeValue(JsonGenerator generator, JsonApiDocument document) {
    Objects.requireNonNull(generator, "generator");
    Objects.requireNonNull(document, DOCUMENT_PARAM);
    validator.validate(document, context);
    mapper.writeValue(generator, document);
  }

  /**
   * Validates {@code mapped.document()} against the bound context composed with {@code mapped}'s
   * sparse-fieldset linkage exemptions, then returns the JSON string.
   */
  public String writeValueAsString(MappedDocument mapped) {
    return mapper.writeValueAsString(validatedDocument(mapped));
  }

  /**
   * Validates {@code mapped.document()} against the bound context composed with {@code mapped}'s
   * sparse-fieldset linkage exemptions, then returns the UTF-8 JSON bytes.
   */
  public byte[] writeValueAsBytes(MappedDocument mapped) {
    return mapper.writeValueAsBytes(validatedDocument(mapped));
  }

  /**
   * Validates {@code mapped.document()} against the bound context composed with {@code mapped}'s
   * sparse-fieldset linkage exemptions, then writes it to {@code out}. The stream is not closed; it
   * remains caller-owned, including its buffering and flushing lifecycle. Only the generator
   * created for this call is closed.
   */
  public void writeValue(OutputStream out, MappedDocument mapped) {
    Objects.requireNonNull(out, "out");
    mapper.writeValue(nonClosing(out), validatedDocument(mapped));
  }

  /**
   * Validates {@code mapped.document()} against the bound context composed with {@code mapped}'s
   * sparse-fieldset linkage exemptions, then writes it to {@code out}. The writer is not closed; it
   * remains caller-owned, including its buffering and flushing lifecycle. Only the generator
   * created for this call is closed.
   */
  public void writeValue(Writer out, MappedDocument mapped) {
    Objects.requireNonNull(out, "out");
    mapper.writeValue(nonClosing(out), validatedDocument(mapped));
  }

  /**
   * Validates {@code mapped.document()} against the bound context composed with {@code mapped}'s
   * sparse-fieldset linkage exemptions, then writes it through {@code generator}. The caller owns
   * the generator and closes it.
   */
  public void writeValue(JsonGenerator generator, MappedDocument mapped) {
    Objects.requireNonNull(generator, "generator");
    mapper.writeValue(generator, validatedDocument(mapped));
  }

  /**
   * Single provenance-composition path shared by every output form: derive the effective context
   * from the bound base plus the mapping's sparse-fieldset linkage exemptions, validate first, and
   * only then emit.
   */
  private JsonApiDocument validatedDocument(MappedDocument mapped) {
    Objects.requireNonNull(mapped, MAPPED_PARAM);
    Set<ResourceIdentity> mappedExemptions = mapped.sparseFieldsetLinkageExemptions();
    ValidationContext effective =
        mappedExemptions.isEmpty() ? context : mergedContext(mappedExemptions);
    validator.validate(mapped.document(), effective);
    return mapped.document();
  }

  private ValidationContext mergedContext(Set<ResourceIdentity> mappedExemptions) {
    Set<ResourceIdentity> exemptions = new HashSet<>(context.sparseFieldsetLinkageExemptions());
    exemptions.addAll(mappedExemptions);
    return context.withSparseFieldsetLinkageExemptions(exemptions);
  }

  /**
   * Wraps a caller-owned stream so Jackson's generator close cannot propagate to it. Mirrors the
   * reader's non-closing input handling: the caller owns the underlying sink.
   */
  private static OutputStream nonClosing(OutputStream delegate) {
    return new FilterOutputStream(delegate) {
      @Override
      public void write(byte[] source, int offset, int length) throws IOException {
        out.write(source, offset, length);
      }

      @Override
      public void close() {
        // Caller owns the underlying stream.
      }
    };
  }

  /**
   * Wraps a caller-owned writer so Jackson's generator close cannot propagate to it. Mirrors the
   * reader's non-closing input handling: the caller owns the underlying sink.
   */
  private static Writer nonClosing(Writer delegate) {
    return new FilterWriter(delegate) {
      @Override
      public void close() {
        // Caller owns the underlying writer.
      }
    };
  }
}
