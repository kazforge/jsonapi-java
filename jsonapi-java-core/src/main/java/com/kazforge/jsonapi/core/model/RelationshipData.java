package com.kazforge.jsonapi.core.model;

import com.kazforge.jsonapi.core.validation.LocalValidation;
import java.util.List;

/**
 * Sealed relationship linkage preserving explicit null, single, and collection states.
 *
 * @apiNote A Java {@code null} linkage component on {@link Relationship} means the {@code data}
 *     member is absent (link-only or meta-only relationships). {@link NullLinkage} is explicit
 *     empty to-one linkage ({@code "data": null}). Empty {@link IdentifierCollectionLinkage} is
 *     empty to-many linkage and must not be conflated with null or absence.
 */
public sealed interface RelationshipData
    permits RelationshipData.NullLinkage,
        RelationshipData.SingleLinkage,
        RelationshipData.IdentifierCollectionLinkage {

  record NullLinkage() implements RelationshipData {
    public static final NullLinkage INSTANCE = new NullLinkage();
  }

  record SingleLinkage(ResourceIdentifier identifier) implements RelationshipData {
    public SingleLinkage {
      LocalValidation.requireNonNull(
          identifier, "/relationships/data", "Linkage identifier must not be null");
    }
  }

  record IdentifierCollectionLinkage(List<ResourceIdentifier> identifiers)
      implements RelationshipData {
    public IdentifierCollectionLinkage {
      identifiers = LocalValidation.copyRequiredList(identifiers, "/relationships/data");
    }

    public static IdentifierCollectionLinkage empty() {
      return new IdentifierCollectionLinkage(List.of());
    }
  }
}
