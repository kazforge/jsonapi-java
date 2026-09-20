package com.kazforge.jsonapi.fixtures.enveloperead;

import com.kazforge.jsonapi.annotation.JsonApiAttribute;
import com.kazforge.jsonapi.annotation.JsonApiId;
import com.kazforge.jsonapi.annotation.JsonApiResource;
import org.jspecify.annotations.Nullable;

/** Flat read-side DTO whose canonical constructor rejects one attribute value. */
@JsonApiResource(type = "throwing-articles")
public record FlatThrowingArticle(@JsonApiId String id, @JsonApiAttribute @Nullable String title) {

  public FlatThrowingArticle {
    if (title != null && title.equals("boom")) {
      throw new IllegalArgumentException("creator rejected value");
    }
  }
}
