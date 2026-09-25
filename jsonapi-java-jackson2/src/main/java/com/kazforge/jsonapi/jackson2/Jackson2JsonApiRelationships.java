package com.kazforge.jsonapi.jackson2;

import com.kazforge.jsonapi.api.JsonApiRelationships;
import com.kazforge.jsonapi.core.model.ResourceIdentifier;
import com.kazforge.jsonapi.mapping.internal.PrimaryDataShape;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Jackson 2 Level-1 relationship-linkage documents in both directions.
 *
 * <p>Reads require identifier primary data and never coerce across to-one and to-many shapes. The
 * bound reader explicitly composes identifier decoding with the relationship endpoint role, while
 * the bound writer validates linkage documents under that same role. Writes emit minimal linkage
 * documents without requiring domain DTO registration. Checked Jackson 2 I/O is exposed as {@link
 * java.io.UncheckedIOException} at this Level-1 boundary.
 */
final class Jackson2JsonApiRelationships implements JsonApiRelationships {

  private final JsonApiDocumentReader relationshipReader;
  private final JsonApiDocumentWriter relationshipWriter;

  Jackson2JsonApiRelationships(
      JsonApiDocumentReader relationshipReader, JsonApiDocumentWriter relationshipWriter) {
    this.relationshipReader = Objects.requireNonNull(relationshipReader, "relationshipReader");
    this.relationshipWriter = Objects.requireNonNull(relationshipWriter, "relationshipWriter");
  }

  @Override
  public @Nullable ResourceIdentifier readToOne(String json) {
    Objects.requireNonNull(json, "json");
    return PrimaryDataShape.requireToOne(Jackson2Io.call(() -> relationshipReader.readValue(json)));
  }

  @Override
  public @Nullable ResourceIdentifier readToOne(InputStream json) {
    Objects.requireNonNull(json, "json");
    return PrimaryDataShape.requireToOne(Jackson2Io.call(() -> relationshipReader.readValue(json)));
  }

  @Override
  public List<ResourceIdentifier> readToMany(String json) {
    Objects.requireNonNull(json, "json");
    return PrimaryDataShape.requireToMany(
        Jackson2Io.call(() -> relationshipReader.readValue(json)));
  }

  @Override
  public List<ResourceIdentifier> readToMany(InputStream json) {
    Objects.requireNonNull(json, "json");
    return PrimaryDataShape.requireToMany(
        Jackson2Io.call(() -> relationshipReader.readValue(json)));
  }

  @Override
  public String writeToOne(@Nullable ResourceIdentifier identifier) {
    return Jackson2Io.call(
        () -> relationshipWriter.writeValueAsString(PrimaryDataShape.linkageDocument(identifier)));
  }

  @Override
  public void writeToOne(@Nullable ResourceIdentifier identifier, OutputStream out) {
    Objects.requireNonNull(out, "out");
    Jackson2Io.run(
        () -> relationshipWriter.writeValue(out, PrimaryDataShape.linkageDocument(identifier)));
  }

  @Override
  @SuppressWarnings("java:S4968")
  public String writeToMany(List<? extends ResourceIdentifier> identifiers) {
    Objects.requireNonNull(identifiers, "identifiers");
    return Jackson2Io.call(
        () ->
            relationshipWriter.writeValueAsString(
                PrimaryDataShape.linkageCollectionDocument(List.copyOf(identifiers))));
  }

  @Override
  @SuppressWarnings("java:S4968")
  public void writeToMany(List<? extends ResourceIdentifier> identifiers, OutputStream out) {
    Objects.requireNonNull(identifiers, "identifiers");
    Objects.requireNonNull(out, "out");
    Jackson2Io.run(
        () ->
            relationshipWriter.writeValue(
                out, PrimaryDataShape.linkageCollectionDocument(List.copyOf(identifiers))));
  }
}
