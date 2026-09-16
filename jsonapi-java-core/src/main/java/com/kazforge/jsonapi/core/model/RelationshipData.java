package com.kazforge.jsonapi.core.model;

import com.kazforge.jsonapi.core.validation.LocalValidation;
import java.util.List;

/**
 * Sealed relationship linkage preserving explicit null, single, and collection states.
 *
 * @apiNote A Java {@code null} linkage component on {@link Relationship} means the {@code data}
 *     member is absent (link-only or meta-only relationships). {@link NullLinkage} is explicit
 *     empty to-one linkage ({@code "data": null}). Empty {@link IdentifierCollectionLinkage} is
 *     empty to-many linkage and must not be conflated with null or absence. These variants preserve
 *     cardinality; aggregate validation applies operation and endpoint policy separately. Single
 *     payloads, collection payloads, and collection elements are required; collections are
 *     immutable snapshots.
 */
public sealed interface RelationshipData
    permits RelationshipData.NullLinkage,
        RelationshipData.SingleLinkage,
        RelationshipData.IdentifierCollectionLinkage {

  /** Explicit JSON {@code null}, representing empty to-one linkage. */
  record NullLinkage() implements RelationshipData {
    public static final NullLinkage INSTANCE = new NullLinkage();
  }

  /** To-one linkage containing one resource identifier. */
  record SingleLinkage(ResourceIdentifier identifier) implements RelationshipData {
    public SingleLinkage {
      LocalValidation.requireNonNull(
          identifier, "/relationships/data", "Linkage identifier must not be null");
    }
  }

  /**
   * To-many linkage containing an immutable snapshot of resource identifiers; an empty list remains
   * present-empty to-many linkage.
   */
  record IdentifierCollectionLinkage(List<ResourceIdentifier> identifiers)
      implements RelationshipData {
    public IdentifierCollectionLinkage {
      identifiers = LocalValidation.copyRequiredList(identifiers, "/relationships/data");
    }

    /** Returns present-empty to-many linkage. */
    public static IdentifierCollectionLinkage empty() {
      return new IdentifierCollectionLinkage(List.of());
    }
  }
}
