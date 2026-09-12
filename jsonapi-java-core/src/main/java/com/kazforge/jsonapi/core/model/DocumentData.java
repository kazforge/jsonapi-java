package com.kazforge.jsonapi.core.model;

import com.kazforge.jsonapi.core.validation.LocalValidation;
import java.util.List;

/**
 * Sealed primary data preserving explicit null, single, and collection states.
 *
 * @apiNote On {@link JsonApiDocument}, a Java {@code null} {@code data} component means the member
 *     is absent. {@link NullData} means the document contains {@code "data": null}. Empty
 *     collections remain collections and never collapse to null or absence.
 */
public sealed interface DocumentData
    permits DocumentData.NullData,
        DocumentData.SingleResource,
        DocumentData.ResourceCollection,
        DocumentData.SingleIdentifier,
        DocumentData.IdentifierCollection {

  record NullData() implements DocumentData {
    public static final NullData INSTANCE = new NullData();
  }

  record SingleResource(ResourceObject resource) implements DocumentData {
    public SingleResource {
      LocalValidation.requireNonNull(resource, path(), "Resource payload must not be null");
    }
  }

  record ResourceCollection(List<ResourceObject> resources) implements DocumentData {
    public ResourceCollection {
      resources = LocalValidation.copyRequiredList(resources, path());
    }
  }

  record SingleIdentifier(ResourceIdentifier identifier) implements DocumentData {
    public SingleIdentifier {
      LocalValidation.requireNonNull(identifier, path(), "Identifier payload must not be null");
    }
  }

  record IdentifierCollection(List<ResourceIdentifier> identifiers) implements DocumentData {
    public IdentifierCollection {
      identifiers = LocalValidation.copyRequiredList(identifiers, path());
    }
  }

  private static String path() {
    return "/data";
  }
}
