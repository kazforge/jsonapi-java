package com.kazforge.jsonapi.gsonpoc;

import com.google.gson.annotations.SerializedName;
import com.kazforge.jsonapi.annotation.JsonApiAttribute;
import com.kazforge.jsonapi.annotation.JsonApiId;
import com.kazforge.jsonapi.annotation.JsonApiResource;

@JsonApiResource(type = "named-articles")
final class GsonNamingFixture {

  @JsonApiId private final String id;

  @JsonApiAttribute
  @SerializedName("headline")
  private final String title;

  GsonNamingFixture(String id, String title) {
    this.id = id;
    this.title = title;
  }
}
