package com.kazforge.jsonapi.jackson.mapping;

import com.kazforge.jsonapi.core.model.ResourceIdentifier;
import java.util.List;
import java.util.Objects;

/**
 * Primary data of a domain document envelope, preserving explicit null, single, and collection
 * states.
 *
 * <p>A Java {@code null} {@code data} component on the envelope means the member is absent; {@link
 * NullData} means the document contains {@code "data": null}. Resource payloads are {@link Object}
 * because primary collections may be heterogeneous; callers cast using their resource-type registry
 * registrations. Identifier variants pass through the core {@link ResourceIdentifier} values
 * without DTO binding.
 */
public sealed interface DomainData
    permits DomainData.NullData,
        DomainData.SingleResource,
        DomainData.ResourceCollection,
        DomainData.SingleIdentifier,
        DomainData.IdentifierCollection {

  record NullData() implements DomainData {
    public static final NullData INSTANCE = new NullData();
  }

  /** One bound resource DTO as primary data. */
  record SingleResource(Object resource) implements DomainData {
    public SingleResource {
      Objects.requireNonNull(resource, "resource");
    }
  }

  /** A collection of bound resource DTOs in wire order; defensive copy with unmodifiable view. */
  record ResourceCollection(List<Object> resources) implements DomainData {
    public ResourceCollection {
      resources = List.copyOf(resources);
    }
  }

  /** One core identifier as primary data; never DTO-bound. */
  record SingleIdentifier(ResourceIdentifier identifier) implements DomainData {
    public SingleIdentifier {
      Objects.requireNonNull(identifier, "identifier");
    }
  }

  /** A collection of core identifiers in wire order; defensive copy with unmodifiable view. */
  record IdentifierCollection(List<ResourceIdentifier> identifiers) implements DomainData {
    public IdentifierCollection {
      identifiers = List.copyOf(identifiers);
    }
  }
}
