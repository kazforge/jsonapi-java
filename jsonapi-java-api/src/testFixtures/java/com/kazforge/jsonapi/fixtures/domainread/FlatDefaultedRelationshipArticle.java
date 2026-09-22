package com.kazforge.jsonapi.fixtures.domainread;

import com.kazforge.jsonapi.annotation.JsonApiAttribute;
import com.kazforge.jsonapi.annotation.JsonApiId;
import com.kazforge.jsonapi.annotation.JsonApiRelationship;
import com.kazforge.jsonapi.annotation.JsonApiResource;
import com.kazforge.jsonapi.core.model.ResourceIdentifier;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Passive, application-shaped read DTO whose attribute and to-one relationship carry non-null Java
 * defaults. It lets a caller observe the read-side distinction between an omitted member (the
 * declared default survives) and a present explicit null (the member binds null).
 */
@JsonApiResource(type = "articles")
public class FlatDefaultedRelationshipArticle {

  @JsonApiId private String id;

  private String title = "default";

  private ResourceIdentifier author = ResourceIdentifier.of("people", "default");

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  @JsonApiAttribute
  public String getTitle() {
    return title;
  }

  public void setTitle(@Nullable String title) {
    this.title = title;
  }

  @JsonApiRelationship
  public @Nullable ResourceIdentifier getAuthor() {
    return author;
  }

  public void setAuthor(@Nullable ResourceIdentifier author) {
    this.author = author;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof FlatDefaultedRelationshipArticle other)) {
      return false;
    }
    return Objects.equals(id, other.id)
        && Objects.equals(title, other.title)
        && Objects.equals(author, other.author);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, title, author);
  }
}
