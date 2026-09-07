package io.github.kazemek.jsonapi.jackson2;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.kazemek.jsonapi.annotation.JsonApiAttribute;
import io.github.kazemek.jsonapi.annotation.JsonApiId;
import io.github.kazemek.jsonapi.annotation.JsonApiLocalId;
import io.github.kazemek.jsonapi.annotation.JsonApiResource;

/**
 * Adapter-owned shapes for configured-Jackson local-identifier coverage. Owned by {@code
 * LocalIdentifierMappingSpec}; adapter-local Jackson 2 copy of the shared shapes (write direction).
 */
@SuppressWarnings({"unused", "NullAway"})
public final class LocalIdFixtures {

  private LocalIdFixtures() {}

  /**
   * Renamed local-id property: configured Jackson naming stays, the wire member stays {@code lid}.
   */
  @JsonApiResource(type = "renamed-lids")
  public record RenamedLocalIdArticle(
      @JsonApiId String id,
      @JsonProperty("wire-local-id") @JsonApiLocalId String localId,
      @JsonApiAttribute String title) {}

  /** Local-id role named by the configured naming strategy rather than an explicit rename. */
  @JsonApiResource(type = "snake-lids")
  public record SnakeCaseLocalIdArticle(
      @JsonApiId String id, @JsonApiLocalId String localId, @JsonApiAttribute String title) {}

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

  /** Mix-in supplying the local-id role annotation for {@link MixinLocalIdArticle}. */
  public abstract static class LocalIdMixIn {

    @JsonApiLocalId
    public abstract String getLocalId();
  }
}
