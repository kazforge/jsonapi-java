package com.kazforge.jsonapi.fixtures.domainread;

import com.kazforge.jsonapi.annotation.JsonApiAttribute;
import com.kazforge.jsonapi.annotation.JsonApiId;
import com.kazforge.jsonapi.annotation.JsonApiResource;
import java.util.Objects;

@JsonApiResource(type = "things")
public class FlatCountedThing {

  @JsonApiId private String id;

  private int count;

  public FlatCountedThing() {}

  public FlatCountedThing(String id, int count) {
    this.id = id;
    this.count = count;
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  @JsonApiAttribute
  public int getCount() {
    return count;
  }

  public void setCount(int count) {
    this.count = count;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof FlatCountedThing other)) {
      return false;
    }
    return count == other.count && Objects.equals(id, other.id);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, count);
  }
}
