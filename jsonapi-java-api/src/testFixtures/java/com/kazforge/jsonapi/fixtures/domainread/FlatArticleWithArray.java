package com.kazforge.jsonapi.fixtures.domainread;

import com.kazforge.jsonapi.annotation.JsonApiAttribute;
import com.kazforge.jsonapi.annotation.JsonApiId;
import com.kazforge.jsonapi.annotation.JsonApiRelationship;
import com.kazforge.jsonapi.annotation.JsonApiResource;
import com.kazforge.jsonapi.core.model.ResourceIdentifier;
import java.util.Arrays;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Flat read-side DTO with an array-based to-many ResourceIdentifier relationship. Adapter suites
 * compare {@code comments} element-wise because the generated record {@code equals} would compare
 * array components by reference. This override is part of the shared bound-value contract.
 */
@JsonApiResource(type = "articles")
@SuppressWarnings({"ArrayRecordComponent", "java:S6218"})
public record FlatArticleWithArray(
    @JsonApiId String id,
    @JsonApiAttribute @Nullable String title,
    @JsonApiRelationship ResourceIdentifier @Nullable [] comments) {

  public FlatArticleWithArray {
    comments = comments == null ? null : comments.clone();
  }

  @Override
  public ResourceIdentifier @Nullable [] comments() {
    return comments == null ? null : comments.clone();
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other
        instanceof
        FlatArticleWithArray(
            String otherId,
            String otherTitle,
            ResourceIdentifier[] otherComments))) {
      return false;
    }
    return Objects.equals(id, otherId)
        && Objects.equals(title, otherTitle)
        && Arrays.equals(comments, otherComments);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, title, Arrays.hashCode(comments));
  }
}
