package com.kazforge.jsonapi.fixtures.domainread;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.kazforge.jsonapi.annotation.JsonApiAttribute;
import com.kazforge.jsonapi.annotation.JsonApiId;
import com.kazforge.jsonapi.annotation.JsonApiResource;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

@JsonApiResource(type = "things")
public final class FlatThrowingCreatorThing {

  private final @Nullable String id;
  private final @Nullable String title;

  @JsonCreator
  public FlatThrowingCreatorThing(
      @JsonProperty("id") @JsonApiId @Nullable String id,
      @JsonProperty("title") @JsonApiAttribute @Nullable String title) {
    if ("boom".equals(title)) {
      throw new IllegalArgumentException("creator rejected value");
    }
    this.id = id;
    this.title = title;
  }

  public @Nullable String getId() {
    return id;
  }

  public @Nullable String getTitle() {
    return title;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof FlatThrowingCreatorThing other)) {
      return false;
    }
    return Objects.equals(id, other.id) && Objects.equals(title, other.title);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, title);
  }
}
