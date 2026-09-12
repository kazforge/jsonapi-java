package com.kazforge.jsonapi.jackson3;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.kazforge.jsonapi.annotation.JsonApiAttribute;
import com.kazforge.jsonapi.annotation.JsonApiId;
import com.kazforge.jsonapi.annotation.JsonApiRelationship;
import com.kazforge.jsonapi.annotation.JsonApiResource;
import java.util.List;

final class DecorationFixtures {

  private DecorationFixtures() {}

  @JsonApiResource(type = "articles")
  public static final class ArticleWithMeta {
    @JsonApiId public String id;
    @JsonApiAttribute public String title;
    @JsonApiRelationship public Person author;
    @JsonApiRelationship public List<Comment> comments;
    @com.kazforge.jsonapi.annotation.JsonApiMeta public ArticleMeta meta;

    public ArticleWithMeta(
        String id, String title, Person author, List<Comment> comments, ArticleMeta meta) {
      this.id = id;
      this.title = title;
      this.author = author;
      this.comments = comments;
      this.meta = meta;
    }
  }

  public static final class ArticleMeta {
    public String source;

    public ArticleMeta(String source) {
      this.source = source;
    }
  }

  @JsonApiResource(type = "articles")
  public static final class RenamedRelationshipArticle {
    @JsonApiId public String id;
    @JsonApiAttribute public String title;

    @JsonApiRelationship
    @JsonProperty("article-comments")
    public List<Comment> comments;

    @JsonApiRelationship public Person author;

    public RenamedRelationshipArticle(
        String id, String title, List<Comment> comments, Person author) {
      this.id = id;
      this.title = title;
      this.comments = comments;
      this.author = author;
    }
  }

  @JsonApiResource(type = "people")
  public static final class Person {
    @JsonApiId public String id;
    @JsonApiAttribute public String name;

    public Person(String id, String name) {
      this.id = id;
      this.name = name;
    }
  }

  @JsonApiResource(type = "comments")
  public static final class Comment {
    @JsonApiId public String id;
    @JsonApiAttribute public String body;
    @JsonApiRelationship public Person author;

    public Comment(String id, String body, Person author) {
      this.id = id;
      this.body = body;
      this.author = author;
    }
  }

  @JsonApiResource(type = "articles")
  public static final class GenericContainer<T> {
    @JsonApiId public String id;
    @JsonApiAttribute public String title;
    @JsonApiRelationship public T related;

    public GenericContainer(String id, String title, T related) {
      this.id = id;
      this.title = title;
      this.related = related;
    }
  }

  @JsonApiResource(type = "things")
  public static final class Thing {
    @JsonApiId public String id;
    @JsonApiAttribute public String name;

    public Thing(String id, String name) {
      this.id = id;
      this.name = name;
    }
  }
}
