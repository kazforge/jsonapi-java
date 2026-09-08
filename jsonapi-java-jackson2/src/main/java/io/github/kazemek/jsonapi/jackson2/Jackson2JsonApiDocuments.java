package io.github.kazemek.jsonapi.jackson2;

import com.fasterxml.jackson.databind.json.JsonMapper;
import io.github.kazemek.jsonapi.core.model.JsonApiDocument;
import io.github.kazemek.jsonapi.jackson.api.JsonApiDocuments;
import io.github.kazemek.jsonapi.jackson.document.DocumentReadContext;
import io.github.kazemek.jsonapi.jackson.mapping.MappedDocument;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Objects;

/**
 * Jackson 2 Level-1 raw/general JSON:API documents.
 *
 * <p>Reads stay explicit where inference is unsafe: callers supply the semantic read context rather
 * than relying on the facade to guess ambiguous shapes. Writes validate as response/other usage
 * only; raw create/update request writing stays advanced. Checked Jackson 2 I/O is exposed as
 * {@link java.io.UncheckedIOException} at this Level-1 boundary.
 */
final class Jackson2JsonApiDocuments implements JsonApiDocuments {

  private final JsonMapper baseMapper;
  private final JsonApiDocumentWriter responseWriter;

  Jackson2JsonApiDocuments(JsonMapper baseMapper, JsonApiDocumentWriter responseWriter) {
    this.baseMapper = Objects.requireNonNull(baseMapper, "baseMapper");
    this.responseWriter = Objects.requireNonNull(responseWriter, "responseWriter");
  }

  @Override
  public JsonApiDocument read(String json, DocumentReadContext context) {
    Objects.requireNonNull(json, "json");
    Objects.requireNonNull(context, "context");
    return Jackson2Io.call(() -> new JsonApiDocumentReader(baseMapper, context).readValue(json));
  }

  @Override
  public JsonApiDocument read(InputStream json, DocumentReadContext context) {
    Objects.requireNonNull(json, "json");
    Objects.requireNonNull(context, "context");
    return Jackson2Io.call(() -> new JsonApiDocumentReader(baseMapper, context).readValue(json));
  }

  @Override
  public String write(JsonApiDocument document) {
    Objects.requireNonNull(document, "document");
    return Jackson2Io.call(() -> responseWriter.writeValueAsString(document));
  }

  @Override
  public void write(JsonApiDocument document, OutputStream out) {
    Objects.requireNonNull(document, "document");
    Objects.requireNonNull(out, "out");
    Jackson2Io.run(() -> responseWriter.writeValue(out, document));
  }

  @Override
  public String write(MappedDocument mapped) {
    Objects.requireNonNull(mapped, "mapped");
    return Jackson2Io.call(() -> responseWriter.writeValueAsString(mapped));
  }

  @Override
  public void write(MappedDocument mapped, OutputStream out) {
    Objects.requireNonNull(mapped, "mapped");
    Objects.requireNonNull(out, "out");
    Jackson2Io.run(() -> responseWriter.writeValue(out, mapped));
  }
}
