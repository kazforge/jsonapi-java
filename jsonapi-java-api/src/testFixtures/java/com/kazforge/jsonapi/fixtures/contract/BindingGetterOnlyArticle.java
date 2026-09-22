package com.kazforge.jsonapi.fixtures.contract;

import com.kazforge.jsonapi.annotation.JsonApiAttribute;
import com.kazforge.jsonapi.annotation.JsonApiId;
import com.kazforge.jsonapi.annotation.JsonApiResource;

/**
 * Getter-only attribute carrier for the shared binding-construction contract: the annotated getter
 * has no effective deserialization target, so a supplied JSON:API member must be rejected as
 * non-deserializable rather than silently discarded.
 */
@JsonApiResource(type = "binding-getter")
public final class BindingGetterOnlyArticle {

  @JsonApiId public String id;

  @JsonApiAttribute
  public String getTitle() {
    return "derived";
  }

  public String idValue() {
    return id;
  }
}
