package io.github.kazemek.jsonapi.jackson2;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.json.JsonMapper;
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

/**
 * Validates a {@link JsonApiDocument} against a bound {@link ValidationContext}, then writes it
 * with deterministic JSON:API v1.1 wire semantics.
 *
 * <p>Aggregate validation always runs before generator output starts, so validation failure cannot
 * leave a partially written document. Validation failures surface as unchecked {@link
 * io.github.kazemek.jsonapi.core.validation.JsonApiValidationException}; emission failures
 * propagate Jackson 2's checked {@link IOException} mechanics unchanged rather than introducing a
 * new exception family.
 *
 * <p>Writing a {@link MappedDocument} is provenance-aware: this writer composes its bound context
 * with the mapping's sparse-fieldset linkage exemptions before validation, so callers never
 * translate mapping provenance into validation policy themselves. Every other bound setting is
 * preserved; an empty exemption set validates exactly like plain document writing. All output forms
 * share one composition path.
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

  /**
   * Validates {@code document} against the bound context, then returns the JSON string.
   *
   * @throws IOException if emission fails through Jackson 2's checked exception mechanics
   */
  public String writeValueAsString(JsonApiDocument document) throws IOException {
    Objects.requireNonNull(document, DOCUMENT_PARAM);
    validator.validate(document, context);
    return mapper.writeValueAsString(document);
  }

  /**
   * Validates {@code document} against the bound context, then returns the UTF-8 JSON bytes.
   *
   * @throws IOException if emission fails through Jackson 2's checked exception mechanics
   */
  public byte[] writeValueAsBytes(JsonApiDocument document) throws IOException {
    Objects.requireNonNull(document, DOCUMENT_PARAM);
    validator.validate(document, context);
    return mapper.writeValueAsBytes(document);
  }

  /**
   * Validates {@code document} against the bound context, then writes it to {@code out}. The stream
   * is not closed; only the generator created for this call is closed.
   *
   * @throws IOException if emission fails through Jackson 2's checked exception mechanics
   */
  public void writeValue(OutputStream out, JsonApiDocument document) throws IOException {
    Objects.requireNonNull(out, "out");
    Objects.requireNonNull(document, DOCUMENT_PARAM);
    validator.validate(document, context);
    mapper.writeValue(nonClosing(out), document);
  }

  /**
   * Validates {@code document} against the bound context, then writes it to {@code out}. The writer
   * is not closed; only the generator created for this call is closed.
   *
   * @throws IOException if emission fails through Jackson 2's checked exception mechanics
   */
  public void writeValue(Writer out, JsonApiDocument document) throws IOException {
    Objects.requireNonNull(out, "out");
    Objects.requireNonNull(document, DOCUMENT_PARAM);
    validator.validate(document, context);
    mapper.writeValue(nonClosing(out), document);
  }

  /**
   * Validates {@code document} against the bound context, then writes it through {@code generator}.
   * The caller owns the generator and closes it.
   *
   * @throws IOException if emission fails through Jackson 2's checked exception mechanics
   */
  public void writeValue(JsonGenerator generator, JsonApiDocument document) throws IOException {
    Objects.requireNonNull(generator, "generator");
    Objects.requireNonNull(document, DOCUMENT_PARAM);
    validator.validate(document, context);
    mapper.writeValue(generator, document);
  }

  /**
   * Validates {@code mapped.document()} against the bound context composed with {@code mapped}'s
   * sparse-fieldset linkage exemptions, then returns the JSON string.
   *
   * @throws IOException if emission fails through Jackson 2's checked exception mechanics
   */
  public String writeValueAsString(MappedDocument mapped) throws IOException {
    return mapper.writeValueAsString(validatedDocument(mapped));
  }

  /**
   * Validates {@code mapped.document()} against the bound context composed with {@code mapped}'s
   * sparse-fieldset linkage exemptions, then returns the UTF-8 JSON bytes.
   *
   * @throws IOException if emission fails through Jackson 2's checked exception mechanics
   */
  public byte[] writeValueAsBytes(MappedDocument mapped) throws IOException {
    return mapper.writeValueAsBytes(validatedDocument(mapped));
  }

  /**
   * Validates {@code mapped.document()} against the bound context composed with {@code mapped}'s
   * sparse-fieldset linkage exemptions, then writes it to {@code out}. The stream is not closed;
   * only the generator created for this call is closed.
   *
   * @throws IOException if emission fails through Jackson 2's checked exception mechanics
   */
  public void writeValue(OutputStream out, MappedDocument mapped) throws IOException {
    Objects.requireNonNull(out, "out");
    mapper.writeValue(nonClosing(out), validatedDocument(mapped));
  }

  /**
   * Validates {@code mapped.document()} against the bound context composed with {@code mapped}'s
   * sparse-fieldset linkage exemptions, then writes it to {@code out}. The writer is not closed;
   * only the generator created for this call is closed.
   *
   * @throws IOException if emission fails through Jackson 2's checked exception mechanics
   */
  public void writeValue(Writer out, MappedDocument mapped) throws IOException {
    Objects.requireNonNull(out, "out");
    mapper.writeValue(nonClosing(out), validatedDocument(mapped));
  }

  /**
   * Validates {@code mapped.document()} against the bound context composed with {@code mapped}'s
   * sparse-fieldset linkage exemptions, then writes it through {@code generator}. The caller owns
   * the generator and closes it.
   *
   * @throws IOException if emission fails through Jackson 2's checked exception mechanics
   */
  public void writeValue(JsonGenerator generator, MappedDocument mapped) throws IOException {
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
    validator.validate(
        mapped.document(), effectiveContext(mapped.sparseFieldsetLinkageExemptions()));
    return mapped.document();
  }

  private ValidationContext effectiveContext(Set<ResourceIdentity> mappedExemptions) {
    if (mappedExemptions.isEmpty()) {
      return context;
    }
    Set<ResourceIdentity> exemptions = new HashSet<>();
    exemptions.addAll(context.sparseFieldsetLinkageExemptions());
    exemptions.addAll(mappedExemptions);
    return context.withSparseFieldsetLinkageExemptions(exemptions);
  }

  /** Caller-owned sink that swallows generator close so the underlying stream stays open. */
  private static final class NonClosingOutputStream extends FilterOutputStream {

    private NonClosingOutputStream(OutputStream delegate) {
      super(delegate);
    }

    @Override
    public void close() {
      // Caller owns the underlying stream.
    }
  }

  /** Caller-owned sink that swallows generator close so the underlying writer stays open. */
  private static final class NonClosingWriter extends FilterWriter {

    private NonClosingWriter(Writer delegate) {
      super(delegate);
    }

    @Override
    public void close() {
      // Caller owns the underlying writer.
    }
  }

  /**
   * Wraps a caller-owned stream so Jackson's generator close cannot propagate to it. Mirrors the
   * reader's non-closing input handling: the caller owns the underlying sink.
   */
  private static OutputStream nonClosing(OutputStream delegate) {
    return new NonClosingOutputStream(delegate);
  }

  /**
   * Wraps a caller-owned writer so Jackson's generator close cannot propagate to it. Mirrors the
   * reader's non-closing input handling: the caller owns the underlying sink.
   */
  private static Writer nonClosing(Writer delegate) {
    return new NonClosingWriter(delegate);
  }
}
