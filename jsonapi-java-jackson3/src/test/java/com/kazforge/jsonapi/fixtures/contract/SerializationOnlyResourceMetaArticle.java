package com.kazforge.jsonapi.fixtures.contract;

import com.kazforge.jsonapi.annotation.JsonApiId;
import com.kazforge.jsonapi.annotation.JsonApiMeta;
import com.kazforge.jsonapi.annotation.JsonApiResource;

/**
 * Serialization-only resource-meta declaration for the shared low-level PATCH contract. The
 * annotated getter is the only member, so the configured mapper has no effective deserialization
 * target; an absent {@code meta} must not block a PATCH, and a supplied {@code meta} must fail as
 * non-deserializable at {@code /meta} instead of being validated from a serialization-side type.
 */
@JsonApiResource(type = "serialization-only-meta")
public final class SerializationOnlyResourceMetaArticle {

  @JsonApiId public String id;

  @JsonApiMeta
  public String getMeta() {
    return "derived";
  }
}
