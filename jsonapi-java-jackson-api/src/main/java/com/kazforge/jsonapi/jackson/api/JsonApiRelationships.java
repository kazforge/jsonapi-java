package com.kazforge.jsonapi.jackson.api;

import com.kazforge.jsonapi.core.model.ResourceIdentifier;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Level-1 relationship-linkage document operations in both directions.
 *
 * <p>The facet is linkage-only: it expresses to-one, explicit-null, and to-many identifier primary
 * data without domain DTO registration and without hydrating related-resource documents. A to-one
 * read never accepts a collection and a to-many read never accepts one identifier or null;
 * cross-shape states fail instead of coercing. Related-resource handling and top-level document
 * members on linkage documents stay on the documents facet or an advanced path.
 */
public interface JsonApiRelationships {

  /**
   * Reads a to-one linkage document. Returns the identifier, or {@code null} for explicit {@code
   * data: null} linkage.
   */
  @Nullable ResourceIdentifier readToOne(String json);

  /**
   * Stream variant of {@link #readToOne(String)}. The stream is not closed. Returns the identifier,
   * or {@code null} for explicit {@code data: null} linkage.
   */
  @Nullable ResourceIdentifier readToOne(InputStream json);

  /** Reads a to-many linkage document, including the empty collection. */
  List<ResourceIdentifier> readToMany(String json);

  /** Stream variant of {@link #readToMany(String)}. The stream is not closed. */
  List<ResourceIdentifier> readToMany(InputStream json);

  /**
   * Writes a to-one linkage document and returns its JSON. A {@code null} identifier emits explicit
   * {@code data: null} linkage.
   */
  String writeToOne(@Nullable ResourceIdentifier identifier);

  /**
   * Writes a to-one linkage document to the given stream. The stream is not closed. A {@code null}
   * identifier emits explicit {@code data: null} linkage.
   */
  void writeToOne(@Nullable ResourceIdentifier identifier, OutputStream out);

  /**
   * Writes a to-many linkage document and returns its JSON.
   *
   * <p>The wildcard bound lets callers pass lists held through generic APIs; it is redundant only
   * because {@code ResourceIdentifier} is final.
   */
  @SuppressWarnings("java:S4968")
  String writeToMany(List<? extends ResourceIdentifier> identifiers);

  /**
   * Writes a to-many linkage document to the given stream. The stream is not closed.
   *
   * <p>The wildcard bound lets callers pass lists held through generic APIs; it is redundant only
   * because {@code ResourceIdentifier} is final.
   */
  @SuppressWarnings("java:S4968")
  void writeToMany(List<? extends ResourceIdentifier> identifiers, OutputStream out);
}
