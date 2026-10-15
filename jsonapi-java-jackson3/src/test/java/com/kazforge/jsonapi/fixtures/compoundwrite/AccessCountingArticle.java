package com.kazforge.jsonapi.fixtures.compoundwrite;

import com.kazforge.jsonapi.annotation.JsonApiId;
import com.kazforge.jsonapi.annotation.JsonApiRelationship;
import com.kazforge.jsonapi.annotation.JsonApiResource;
import com.kazforge.jsonapi.fixtures.domainwrite.Comment;
import com.kazforge.jsonapi.fixtures.domainwrite.Person;
import java.util.List;

/** Mutable article that counts relationship getter reads for traversal-scoped assertions. */
@JsonApiResource(type = "articles")
public final class AccessCountingArticle {

  @JsonApiId public final String id;
  private final Person author;
  private final List<Comment> comments;
  public int authorReads;
  public int commentsReads;

  public AccessCountingArticle(String id, Person author, List<Comment> comments) {
    this.id = id;
    this.author = author;
    this.comments = comments;
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
