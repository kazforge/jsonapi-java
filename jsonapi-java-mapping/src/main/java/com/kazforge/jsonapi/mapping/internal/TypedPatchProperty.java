package com.kazforge.jsonapi.mapping.internal;

import java.util.Objects;
import org.jspecify.annotations.NullMarked;

/**
 * Backend-neutral pairing of one adapter-native typed-PATCH DTO property token with its {@link
 * SemanticProperty} metadata and the adapter-resolved facts the shared typed PATCH declaration
 * preflight needs.
 *
 * <p>{@code declaredType} is the member's declared native type token (typically {@code
 * PatchPresence<T>}); {@code patchPresence} is true only for an exact {@code PatchPresence<T>} with
 * one type argument, and {@code wrapperCustomization} is true when wrapper-level
 * {@code @JsonDeserialize}/{@code @JsonSerialize} customization is present. For whole-meta members,
 * {@code validMetaTarget} records the adapter's resolved Bean/Map/Object target verdict. All three
 * are native facts; the shared binder owns the policy that consumes them.
 *
 * <p>This type is unsupported implementation detail for backend cooperation, not consumer SPI, and
 * must not appear in supported backend public signatures.
 *
 * @param <P> opaque backend-native property token
 * @param <T> opaque backend-native type token
 */
@NullMarked
public record TypedPatchProperty<P, T>(
    P token,
    SemanticProperty metadata,
    T declaredType,
    boolean patchPresence,
    boolean wrapperCustomization,
    boolean validMetaTarget) {

  public TypedPatchProperty {
    Objects.requireNonNull(token, "token");
    Objects.requireNonNull(metadata, "metadata");
    Objects.requireNonNull(declaredType, "declaredType");
  }

  public String logicalName() {
    return metadata.logicalName();
  }

  public String externalName() {
    return metadata.externalName();
  }

  public String jsonapiName() {
    return metadata.jsonapiName();
  }
}
