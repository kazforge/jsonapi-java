package com.kazforge.jsonapi.fixtures.contract;

import com.kazforge.jsonapi.annotation.JsonApiId;
import com.kazforge.jsonapi.annotation.JsonApiRelationship;
import com.kazforge.jsonapi.annotation.JsonApiRelationshipMeta;
import com.kazforge.jsonapi.annotation.JsonApiResource;
import com.kazforge.jsonapi.core.model.ResourceIdentifier;

/**
 * Serialization-only relationship-meta declaration for the shared low-level PATCH contract. The
 * relationship is a normal bindable member, but its matched {@code authorMeta} declaration is
 * getter-only; an absent relationship meta must not block a PATCH, and a supplied one must fail as
 * non-deserializable at {@code /relationships/author/meta}.
 */
@JsonApiResource(type = "serialization-only-rel-meta")
public final class SerializationOnlyRelationshipMetaArticle {

  @JsonApiId public String id;

  @JsonApiRelationship public ResourceIdentifier author;

  @JsonApiRelationshipMeta(relationship = "author")
  public String getAuthorMeta() {
    return "derived";
  }
}
