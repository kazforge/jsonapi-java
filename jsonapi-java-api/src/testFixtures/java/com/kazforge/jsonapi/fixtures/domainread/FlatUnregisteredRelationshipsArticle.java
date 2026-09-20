package com.kazforge.jsonapi.fixtures.domainread;

import com.kazforge.jsonapi.annotation.JsonApiId;
import com.kazforge.jsonapi.annotation.JsonApiRelationship;
import com.kazforge.jsonapi.annotation.JsonApiResource;
import com.kazforge.jsonapi.fixtures.domainwrite.Comment;
import com.kazforge.jsonapi.fixtures.domainwrite.Person;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Article DTO whose relationship property types are deliberately not registered as bindable
 * relationship targets: {@code author} is an unregistered to-one target and {@code comments} is an
 * unregistered to-many target. Scenarios bind documents containing only one of the two
 * relationships at a time and assert {@code UNSUPPORTED_RELATIONSHIP_TARGET} at that relationship's
 * data pointer.
 */
@JsonApiResource(type = "articles")
public class FlatUnregisteredRelationshipsArticle {

  @JsonApiId private @Nullable String id;

  @JsonApiRelationship private @Nullable Person author;

  @JsonApiRelationship private @Nullable List<Comment> comments;

  public FlatUnregisteredRelationshipsArticle() {}

  public FlatUnregisteredRelationshipsArticle(
      @Nullable String id, @Nullable Person author, @Nullable List<Comment> comments) {
    this.id = id;
    this.author = author;
    this.comments = comments;
  }

  public @Nullable String getId() {
    return id;
  }

  public void setId(@Nullable String id) {
    this.id = id;
  }

  public @Nullable Person getAuthor() {
    return author;
  }

  public void setAuthor(@Nullable Person author) {
    this.author = author;
  }

  public @Nullable List<Comment> getComments() {
    return comments;
  }

  public void setComments(@Nullable List<Comment> comments) {
    this.comments = comments;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof FlatUnregisteredRelationshipsArticle other)) {
      return false;
    }
    return Objects.equals(id, other.id)
        && Objects.equals(author, other.author)
        && Objects.equals(comments, other.comments);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, author, comments);
  }
}
