package com.kazforge.jsonapi.fixtures.contract;

import com.kazforge.jsonapi.annotation.JsonApiId;
import com.kazforge.jsonapi.annotation.JsonApiRelationship;
import com.kazforge.jsonapi.annotation.JsonApiResource;
import com.kazforge.jsonapi.core.model.ResourceIdentifier;
import com.kazforge.jsonapi.mapping.RelationshipLinkage;
import org.jspecify.annotations.Nullable;

/**
 * Serialization-only identifier-meta declaration for the shared low-level PATCH contract. The
 * relationship is getter-only and its {@code RelationshipLinkage} identifier-meta type is a scalar,
 * which is an invalid whole-meta target; because the declaration has no effective deserialization
 * target, an absent relationship must not block a PATCH, and a supplied one must fail as
 * non-deserializable at {@code /relationships/author/data}.
 */
@JsonApiResource(type = "serialization-only-id-meta")
public final class SerializationOnlyIdentifierMetaArticle {

  @JsonApiId public String id;

  @JsonApiRelationship
  public @Nullable RelationshipLinkage<ResourceIdentifier, String> getAuthor() {
    return null;
  }
}
