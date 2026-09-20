package com.kazforge.jsonapi.fixtures.domainread;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.kazforge.jsonapi.annotation.JsonApiAttribute;
import com.kazforge.jsonapi.annotation.JsonApiId;
import com.kazforge.jsonapi.annotation.JsonApiResource;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

@JsonApiResource(type = "things")
public final class FlatRequiredThing {

  private final @Nullable String id;
  private final @Nullable String required;

  @JsonCreator
  public FlatRequiredThing(
      @JsonProperty("id") @JsonApiId @Nullable String id,
      @JsonProperty(value = "required", required = true) @JsonApiAttribute
          @Nullable String required) {
    this.id = id;
    this.required = required;
  }

  public @Nullable String getId() {
    return id;
  }

  public @Nullable String getRequired() {
    return required;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof FlatRequiredThing other)) {
      return false;
    }
    return Objects.equals(id, other.id) && Objects.equals(required, other.required);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, required);
  }
}
