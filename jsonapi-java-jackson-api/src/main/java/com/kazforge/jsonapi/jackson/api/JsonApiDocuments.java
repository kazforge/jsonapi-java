package com.kazforge.jsonapi.jackson.api;

import com.kazforge.jsonapi.core.model.JsonApiDocument;
import com.kazforge.jsonapi.jackson.document.DocumentReadContext;
import com.kazforge.jsonapi.jackson.mapping.MappedDocument;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Level-1 raw/general JSON:API document operations.
 *
 * <p>Raw document operations stay explicit where inference is unsafe: callers supply the semantic
 * {@link DocumentReadContext} (including the primary-data kind) rather than relying on the facade
 * to guess ambiguous document shapes. Writes validate before emission as response/other usage;
 * {@link MappedDocument} writes additionally compose sparse-fieldset linkage provenance into
 * validation. Raw create/update request writing stays advanced: ordinary request authoring uses
 * {@link JsonApiResources#writeCreateDocument(Object)} and {@link
 * JsonApiResources#writeUpdateDocument(Object,
 * com.kazforge.jsonapi.core.validation.EndpointIdentity)}.
 */
public interface JsonApiDocuments {

  /** Decodes and validates one document under the given read context. */
  JsonApiDocument read(String json, DocumentReadContext context);

  /** Stream variant of {@link #read(String, DocumentReadContext)}. The stream is not closed. */
  JsonApiDocument read(InputStream json, DocumentReadContext context);

  /** Validates a document as response/other usage, then returns its JSON. */
  String write(JsonApiDocument document);

  /**
   * Validates a document as response/other usage, then writes it to the given stream. The stream is
   * not closed.
   */
  void write(JsonApiDocument document, OutputStream out);

  /**
   * Validates a mapped document as response/other usage against the bound context composed with its
   * sparse-fieldset linkage provenance, then returns its JSON.
   */
  String write(MappedDocument mapped);

  /**
   * Validates a mapped document as response/other usage against the bound context composed with its
   * sparse-fieldset linkage provenance, then writes it to the given stream. The stream is not
   * closed.
   */
  void write(MappedDocument mapped, OutputStream out);
}
