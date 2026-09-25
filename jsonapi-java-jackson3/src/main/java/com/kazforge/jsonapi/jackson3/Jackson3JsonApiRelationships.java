package com.kazforge.jsonapi.jackson3;

import com.kazforge.jsonapi.api.JsonApiRelationships;
import com.kazforge.jsonapi.core.model.ResourceIdentifier;
import com.kazforge.jsonapi.mapping.internal.PrimaryDataShape;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
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

  @Override
  public @Nullable ResourceIdentifier readToOne(String json) {
    Objects.requireNonNull(json, "json");
    return PrimaryDataShape.requireToOne(relationshipReader.readValue(json));
  }

  @Override
  public @Nullable ResourceIdentifier readToOne(InputStream json) {
    Objects.requireNonNull(json, "json");
    return PrimaryDataShape.requireToOne(relationshipReader.readValue(json));
  }

  @Override
  public List<ResourceIdentifier> readToMany(String json) {
    Objects.requireNonNull(json, "json");
    return PrimaryDataShape.requireToMany(relationshipReader.readValue(json));
  }

  @Override
  public List<ResourceIdentifier> readToMany(InputStream json) {
    Objects.requireNonNull(json, "json");
    return PrimaryDataShape.requireToMany(relationshipReader.readValue(json));
  }

  @Override
  public String writeToOne(@Nullable ResourceIdentifier identifier) {
    return relationshipWriter.writeValueAsString(PrimaryDataShape.linkageDocument(identifier));
  }

  @Override
  public void writeToOne(@Nullable ResourceIdentifier identifier, OutputStream out) {
    Objects.requireNonNull(out, "out");
    relationshipWriter.writeValue(out, PrimaryDataShape.linkageDocument(identifier));
  }

  // The wildcard mirrors the neutral contract: it is redundant only because
  // ResourceIdentifier is final, and lets callers pass lists held through generic APIs.
  @Override
  @SuppressWarnings("java:S4968")
  public String writeToMany(List<? extends ResourceIdentifier> identifiers) {
    Objects.requireNonNull(identifiers, "identifiers");
    return relationshipWriter.writeValueAsString(
        PrimaryDataShape.linkageCollectionDocument(List.copyOf(identifiers)));
  }

  @Override
  @SuppressWarnings("java:S4968")
  public void writeToMany(List<? extends ResourceIdentifier> identifiers, OutputStream out) {
    Objects.requireNonNull(identifiers, "identifiers");
    Objects.requireNonNull(out, "out");
    relationshipWriter.writeValue(
        out, PrimaryDataShape.linkageCollectionDocument(List.copyOf(identifiers)));
  }
}
