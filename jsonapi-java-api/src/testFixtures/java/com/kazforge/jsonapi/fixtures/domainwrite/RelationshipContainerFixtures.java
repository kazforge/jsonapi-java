package com.kazforge.jsonapi.fixtures.domainwrite;

import com.kazforge.jsonapi.annotation.JsonApiAttribute;
import com.kazforge.jsonapi.annotation.JsonApiId;
import com.kazforge.jsonapi.annotation.JsonApiRelationship;
import com.kazforge.jsonapi.annotation.JsonApiResource;
import com.kazforge.jsonapi.core.model.RelationshipData;
import com.kazforge.jsonapi.core.model.ResourceIdentifier;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * Supported relationship and identifier container shapes shared by Jackson-major write suites:
 * {@code Optional} attributes/ids/to-one relationships, to-many arrays, and {@code
 * ResourceIdentifier} collections that may contain leading nulls.
 */
public final class RelationshipContainerFixtures {

  private RelationshipContainerFixtures() {}

  @JsonApiResource(type = "articles")
  public record ArticleWithOptionalAttribute(
      @JsonApiId String id,
      @JsonApiAttribute String title,
      @JsonApiAttribute Optional<String> subtitle) {}

  @JsonApiResource(type = "articles")
  public record ArticleWithOptionalId(
      @JsonApiId Optional<String> id, @JsonApiAttribute String title) {}

  @JsonApiResource(type = "articles")
  public record ArticleWithOptionalRelationship(
      @JsonApiId String id, @JsonApiRelationship Optional<Comment> comment) {}

  /** Direct to-one relationship holding a prebuilt {@link ResourceIdentifier}. */
  @JsonApiResource(type = "articles")
  public record ArticleWithIdentifierRelationship(
      @JsonApiId String id,
      @JsonApiAttribute String title,
      @JsonApiRelationship @Nullable ResourceIdentifier author) {}

  /** To-one relationship carrying a prebuilt core {@link RelationshipData} linkage value. */
  @JsonApiResource(type = "articles")
  public record ArticleWithDirectLinkageData(
      @JsonApiId String id,
      @JsonApiAttribute String title,
      @JsonApiRelationship @Nullable RelationshipData author) {}

  @JsonApiResource(type = "articles")
  @SuppressWarnings({"ArrayRecordComponent", "java:S6218"})
  public record ArticleWithCommentArray(
      @JsonApiId String id,
      @JsonApiAttribute String title,
      @JsonApiRelationship Comment[] comments) {

    @Override
    public boolean equals(Object obj) {
      return obj
              instanceof
              ArticleWithCommentArray(String otherId, String otherTitle, Comment[] otherComments)
          && Objects.equals(id, otherId)
          && Objects.equals(title, otherTitle)
          && Arrays.equals(comments, otherComments);
    }

    @Override
    public int hashCode() {
      return Objects.hash(id, title, Arrays.hashCode(comments));
    }

    @Override
    public String toString() {
      return "ArticleWithCommentArray[id="
          + id
          + ", title="
          + title
          + ", comments="
          + Arrays.toString(comments)
          + "]";
    }
  }

  @JsonApiResource(type = "articles")
  public record ArticleWithNullableIdentifierList(
      @JsonApiId String id, @JsonApiRelationship List<@Nullable ResourceIdentifier> items) {}

  @JsonApiResource(type = "articles")
  @SuppressWarnings({"ArrayRecordComponent", "java:S6218"})
  public record ArticleWithNullableIdentifierArray(
      @JsonApiId String id, @JsonApiRelationship @Nullable ResourceIdentifier[] items) {

    @Override
    public boolean equals(Object obj) {
      return obj
              instanceof
              ArticleWithNullableIdentifierArray(
                  String otherId,
                  @Nullable ResourceIdentifier[] otherItems)
          && Objects.equals(id, otherId)
          && Arrays.equals(items, otherItems);
    }

    @Override
    public int hashCode() {
      return Objects.hash(id, Arrays.hashCode(items));
    }

    @Override
    public String toString() {
      return "ArticleWithNullableIdentifierArray[id="
          + id
          + ", items="
          + Arrays.toString(items)
          + "]";
    }
  }
}
