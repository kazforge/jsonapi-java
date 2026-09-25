package com.kazforge.jsonapi.fixtures.domainpatch;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.kazforge.jsonapi.annotation.JsonApiAttribute;
import com.kazforge.jsonapi.annotation.JsonApiId;
import com.kazforge.jsonapi.annotation.JsonApiMeta;
import com.kazforge.jsonapi.annotation.JsonApiRelationship;
import com.kazforge.jsonapi.annotation.JsonApiRelationshipMeta;
import com.kazforge.jsonapi.annotation.JsonApiResource;
import com.kazforge.jsonapi.core.model.ResourceIdentifier;
import com.kazforge.jsonapi.patch.PatchPresence;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Whole-meta declaration shapes shared across Jackson majors: valid Object/renamed
 * relationship-meta targets, missing-meta PATCH DTOs, and invalid scalar/list/JDK-scalar
 * declarations. Polymorphic {@code TypeDeserializer} targets stay adapter-local.
 */
public final class WholeMetaTargetFixtures {

  private WholeMetaTargetFixtures() {}

  /** Write/low-level model with a Jackson-renamed relationship whose meta follows the property. */
  @JsonApiResource(type = "articles")
  public record RenamedRelationshipMetaArticle(
      @JsonApiId String id,
      @JsonApiAttribute String title,
      @JsonApiRelationship @JsonProperty("author") @Nullable ResourceIdentifier writtenBy,
      @JsonApiMeta @Nullable ArticleMeta meta,
      @JsonApiRelationshipMeta(relationship = "writtenBy") @Nullable AuthorMeta authorMeta) {}

  /** Invalid declaration: two resource meta properties on one mapping. */
  @JsonApiResource(type = "articles")
  public record DuplicateMetaArticle(
      @JsonApiId String id, @JsonApiMeta String meta, @JsonApiMeta String otherMeta) {}

  /** Invalid declaration: two relationship meta properties targeting the same relationship. */
  @JsonApiResource(type = "articles")
  public record DuplicateRelationshipMetaArticle(
      @JsonApiId String id,
      @JsonApiRelationship ResourceIdentifier author,
      @JsonApiRelationshipMeta(relationship = "author") String authorMeta,
      @JsonApiRelationshipMeta(relationship = "author") String authorMeta2) {}

  /** Invalid declaration: empty relationship-meta target identity. */
  @JsonApiResource(type = "articles")
  public record EmptyRelationshipMetaTargetArticle(
      @JsonApiId String id, @JsonApiRelationshipMeta(relationship = "") AuthorMeta authorMeta) {}

  /** Invalid declaration: relationship meta referencing an unmapped relationship. */
  @JsonApiResource(type = "articles")
  public record UnmappedRelationshipMetaArticle(
      @JsonApiId String id,
      @JsonApiRelationshipMeta(relationship = "nonexistent") AuthorMeta authorMeta) {}

  /**
   * Unmapped relationship-meta target on an identity-less resource: target binding fails before
   * resource-level role validation reports the missing identifier.
   */
  @JsonApiResource(type = "articles")
  public record UnmappedRelationshipMetaNoIdArticle(
      @JsonApiRelationshipMeta(relationship = "nonexistent") AuthorMeta authorMeta) {}

  /**
   * Two resource meta properties on an identity-less resource: identity validation fails before the
   * duplicate resource meta is reported.
   */
  @JsonApiResource(type = "articles")
  public record DuplicateMetaNoIdArticle(@JsonApiMeta String meta, @JsonApiMeta String otherMeta) {}

  /**
   * Invalid declaration: two relationship meta properties targeting one externally renamed
   * relationship. The duplicate-target location must use the target's wire name.
   */
  @JsonApiResource(type = "articles")
  public record RenamedDuplicateRelationshipMetaArticle(
      @JsonApiId String id,
      @JsonApiRelationship @JsonProperty("author") @Nullable ResourceIdentifier writtenBy,
      @JsonApiRelationshipMeta(relationship = "writtenBy") @Nullable String authorMeta,
      @JsonApiRelationshipMeta(relationship = "writtenBy") @Nullable String authorMeta2) {}

  /** Invalid declaration: scalar whole-meta target. */
  @JsonApiResource(type = "articles")
  public record ScalarMetaArticle(@JsonApiId String id, @JsonApiMeta String meta) {}

  /** Invalid typed PATCH declaration: PatchPresence wrapping a scalar meta target. */
  @JsonApiResource(type = "articles")
  public record ScalarMetaPatch(
      @JsonApiId String id,
      @JsonApiAttribute PatchPresence<String> title,
      @JsonApiMeta PatchPresence<String> meta) {}

  /** Invalid declaration: list whole-meta target. */
  @JsonApiResource(type = "articles")
  public record ListMetaArticle(@JsonApiId String id, @JsonApiMeta List<String> meta) {}

  /**
   * Object-typed resource meta target: declared-valid, but the runtime value must be map-shaped.
   */
  @JsonApiResource(type = "articles")
  public record ObjectMetaArticle(@JsonApiId String id, @JsonApiMeta Object meta) {}

  /** Typed PATCH model with an explicit {@code PatchPresence<Object>} whole-meta target. */
  @JsonApiResource(type = "articles")
  public record ObjectMetaPatch(@JsonApiId String id, @JsonApiMeta PatchPresence<Object> meta) {}

  /** Invalid declaration: UUID whole-meta target (scalar, not bean-shaped). */
  @JsonApiResource(type = "articles")
  public record UuidMetaArticle(@JsonApiId String id, @JsonApiMeta @Nullable UUID meta) {}

  /** Invalid typed PATCH declaration: PatchPresence wrapping a UUID scalar meta target. */
  @JsonApiResource(type = "articles")
  public record UuidMetaPatch(
      @JsonApiId String id,
      @JsonApiAttribute PatchPresence<String> title,
      @JsonApiMeta PatchPresence<UUID> meta) {}

  /** Invalid declaration: java.time whole-meta target (scalar, not bean-shaped). */
  @JsonApiResource(type = "articles")
  public record InstantMetaArticle(@JsonApiId String id, @JsonApiMeta @Nullable Instant meta) {}

  /** Invalid declaration: URI whole-meta target (scalar, not bean-shaped). */
  @JsonApiResource(type = "articles")
  public record UriMetaArticle(@JsonApiId String id, @JsonApiMeta @Nullable URI meta) {}

  /** Invalid typed PATCH declaration: nested PatchPresence wrapper chain for meta. */
  @JsonApiResource(type = "articles")
  public record NestedPresenceMetaPatch(
      @JsonApiId String id,
      @JsonApiAttribute PatchPresence<String> title,
      @JsonApiMeta PatchPresence<PatchPresence<String>> meta) {}

  /** Typed PATCH DTO without any meta member: supplied meta must be rejected. */
  @JsonApiResource(type = "articles")
  public record NoMetaPatch(
      @JsonApiId String id,
      @JsonApiAttribute PatchPresence<String> title,
      @JsonApiRelationship PatchPresence<ResourceIdentifier> author) {}

  /**
   * Typed PATCH DTO with resource meta but no relationship meta member: supplied rel meta rejected.
   */
  @JsonApiResource(type = "articles")
  public record NoRelMetaPatch(
      @JsonApiId String id,
      @JsonApiAttribute PatchPresence<String> title,
      @JsonApiRelationship PatchPresence<ResourceIdentifier> author,
      @JsonApiMeta PatchPresence<ArticleMetaPatch> meta) {}
}
