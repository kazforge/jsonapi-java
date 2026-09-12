package com.kazforge.jsonapi.jackson3;

import com.kazforge.jsonapi.core.model.JsonApiDocument;
import com.kazforge.jsonapi.jackson.api.JsonApiDocuments;
import com.kazforge.jsonapi.jackson.document.DocumentReadContext;
import com.kazforge.jsonapi.jackson.mapping.MappedDocument;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Objects;
import tools.jackson.databind.json.JsonMapper;

/**
 * Jackson 3 Level-1 raw/general JSON:API documents.
 *
 * <p>Reads stay explicit where inference is unsafe: callers supply the semantic read context rather
 * than relying on the facade to guess ambiguous shapes. Writes validate as response/other usage
 * only; raw create/update request writing stays advanced.
 */
final class Jackson3JsonApiDocuments implements JsonApiDocuments {

  private final JsonMapper baseMapper;
  private final JsonApiDocumentWriter responseWriter;

  Jackson3JsonApiDocuments(JsonMapper baseMapper, JsonApiDocumentWriter responseWriter) {
    this.baseMapper = Objects.requireNonNull(baseMapper, "baseMapper");
    this.responseWriter = Objects.requireNonNull(responseWriter, "responseWriter");
  }

  @Override
  public JsonApiDocument read(String json, DocumentReadContext context) {
    Objects.requireNonNull(json, "json");
    Objects.requireNonNull(context, "context");
    return new JsonApiDocumentReader(baseMapper, context).readValue(json);
  }

  @Override
  public JsonApiDocument read(InputStream json, DocumentReadContext context) {
    Objects.requireNonNull(json, "json");
    Objects.requireNonNull(context, "context");
    return new JsonApiDocumentReader(baseMapper, context).readValue(json);
  }

  @Override
  public String write(JsonApiDocument document) {
    Objects.requireNonNull(document, "document");
    return responseWriter.writeValueAsString(document);
  }

  @Override
  public void write(JsonApiDocument document, OutputStream out) {
    Objects.requireNonNull(document, "document");
    Objects.requireNonNull(out, "out");
    responseWriter.writeValue(out, document);
  }

  @Override
  public String write(MappedDocument mapped) {
    Objects.requireNonNull(mapped, "mapped");
    return responseWriter.writeValueAsString(mapped);
  }

  @Override
  public void write(MappedDocument mapped, OutputStream out) {
    Objects.requireNonNull(mapped, "mapped");
    Objects.requireNonNull(out, "out");
    responseWriter.writeValue(out, mapped);
  }
}
