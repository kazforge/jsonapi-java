package io.github.kazemek.jsonapi.jackson2;

import io.github.kazemek.jsonapi.annotation.JsonApiAttribute;
import io.github.kazemek.jsonapi.annotation.JsonApiId;
import io.github.kazemek.jsonapi.annotation.JsonApiRelationship;
import io.github.kazemek.jsonapi.annotation.JsonApiResource;
import java.util.List;
import java.util.Optional;

/**
 * Fixture family for {@link RelationshipLinkageMapper} mechanics for the flat DTO resource binder.
 * Owned by {@code ResourceBinderSpec}; these shapes exist to prove mapper registration/dispatch
 * behavior, not shared wire semantics.
 */
public final class LinkageMapperFixtures {

  private LinkageMapperFixtures() {}

  /** Value target for a registered RelationshipLinkageMapper (not a built-in identifier shape). */
  public record FlatAuthor(String type, String id) {}

  /** Flat read-side DTO whose relationships target a custom mapper type. */
  @JsonApiResource(type = "articles")
  public record FlatMappedArticle(
      @JsonApiId String id,
      @JsonApiAttribute String title,
      @JsonApiRelationship FlatAuthor author,
      @JsonApiRelationship List<FlatAuthor> contributors) {}

  /** Variant of {@link FlatMappedArticle} whose to-one member is Optional-wrapped. */
  @JsonApiResource(type = "articles")
  public record FlatMappedOptionalArticle(
      @JsonApiId String id,
      @JsonApiRelationship Optional<FlatAuthor> author,
      @JsonApiRelationship List<FlatAuthor> contributors) {}
}
