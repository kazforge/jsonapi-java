package com.kazforge.jsonapi.fixtures.domainread;

import com.kazforge.jsonapi.annotation.JsonApiAttribute;
import com.kazforge.jsonapi.annotation.JsonApiId;
import com.kazforge.jsonapi.annotation.JsonApiRelationship;
import com.kazforge.jsonapi.annotation.JsonApiResource;
import com.kazforge.jsonapi.core.model.ResourceIdentifier;
import java.util.Objects;

@JsonApiResource(type = "articles")
public class FlatMutableArticle {

  @JsonApiId private String id;

  private String title;

  @JsonApiRelationship private ResourceIdentifier author;

  public FlatMutableArticle() {}

  public FlatMutableArticle(String id, String title, ResourceIdentifier author) {
    this.id = id;
    this.title = title;
    this.author = author;
  }

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

  public void setTitle(String title) {
    this.title = title;
  }

  public ResourceIdentifier getAuthor() {
    return author;
  }

  public void setAuthor(ResourceIdentifier author) {
    this.author = author;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof FlatMutableArticle other)) {
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
