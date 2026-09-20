package com.kazforge.jsonapi.mapping.contract;

import com.kazforge.jsonapi.annotation.JsonApiAttribute;
import com.kazforge.jsonapi.annotation.JsonApiId;
import com.kazforge.jsonapi.annotation.JsonApiLocalId;
import com.kazforge.jsonapi.annotation.JsonApiRelationship;
import com.kazforge.jsonapi.annotation.JsonApiResource;
import com.kazforge.jsonapi.core.model.ResourceIdentifier;
import java.util.List;
import java.util.Map;

/** Shared application-shaped fixtures for JSON-library mapping backend contract tests. */
public final class MappingContractFixtures {

  private MappingContractFixtures() {}

  @JsonApiResource(type = "drafts")
  public record Draft(@JsonApiLocalId String localId, @JsonApiAttribute String title) {}

  @JsonApiResource(type = "open-values")
  public record OpenValues(
      @JsonApiId String id,
      @JsonApiAttribute List<Object> items,
      @JsonApiAttribute Map<String, Object> object) {}

  @JsonApiResource(type = "open-values")
  public record OpenValueResource(
      @JsonApiId String id, @JsonApiAttribute Map<String, Object> payload) {}

  @JsonApiResource(type = "people")
  public record Person(@JsonApiId String id, @JsonApiAttribute String name) {}

  @JsonApiResource(type = "comments")
  public record Comment(@JsonApiId String id, @JsonApiAttribute String body) {}

  @JsonApiResource(type = "articles")
  public record Article(
      @JsonApiId String id,
      @JsonApiAttribute String title,
      @JsonApiRelationship Person author,
      @JsonApiRelationship List<Comment> comments) {}

  @JsonApiResource(type = "articles")
  public static final class BoundArticle {
    @JsonApiId public String id;
    @JsonApiAttribute public String title;
    @JsonApiRelationship public ResourceIdentifier author;
    @JsonApiRelationship public List<ResourceIdentifier> comments;

    public BoundArticle() {}
  }

  @JsonApiResource(type = "drafts")
  public static final class BoundDraft {
    @JsonApiLocalId public String localId;
    @JsonApiAttribute public String title;

    public BoundDraft() {}
  }
}
