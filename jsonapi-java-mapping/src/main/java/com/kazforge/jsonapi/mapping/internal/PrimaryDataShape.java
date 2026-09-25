package com.kazforge.jsonapi.mapping.internal;

import com.kazforge.jsonapi.core.model.DocumentData;
import com.kazforge.jsonapi.core.model.JsonApiDocument;
import com.kazforge.jsonapi.core.model.ResourceIdentifier;
import com.kazforge.jsonapi.core.model.ResourceObject;
import com.kazforge.jsonapi.diagnostic.JsonApiMappingException;
import com.kazforge.jsonapi.diagnostic.MappingDiagnostic;
import com.kazforge.jsonapi.diagnostic.MappingLocation;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Shared Level-1 primary-data shape policy for resource and relationship-linkage documents. */
public final class PrimaryDataShape {

  private static final MappingLocation DATA_LOCATION = MappingLocation.of("data");

  private PrimaryDataShape() {}

  public static ResourceObject requireSingleResource(
      JsonApiDocument document, Class<?> resourceClass) {
    Objects.requireNonNull(resourceClass, "resourceClass");
    if (document.data() instanceof DocumentData.SingleResource(ResourceObject resource)) {
      return resource;
    }
    throw shapeMismatch(resourceClass, "single-resource", describe(document));
  }

  public static List<ResourceObject> requireResourceCollection(JsonApiDocument document) {
    if (document.data() instanceof DocumentData.ResourceCollection(List<ResourceObject> items)) {
      return items;
    }
    throw shapeMismatch(null, "resource-collection", describe(document));
  }

  public static @Nullable ResourceIdentifier requireToOne(JsonApiDocument document) {
    if (document.data() instanceof DocumentData.SingleIdentifier(ResourceIdentifier identifier)) {
      return identifier;
    }
    if (document.data() instanceof DocumentData.NullData) {
      return null;
    }
    throw linkageMismatch("to-one identifier or explicit null", document);
  }

  public static List<ResourceIdentifier> requireToMany(JsonApiDocument document) {
    if (document.data()
        instanceof DocumentData.IdentifierCollection(List<ResourceIdentifier> identifiers)) {
      return identifiers;
    }
    throw linkageMismatch("to-many identifier collection", document);
  }

  public static JsonApiDocument linkageDocument(@Nullable ResourceIdentifier identifier) {
    DocumentData data =
        identifier == null
            ? DocumentData.NullData.INSTANCE
            : new DocumentData.SingleIdentifier(identifier);
    return new JsonApiDocument(data, null, null, null, null, null, Map.of());
  }

  public static JsonApiDocument linkageCollectionDocument(List<ResourceIdentifier> identifiers) {
    Objects.requireNonNull(identifiers, "identifiers");
    return new JsonApiDocument(
        new DocumentData.IdentifierCollection(identifiers), null, null, null, null, null, Map.of());
  }

  private static JsonApiMappingException shapeMismatch(
      @Nullable Class<?> type, String expected, String actual) {
    return new JsonApiMappingException(
        MappingDiagnostic.RESOURCE_TYPE_MISMATCH,
        type,
        DATA_LOCATION,
        "Level-1 read requires " + expected + " primary data but found " + actual);
  }

  private static JsonApiMappingException linkageMismatch(
      String expected, JsonApiDocument document) {
    return new JsonApiMappingException(
        MappingDiagnostic.RESOURCE_TYPE_MISMATCH,
        null,
        DATA_LOCATION,
        "Level-1 relationship read requires "
            + expected
            + " primary data but found "
            + describe(document));
  }

  private static String describe(JsonApiDocument document) {
    if (document.errors() != null) {
      return "an error document";
    }
    return switch (document.data()) {
      case null -> "absent data";
      case DocumentData.NullData ignored -> "explicit null data";
      case DocumentData.SingleResource ignored -> "single-resource data";
      case DocumentData.ResourceCollection ignored -> "resource-collection data";
      case DocumentData.SingleIdentifier ignored -> "single-identifier data";
      case DocumentData.IdentifierCollection ignored -> "identifier-collection data";
    };
  }
}
