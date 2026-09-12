package com.kazforge.jsonapi.jackson3;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.kazforge.jsonapi.annotation.JsonApiAttribute;
import com.kazforge.jsonapi.annotation.JsonApiId;
import com.kazforge.jsonapi.annotation.JsonApiMeta;
import com.kazforge.jsonapi.annotation.JsonApiRelationship;
import com.kazforge.jsonapi.annotation.JsonApiRelationshipMeta;
import com.kazforge.jsonapi.annotation.JsonApiResource;
import com.kazforge.jsonapi.core.model.ResourceIdentifier;
import com.kazforge.jsonapi.jackson.patch.PatchPresence;
import java.util.Objects;

/**
 * Recursive meta conversion probes owned by {@code FlatMetaMappingSpec}: shapes proving that
 * low-level and typed PATCH conversion into a whole-meta target preserves generic JavaType binding,
 * property null providers, property-level polymorphic TypeDeserializers, and reports construction
 * failures at the nested wire pointer (ADR-015).
 */
@SuppressWarnings({"unused", "NullAway"})
public final class MetaConversionProbeFixtures {

  private MetaConversionProbeFixtures() {}

  /** Generic structured value type used to prove whole-meta JavaType binding preservation. */
  public record MetaBox<T>(T value) {}

  /** Low-level domain model with a parameterized generic whole-meta target. */
  @JsonApiResource(type = "articles")
  public record ArticleWithBoxMeta(@JsonApiId String id, @JsonApiMeta MetaBox<Integer> meta) {}

  /** Typed PATCH DTO with a parameterized generic whole-meta target. */
  @JsonApiResource(type = "articles")
  public record ArticleWithBoxMetaPatch(
      @JsonApiId String id,
      @JsonApiAttribute PatchPresence<String> title,
      @JsonApiMeta PatchPresence<MetaBox<Integer>> meta) {}

  /** Low-level domain model whose meta bean carries a property-scoped null provider member. */
  @JsonApiResource(type = "articles")
  public record ArticleWithNullEmptyCityMeta(
      @JsonApiId String id, @JsonApiMeta StructuredRecursionFixtures.OuterWithNullEmptyCity meta) {}

  /**
   * Low-level domain model whose meta bean carries a polymorphic property-level TypeDeserializer
   * member.
   */
  @JsonApiResource(type = "articles")
  public record ArticleWithTypedContactMeta(
      @JsonApiId String id, @JsonApiMeta StructuredRecursionFixtures.OuterWithTypedContact meta) {}

  /** Nested presence-aware whole-meta shape whose member is renamed to a distinct wire name. */
  public record RenamedMetaBeanPatch(
      @JsonProperty("w_source") PatchPresence<String> source, PatchPresence<String> note) {}

  /** Typed PATCH model with a renamed nested whole-meta member. */
  @JsonApiResource(type = "articles")
  public record RenamedNestedMetaPatch(
      @JsonApiId String id, @JsonApiMeta PatchPresence<RenamedMetaBeanPatch> meta) {}

  /**
   * Presence-aware nested whole-meta PATCH shape whose setter throws when a supplied member is
   * present, forcing a genuine Jackson construction failure during the final DTO construction whose
   * deep path must be translated to a wire-name pointer (ADR-015). The {@code source} member's
   * logical name differs from its wire name {@code w_source}.
   */
  public static final class ThrowingMetaPatch {

    private PatchPresence<String> source;
    private PatchPresence<String> note;

    public ThrowingMetaPatch() {}

    public ThrowingMetaPatch(PatchPresence<String> source, PatchPresence<String> note) {
      this.source = source;
      this.note = note;
    }

    @JsonProperty("w_source")
    public PatchPresence<String> getSource() {
      return source;
    }

    public void setSource(PatchPresence<String> source) {
      if (source instanceof PatchPresence.Present) {
        throw new IllegalStateException("boom");
      }
      this.source = source;
    }

    public PatchPresence<String> getNote() {
      return note;
    }

    public void setNote(PatchPresence<String> note) {
      this.note = note;
    }

    @Override
    public boolean equals(Object other) {
      if (!(other instanceof ThrowingMetaPatch that)) {
        return false;
      }
      return Objects.equals(source, that.source) && Objects.equals(note, that.note);
    }

    @Override
    public int hashCode() {
      return Objects.hash(source, note);
    }
  }

  /** Typed PATCH model whose whole-meta member fails during final DTO construction. */
  @JsonApiResource(type = "articles")
  public record ThrowingMetaPatchArticle(
      @JsonApiId String id, @JsonApiMeta PatchPresence<ThrowingMetaPatch> meta) {}

  /**
   * Presence-aware nested relationship-meta PATCH shape whose setter throws when a supplied member
   * is present, forcing a genuine Jackson construction failure during the final DTO construction
   * whose deep path must be translated to a wire-name pointer (ADR-015). The {@code source}
   * member's logical name differs from its wire name {@code w_source}.
   */
  public static final class ThrowingRelMetaPatch {

    private PatchPresence<String> source;
    private PatchPresence<String> note;

    public ThrowingRelMetaPatch() {}

    public ThrowingRelMetaPatch(PatchPresence<String> source, PatchPresence<String> note) {
      this.source = source;
      this.note = note;
    }

    @JsonProperty("w_source")
    public PatchPresence<String> getSource() {
      return source;
    }

    public void setSource(PatchPresence<String> source) {
      if (source instanceof PatchPresence.Present) {
        throw new IllegalStateException("boom");
      }
      this.source = source;
    }

    public PatchPresence<String> getNote() {
      return note;
    }

    public void setNote(PatchPresence<String> note) {
      this.note = note;
    }

    @Override
    public boolean equals(Object other) {
      if (!(other instanceof ThrowingRelMetaPatch that)) {
        return false;
      }
      return Objects.equals(source, that.source) && Objects.equals(note, that.note);
    }

    @Override
    public int hashCode() {
      return Objects.hash(source, note);
    }
  }

  /** Typed PATCH model whose relationship-meta member fails during final DTO construction. */
  @JsonApiResource(type = "articles")
  public record ThrowingRelMetaPatchArticle(
      @JsonApiId String id,
      @JsonApiRelationship PatchPresence<ResourceIdentifier> author,
      @JsonApiRelationshipMeta(relationship = "author")
          PatchPresence<ThrowingRelMetaPatch> authorMeta) {}
}
