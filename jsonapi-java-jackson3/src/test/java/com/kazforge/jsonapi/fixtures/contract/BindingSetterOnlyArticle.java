package com.kazforge.jsonapi.fixtures.contract;

import com.kazforge.jsonapi.annotation.JsonApiAttribute;
import com.kazforge.jsonapi.annotation.JsonApiId;
import com.kazforge.jsonapi.annotation.JsonApiResource;

/**
 * Setter-only attribute carrier for the shared binding-construction contract. The annotated setter
 * is the effective deserialization target; the non-bean-named {@code titleValue} observation
 * accessor avoids turning the property into a serialization accessor.
 */
@JsonApiResource(type = "binding-setter")
public final class BindingSetterOnlyArticle {

  @JsonApiId public String id;

  private String title;

  @JsonApiAttribute
  public void setTitle(String title) {
    this.title = title;
  }

  public String idValue() {
    return id;
  }

  public String titleValue() {
    return title;
  }
}
