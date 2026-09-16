package com.kazforge.jsonapi.core.model;

import com.kazforge.jsonapi.core.validation.LocalValidation;
import java.util.List;

/**
 * Sealed primary data preserving explicit null, single, and collection states.
 *
 * @apiNote On {@link JsonApiDocument}, a Java {@code null} {@code data} component means the member
 *     is absent. {@link NullData} means the document contains {@code "data": null}. Empty
 *     collections remain collections and never collapse to null or absence. These variants preserve
 *     wire shape; aggregate validation decides which shapes are valid for an operation and endpoint
 *     role. Single payloads, collection payloads, and collection elements are required; collections
 *     are immutable snapshots.
 */
public sealed interface DocumentData
    permits DocumentData.NullData,
        DocumentData.SingleResource,
        DocumentData.ResourceCollection,
        DocumentData.SingleIdentifier,
        DocumentData.IdentifierCollection {

  /** Explicit JSON {@code null} primary data. */
  record NullData() implements DocumentData {
    public static final NullData INSTANCE = new NullData();
  }

  /** Primary data containing one resource object. */
  record SingleResource(ResourceObject resource) implements DocumentData {
    public SingleResource {
      LocalValidation.requireNonNull(resource, path(), "Resource payload must not be null");
    }
  }

  /**
   * Primary data containing an immutable snapshot of resource objects, including a present-empty
   * collection.
   */
  record ResourceCollection(List<ResourceObject> resources) implements DocumentData {
    public ResourceCollection {
      resources = LocalValidation.copyRequiredList(resources, path());
    }
  }

  /** Primary data containing one resource identifier. */
  record SingleIdentifier(ResourceIdentifier identifier) implements DocumentData {
    public SingleIdentifier {
      LocalValidation.requireNonNull(identifier, path(), "Identifier payload must not be null");
    }
  }

  /**
   * Primary data containing an immutable snapshot of resource identifiers, including a
   * present-empty collection.
   */
  record IdentifierCollection(List<ResourceIdentifier> identifiers) implements DocumentData {
    public IdentifierCollection {
      identifiers = LocalValidation.copyRequiredList(identifiers, path());
    }
  }

  private static String path() {
    return "/data";
  }
}
