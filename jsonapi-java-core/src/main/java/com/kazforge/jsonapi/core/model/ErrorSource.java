package com.kazforge.jsonapi.core.model;

import com.kazforge.jsonapi.core.internal.AdditionalMembers;
import com.kazforge.jsonapi.core.internal.SyntaxValidators;
import com.kazforge.jsonapi.core.validation.LocalValidation;
import com.kazforge.jsonapi.core.validation.ValidationRuleCode;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Error object {@code source} members: optional JSON Pointer, parameter name, and header name.
 *
 * <p>When present, {@code pointer} must be RFC 6901 JSON Pointer syntax (syntax only; not resolved
 * against a document). See {@code docs/conformance.md}.
 *
 * <p>Use {@link #builder()} for incremental application-facing construction. The builder produces
 * this immutable value and leaves pointer validation and additional-member normalization to this
 * record's compact constructor.
 */
public record ErrorSource(
    @Nullable String pointer,
    @Nullable String parameter,
    @Nullable String header,
    Map<String, @Nullable Object> additionalMembers) {

  private static final Set<String> RESERVED_ADDITIONAL =
      Set.of(JsonApiMembers.POINTER, JsonApiMembers.PARAMETER, JsonApiMembers.HEADER);

  public ErrorSource {
    if (pointer != null && !SyntaxValidators.isValidJsonPointer(pointer)) {
      LocalValidation.fail(
          ValidationRuleCode.INVALID_JSON_POINTER,
          "/errors/source/pointer",
          "Invalid JSON Pointer: " + pointer);
    }
    additionalMembers =
        AdditionalMembers.copy(
            additionalMembers,
            "/errors/source",
            "Invalid error source member name: ",
            RESERVED_ADDITIONAL);
  }

  /** Creates a mutable builder for one error source. */
  public static Builder builder() {
    return new Builder();
  }

  /** Returns a parameter-only error source. */
  public static ErrorSource ofParameter(String parameter) {
    return new ErrorSource(null, parameter, null, Map.of());
  }

  /**
   * Mutable builder for one {@link ErrorSource}.
   *
   * <p>{@link #build()} delegates validation and defensive copying to {@link ErrorSource}; only the
   * built value is immutable.
   */
  public static final class Builder {

    private @Nullable String pointer;
    private @Nullable String parameter;
    private @Nullable String header;
    private final Map<String, @Nullable Object> additionalMembers = new LinkedHashMap<>();

    private Builder() {}

    /** Sets the optional RFC 6901 JSON Pointer, or clears it when {@code null}. */
    public Builder pointer(@Nullable String pointer) {
      this.pointer = pointer;
      return this;
    }

    /** Sets the optional query parameter name, or clears it when {@code null}. */
    public Builder parameter(@Nullable String parameter) {
      this.parameter = parameter;
      return this;
    }

    /** Sets the optional request header name, or clears it when {@code null}. */
    public Builder header(@Nullable String header) {
      this.header = header;
      return this;
    }

    /**
     * Adds or replaces one additional error-source member.
     *
     * <p>Member-name and open-JSON value validation are performed by {@link #build()}.
     */
    public Builder additionalMember(String name, @Nullable Object value) {
      additionalMembers.put(Objects.requireNonNull(name, "name"), value);
      return this;
    }

    /**
     * Adds or replaces additional error-source members. Later values replace an already supplied
     * name.
     *
     * <p>Member-name and open-JSON value validation are performed by {@link #build()}.
     */
    public Builder additionalMembers(Map<String, ?> members) {
      additionalMembers.putAll(Objects.requireNonNull(members, "members"));
      return this;
    }

    /** Builds the immutable error source with the record's existing local validation. */
    public ErrorSource build() {
      return new ErrorSource(pointer, parameter, header, additionalMembers);
    }
  }
}
