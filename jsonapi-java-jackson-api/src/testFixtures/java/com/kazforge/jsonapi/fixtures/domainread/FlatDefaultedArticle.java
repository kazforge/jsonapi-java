package com.kazforge.jsonapi.fixtures.domainread;

import com.kazforge.jsonapi.annotation.JsonApiAttribute;
import com.kazforge.jsonapi.annotation.JsonApiId;
import com.kazforge.jsonapi.annotation.JsonApiResource;
import java.util.Objects;

@JsonApiResource(type = "articles")
public class FlatDefaultedArticle {

  @JsonApiId private String id;

  private String title = "default";

  private String body = "default";

  public FlatDefaultedArticle() {}

  public FlatDefaultedArticle(String id, String title, String body) {
    this.id = id;
    this.title = title;
    this.body = body;
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

  @JsonApiAttribute
  public String getBody() {
    return body;
  }

  public void setBody(String body) {
    this.body = body;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof FlatDefaultedArticle other)) {
      return false;
    }
    return Objects.equals(id, other.id)
        && Objects.equals(title, other.title)
        && Objects.equals(body, other.body);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, title, body);
  }
}
