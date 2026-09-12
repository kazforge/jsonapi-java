package com.kazforge.jsonapi.fixtures.sparsefieldset;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.kazforge.jsonapi.annotation.JsonApiAttribute;
import com.kazforge.jsonapi.annotation.JsonApiId;
import com.kazforge.jsonapi.annotation.JsonApiRelationship;
import com.kazforge.jsonapi.annotation.JsonApiResource;
import com.kazforge.jsonapi.fixtures.domainwrite.Comment;
import com.kazforge.jsonapi.fixtures.domainwrite.Person;
import java.util.List;

/**
 * Mutable article that counts attribute and relationship getter reads for sparse-fieldset
 * access-split assertions.
 */
@JsonApiResource(type = "articles")
public final class AccessCountingFieldsetArticle {

  @JsonApiId public final String id;
  private final String title;
  private final String body;
  private final Person author;
  private final List<Comment> comments;
  public int titleReads;
  public int bodyReads;
  public int authorReads;
  public int commentsReads;

  public AccessCountingFieldsetArticle(
      String id, String title, String body, Person author, List<Comment> comments) {
    this.id = id;
    this.title = title;
    this.body = body;
    this.author = author;
    this.comments = comments;
  }

  @JsonApiAttribute
  public String getTitle() {
    titleReads++;
    return title;
  }

  @JsonApiAttribute
  @JsonProperty("body-text")
  public String getBody() {
    bodyReads++;
    return body;
  }

  @JsonApiRelationship
  public Person getAuthor() {
    authorReads++;
    return author;
  }

  @JsonApiRelationship
  public List<Comment> getComments() {
    commentsReads++;
    return comments;
  }
}
