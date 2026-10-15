package com.kazforge.jsonapi.fixtures.domainread;

import com.kazforge.jsonapi.annotation.JsonApiAttribute;
import com.kazforge.jsonapi.annotation.JsonApiResource;
import java.util.Objects;

@JsonApiResource(type = "blogs")
public class FlatInheritedBlog extends FlatBlogBase {

  private String description;

  public FlatInheritedBlog() {}

  public FlatInheritedBlog(String id, String name, String description) {
    super(id, name);
    this.description = description;
  }

  @JsonApiAttribute
  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    FlatInheritedBlog other = (FlatInheritedBlog) obj;
    return Objects.equals(getId(), other.getId())
        && Objects.equals(getName(), other.getName())
        && Objects.equals(description, other.description);
  }

  @Override
  public int hashCode() {
    return Objects.hash(getId(), getName(), description);
  }
}
