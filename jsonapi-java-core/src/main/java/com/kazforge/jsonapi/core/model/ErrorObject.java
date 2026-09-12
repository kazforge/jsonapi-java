package com.kazforge.jsonapi.core.model;

import com.kazforge.jsonapi.core.internal.AdditionalMembers;
import com.kazforge.jsonapi.core.validation.LocalValidation;
import com.kazforge.jsonapi.core.validation.ValidationRuleCode;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * JSON:API error object.
 *
 * <p>Use {@link #builder()} for incremental application-facing construction. The builder produces
 * this immutable value and delegates local validation and additional-member normalization to this
 * record's compact constructor. It does not select HTTP status codes or application error
 * taxonomies.
 */
public record ErrorObject(
    @Nullable String id,
    @Nullable Links links,
    @Nullable String status,
    @Nullable String code,
    @Nullable String title,
    @Nullable String detail,
    @Nullable ErrorSource source,
    @Nullable Meta meta,
    Map<String, @Nullable Object> additionalMembers) {

  private static final Set<String> RESERVED_ADDITIONAL =
      Set.of(
          JsonApiMembers.ID,
          JsonApiMembers.LINKS,
          JsonApiMembers.STATUS,
          JsonApiMembers.CODE,
          JsonApiMembers.TITLE,
          JsonApiMembers.DETAIL,
          JsonApiMembers.SOURCE,
          JsonApiMembers.META);

  public ErrorObject {
    boolean hasStandardMember =
        id != null
            || links != null
            || status != null
            || code != null
            || title != null
            || detail != null
            || source != null
            || meta != null;
    additionalMembers =
        AdditionalMembers.copy(
            additionalMembers, "/errors", "Invalid error member name: ", RESERVED_ADDITIONAL);
    if (!hasStandardMember) {
      LocalValidation.fail(
          ValidationRuleCode.MISSING_ERROR_MEMBER,
          "/errors",
          "Error object must contain at least one standard member");
    }
  }

  /** Creates a mutable builder for one error object. */
  public static Builder builder() {
    return new Builder();
  }

  /** Returns a title-only error object. */
  public static ErrorObject ofTitle(String title) {
    return new ErrorObject(null, null, null, null, title, null, null, null, Map.of());
  }

  /**
   * Mutable builder for one {@link ErrorObject}.
   *
   * <p>{@link #build()} delegates validation and defensive copying to {@link ErrorObject}; only the
   * built value is immutable.
   */
  public static final class Builder {

    private @Nullable String id;
    private @Nullable Links links;
    private @Nullable String status;
    private @Nullable String code;
    private @Nullable String title;
    private @Nullable String detail;
    private @Nullable ErrorSource source;
    private @Nullable Meta meta;
    private final Map<String, @Nullable Object> additionalMembers = new LinkedHashMap<>();

    private Builder() {}

    /** Sets the optional error identifier, or clears it when {@code null}. */
    public Builder id(@Nullable String id) {
      this.id = id;
      return this;
    }

    /** Sets the optional error links, or clears them when {@code null}. */
    public Builder links(@Nullable Links links) {
      this.links = links;
      return this;
    }

    /** Sets the optional opaque JSON:API status string, or clears it when {@code null}. */
    public Builder status(@Nullable String status) {
      this.status = status;
      return this;
    }

    /** Sets the optional opaque application error code, or clears it when {@code null}. */
    public Builder code(@Nullable String code) {
      this.code = code;
      return this;
    }

    /** Sets the optional error title, or clears it when {@code null}. */
    public Builder title(@Nullable String title) {
      this.title = title;
      return this;
    }

    /** Sets the optional error detail, or clears it when {@code null}. */
    public Builder detail(@Nullable String detail) {
      this.detail = detail;
      return this;
    }

    /** Sets the optional error source, or clears it when {@code null}. */
    public Builder source(@Nullable ErrorSource source) {
      this.source = source;
      return this;
    }

    /** Sets the optional error meta object, or clears it when {@code null}. */
    public Builder meta(@Nullable Meta meta) {
      this.meta = meta;
      return this;
    }

    /**
     * Adds or replaces one additional error member.
     *
     * <p>Member-name and open-JSON value validation are performed by {@link #build()}.
     */
    public Builder additionalMember(String name, @Nullable Object value) {
      additionalMembers.put(Objects.requireNonNull(name, "name"), value);
      return this;
    }

    /**
     * Adds or replaces additional error members. Later values replace an already supplied name.
     *
     * <p>Member-name and open-JSON value validation are performed by {@link #build()}.
     */
    public Builder additionalMembers(Map<String, ?> members) {
      additionalMembers.putAll(Objects.requireNonNull(members, "members"));
      return this;
    }

    /** Builds the immutable error object with the record's existing local validation. */
    public ErrorObject build() {
      return new ErrorObject(
          id, links, status, code, title, detail, source, meta, additionalMembers);
    }
  }
}
