package com.kazforge.jsonapi.jackson3;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.kazforge.jsonapi.annotation.JsonApiAttribute;
import com.kazforge.jsonapi.annotation.JsonApiId;
import com.kazforge.jsonapi.annotation.JsonApiLocalId;
import com.kazforge.jsonapi.annotation.JsonApiResource;

/**
 * Adapter-owned shapes for local-identifier read directionality and configured-Jackson coverage.
 * Owned by {@code LocalIdentifierMappingSpec}; each nested shape isolates one mapping mechanic.
 */
@SuppressWarnings({"unused", "NullAway"})
public final class LocalIdFixtures {

  private LocalIdFixtures() {}

  /** Id-role-only DTO: a supplied wire lid has no local-id role to bind and must be ignored. */
  @JsonApiResource(type = "id-only")
  public static final class IdOnlyArticle {

    @JsonApiId public String id;

    @JsonApiAttribute public String title;
  }

  /** Getter-only local-id role: a supplied wire lid fails at {@code /lid}. */
  @JsonApiResource(type = "getter-only-lid")
  public static final class GetterOnlyLocalId {

    @JsonApiId public String id;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @JsonApiLocalId
    public String getLocalId() {
      return "derived";
    }
  }

  /**
   * Renamed local-id property: configured Jackson naming stays, the wire member stays {@code lid}.
   */
  @JsonApiResource(type = "renamed-lids")
  public record RenamedLocalIdArticle(
      @JsonApiId String id,
      @JsonProperty("wire-local-id") @JsonApiLocalId String localId,
      @JsonApiAttribute String title) {}

  /** Local-id role whose annotation comes only from a class-level mix-in. */
  @JsonApiResource(type = "mixin-lids")
  public static final class MixinLocalIdArticle {

    private String id;
    private String localId;

    public MixinLocalIdArticle() {}

    public MixinLocalIdArticle(String id, String localId) {
      this.id = id;
      this.localId = localId;
    }

    public String getId() {
      return id;
    }

    public void setId(String id) {
      this.id = id;
    }

    public String getLocalId() {
      return localId;
    }

    public void setLocalId(String localId) {
      this.localId = localId;
    }
  }

  /** Local-id role named by the configured naming strategy rather than an explicit rename. */
  @JsonApiResource(type = "snake-lids")
  public record SnakeCaseLocalIdArticle(
      @JsonApiId String id, @JsonApiLocalId String localId, @JsonApiAttribute String title) {}

  /** Mix-in supplying the local-id role annotation for {@link MixinLocalIdArticle}. */
  public abstract static class LocalIdMixIn {

    @JsonApiLocalId
    public abstract String getLocalId();
  }
}
