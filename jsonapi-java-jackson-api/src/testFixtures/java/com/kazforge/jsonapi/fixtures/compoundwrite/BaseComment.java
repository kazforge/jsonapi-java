package com.kazforge.jsonapi.fixtures.compoundwrite;

import com.kazforge.jsonapi.annotation.JsonApiAttribute;
import com.kazforge.jsonapi.annotation.JsonApiId;
import com.kazforge.jsonapi.annotation.JsonApiRelationship;
import com.kazforge.jsonapi.annotation.JsonApiResource;
import com.kazforge.jsonapi.fixtures.domainwrite.Person;
import org.jspecify.annotations.Nullable;

/** Declared comment owner type for polymorphic include-policy tests. */
@JsonApiResource(type = "comments")
public class BaseComment {

  private @Nullable String id;
  private @Nullable String body;
  private @Nullable Person author;

  public BaseComment() {}

  public BaseComment(@Nullable String id, @Nullable String body, @Nullable Person author) {
    this.id = id;
    this.body = body;
    this.author = author;
  }

  @JsonApiId
  public @Nullable String getId() {
    return id;
  }

  public void setId(@Nullable String id) {
    this.id = id;
  }

  @JsonApiAttribute
  public @Nullable String getBody() {
    return body;
  }

  public void setBody(@Nullable String body) {
    this.body = body;
  }

  @JsonApiRelationship
  public @Nullable Person getAuthor() {
    return author;
  }

  public void setAuthor(@Nullable Person author) {
    this.author = author;
  }
}
