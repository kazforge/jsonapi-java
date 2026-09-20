package com.kazforge.jsonapi.jackson3;

import com.kazforge.jsonapi.api.JsonApiRelationships;
import com.kazforge.jsonapi.core.model.DocumentData;
import com.kazforge.jsonapi.core.model.JsonApiDocument;
import com.kazforge.jsonapi.core.model.ResourceIdentifier;
import com.kazforge.jsonapi.diagnostic.JsonApiMappingException;
import com.kazforge.jsonapi.diagnostic.MappingDiagnostic;
import com.kazforge.jsonapi.diagnostic.MappingLocation;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Jackson 3 Level-1 relationship-linkage documents in both directions.
 *
 * <p>Reads require identifier primary data and never coerce across to-one and to-many shapes. The
 * bound reader explicitly composes identifier decoding with the relationship endpoint role, while
 * the bound writer validates linkage documents under that same role. Writes emit minimal linkage
 * documents without requiring domain DTO registration. Top-level linkage-document members stay on
 * the documents facet.
 */
final class Jackson3JsonApiRelationships implements JsonApiRelationships {

  private final JsonApiDocumentReader relationshipReader;
  private final JsonApiDocumentWriter relationshipWriter;

  Jackson3JsonApiRelationships(
      JsonApiDocumentReader relationshipReader, JsonApiDocumentWriter relationshipWriter) {
    this.relationshipReader = Objects.requireNonNull(relationshipReader, "relationshipReader");
    this.relationshipWriter = Objects.requireNonNull(relationshipWriter, "relationshipWriter");
  }

  /** Document reader bound to this facet, for structural assertions in its own package. */
  JsonApiDocumentReader reader() {
    return relationshipReader;
  }

  /** Document writer bound to this facet, for structural assertions in its own package. */
  JsonApiDocumentWriter writer() {
    return relationshipWriter;
  }

  @Override
  public @Nullable ResourceIdentifier readToOne(String json) {
    Objects.requireNonNull(json, "json");
    return requireToOne(relationshipReader.readValue(json));
  }

  @Override
  public @Nullable ResourceIdentifier readToOne(InputStream json) {
    Objects.requireNonNull(json, "json");
    return requireToOne(relationshipReader.readValue(json));
  }

  @Override
  public List<ResourceIdentifier> readToMany(String json) {
    Objects.requireNonNull(json, "json");
    return requireToMany(relationshipReader.readValue(json));
  }

  @Override
  public List<ResourceIdentifier> readToMany(InputStream json) {
    Objects.requireNonNull(json, "json");
    return requireToMany(relationshipReader.readValue(json));
  }

  @Override
  public String writeToOne(@Nullable ResourceIdentifier identifier) {
    return relationshipWriter.writeValueAsString(linkageDocument(identifier));
  }

  @Override
  public void writeToOne(@Nullable ResourceIdentifier identifier, OutputStream out) {
    Objects.requireNonNull(out, "out");
    relationshipWriter.writeValue(out, linkageDocument(identifier));
  }

  // The wildcard mirrors the neutral contract: it is redundant only because
  // ResourceIdentifier is final, and lets callers pass lists held through generic APIs.
  @Override
  @SuppressWarnings("java:S4968")
  public String writeToMany(List<? extends ResourceIdentifier> identifiers) {
    Objects.requireNonNull(identifiers, "identifiers");
    return relationshipWriter.writeValueAsString(
        linkageCollectionDocument(List.copyOf(identifiers)));
  }

  @Override
  @SuppressWarnings("java:S4968")
  public void writeToMany(List<? extends ResourceIdentifier> identifiers, OutputStream out) {
    Objects.requireNonNull(identifiers, "identifiers");
    Objects.requireNonNull(out, "out");
    relationshipWriter.writeValue(out, linkageCollectionDocument(List.copyOf(identifiers)));
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
