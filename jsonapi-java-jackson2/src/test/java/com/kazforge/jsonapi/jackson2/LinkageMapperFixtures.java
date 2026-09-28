package com.kazforge.jsonapi.jackson2;

import com.kazforge.jsonapi.annotation.JsonApiAttribute;
import com.kazforge.jsonapi.annotation.JsonApiId;
import com.kazforge.jsonapi.annotation.JsonApiRelationship;
import com.kazforge.jsonapi.annotation.JsonApiResource;
import com.kazforge.jsonapi.jackson2.mapping.RelationshipLinkageMapper;
import java.util.List;
import java.util.Optional;

/**
 * Fixture family for {@link RelationshipLinkageMapper} mechanics across the adapter entry points
 * that accept registered mappers. Owned by {@code ResourceBinderSpec} and {@code
 * PatchCommandBindingSpec}; these shapes exist to prove mapper registration/dispatch behavior, not
 * shared wire semantics.
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
