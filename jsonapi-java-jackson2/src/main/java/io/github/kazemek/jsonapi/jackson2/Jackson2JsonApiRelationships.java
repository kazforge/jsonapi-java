package io.github.kazemek.jsonapi.jackson2;

import io.github.kazemek.jsonapi.core.model.DocumentData;
import io.github.kazemek.jsonapi.core.model.JsonApiDocument;
import io.github.kazemek.jsonapi.core.model.ResourceIdentifier;
import io.github.kazemek.jsonapi.jackson.api.JsonApiRelationships;
import io.github.kazemek.jsonapi.jackson.diagnostic.JsonApiMappingException;
import io.github.kazemek.jsonapi.jackson.diagnostic.MappingDiagnostic;
import io.github.kazemek.jsonapi.jackson.diagnostic.MappingLocation;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Jackson 2 Level-1 relationship-linkage documents in both directions.
 *
 * <p>Reads require identifier primary data and never coerce across to-one and to-many shapes.
 * Writes emit minimal linkage documents without requiring domain DTO registration. Checked Jackson
 * 2 I/O is exposed as {@link java.io.UncheckedIOException} at this Level-1 boundary.
 */
final class Jackson2JsonApiRelationships implements JsonApiRelationships {

  private final JsonApiDocumentReader identifierReader;
  private final JsonApiDocumentWriter responseWriter;

  Jackson2JsonApiRelationships(
      JsonApiDocumentReader identifierReader, JsonApiDocumentWriter responseWriter) {
    this.identifierReader = Objects.requireNonNull(identifierReader, "identifierReader");
    this.responseWriter = Objects.requireNonNull(responseWriter, "responseWriter");
  }

  @Override
  public @Nullable ResourceIdentifier readToOne(String json) {
    Objects.requireNonNull(json, "json");
    return requireToOne(Jackson2Io.call(() -> identifierReader.readValue(json)));
  }

  @Override
  public @Nullable ResourceIdentifier readToOne(InputStream json) {
    Objects.requireNonNull(json, "json");
    return requireToOne(Jackson2Io.call(() -> identifierReader.readValue(json)));
  }

  @Override
  public List<ResourceIdentifier> readToMany(String json) {
    Objects.requireNonNull(json, "json");
    return requireToMany(Jackson2Io.call(() -> identifierReader.readValue(json)));
  }

  @Override
  public List<ResourceIdentifier> readToMany(InputStream json) {
    Objects.requireNonNull(json, "json");
    return requireToMany(Jackson2Io.call(() -> identifierReader.readValue(json)));
  }

  @Override
  public String writeToOne(@Nullable ResourceIdentifier identifier) {
    return Jackson2Io.call(() -> responseWriter.writeValueAsString(linkageDocument(identifier)));
  }

  @Override
  public void writeToOne(@Nullable ResourceIdentifier identifier, OutputStream out) {
    Objects.requireNonNull(out, "out");
    Jackson2Io.run(() -> responseWriter.writeValue(out, linkageDocument(identifier)));
  }

  @Override
  @SuppressWarnings("java:S4968")
  public String writeToMany(List<? extends ResourceIdentifier> identifiers) {
    Objects.requireNonNull(identifiers, "identifiers");
    return Jackson2Io.call(
        () ->
            responseWriter.writeValueAsString(linkageCollectionDocument(List.copyOf(identifiers))));
  }

  @Override
  @SuppressWarnings("java:S4968")
  public void writeToMany(List<? extends ResourceIdentifier> identifiers, OutputStream out) {
    Objects.requireNonNull(identifiers, "identifiers");
    Objects.requireNonNull(out, "out");
    Jackson2Io.run(
        () -> responseWriter.writeValue(out, linkageCollectionDocument(List.copyOf(identifiers))));
  }

  private static @Nullable ResourceIdentifier requireToOne(JsonApiDocument document) {
    if (document.data() instanceof DocumentData.SingleIdentifier(ResourceIdentifier identifier)) {
      return identifier;
    }
    if (document.data() instanceof DocumentData.NullData) {
      return null;
    }
    throw linkageMismatch("to-one identifier or explicit null", document);
  }

  private static List<ResourceIdentifier> requireToMany(JsonApiDocument document) {
    if (document.data()
        instanceof DocumentData.IdentifierCollection(List<ResourceIdentifier> identifiers)) {
      return identifiers;
    }
    throw linkageMismatch("to-many identifier collection", document);
  }

  private static JsonApiDocument linkageDocument(@Nullable ResourceIdentifier identifier) {
    DocumentData data =
        identifier == null
            ? DocumentData.NullData.INSTANCE
            : new DocumentData.SingleIdentifier(identifier);
    return new JsonApiDocument(data, null, null, null, null, null, Map.of());
  }

  private static JsonApiDocument linkageCollectionDocument(List<ResourceIdentifier> identifiers) {
    for (ResourceIdentifier identifier : identifiers) {
      Objects.requireNonNull(identifier, "identifiers element");
    }
    return new JsonApiDocument(
        new DocumentData.IdentifierCollection(identifiers), null, null, null, null, null, Map.of());
  }

  private static JsonApiMappingException linkageMismatch(
      String expected, JsonApiDocument document) {
    String actual;
    if (document.errors() != null) {
      actual = "an error document";
    } else if (document.data() == null) {
      actual = "absent data";
    } else if (document.data() instanceof DocumentData.NullData) {
      actual = "explicit null data";
    } else if (document.data() instanceof DocumentData.SingleResource) {
      actual = "single-resource data";
    } else if (document.data() instanceof DocumentData.ResourceCollection) {
      actual = "resource-collection data";
    } else if (document.data() instanceof DocumentData.SingleIdentifier) {
      actual = "single-identifier data";
    } else {
      actual = "identifier-collection data";
    }
    return new JsonApiMappingException(
        MappingDiagnostic.RESOURCE_TYPE_MISMATCH,
        null,
        MappingLocation.of("data"),
        "Level-1 relationship read requires " + expected + " primary data but found " + actual);
  }
}
